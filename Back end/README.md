# CodeAssess Backend Foundation

This is the Java backend for the CodeAssess Plagiarism Detection System.

## Architecture
- Feature-Based Architecture
- Spring Boot 3
- Spring Security (Server-side JSESSIONID, HttpOnly)
- MySQL 8 + Flyway

## Setup
1. Copy `.env.example` to `.env` and fill in local database credentials.
2. Run `mvn clean install`.
3. Run `mvn spring-boot:run`.

## Development Rules
- Do NOT implement logic in controllers.
- Do NOT bypass `HttpOnly` cookie constraints.
- Do NOT expose `password_hash` in DTOs.
- Plagiarism AST Engine MUST remain encapsulated in the `plagiarism` feature module.
