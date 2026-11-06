import { test, expect } from '@playwright/test';
import {
  login,
  createSyntheticSubmissionFixture,
  cleanupSyntheticSubmissionFixture,
  runStagingSql,
} from './helpers';

const STUDENT_A = 'studenta@fpt.edu.vn';
const STUDENT_B = 'studentb@fpt.edu.vn';
const TEACHER_EMAIL = 'teachera@fpt.edu.vn';

test.describe('WebSocket Browser End-to-End Real-Time Verification', () => {
  let syntheticSubmissionId: string;

  test.beforeEach(() => {
    // Create an isolated synthetic submission fixture belonging to Student A
    syntheticSubmissionId = createSyntheticSubmissionFixture({
      totalScore: 5.0,
      autoScore: 5.0,
      overrideScore: null,
      status: 'COMPLETED',
    });
  });

  test.afterEach(() => {
    if (syntheticSubmissionId) {
      cleanupSyntheticSubmissionFixture(syntheticSubmissionId);
    }
  });

  test('1. Student connects to WebSocket via session cookie and displays Live connection badge', async ({ page }) => {
    await login(page, STUDENT_A);

    // Navigate to student submissions list
    await page.goto('/student/submissions');
    await page.waitForLoadState('networkidle');

    // Verify ConnectionStatusIndicator badge is mounted and renders Live state
    const liveIndicator = page.locator('text=Live').first();
    await expect(liveIndicator).toBeVisible({ timeout: 10000 });
  });

  test('2. Live real-time score and status update delivered via WebSocket WITHOUT page reload', async ({ page, request }) => {
    await login(page, STUDENT_A);

    // Open synthetic submission detail
    await page.goto(`/student/submissions/${syntheticSubmissionId}`);
    await page.waitForLoadState('networkidle');

    // Verify initial score contains 5
    const scoreDisplay = page.locator('text=/^5(\\.0+)?$/').first();
    await expect(scoreDisplay).toBeVisible({ timeout: 10000 });

    // Verify WebSocket is connected and Live badge is active
    const liveIndicator = page.locator('text=Live').first();
    await expect(liveIndicator).toBeVisible({ timeout: 10000 });

    // Login as Teacher via API request context to trigger a live score override
    const teacherLoginRes = await request.post('http://localhost:8081/api/auth/login', {
      data: {
        email: TEACHER_EMAIL,
        password: process.env.E2E_TEST_PASSWORD || 'TestPass123!',
      },
    });
    expect(teacherLoginRes.status()).toBe(200);
    const teacherCookie = teacherLoginRes.headers()['set-cookie']?.split(';')[0];

    // Teacher applies manual score override from 5 -> 9.5
    const overrideRes = await request.put(`http://localhost:8081/api/teacher/submissions/${syntheticSubmissionId}/scoring`, {
      headers: {
        Cookie: teacherCookie,
        'Content-Type': 'application/json',
      },
      data: {
        overrideTotalScore: 9.5,
        overrideRationale: 'WebSocket E2E Real-time verification test',
        instructorEvaluationComments: 'Live WebSocket push update',
      },
    });
    expect(overrideRes.status()).toBe(200);

    // CRITICAL: Verify that the student browser updates to 9.5 in the DOM WITHOUT page reload!
    const updatedScoreDisplay = page.locator('text=/^9\\.5/').first();
    await expect(updatedScoreDisplay).toBeVisible({ timeout: 15000 });

    // Verify "Giảng viên điều chỉnh" badge is updated dynamically
    const gradedBadge = page.locator('text=Giảng viên điều chỉnh').first();
    await expect(gradedBadge).toBeVisible({ timeout: 5000 });
  });

  test('3. Network disconnect transitions to Polling fallback and auto-recovers to Live', async ({ page, context }) => {
    await login(page, STUDENT_A);

    await page.goto('/student/submissions');
    await page.waitForLoadState('networkidle');

    // Initially Live
    const liveIndicator = page.locator('text=Live').first();
    await expect(liveIndicator).toBeVisible({ timeout: 10000 });

    // Simulate network disconnect / offline
    await context.setOffline(true);

    // Expect transition to Polling fallback or Reconnecting state
    const offlineIndicator = page.locator('text=Polling fallback').or(page.locator('text=Reconnecting'));
    await expect(offlineIndicator.first()).toBeVisible({ timeout: 10000 });

    // Restore network connection
    await context.setOffline(false);

    // Expect recovery to Live
    await expect(liveIndicator).toBeVisible({ timeout: 15000 });
  });

  test('4. Cross-student subscription isolation strictly enforced (Student B cannot receive Student A events)', async ({ page, request }) => {
    // Student B logs in
    await login(page, STUDENT_B);

    // Student B navigates to submissions
    await page.goto('/student/submissions');
    await page.waitForLoadState('networkidle');

    // Verify Student B has Live connection to their own user channel
    const liveIndicator = page.locator('text=Live').first();
    await expect(liveIndicator).toBeVisible({ timeout: 10000 });

    // Now, trigger an update to Student A's submission
    const teacherLoginRes = await request.post('http://localhost:8081/api/auth/login', {
      data: {
        email: TEACHER_EMAIL,
        password: process.env.E2E_TEST_PASSWORD || 'TestPass123!',
      },
    });
    const teacherCookie = teacherLoginRes.headers()['set-cookie']?.split(';')[0];

    await request.put(`http://localhost:8081/api/teacher/submissions/${syntheticSubmissionId}/scoring`, {
      headers: {
        Cookie: teacherCookie,
        'Content-Type': 'application/json',
      },
      data: {
        overrideTotalScore: 8.0,
        overrideRationale: 'Cross-student isolation check',
      },
    });

    // Wait 2 seconds and verify Student B's submissions page never showed Student A's submission
    await page.waitForTimeout(2000);
    const studentASubmissionRow = page.locator(`text=${syntheticSubmissionId}`);
    await expect(studentASubmissionRow).toHaveCount(0);
  });
});

