# ============================================================
# Dockerfile — Multi-stage build for cs-platform
# ============================================================

# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Pre-download dependencies (layer caching)
COPY pom.xml local.properties ./
COPY .mvn/ .mvn/
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -q

# Build application
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B -q

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy JAR from builder
COPY --from=builder /build/target/cs-platform-1.0.0.jar app.jar

# Application configuration
EXPOSE 8080
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/dashboard/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
