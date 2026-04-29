FROM clojure:temurin-21-tools-deps AS builder

WORKDIR /app
COPY deps.edn build.clj ./
RUN clojure -P

COPY src ./src
COPY resources ./resources
RUN clojure -T:build uber

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/api-principal.jar ./app.jar

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
