# Test Coverage, Sandbox, and Architectural Scope Audit
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Scope:** Maven build configuration, test execution, execution sandboxing, and architectural subsystems
- **Status:** Complete & Independently Audited Against Local Codebase

---

## 1. Executive Summary

This audit evaluated three critical engineering requirements:
1. **Automated Test Coverage Reporting (JaCoCo)**
2. **Untrusted Code Execution & Docker Sandboxing**
3. **Subsystem Scope & Integration Accuracy**

### Key Findings Matrix

| Audit Area | Claimed / Expected | Verified Codebase Status | Evidence / File Reference | Label |
| :--- | :--- | :--- | :--- | :---: |
| **JaCoCo Configuration** | Automated coverage reports ($\ge 80\%$) | **NOT CONFIGURED** in `pom.xml`. No XML/HTML coverage report exists. | `Back end/pom.xml` lines 79–94 | **VERIFIED** |
| **Test Suite Execution** | All tests pass | **140 tests pass** (0 failures, 0 errors, 0 skipped), including 25 AST unit tests. | Maven Surefire Test Run (`task-617`) | **VERIFIED** |
| **Code Execution Sandbox**| Docker container isolation with CPU/RAM limits | **NOT IMPLEMENTED**. Student code is never executed dynamically. | `Back end/src/main/java` grep for `ProcessBuilder`/`Runtime` | **VERIFIED** |
| **Parser Language Scope** | Multi-language support | **Java Only**. Uses JavaParser 3.25.9. | `JavaAstParser.java` | **VERIFIED** |
| **AI Assistant Client** | Production LLM provider | **STUB IMPLEMENTATION** (`StubAiClient` throws exception). | `StubAiClient.java` lines 7–18 | **VERIFIED** |
| **Queue / Event System** | Redis / BullMQ distributed queue | **Spring In-Memory Events + `@Async`** (`PlagiarismAnalysisListener`). | `PlagiarismAnalysisListener.java` lines 23–25 | **VERIFIED** |
| **WebSocket Support** | Real-time WebSocket notifications | **NOT IMPLEMENTED**. No WebSocket configuration or endpoint. | Grep search across `Back end/` | **VERIFIED** |
| **AST Tree Edit Distance**| TED / Loop desugaring | **NOT IMPLEMENTED**. Linear K-gram winnowing only. | Production plagiarism package audit | **VERIFIED** |

---

## 2. Test Coverage Audit (JaCoCo)

### 2.1 Inspection of `Back end/pom.xml`
A comprehensive examination of `Back end/pom.xml` reveals that only the standard `spring-boot-maven-plugin` is configured under `<build><plugins>`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

`jacoco-maven-plugin` is **completely absent** from the repository.

### 2.2 Test Suite Execution Evidence
Running `mvnw test` on 2026-10-12 executed the full test suite with the following verified outcome:
- **Total Tests Run:** **140**
- **Failures:** **0**
- **Errors:** **0**
- **Skipped:** **0**
- **Build Status:** `BUILD SUCCESS` (Execution time: 40.72s)

Among the 140 passing tests, 25 directly test Module 4 plagiarism components:
- `JavaAstParserTest`
- `AstNormalizerTest`
- `KGramGeneratorTest`
- `FingerprintHasherTest`
- `WinnowerTest`
- `JaccardSimilarityCalculatorTest`
- `AstPlagiarismServiceTest`
- `SimilarityMatrixServiceTest`

### 2.3 Coverage Claim Assessment
> [!CAUTION]
> **Strict Verification Standard:**
> Because JaCoCo is not configured, **no line or branch coverage metrics can be mathematically reported**. It is strictly unacceptable to claim that an 80% coverage target is met without an empirical JaCoCo execution report.
> 
> *Action Required:* Before Milestone 3, `jacoco-maven-plugin:0.8.12` should be added to `Back end/pom.xml` with `prepare-agent` and `report` goals.

---

## 3. Untrusted Code Execution & Sandbox Audit

### 3.1 Does CodeAssess Execute Student Code?
**No.** An exhaustive search for process invocation mechanisms (`ProcessBuilder`, `Runtime.getRuntime().exec`, Java Compiler API `ToolProvider`) confirms that **CodeAssess does not execute student submissions**.

The platform is designed exclusively for **static AST analysis**:
1. Student code is ingested as a plain text string via REST API.
2. The code is parsed into an abstract syntax tree using JavaParser (`JavaAstParser.java`).
3. The AST is normalized, tokenized into K-grams, hashed, and winnowed.
4. No bytecode is compiled, and no JVM or system process is spawned to run the student code.

### 3.2 Docker Sandboxing Status
Because student code is not dynamically executed, **Docker container isolation is neither present nor active in the backend repository**:
- No `Dockerfile` for execution sandboxes exists in `Back end/`.
- No `docker-java` client or container orchestration dependency is present in `pom.xml`.
- No cgroup controls (CPU limits, RAM limits, execution timeouts) exist for process containment.

### 3.3 Security Considerations for Static AST Parsing
Although code execution attacks (fork bombs, infinite loops, host filesystem compromise) are prevented by not executing the code, static parsing carries its own operational risks:

| Threat Vector | Risk Description | Current Mitigation Status |
| :--- | :--- | :--- |
| **Parser Denial of Service** | Gigabyte-sized source files or millions of tokens could exhaust JVM heap memory. | Partially mitigated by default Spring multipart upload limits (10MB). No submission-level token limit in `JavaAstParser`. |
| **Deep AST Recursion** | Deeply nested expressions (`((((...))))`) could cause a `StackOverflowError` in JavaParser. | Unmitigated; JavaParser runs directly on Spring worker threads. |
| **Thread Blockage** | Complex parsing could hold thread indefinitely. | Unmitigated; no parsing timeout wrapper around `StaticJavaParser.parse()`. |

---

## 4. Architectural Scope & Subsystem Audit

### 4.1 Programming Language Scope
- **Current State:** **Java Only** (`JavaAstParser.java`).
- **Implementation:** JavaParser 3.25.9 parses Java grammar up to Java 17/21 features.
- **Multi-language Support:** C++, Python, and JavaScript parsers are **not implemented**.

### 4.2 AI Assistant Integration
- **Current State:** **Stub Client** (`com.codeassess.ai.client.StubAiClient`).
- **Code Inspection:**
  ```java
  @Service
  public class StubAiClient implements AiClient {
      @Override
      public String generateSimilarityExplanation(String prompt) {
          throw new AiIntegrationException("AI provider is not configured. Service is pending integration.");
      }
      @Override
      public String generateCodeReview(String prompt) {
          throw new AiIntegrationException("AI provider is not configured. Service is pending integration.");
      }
  }
  ```
- **Conclusion:** There is zero active integration with OpenAI, Google Gemini, or Anthropic Claude. The frontend UI modal handles the error gracefully as a mock state.

### 4.3 Task Scheduling and Event Processing
- **Current State:** **Spring In-Memory Event Bus + `@Async`**.
- **Implementation:** `PlagiarismAnalysisListener` intercepts `SubmissionCreatedEvent` asynchronously via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
- **Queue System:** Neither Redis, BullMQ, Kafka, nor RabbitMQ is configured. Plagiarism analysis executes entirely within the Spring Boot application JVM thread pool.

### 4.4 WebSocket Real-Time Communication
- **Current State:** **Not Implemented**.
- **Evidence:** `Back end/pom.xml` does not include `spring-boot-starter-websocket`. All frontend views query plagiarism status via standard HTTP REST endpoints (`GET /api/v1/plagiarism/reports/{id}`).

### 4.5 Tree Edit Distance & Control-Flow Desugaring
- **Current State:** **Not Implemented**.
- **Evidence:** AST normalization does not transform `for` loops into `while` loops, nor does it perform Zhang-Shasha or RTED comparisons. Similarity calculation relies strictly on winnowed 5-gram Jaccard matching.
