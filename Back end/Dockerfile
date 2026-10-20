# =============================================================================
# Multi-stage Dockerfile for the CodeAssess Spring Boot backend
# Java 17 / Spring Boot 3.3.4 / Maven (via mvnw wrapper)
# =============================================================================

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper and POM first (layer-cache friendly: only re-downloads
# dependencies when pom.xml changes, not when source code changes).
COPY mvnw ./mvnw
COPY .mvn ./.mvn
COPY pom.xml ./pom.xml

# Give mvnw execute permission (needed after COPY on Linux)
RUN chmod +x mvnw

# Pre-download all dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# Copy actual source code
COPY src ./src

# Build the application JAR; skip unit tests here
# (tests require a running database and should be run in CI, not in image build)
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the executable JAR from the build stage
COPY --from=build /workspace/target/*.jar app.jar

# Set file ownership
RUN chown appuser:appgroup app.jar

USER appuser

# Expose the port the Spring Boot app listens on (matches server.port in YML)
EXPOSE 8080

# Activate the docker profile so application-docker.yml overrides are applied
ENV SPRING_PROFILES_ACTIVE=docker

# Health check: uses the /api/health endpoint (HealthController exists in project)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
