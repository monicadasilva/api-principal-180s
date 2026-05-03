# Scaling Plan API Principal 180 Seguros

## Diagnóstico atual

| Camada            | Limite hoje                            | Risco sob carga                                |
| ----------------- | -------------------------------------- | ---------------------------------------------- |
| Jetty thread pool | ~200 threads simultâneas               | Esgota com picos ou seguradora lenta           |
| HikariCP          | 10 conexões ao Postgres                | Fila e timeout sob contenção                   |
| Worker de retry   | Sem `SKIP LOCKED`                      | Race condition com múltiplos pods              |
| Token JWT         | Cache em memória por processo          | Múltiplos pods buscam tokens independentemente |
| Seguradora        | Retry com backoff, sem circuit breaker | Instabilidade propaga para o cliente           |
| `POST /policies`  | Síncrono end-to-end                    | Latência da seguradora = latência do cliente   |

---

## Fase 1 Fundação (pré-requisito para escala horizontal)

**Objetivo:** tornar a aplicação segura para rodar com N pods sem comportamento incorreto.

### 1.1 `FOR UPDATE SKIP LOCKED` no worker

Sem isso, dois pods disputam a mesma linha de `pending_policy_saves`.

```sql
-- pending_policy_repo.clj list-all
SELECT * FROM pending_policy_saves
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT 10
```

O `LIMIT` evita que um pod tente processar toda a fila sozinho num burst.

### 1.2 Virtual threads no Jetty

Elimina o limite de threads sem mudar a arquitetura. Cada request ganha sua própria virtual thread; bloqueio na seguradora libera o carrier thread para outras.

```clojure
;; http_server.clj
(jetty/run-jetty handler {:port port :join? false
                          :virtual-threads? true})
```

Requer Java 21 (já disponível). Com virtual threads, o gargalo passa a ser o DB e a seguradora não mais o pool de threads.

### 1.3 Ajuste do HikariCP por pod

Com N pods e `pool-size 10`, o Postgres recebe `N * 10` conexões. Calibrar antes de escalar:

```edn
;; system.edn
:db/pool {:pool-size 20            ; conexões por pod
          :connection-timeout 3000 ; ms antes de lançar exceção
          :max-lifetime 1800000}   ; recicla conexões a cada 30min
```

Regra prática: `pool-size = max(5, (núcleos_postgres * 2) / N_pods)`.

O floor de 5 evita pools inviáveis com muitos pods — por exemplo, 4 núcleos e 8 pods resultariam em 1 conexão por pod sem esse mínimo.

### 1.4 Cache de JWT da seguradora no Redis

O JWT hoje é memoizado em memória por processo. Com N pods, cada um busca e renova o token de forma independente, multiplicando desnecessariamente as chamadas de autenticação à seguradora.

Solução: armazenar o token no Redis com TTL nativo, compartilhado entre todos os pods:

```clojure
;; insurer_token_cache.clj  (biblioteca: carmine)
(defn fetch-valid [redis-conn]
  (car/wcar redis-conn (car/get "insurer:jwt")))

(defn store! [redis-conn token ttl-seconds]
  ;; folga de 5 min para não usar token prestes a expirar
  (car/wcar redis-conn
    (car/setex "insurer:jwt" (- ttl-seconds 300) token)))
```

```clojure
;; system.edn
:cache/redis {:host #env REDIS_HOST
              :port 6379}
```

O Redis é preferível ao banco aqui porque o TTL é nativo (não precisa de coluna `expires_at` nem query condicional), a leitura é O(1) sem pressão no pool de conexões do Postgres, e a semântica de cache é explícita. A memoização local pode ser mantida como cache de primeiro nível (evita round-trip ao Redis em cada request), com fallback para o Redis quando expirada.

### 1.5 PgBouncer na frente do Postgres

Com múltiplos pods, o PgBouncer multiplexa N×pool-size conexões lógicas em poucas conexões reais ao Postgres que suporta ~300 antes de degradar.

```
Pods (N × 20 conexões lógicas) → PgBouncer → Postgres (20–40 conexões reais)
```

Modo `transaction` é suficiente para esse padrão de uso (next.jdbc sem transações longas abertas).

---

## Fase 2 Resiliência da seguradora

**Objetivo:** instabilidade da seguradora não propaga para o cliente nem derruba o pod.

### 2.1 Circuit breaker

O retry atual (2 tentativas, backoff exponencial) é suficiente para instabilidades curtas. Para instabilidades prolongadas, um circuit breaker evita que todas as threads fiquem presas esperando timeout:

```
CLOSED → falhas acima do threshold → OPEN (falha rápida por N segundos)
       ← sucesso na probe           ← HALF-OPEN (testa uma chamada real)
```

Biblioteca: [resilience4clj](https://github.com/resilience4clj/resilience4clj-circuitbreaker) ou implementação simples com atom + timestamp.

O circuit breaker protege apenas as chamadas à seguradora, não os endpoints que leem dados locais. `GET /policies` lê do banco local e continua respondendo 200 mesmo com o circuit aberto.

Estados e comportamento por endpoint:

| Estado    | `POST /quotes` | `POST /policies` | `GET /policies`         |
| --------- | -------------- | ---------------- | ----------------------- |
| OPEN      | 503 imediato   | 503 imediato     | 200 (leitura local)     |
| HALF-OPEN | testa          | testa            | 200 (leitura local)     |

### 2.2 Timeout agressivo por operação

O hato já suporta timeout por request. Definir explicitamente para não herdar o default do JVM:

```clojure
;; client.clj
{:connect-timeout 2000   ; ms para estabelecer conexão
 :request-timeout 10000} ; ms para receber resposta completa
```

Sem timeout explícito, uma seguradora travada prende threads indefinidamente.

### 2.3 `POST /policies` assíncrono (202 pattern)

É a mudança arquitetural mais impactante para desacoplar da seguradora.

**Fluxo atual (síncrono):**

```
Cliente → POST /policies → [valida → seguradora → salva] → 200/422
                                  ↑ cliente espera tudo isso
```

**Fluxo proposto (assíncrono):**

```
Cliente → POST /policies → [valida] → 202 {"job_id": "uuid"}
                                           ↓ (background)
                                    seguradora → salva → job status = done
Cliente → GET /policies/jobs/:job_id → {"status": "done", "policy_id": "uuid"}
```

Vantagens:

- Latência do cliente cai para o tempo de validação local (~5ms)
- Instabilidade da seguradora não afeta o SLA do endpoint
- Fila de jobs absorve picos sem pressão no thread pool

Custo: muda o contrato da API — clientes precisam implementar polling ou webhook.

**Implementação nesta fase (pré-RabbitMQ):** reutiliza a tabela `pending_policy_saves` existente. O `POST /policies` insere uma linha com status `pending` e retorna 202; o worker existente processa a fila e atualiza para `done`. O endpoint `GET /policies/jobs/:job_id` consulta essa tabela. A Fase 5 substitui esse mecanismo por RabbitMQ sem alterar o contrato externo da API.

---

## Fase 3 Observabilidade para escala

**Objetivo:** saber onde está o gargalo antes de escalar às cegas.

### 3.1 Métricas por endpoint

Adicionar ao `wrap-logging`:

- Latência p50/p95/p99 por rota
- Taxa de erro por rota e por status code
- Latência da seguradora separada da latência total

```clojure
;; wrap-logging estender com tempo de resposta
(let [start    (System/currentTimeMillis)
      response (handler request)
      elapsed  (- (System/currentTimeMillis) start)]
  (t/log! :info {:method  (name request-method)
                 :uri     uri
                 :status  (:status response)
                 :elapsed elapsed})
  response)
```

### 3.2 Health checks distintos para Kubernetes

O `/health` atual verifica o DB. Kubernetes precisa de dois endpoints separados:

```
GET /health/live   → 200 sempre que o processo está vivo (liveness probe)
GET /health/ready  → 200 quando DB conectado e worker rodando (readiness probe)
```

Separar os dois evita que um pod seja morto (liveness) quando deveria apenas sair da rotação (readiness) por exemplo, durante uma lentidão temporária do DB.

### 3.3 Correlation ID

Cada request recebe um ID único propagado nos logs e nas chamadas à seguradora:

```clojure
(defn wrap-correlation-id [handler]
  (fn [request]
    (let [id (or (get-in request [:headers "x-request-id"])
                 (str (random-uuid)))]
      (t/with-context {:request-id id}
        (handler (assoc request :request-id id))))))
```

O ID também deve ser propagado como header nas chamadas à seguradora, para correlacionar logs da API com logs externos:

```clojure
;; insurer_client.clj — passar request-id no header HTTP
(hato/get url {:headers {"x-request-id" request-id}
               :oauth-token token})
```

Com múltiplos pods e logs agregados no Loki/Datadog, o correlation ID é o que permite rastrear um request específico entre instâncias e entre serviços.

---

## Fase 4 Proteção da borda

**Objetivo:** o pod não é a última linha de defesa contra abuso ou pico inesperado.

### 4.1 Rate limiting por partner-id

Sem rate limit, um parceiro mal configurado pode monopolizar o thread pool. O limite deve ser por `partner-id` (não por IP, que pode ser compartilhado):

```
POST /partners/:id/policies → máx 10 req/s por partner-id
POST /partners/:id/quotes   → máx 30 req/s por partner-id
```

Implementação: Redis com sliding window, ou no API Gateway (Kong, AWS API GW) sem tocar no código.

### 4.2 Backpressure explícito

Se o pod está saturado, melhor rejeitar cedo com `429 Too Many Requests` do que enfileirar requests que vão expirar do lado do cliente. Um middleware simples com semáforo:

```clojure
(defn wrap-concurrency-limit [handler max-concurrent]
  (let [sem (java.util.concurrent.Semaphore. max-concurrent)]
    (fn [request]
      (if (.tryAcquire sem)
        (try (handler request) (finally (.release sem)))
        {:status 429 :body {:error "Too many requests"}}))))
```

---

## Fase 5 Mensageria com RabbitMQ

**Objetivo:** substituir o worker de retry por uma fila de trabalho resiliente, desacoplando a criação da apólice da chamada à seguradora e eliminando o gap de queda de pod.

### Por que RabbitMQ e não Kafka

O problema aqui é **work queue** garantir que cada criação de apólice seja processada exatamente uma vez, com retry automático e dead-letter queue para falhas persistentes. Kafka seria superdimensionado: seu ponto forte é múltiplos consumers independentes lendo o mesmo evento e replay de histórico necessidades que não existem hoje nesse serviço.

| Critério                          | RabbitMQ         | Kafka              |
| --------------------------------- | ---------------- | ------------------ |
| Caso de uso                       | fila de trabalho | stream de eventos  |
| DLQ nativa                        | sim              | precisa configurar |
| Múltiplos consumers independentes | não é o foco     | ponto forte        |
| Replay de eventos                 | não tem          | ponto forte        |
| Complexidade operacional          | baixa            | alta               |
| Volume esperado (apólices)        | suficiente       | superdimensionado  |

Kafka faria sentido se outros sistemas (CRM, BI, notificações) precisarem consumir o mesmo evento `policy.created`, ou se a regulação exigir auditoria com reprocessamento.

### Fluxo proposto

```
POST /policies
  → valida localmente (Malli + DB)
  → publica mensagem em "policies" exchange
  → retorna 202 {"job_id": "uuid"}

Consumer (mesmo pod ou serviço separado):
  → lê mensagem da fila
  → chama seguradora
  → salva local (policy_id, partner_id)
  → ack → mensagem removida da fila

  → se falha → nack → RabbitMQ recoloca na fila
  → após N falhas → Dead Letter Queue → alerta / intervenção manual
```

### O que muda em relação ao worker atual

| Aspecto                    | Worker atual                        | RabbitMQ                                        |
| -------------------------- | ----------------------------------- | ----------------------------------------------- |
| Pod cai antes de processar | apólice perdida silenciosamente     | mensagem permanece na fila, outro pod processa  |
| Race condition multi-pod   | precisa de `SKIP LOCKED`            | RabbitMQ garante entrega para um único consumer |
| Falhas persistentes        | `attempts` + `last_error` na tabela | Dead Letter Queue com alerta                    |
| Polling                    | a cada 30s mesmo com fila vazia     | push consumer notificado imediatamente          |
| Escala de consumers        | limitado ao número de pods          | consumer group escala independentemente         |

### Componentes a adicionar

- **Exchange:** `policies` (direct)
- **Queue:** `policy.create` consumer principal
- **Dead Letter Queue:** `policy.create.dlq` mensagens que falharam N vezes
- **Biblioteca Clojure:** [langohr](https://github.com/michaelklishin/langohr) wrapper idiomático sobre o client AMQP oficial

### O que pode ser removido após migração

- Tabela `pending_policy_saves`
- Migrations 002 (criação da tabela)
- `policy_retry_worker.clj`
- Componente `:worker/policy-retry` no `system.edn`

---

## Roadmap resumido

| Fase | Item                                    | Complexidade                  | Impacto                                           |
| ---- | --------------------------------------- | ----------------------------- | ------------------------------------------------- |
| 1    | `FOR UPDATE SKIP LOCKED`                | baixa                         | libera escala horizontal                          |
| 1    | Virtual threads Jetty                   | baixa                         | multiplica throughput por pod                     |
| 1    | JWT cache compartilhado no Redis        | baixa                         | evita autenticação redundante entre pods          |
| 1    | Calibrar HikariCP + PgBouncer           | infra                         | protege o Postgres                                |
| 2    | Timeout explícito por operação          | baixa                         | limita dano de seguradora travada                 |
| 2    | Circuit breaker (apenas chamadas ext.)  | média                         | isola instabilidade sem afetar leituras locais    |
| 2    | `POST /policies` assíncrono (pré-MQ)    | alta                          | desacopla SLA do cliente da seguradora            |
| 3    | Latência nos logs                       | baixa                         | visibilidade do gargalo real                      |
| 3    | `/health/live` + `/health/ready`        | baixa                         | Kubernetes gerencia pods corretamente             |
| 3    | Correlation ID (logs + header ext.)     | baixa                         | rastreabilidade entre pods e seguradora           |
| 4    | Rate limiting por partner-id            | média                         | proteção contra abuso                             |
| 4    | Backpressure com semáforo               | baixa                         | rejeição rápida sob saturação                     |
| 5    | RabbitMQ `POST /policies` assíncrono    | alta                          | elimina gap de queda de pod, desacopla seguradora |
| 5    | Dead Letter Queue                       | baixa (junto com RabbitMQ)    | visibilidade de falhas persistentes               |
| 5    | Remover worker + `pending_policy_saves` | baixa (após RabbitMQ estável) | simplifica o sistema                              |
