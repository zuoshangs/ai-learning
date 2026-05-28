# ============================================================
# Multi-stage Dockerfile for Production Gateway
# ============================================================

# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -q -B

# Build application
COPY src src
RUN mvn package -q -DskipTests -Pproduction

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# Create log directory
RUN mkdir -p /var/log/gateway && chown -R appuser:appgroup /var/log/gateway

# Health check
HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=30s \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Run as non-root user
USER appuser

# Start with production profile
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
