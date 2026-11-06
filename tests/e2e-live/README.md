# CodeAssess — Live E2E & Sandbox Security Verification Suite

This directory contains standalone, production-ready verification scripts designed for automated CI/CD and Black-Box verification of the CodeAssess backend autograding engine, Docker sandbox isolation, and AST plagiarism detection pipeline.

---

## 1. Directory Structure

```text
tests/e2e-live/
├── README.md                     # Documentation and execution instructions
├── run_live_e2e_pipeline.js      # Full lifecycle submission-to-plagiarism pipeline
└── test_sandbox_security.js      # 6-vector Docker sandbox isolation audit
```

---

## 2. Environment Variables & Credentials

All test scripts strictly adhere to security guidelines: **zero hardcoded credentials or API keys**. Credentials and endpoints are configured exclusively via environment variables:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `BACKEND_URL` | `http://localhost:8081` | Spring Boot Backend REST API base URL |
| `E2E_EXAM_ID` | `exm-e2e-exam-a-00000001` | Target Exam ID for live submissions |
| `E2E_STUDENT_EMAIL` | `studenta@fpt.edu.vn` | Primary student test account (Student A) |
| `E2E_STUDENT_B_EMAIL` | `studentb@fpt.edu.vn` | Secondary student test account (Student B) |
| `E2E_TEACHER_EMAIL` | `teachera@fpt.edu.vn` | Teacher account authorized to query plagiarism matrix |
| `E2E_TEST_PASSWORD` | *(None - Required)* | Test account password for authentication |

---

## 3. How to Run

### A. Run Full Live E2E Pipeline
```bash
# Linux / macOS
E2E_TEST_PASSWORD="<YOUR_TEST_PASSWORD>" node tests/e2e-live/run_live_e2e_pipeline.js

# Windows PowerShell
$env:E2E_TEST_PASSWORD="<YOUR_TEST_PASSWORD>"
node tests/e2e-live/run_live_e2e_pipeline.js
```

### B. Run Docker Sandbox Security Audit Suite
```bash
# Linux / macOS
E2E_TEST_PASSWORD="<YOUR_TEST_PASSWORD>" node tests/e2e-live/test_sandbox_security.js

# Windows PowerShell
$env:E2E_TEST_PASSWORD="<YOUR_TEST_PASSWORD>"
node tests/e2e-live/test_sandbox_security.js
```

---

## 4. Test Vectors Verified

| # | Test Vector | Protection Mechanism | Expected Outcome |
| :-: | :--- | :--- | :--- |
| 1 | **Infinite Loop** | Wall-clock execution timeout (`10s`) | Killed, `TIMEOUT` status returned |
| 2 | **Fork Bomb** | Container PID limit (`pidsLimit=50`) | `pthread_create failed (EAGAIN)`, zero host impact |
| 3 | **Off-Heap Memory** | Container RAM limit (`512MB`) | Linux cgroup OOM-killer (`exit code 137`), `Killed` |
| 4 | **Filesystem Escape** | `readOnlyRootfs=true` | `Read-only file system` error on write attempts |
| 5 | **Network Exfiltration**| `network="none"` | `Network is unreachable` on outbound HTTP/TCP |
| 6 | **Host Information Leak**| Container filesystem namespace | No host `/etc/passwd`, no host `.env`, no DB secrets |

