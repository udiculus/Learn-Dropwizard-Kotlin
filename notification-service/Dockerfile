FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY account-service/pom.xml ./account-service/pom.xml
COPY transaction-service/pom.xml ./transaction-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
RUN mvn -pl notification-service -am dependency:go-offline -q
COPY notification-service/src ./notification-service/src
RUN mvn -pl notification-service -am package -Dmaven.test.skip=true -q

FROM eclipse-temurin:17-jre-alpine AS runtime
LABEL service="notification-service"
WORKDIR /app
COPY --from=builder /build/notification-service/target/notification-service.jar app.jar
COPY notification-service/config.yml config.yml
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8081/healthcheck || exit 1
ENTRYPOINT ["java", "-jar", "app.jar", "server", "config.yml"]
