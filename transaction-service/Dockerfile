FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY account-service/pom.xml ./account-service/pom.xml
COPY transaction-service/pom.xml ./transaction-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
RUN mvn -pl transaction-service -am dependency:go-offline -q
COPY transaction-service/src ./transaction-service/src
RUN mvn -pl transaction-service -am package -Dmaven.test.skip=true -q

FROM eclipse-temurin:17-jre-alpine AS runtime
LABEL service="transaction-service"
WORKDIR /app
COPY --from=builder /build/transaction-service/target/transaction-service.jar app.jar
COPY transaction-service/config.yml config.yml
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8081/healthcheck || exit 1
ENTRYPOINT ["java", "-jar", "app.jar", "server", "config.yml"]
