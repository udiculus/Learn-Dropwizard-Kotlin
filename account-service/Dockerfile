# ── Multi-stage build for Account Service ─────────────────────────────────────
# Stage 1: Build the fat JAR
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy parent POM and all module POMs (required by Maven reactor)
COPY pom.xml ./pom.xml
COPY account-service/pom.xml ./account-service/pom.xml
COPY transaction-service/pom.xml ./transaction-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml

# Download dependencies (cached unless POM changes)
RUN mvn -pl account-service -am dependency:go-offline -q

# Copy source and build
COPY account-service/src ./account-service/src
RUN mvn -pl account-service -am package -Dmaven.test.skip=true -q

# Stage 2: Minimal JRE runtime image
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="Finance Learning Project"
LABEL service="account-service"

WORKDIR /app

# Copy the fat JAR from builder stage
COPY --from=builder /build/account-service/target/account-service.jar app.jar

# Copy Dropwizard config
COPY account-service/config.yml config.yml

# Expose application and admin ports
EXPOSE 8080 8081

# Health check using the Dropwizard admin endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8081/healthcheck || exit 1

ENTRYPOINT ["java", "-jar", "app.jar", "server", "config.yml"]
