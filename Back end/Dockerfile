# =============================================================================
# Multi-stage Dockerfile for CodeAssess Spring Boot Backend
# Optimized for Cloud Deployment & Low Memory Footprint (Render Free Tier 512MB)
# =============================================================================

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper and POM first (layer caching)
COPY mvnw ./mvnw
COPY .mvn ./.mvn
COPY pom.xml ./pom.xml

# Ensure Unix line endings for mvnw on Alpine Linux to prevent exit code 127
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw

# Pre-download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# Copy source code and build production JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Install wget for healthcheck
RUN apk add --no-cache wget

# Create non-root application user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Create config and data directories, copy CA certificate for TiDB Cloud SSL
RUN mkdir -p /app/config /app/data/submissions
COPY config/isrgrootx1.pem /app/config/isrgrootx1.pem

# Copy executable JAR from build stage
COPY --from=build /workspace/target/*.jar app.jar

# Set proper ownership for non-root execution
RUN chown -R appuser:appgroup /app

USER appuser

# Default port (Render automatically overrides this via the $PORT environment variable)
ENV PORT=8081
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8081

# Health check using dynamic PORT
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT}/api/health" || exit 1

# Optimized JVM memory settings for 512MB RAM environment:
# - UseContainerSupport: respects container cgroup limits
# - MaxRAMPercentage=75.0: caps heap at ~384MB on a 512MB container, leaving headroom for Metaspace/OS
# - InitialRAMPercentage=50.0: sets initial heap at ~256MB to minimize startup allocation spikes
# - Xss512k: reduces thread stack from 1MB to 512KB to save memory with concurrent requests
# - exec sh ensures Java runs as PID 1 to gracefully receive SIGTERM on shutdown
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Xss512k -Dserver.port=${PORT} -jar app.jar"]
