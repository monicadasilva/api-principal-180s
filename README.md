# Main API 180 Seguros

> [🇺🇸 Read this in English](README.en.md)

API REST que intermedia a comunicação entre parceiros e a API da seguradora. O fluxo é: parceiro se cadastra → faz cotação → gera apólice.

## Stack

- **Clojure 1.12** · Ring + Reitit + Malli · Integrant
- **Banco:** PostgreSQL 16 · next.jdbc · HoneySQL · HikariCP (pool default 10)
- **HTTP client:** hato (com retry e backoff exponencial)
- **Auth insurer:** JWT memoizado via `core.memoize` (TTL 55 min)
- **Logging:** Telemere com sink JSON estruturado (uma linha por evento, ready para agregadores)
- **Migrations:** ragtime

## Requisitos

- Docker e Docker Compose

## Configuração

```bash
cp .env.example .env
```

| Variável             | Descrição                        | Exemplo                                                                   |
| -------------------- | -------------------------------- | ------------------------------------------------------------------------- |
| `HTTP_PORT`          | Porta da Main API                | `3000`                                                                    |
| `DATABASE_URL`       | URL de conexão com o PostgreSQL  | `jdbc:postgresql://db:5432/api_principal?user=postgres&password=postgres` |
| `INSURANCE_BASE_URL` | URL base da API Seguradora       | `http://insurance-api:5000`                                               |
| `INSURANCE_API_KEY`  | Chave de acesso à API Seguradora | ``                                                                        |

## Como rodar

**Desenvolvimento** (hot-reload via volume):

```bash
docker compose --profile dev up
```

**Produção** (uber JAR compilado):

```bash
docker compose --profile prod up --build
```

A API estará disponível em `http://localhost:3000`.

## Migrations

As migrations são executadas automaticamente no startup via ragtime. Os arquivos ficam em `resources/migrations/` no formato `NNN-descricao.up.sql`.

Para adicionar uma nova migration, basta criar o próximo arquivo na sequência o sistema aplica apenas as pendentes. Rollbacks de schema são feitos via nova migration, não via down files.

## Postman

A collection com todos os endpoints está em [`docs/postman/`](docs/postman/). Importe o arquivo `.json` diretamente no Postman ou no Insomnia.

## Swagger UI

Com a API rodando, acesse:

- **UI interativa:** `http://localhost:3000/api-docs/index.html`
- **Spec JSON:** `http://localhost:3000/swagger.json`

## Como testar

Os testes de integração requerem o PostgreSQL rodando:

```bash
docker compose up db -d
clojure -M:test -m cognitect.test-runner -d test
```

## Autenticação

`POST /partners` é público. Todas as demais rotas exigem:

```
Authorization: Bearer <api_key>
```

O `api_key` é retornado **uma única vez** no corpo do `POST /partners`. Guarde-o com segurança - não há endpoint para recuperá-lo.

> O servidor armazena apenas o **SHA-256** do `api_key` (coluna `api_key_hash`). Um dump do banco não compromete os tokens dos parceiros: a comparação na autenticação é feita entre o hash do token recebido e o hash armazenado.

## Jornada do parceiro

### 1. Cadastro

```http
POST /partners
Content-Type: application/json

{ "name": "Acme Corretora", "cnpj": "12.345.678/0001-95" }
```

```json
{
  "id": "uuid-do-parceiro",
  "name": "Acme Corretora",
  "cnpj": "12345678000195",
  "api_key": "uuid-da-chave"
}
```

> Guarde `id` e `api_key`. O `api_key` não será exibido novamente.

### 2. Cotação

```http
POST /partners/uuid-do-parceiro/quotes
Authorization: Bearer uuid-da-chave
Content-Type: application/json

{ "age": 30, "sex": "f" }
```

```json
{
  "id": "uuid-da-cotacao",
  "age": 30,
  "gender": "F",
  "price": "500.0",
  "expire_at": "2025-12-31"
}
```

### 3. Apólice

```http
POST /partners/uuid-do-parceiro/policies
Authorization: Bearer uuid-da-chave
Content-Type: application/json

{
  "quotation_id":  "uuid-da-cotacao",
  "name":          "João Silva",
  "sex":           "f",
  "date_of_birth": "1996-03-15"
}
```

### 4. Consulta

```http
GET /partners/uuid-do-parceiro/policies/uuid-da-apolice
Authorization: Bearer uuid-da-chave
```

## Operação

### Healthcheck

```http
GET /health
```

Retorna `200 {"db": "up"}` quando o pool consegue executar `SELECT 1`, ou `503 {"db": "down"}` em caso de falha. Não checa a seguradora bater no `/api/auth` em cada probe geraria carga e latência desnecessárias; a saúde do upstream é refletida indiretamente nas respostas `502/504` dos endpoints reais.

### Graceful shutdown

O Jetty é configurado com `setStopTimeout(30_000)`: ao receber SIGTERM, deixa de aceitar conexões novas e aguarda até 30 s para drenar requests em voo antes do `halt!` propagar para os demais componentes (worker → pool).

### Logs

Saída em **JSON line-per-event** no stdout, com campos `level`, `time`, `ns`, `msg`, opcionalmente `id`, `data`, `error.{type,message}`. Pronto para ingestão por Loki/Datadog/Cloudwatch sem parser custom.

## Endpoints

### `POST /partners`

Cadastra um parceiro. Rota pública.

```json
{ "name": "Acme", "cnpj": "12.345.678/0001-95" }
```

**Respostas:** `201` criado (inclui `api_key`) · `400` formato inválido · `409` CNPJ duplicado · `422` dígitos verificadores inválidos

---

### `POST /partners/:partner-id/quotes`

Solicita uma cotação à seguradora e a persiste localmente.

```json
{ "age": 30, "sex": "f" }
```

`sex`: `m | M | f | F | n | N`

**Respostas:** `201` criado · `400` validação · `401` não autorizado

---

### `POST /partners/:partner-id/policies`

Cria uma apólice a partir de uma cotação. Valida regras de negócio antes de chamar a seguradora.

```json
{
  "quotation_id": "uuid",
  "name": "João Silva",
  "sex": "f",
  "date_of_birth": "1996-03-15"
}
```

**Respostas:** `200` criado · `400` validação HTTP · `401` não autorizado · `404` cotação não encontrada ou não pertence ao parceiro · `422` cotação expirada · `422` sexo divergente da cotação · `422` idade incompatível com data de nascimento (±1 ano)

---

### `GET /partners/:partner-id/policies/:policy-id`

Busca uma apólice na seguradora. Se não pertencer ao parceiro autenticado, retorna `404`.

**Respostas:** `200` encontrado · `401` não autorizado · `404` não encontrado

## Arquitetura

O padrão escolhido é **Ports & Adapters funcional** - sem `defprotocol`. Portas são assinaturas de função implícitas; adaptadores são mapas de funções construídos por higher-order functions e injetados via Integrant no startup.

Cada camada tem uma responsabilidade clara e sem vazamento:

- **domain** - regras de negócio puras, sem I/O. Funções que recebem dados e retornam dados ou booleanos.
- **use_cases** - orquestração: recebe as portas (repos, insurer) como argumento e coordena domínio + efeitos colaterais.
- **adapters/inbound** - traduz HTTP → use case → HTTP. Não contém lógica de negócio.
- **adapters/outbound** - implementa as portas: DB via next.jdbc/HoneySQL, seguradora via hato.
- **infrastructure** - monta e conecta tudo via Integrant; sem lógica de domínio ou transporte.

```
src/api_principal/
├── core/
│   ├── domain/          # Regras de negócio puras (sem side effects)
│   │   ├── partner.clj  # valid-cnpj?, build
│   │   ├── quote.clj    # build
│   │   └── policy.clj   # owned-by?, quote-expired?, age-matches-dob?, sex-matches?
│   └── use_cases/       # Orquestração: domínio + portas
│       ├── create_partner.clj
│       ├── create_quote.clj
│       ├── create_policy.clj
│       └── fetch_policy.clj
├── adapters/
│   ├── inbound/http/    # Ring handlers, rotas Reitit/Malli, middleware
│   └── outbound/
│       ├── db/          # next.jdbc + HoneySQL (partner, quote, policy repos)
│       └── insurer/     # hato: auth.clj (token + memoize) · client.clj (retry/backoff)
└── infrastructure/      # Integrant: db pool, http client, server, adapters, logging
```

### Modelo de dados

| Tabela     | Colunas principais                              | Responsabilidade                               |
| ---------- | ----------------------------------------------- | ---------------------------------------------- |
| `partners` | `id, name, cnpj, api_key_hash, created_at`      | Entidade exclusiva da Main API                 |
| `quotes`   | `id, partner_id, age, gender, price, expire_at` | Persistida localmente (seguradora não tem GET) |
| `policies` | `id, partner_id, created_at`                    | Vínculo local; seguradora é source of truth    |

### Resiliência na comunicação com a seguradora

Cada chamada passa por uma camada `attempt` que captura exceções específicas e as traduz em respostas HTTP-shaped com `:body` estruturado. O cliente nunca recebe `:status` sem `:body`:

| Cenário                               | Status | Código no body         | Retry? |
| ------------------------------------- | ------ | ---------------------- | ------ |
| `200/4xx` da seguradora               | passa  | passa                  | não    |
| `5xx` da seguradora                   | passa  | passa (ou normalizado) | sim    |
| `UnknownHostException` (DNS)          | 502    | `insurer_unreachable`  | sim    |
| `ConnectException`                    | 502    | `insurer_unreachable`  | sim    |
| `HttpTimeoutException`                | 504    | `insurer_timeout`      | sim    |
| Falha no `/api/auth` (token endpoint) | 502    | `insurer_auth_failed`  | não    |
| `Exception` genérica                  | 502    | `insurer_error`        | não    |
| Resposta `5xx` com body vazio         | passa  | normalizado            | sim    |

- **Retry de transientes:** `500/502/504` com backoff exponencial (2 tentativas, base 200 ms)
- **Token expirado (401):** invalida o cache do JWT (`memo-clear!`) e retenta uma única vez com token novo
- **Auth falhou (`fetch-token!` lançou):** convertido em `502 insurer_auth_failed`, sem retry refazer a chamada teria o mesmo desfecho

A premissa do retry em 5xx é que **inputs já foram validados pelo Malli na borda HTTP**, então um 5xx da seguradora só pode ser instabilidade de infraestrutura, não payload malformado. Se a seguradora um dia retornar 5xx para validação semântica, o retry desperdiça duas tentativas mas não causa dano.

### Consistência local ↔ seguradora

A criação de apólice é uma operação distribuída: a seguradora cria o registro e o serviço persiste o vínculo `(policy_id, partner_id)` localmente. Se o `INSERT` no Postgres falhar depois do `200` da seguradora, ficaríamos com a apólice existindo lá fora e nenhum registro local toda futura `GET /policies/:id` retornaria `404` por falha de ownership.

Para evitar essa inconsistência silenciosa:

1. O `INSERT` na tabela `policies` é tentado dentro de um `try`.
2. Se falhar, a tupla `(policy_id, partner_id)` é enfileirada na tabela `pending_policy_saves` com o erro original.
3. Um worker em background (componente Integrant `:worker/policy-retry`, intervalo 30 s) lê linhas pendentes, retenta o `INSERT`, e remove da fila quando sucede. Em caso de falha, atualiza `attempts` e `last_error` para diagnóstico.
4. Se o próprio enqueue falhar (DB totalmente fora), um log crítico (`::policy-persistence-lost`) é emitido com todos os dados última linha de defesa para reconciliação manual.

A request original sempre retorna `200` ao cliente quando a seguradora confirma a criação, mesmo que o save local tenha sido deferido. A apólice é completamente utilizável (consultas locais via `/policies/:id` funcionam assim que o worker drena a fila).

> Limitação conhecida: em deployment multi-instância, duas instâncias do worker podem disputar a mesma linha. Para evitar, basta envolver o `SELECT` em `FOR UPDATE SKIP LOCKED`. Não está implementado por ser single-instance hoje.

## Decisões técnicas

### Ports & Adapters sem `defprotocol`

`defprotocol` cria acoplamento nominal: obriga a definir um tipo concreto que implemente o protocolo, gerando boilerplate desnecessário para um serviço com poucas implementações por porta. Mapas de funções (`{:save-partner! fn, :find-partner fn}`) são igualmente substituíveis em testes - basta trocar o mapa - e mais idiomáticos em Clojure, onde funções de primeira classe eliminam a necessidade de dispatch polimórfico nesse nível.

### Integrant

Component e Mount são alternativas comuns. Integrant foi escolhido porque o grafo de dependências é declarado em EDN (`resources/system.edn`), separando configuração de código. Isso permite substituir adaptadores em tempo de teste sem alterar namespaces de produção e deixa o sistema inteiro legível em um único arquivo de configuração.

### Reitit + Malli

Compojure é mais simples, mas sem coerção nativa de tipos. Reitit integra com Malli para validar e coagir parâmetros de entrada e saída na borda HTTP, eliminando conversões manuais nos handlers. Malli foi preferido a `clojure.spec` por ter suporte nativo a JSON Schema e mensagens de erro humanizadas prontas para o cliente.

### next.jdbc + HoneySQL

next.jdbc é o wrapper JDBC moderno recomendado pela comunidade Clojure (substitui `clojure.java.jdbc`). HoneySQL representa queries como mapas Clojure - testáveis, componíveis e imunes a SQL injection por construção. A alternativa de strings SQL brutas é simples mas frágil a refatorações; um ORM seria complexidade desnecessária para três tabelas.

### HikariCP

Pool de conexões JDBC configurado com os seguintes parâmetros:

| Parâmetro | Valor padrão | Motivo |
|---|---|---|
| `maximumPoolSize` | 10 | Balanceia throughput e carga no banco; configurável por env |
| `minimumIdle` | `max(1, poolSize/5)` | Mantém conexões aquecidas sem desperdiçar recursos em baixa carga |
| `connectionTimeout` | 30 s | Falha rápido se o banco estiver indisponível; evita enfileirar requests indefinidamente |
| `idleTimeout` | 10 min | Recicla conexões ociosas; evita que o banco feche por inatividade |
| `maxLifetime` | 30 min | Reciclagem periódica previne stale connections causadas por firewalls ou load balancers com timeout de longa duração |

O `halt-key!` chama `.close` no `DataSource`, garantindo que o pool seja drenado antes do shutdown do servidor (ordem imposta pelo grafo de dependências do Integrant: `http/server` → `adapter/repository` → `db/pool`).

### hato

hato é um wrapper fino sobre o `java.net.http.HttpClient` nativo do Java 11, sem dependência de Apache HTTPClient. O retry com backoff exponencial e a invalidação de token são implementados na camada de aplicação (`client.clj`), mantendo controle explícito do comportamento em vez de depender de configuração opaca de um cliente HTTP.

### core.memoize/ttl

A seguradora exige autenticação JWT com expiração. Buscar um token novo a cada requisição adicionaria latência e poderia atingir rate-limits. `core.memoize/ttl` cacheia o token com TTL de 55 minutos - 5 minutos abaixo do prazo do token - garantindo que nunca seja enviado um token expirado. Em caso de 401 inesperado (rotação antecipada), `memo-clear!` invalida o cache e força nova autenticação imediata.

### Telemere

Telemere é o sucessor do Timbre (mesma autoria, taoensso). Tem suporte nativo a structured logging e é compatível com OpenTelemetry, permitindo evolução para observabilidade distribuída sem troca de biblioteca. O Timbre foi descartado por ser considerado legado pelo próprio autor.
