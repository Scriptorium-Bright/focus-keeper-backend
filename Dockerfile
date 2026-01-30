# ================================
# Multi-stage Dockerfile
# ================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy gradle files first for better caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached layer)
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon

# Copy source and build
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Non-root user for security
RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
