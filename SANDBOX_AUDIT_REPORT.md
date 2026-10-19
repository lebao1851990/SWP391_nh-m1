# Docker Sandbox Audit & Environment Verification Report
**Project:** CodeAssess | Module: Docker Sandbox Execution Engine  
**Course & Milestone:** SWP391 RBL — Milestone 2  
**Date:** 2026-10-15  
**Auditor:** Senior Java Backend & Docker Security Engineer  
**Status:** AUDIT COMPLETE (Gaps Identified & Remediation Plan Formulated)

---

## 1. Environment & Infrastructure Audit

| Component | Target Requirement | Detected Environment | Status | Notes |
| :--- | :---: | :---: | :---: | :--- |
| **Java JDK** | JDK 17 / 21 | Oracle JDK 17.0.12 LTS (`build 17.0.12+8-LTS-286`) | COMPATIBLE | Machine default runtime is JDK 17 |
| **Build Tool** | Apache Maven 3.9+ | Apache Maven 3.9.9 | COMPATIBLE | Installed at `C:\Users\QUANG VINH\apache-maven-3.9.9` |
| **Docker Engine** | Docker Engine 24+ | Docker Desktop 4.92.0 (Engine 29.8.0, API 1.56, Linux/amd64) | ACTIVE | Verified connected and responsive |
| **Docker Image** | `eclipse-temurin:21-jdk-alpine` | Pulled & Verified (`sha256:0bfc69a4...`) | READY | Available for container creation |
| **Database** | MySQL 8.0 | MySQL Server 8.0 running on port 3306 | RUNNING | Production schema managed via Flyway |

---

## 2. Codebase & Component Structure Audit

The sandbox subsystem is encapsulated within `com.codeassess.sandbox`:

```
Back end/src/main/java/com/codeassess/sandbox/
├── config/
│   ├── DockerClientConfiguration.java   # Spring bean configuration for DockerClient (Zerodep HTTP)
│   └── SandboxProperties.java           # Configuration properties bound to sandbox.* (limits)
├── controller/
│   └── SandboxController.java           # REST endpoint: POST /api/student/sandbox/execute
├── dto/
│   ├── request/
│   │   └── SandboxExecuteRequest.java   # Request payload (sourceCode)
│   └── response/
│       └── SandboxResult.java           # Response payload (status, stdout, stderr, exitCode, ms, truncated)
├── exception/
│   └── SandboxException.java            # Unchecked runtime exception for sandbox failures
└── service/
    └── DockerSandboxService.java        # Core container orchestration & security enforcement
```

---

## 3. Verified Findings & Discrepancies

### Finding 1: Controller Integration Test Context Failure (Severity: Medium)
- **Location:** `Back end/src/test/java/com/codeassess/sandbox/controller/SandboxControllerTest.java`
- **Issue:** The test class was annotated with `@SpringBootTest`, which boots the full JPA entity lifecycle and attempts to connect to a local MySQL instance with default credentials (`root:123456`). Because the test only verifies Web MVC routing, validation, error mapping, and security filters, this full context initialization causes `ApplicationContext failure threshold exceeded` when MySQL credentials do not match.
- **Remediation:** Refactor `SandboxControllerTest` to use `@WebMvcTest(SandboxController.class)` and `@Import({SecurityConfig.class, GlobalExceptionHandler.class})` with appropriate service and user details mocks, isolating controller testing from the database tier.

### Finding 2: Class Name Sensitivity in Source Code Execution (Severity: Medium)
- **Location:** `DockerSandboxService.java` (`SCRIPT_TEMPLATE`)
- **Issue:** The execution script unconditionally names the source file `/tmp/sandbox/Main.java` and executes `java Main`. If a student submits code with a public class declaration other than `Main` (e.g. `public class Solution` or `public class Calculator`), `javac` fails with `class ... is public, should be declared in a file named ...`.
- **Remediation:** Dynamically detect the top-level public class name from the source code via regex before generating the shell script. If no public class is declared, default to `Main`.

### Finding 3: Compiler Output Channel Alignment (Severity: Low)
- **Location:** `DockerSandboxService.java` (`SCRIPT_TEMPLATE`)
- **Issue:** The compile failure handler outputs errors using `cat /tmp/sandbox/compile.err; exit 1;`, sending compiler errors to `stdout` instead of `stderr`. Standard Unix semantics dictate that diagnostic and compilation errors belong on `stderr`.
- **Remediation:** Redirect error diagnostics to standard error (`cat /tmp/sandbox/compile.err >&2; exit 1;`).

### Finding 4: Container Hygiene & Process Reaping (Severity: Low)
- **Location:** `DockerSandboxService.java` (`HostConfig`)
- **Issue:** Containers run without an explicit init process (`--init`), leaving them potentially susceptible to zombie process accumulation if untrusted code forks child threads.
- **Remediation:** Enable Docker's built-in init process via `.withInit(true)` on `HostConfig`, and enforce zero core dumps (`ulimit core=0`).

### Finding 5: Real Docker Integration Test Validation
- **Location:** `Back end/src/test/java/com/codeassess/sandbox/DockerSandboxIntegrationTest.java`
- **Status:** Executed and passed 12/12 test cases against real Docker Engine:
  - Valid Java execution (Exit 0)
  - Compilation error differentiation
  - Runtime exception capture
  - Timeout enforcement (10s wall clock) and force stop
  - Output truncation bound (64 KiB)
  - Concurrency limit (Semaphore) enforcement
  - Container removal on exit

---

## 4. Multi-Stage Remediation Plan (8 Commits)

1. `chore(sandbox): audit existing sandbox implementation` (This audit report)
2. `fix(sandbox): resolve verified compilation issues` (Controller test context isolation)
3. `feat(sandbox): complete Java execution workflow` (Dynamic class name detection & compiler stream alignment)
4. `fix(sandbox): enforce execution timeout and cleanup` (Graceful timeout cancellation & robust cleanup)
5. `fix(sandbox): harden Docker container isolation` (Init process, core dump limit, extra network isolation)
6. `fix(sandbox): validate requests and enforce authorization` (Bean validation constraints & security annotations)
7. `test(sandbox): cover execution and security edge cases` (Additional unit & integration test edge cases)
8. `docs(sandbox): document setup and verification results` (Comprehensive setup guide and verification logs)
