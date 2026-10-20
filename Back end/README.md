# CodeAssess Backend Foundation

This is the Java backend for the CodeAssess Plagiarism Detection System.

## Architecture
- Feature-Based Architecture
- Spring Boot 3
- Spring Security (Server-side JSESSIONID, HttpOnly)
- MySQL 8 + Flyway
- Docker Sandbox Engine (`eclipse-temurin:21-jdk-alpine`, cgroups resource limits, non-root nobody, network isolation)

## Setup
1. Copy `.env.example` to `.env` and fill in local database credentials.
2. Ensure Docker Desktop is running and pull the sandbox image:
   ```bash
   docker pull eclipse-temurin:21-jdk-alpine
   ```
3. Run `mvn clean install`.
4. Run `mvn spring-boot:run`.

## Development Rules
- Do NOT implement logic in controllers.
- Do NOT bypass `HttpOnly` cookie constraints.
- Do NOT expose `password_hash` in DTOs.
- Plagiarism AST Engine MUST remain encapsulated in the `plagiarism` feature module.
- Docker Sandbox MUST remain stateless and NEVER trigger database submissions or plagiarism checks.
