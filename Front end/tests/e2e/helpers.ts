import { Page, expect } from '@playwright/test';
import crypto from 'crypto';

export interface LoginOptions {
  role?: 'TEACHER' | 'STUDENT' | 'ADMIN';
  password?: string;
}

export async function login(
  page: Page,
  email: string,
  passwordOrOptions?: string | LoginOptions
) {
  let explicitPassword: string | undefined;
  let explicitRole: 'TEACHER' | 'STUDENT' | 'ADMIN' | undefined;

  if (typeof passwordOrOptions === 'string') {
    explicitPassword = passwordOrOptions;
  } else if (passwordOrOptions) {
    explicitPassword = passwordOrOptions.password;
    explicitRole = passwordOrOptions.role;
  }

  // Resolve role: prefer explicit role, fallback to email prefix/substring heuristic
  const resolvedRole =
    explicitRole ||
    (email.toLowerCase().includes('admin') ? 'ADMIN' :
     email.toLowerCase().includes('teacher') ? 'TEACHER' :
     email.toLowerCase().includes('student') ? 'STUDENT' :
     undefined);

  const rolePassword =
    resolvedRole === 'ADMIN' ? process.env.E2E_ADMIN_PASSWORD :
    resolvedRole === 'TEACHER' ? process.env.E2E_TEACHER_PASSWORD :
    resolvedRole === 'STUDENT' ? process.env.E2E_STUDENT_PASSWORD :
    undefined;

  const resolvedPassword =
    explicitPassword ||
    rolePassword ||
    process.env.E2E_TEST_PASSWORD;

  if (!resolvedPassword) {
    throw new Error(
      `[Playwright Test Auth Error] No test password provided for user '${email}' (role: ${resolvedRole || 'UNSPECIFIED'}). ` +
      `Hardcoded default credentials have been eliminated from test fixtures. ` +
      `Please provide the password parameter to login() or configure environment variables: ` +
      `'E2E_TEST_PASSWORD' (or role-specific 'E2E_TEACHER_PASSWORD' / 'E2E_STUDENT_PASSWORD' / 'E2E_ADMIN_PASSWORD').`
    );
  }

  await page.goto('/login');
  
  // Setup request listener
  page.on('response', async response => {
    if (response.url().includes('/api/')) {
      console.log(`API Response ${response.request().method()} ${response.url()} -> ${response.status()}`);
    }
  });

  page.on('console', msg => {
    console.log(`Browser Console [${msg.type()}]: ${msg.text()}`);
  });
  page.on('pageerror', err => {
    console.log(`Page Exception: ${err.message}`);
  });

  await page.fill('input[name="identity"]', email);
  await page.fill('input[name="password"]', resolvedPassword);
  await page.click('button[type="submit"]');
  
  try {
    await page.waitForURL(/\/.*\/dashboard/);
  } catch (e) {
    await page.screenshot({ path: 'test-results/login-failure.png' });
    throw e;
  }
}

export interface MockAuthCookieOptions {
  role?: 'TEACHER' | 'STUDENT' | 'ADMIN';
  sessionToken?: string;
  baseUrl?: string;
}

/**
 * Standardized fixture to inject session and role cookies for mocked/isolated UI test suites.
 * Eliminates duplication across admin, teacher, and student mocked E2E specifications.
 */
export async function setMockAuthCookies(
  page: Page,
  options: MockAuthCookieOptions = {}
) {
  const role = options.role || 'TEACHER';
  const sessionToken = options.sessionToken || `mock-${role.toLowerCase()}-session-token`;
  const url = options.baseUrl || process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000';

  await page.context().addCookies([
    { name: 'codeassess_session', value: sessionToken, url },
    { name: 'codeassess_role', value: role, url },
    { name: 'codeassess_session', value: sessionToken, domain: 'localhost', path: '/' },
    { name: 'codeassess_role', value: role, domain: 'localhost', path: '/' },
  ]);
}

export interface MockUserProfileOptions {
  id?: string;
  email?: string;
  fullName?: string;
  role?: 'TEACHER' | 'STUDENT' | 'ADMIN';
}

/**
 * Standardized fixture to mock the /api/user/profile endpoint to satisfy AuthGuard.
 */
export async function mockUserProfile(
  page: Page,
  user: MockUserProfileOptions = {}
) {
  const role = user.role || 'TEACHER';
  const profile = {
    id: user.id || `mock-${role.toLowerCase()}-01`,
    email: user.email || `${role.toLowerCase()}@codeassess.edu.vn`,
    fullName: user.fullName || `Giảng viên/Người dùng Kiểm thử (${role})`,
    role,
  };

  await page.route('**/api/user/profile', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(profile),
    });
  });
}

export interface RetryOptions {
  maxAttempts?: number;
  initialDelayMs?: number;
  factor?: number;
  onRetry?: (attempt: number, errorOrStatus: any, delayMs: number) => void;
}

/**
 * Centralized retry utility for AI / Gemini integrations.
 * Strictly retries transient rate limits (429) or upstream unavailability (503).
 * Never retries deterministic client errors (400, 401, 403, 404).
 */
export async function withGeminiRetry<T>(
  action: () => Promise<T>,
  options: RetryOptions = {}
): Promise<T> {
  const maxAttempts = options.maxAttempts ?? 3;
  let delayMs = options.initialDelayMs ?? 5000;
  const factor = options.factor ?? 2;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const result = await action();
      if (result && typeof (result as any).status === 'function') {
        const status = (result as any).status();
        if (status === 429 || status === 503) {
          if (attempt === maxAttempts) {
            return result;
          }
          if (options.onRetry) {
            options.onRetry(attempt, status, delayMs);
          } else {
            console.log(`[Gemini Retry] Received transient HTTP ${status} (attempt ${attempt}/${maxAttempts}). Retrying in ${delayMs}ms...`);
          }
          await new Promise((resolve) => setTimeout(resolve, delayMs));
          delayMs = Math.round(delayMs * factor);
          continue;
        }
      }
      return result;
    } catch (err: any) {
      const errStr = String(err);
      const isTransient = errStr.includes('503') || errStr.includes('429') || errStr.includes('Service Unavailable') || errStr.includes('rate limit');
      if (isTransient && attempt < maxAttempts) {
        if (options.onRetry) {
          options.onRetry(attempt, err, delayMs);
        } else {
          console.log(`[Gemini Retry] Caught transient error on attempt ${attempt}/${maxAttempts}: ${errStr}. Retrying in ${delayMs}ms...`);
        }
        await new Promise((resolve) => setTimeout(resolve, delayMs));
        delayMs = Math.round(delayMs * factor);
        continue;
      }
      throw err;
    }
  }
  throw new Error(`[Gemini Retry] Exceeded max attempts (${maxAttempts}) without success.`);
}

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

/**
 * Execute targeted maintenance SQL against the Local Staging MySQL container.
 * Strictly restricted to codeassess-mysql on Staging Local.
 */
export function runStagingSql(sql: string): string {
  try {
    const { execSync } = require('child_process');
    return execSync('docker exec -i codeassess-mysql mysql -uroot -p123456 -Dcodeassess', {
      input: sql,
      encoding: 'utf-8',
      stdio: ['pipe', 'pipe', 'pipe']
    });
  } catch (error: any) {
    console.error('[Staging DB Teardown Error]:', error.stderr || error.message);
    throw error;
  }
}

/**
 * Cleans up synthetic exams created during test execution, respecting foreign key hierarchies.
 */
export function cleanupSyntheticExams(examIds: string[]): void {
  const validIds = examIds.filter(id => typeof id === 'string' && UUID_REGEX.test(id));
  if (validIds.length === 0) return;

  const idList = validIds.map(id => `'${id}'`).join(',');
  const sql = `
    DELETE FROM exam_classes WHERE exam_id IN (${idList});
    DELETE FROM test_cases WHERE exam_id IN (${idList});
    DELETE FROM exam_questions WHERE exam_id IN (${idList});
    DELETE FROM exam_rubric_criteria WHERE exam_id IN (${idList});
    DELETE FROM exam_matrix_coordination WHERE exam_id IN (${idList});
    DELETE FROM student_question_scores WHERE exam_id IN (${idList});
    DELETE FROM exams WHERE id IN (${idList});
  `;
  runStagingSql(sql);
  console.log(`[Test Teardown] Successfully purged ${validIds.length} synthetic exam(s): ${validIds.join(', ')}`);
}

/**
 * Cleans up synthetic submissions created during test execution, along with all downstream
 * AST fingerprints, similarity matrix records, plagiarism evidence, and grade audit logs.
 */
export function cleanupSyntheticSubmissions(submissionIds: string[]): void {
  const validIds = submissionIds.filter(id => typeof id === 'string' && UUID_REGEX.test(id));
  if (validIds.length === 0) return;

  const idList = validIds.map(id => `'${id}'`).join(',');
  const sql = `
    DELETE FROM plagiarism_evidence WHERE similarity_matrix_id IN (
      SELECT id FROM similarity_matrix WHERE submission_a_id IN (${idList}) OR submission_b_id IN (${idList})
    );
    DELETE FROM similarity_matrix WHERE submission_a_id IN (${idList}) OR submission_b_id IN (${idList});
    DELETE FROM ast_fingerprints WHERE submission_id IN (${idList});
    DELETE FROM submission_test_results WHERE submission_id IN (${idList});
    DELETE FROM rule_scores WHERE submission_id IN (${idList});
    DELETE FROM plagiarism_appeals WHERE submission_id IN (${idList});
    DELETE FROM grade_audit_logs WHERE submission_id IN (${idList});
    DELETE FROM submissions WHERE id IN (${idList});
  `;
  runStagingSql(sql);
  console.log(`[Test Teardown] Successfully purged ${validIds.length} synthetic submission(s) and downstream artifacts.`);
}

export interface SyntheticSubmissionOptions {
  id?: string;
  examId?: string;
  studentId?: string;
  questionId?: string;
  status?: string;
  totalScore?: number;
  overrideScore?: number | null;
  autoScore?: number;
}

/**
 * Creates an isolated synthetic submission fixture in Staging Local MySQL.
 * Eliminates the need to mutate existing seed submissions during tests.
 */
export function createSyntheticSubmissionFixture(
  options: SyntheticSubmissionOptions = {}
): string {
  const id = options.id || crypto.randomUUID();
  const examId = options.examId || 'exm-e2e-pro192-midterm1';
  const studentId = options.studentId || 'usr-e2e-student-000000000001';
  const questionId = options.questionId || '9f1d7d109538e47501e395f9c927ae72';
  const status = options.status || 'COMPLETED';
  const totalScore = options.totalScore ?? 0.00;
  const autoScore = options.autoScore ?? 0.00;
  const overrideScoreVal = options.overrideScore === undefined || options.overrideScore === null ? 'NULL' : options.overrideScore;

  const sql = `
    INSERT INTO submissions (
      id, exam_id, student_id, question_id, status, source_code,
      total_score, override_score, evaluated_at, created_at,
      submission_type, original_filename, execution_status, exit_code,
      compile_output, stdout, execution_duration_ms, auto_score,
      test_cases_passed, test_cases_total, review_status, publication_status, score_version
    ) VALUES (
      '${id}', '${examId}', '${studentId}', '${questionId}', '${status}',
      'public class SolutionSynthetic { public static void main(String[] args) { System.out.println("Synthetic override test"); } }',
      ${totalScore}, ${overrideScoreVal}, NOW(), NOW(),
      'SINGLE_FILE', 'SolutionSynthetic.java', 'SUCCESS', 0,
      'Compilation succeeded', 'Synthetic override test\\n', 150, ${autoScore},
      0, 5, 'PENDING_REVIEW', 'PUBLISHED', 1
    );
  `;
  runStagingSql(sql);
  console.log(`[Test Fixture] Created synthetic submission: ${id}`);
  return id;
}

/**
 * Cleans up a synthetic submission fixture and any audit logs created specifically for it.
 * Strictly guarantees that no seed submission or unrelated audit logs are touched.
 */
export function cleanupSyntheticSubmissionFixture(submissionId: string): void {
  if (!UUID_REGEX.test(submissionId)) return;

  const sql = `
    DELETE FROM grade_audit_logs WHERE submission_id = '${submissionId}';
    DELETE FROM rule_scores WHERE submission_id = '${submissionId}';
    DELETE FROM plagiarism_appeals WHERE submission_id = '${submissionId}';
    DELETE FROM submission_test_results WHERE submission_id = '${submissionId}';
    DELETE FROM ast_fingerprints WHERE submission_id = '${submissionId}';
    DELETE FROM submissions WHERE id = '${submissionId}';
  `;
  runStagingSql(sql);
  console.log(`[Test Teardown] Successfully purged synthetic submission fixture: ${submissionId}`);
}

export interface SubmissionSnapshot {
  id: string;
  overrideScore: string | null;
  status: string | null;
  totalScore: string | null;
}

/**
 * Captures a dynamic snapshot of a submission record prior to test execution.
 */
export function takeSubmissionSnapshot(submissionId: string): SubmissionSnapshot {
  if (!UUID_REGEX.test(submissionId)) {
    throw new Error(`Invalid UUID: ${submissionId}`);
  }
  const result = runStagingSql(
    `SELECT id, override_score, status, total_score FROM submissions WHERE id = '${submissionId}';`
  );
  const lines = result.trim().split('\n');
  if (lines.length < 2) {
    throw new Error(`Submission not found for snapshot: ${submissionId}`);
  }
  const parts = lines[1].split('\t');
  return {
    id: parts[0]?.trim(),
    overrideScore: parts[1]?.trim() === 'NULL' ? null : parts[1]?.trim(),
    status: parts[2]?.trim() === 'NULL' ? null : parts[2]?.trim(),
    totalScore: parts[3]?.trim() === 'NULL' ? null : parts[3]?.trim(),
  };
}

/**
 * Restores a submission record to its exact dynamic snapshot state without hardcoding.
 * Strictly purges only test-generated audit logs, preserving any pre-existing seed audit logs.
 */
export function restoreSubmissionSnapshot(
  snapshot: SubmissionSnapshot,
  options: { reviewerId?: string; action?: string } = {}
): void {
  if (!UUID_REGEX.test(snapshot.id)) return;

  const overrideVal = snapshot.overrideScore !== null ? `'${snapshot.overrideScore}'` : 'NULL';
  const statusVal = snapshot.status !== null ? `'${snapshot.status}'` : 'NULL';
  const totalVal = snapshot.totalScore !== null ? `'${snapshot.totalScore}'` : 'NULL';

  const reviewerClause = options.reviewerId ? ` AND reviewer_id = '${options.reviewerId}'` : '';
  const actionClause = options.action ? ` AND action = '${options.action}'` : '';

  const sql = `
    UPDATE submissions SET override_score = ${overrideVal}, status = ${statusVal}, total_score = ${totalVal} WHERE id = '${snapshot.id}';
    DELETE FROM grade_audit_logs WHERE submission_id = '${snapshot.id}'${reviewerClause}${actionClause};
  `;
  runStagingSql(sql);
  console.log(`[Test Teardown] Successfully restored submission ${snapshot.id} to dynamic snapshot.`);
}

/**
 * Fallback restoration helper using dynamic snapshot if synthetic fixture is bypassed.
 */
export function restoreSeedSubmissionScoring(submissionId: string = '007cf9ae-4385-46dc-b492-ba891af6a535'): void {
  if (!UUID_REGEX.test(submissionId)) return;

  // Surgical cleanup: only delete test-generated OVERRIDE_SET logs by test teacher
  const sql = `
    UPDATE submissions SET override_score = NULL, status = 'COMPLETED' WHERE id = '${submissionId}';
    DELETE FROM grade_audit_logs WHERE submission_id = '${submissionId}' AND action IN ('OVERRIDE_SET', 'OVERRIDE_CLEARED', 'OVERRIDE_UPDATE');
  `;
  runStagingSql(sql);
  console.log(`[Test Teardown] Restored submission ${submissionId} (surgical audit log cleanup).`);
}
