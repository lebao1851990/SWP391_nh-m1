import { test, expect } from '@playwright/test';
import {
  login,
  createSyntheticSubmissionFixture,
  cleanupSyntheticSubmissionFixture,
} from './helpers';

const TEACHER_EMAIL = process.env.E2E_TEACHER_EMAIL || 'teachera@fpt.edu.vn';
const STUDENT_EMAIL = process.env.E2E_STUDENT_EMAIL || 'studenta@fpt.edu.vn';

test.describe.serial('Scoring & Override Critical Flow Verification', () => {
  let submissionId: string;

  test.beforeAll(async () => {
    // Isolated Synthetic Submission Fixture: Does NOT mutate existing seed submission 007cf9ae...
    submissionId = createSyntheticSubmissionFixture();
  });

  test.afterAll(async () => {
    // Automated Fixture Teardown: Purges synthetic submission and its specific audit logs
    if (submissionId) {
      cleanupSyntheticSubmissionFixture(submissionId);
    }
  });

  test('Teacher sets override, reloads page, and confirms persistence', async ({ page }) => {
    expect(submissionId).toBeTruthy();

    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // Navigate to teacher scoring page
    await page.goto(`/teacher/scoring/${submissionId}`);
    await expect(page.locator('h1')).toContainText(/Bảng điểm Đánh giá|Evaluation Rubric/, { timeout: 10000 });

    // Open Manual Score Override modal
    const overrideBtn = page.locator('button:has-text("Điều chỉnh điểm thủ công"), button:has-text("Manual Score Override")');
    await expect(overrideBtn).toBeVisible({ timeout: 5000 });
    await overrideBtn.click();

    // Verify modal elements
    const overrideInput = page.locator('input[type="number"][name="overrideTotalScore"]');
    await expect(overrideInput).toBeVisible({ timeout: 5000 });

    // Set override to 9.5 (on standard 10-point scale)
    await overrideInput.fill('9.5');
    await page.click('button:has-text("Áp dụng vào form"), button:has-text("Apply to Form")');

    // Verify dynamic score card reflects manual override
    await expect(page.locator('text=Điểm Hiệu lực (Điều chỉnh thủ công)').or(page.locator('text=Effective Score (Manual Override)'))).toBeVisible({ timeout: 5000 });
    await expect(page.locator('div.font-display-lg').filter({ hasText: '9.5' }).first()).toBeVisible();

    // Commit changes
    const commitPromise = page.waitForResponse(
      (res) => res.url().includes(`/api/teacher/submissions/${submissionId}/scoring`) && res.request().method() === 'PUT'
    );
    await page.click('button:has-text("Lưu bảng điểm"), button:has-text("Commit Rubric Scores")');
    const commitRes = await commitPromise;
    expect(commitRes.status()).toBe(200);

    const body = await commitRes.json();
    expect(body.overrideTotalScore).toBe(9.5);
    expect(body.effectiveScore).toBe(9.5);

    // Reload page to verify persistence after reload
    await page.reload();
    await expect(page.locator('h1')).toContainText(/Bảng điểm Đánh giá|Evaluation Rubric/, { timeout: 10000 });

    // Confirm that after reload, override is preserved and displayed as active
    await expect(page.locator('text=Điểm Hiệu lực (Điều chỉnh thủ công)').or(page.locator('text=Effective Score (Manual Override)'))).toBeVisible({ timeout: 5000 });
    await expect(page.locator('button:has-text("Điều chỉnh điểm thủ công (9.5 đ)"), button:has-text("Manual Score Override (9.5 pts)")')).toBeVisible({ timeout: 5000 });
  });

  test('Student views effective score and instructor adjustment badge', async ({ page }) => {
    expect(submissionId).toBeTruthy();

    await login(page, STUDENT_EMAIL, { role: 'STUDENT' });
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Navigate to student result view
    await page.goto(`/student/submissions/${submissionId}`);
    await expect(page.locator('h1')).toContainText(/Kết quả Chính thức|Official Result/, { timeout: 10000 });

    // Confirm student sees effective score (9.5), NOT the 0 rubric total!
    await expect(page.getByText(/Tổng điểm đánh giá hợp nhất|Consolidated Assessment Total/)).toBeVisible({ timeout: 5000 });
    await expect(page.locator('span.font-display-lg:has-text("9.5")')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=/ 10 điểm').or(page.locator('text=/ 10 pts')).or(page.locator('text=/ 10'))).toBeVisible();

    // Confirm adjustment badge
    await expect(page.getByText(/Giảng viên điều chỉnh|Adjusted by Instructor/)).toBeVisible({ timeout: 5000 });

    // Navigate to submissions list and verify score table & stats
    await page.goto('/student/submissions');
    await expect(page.locator('h1')).toContainText(/Bài nộp của tôi|My Submissions/, { timeout: 10000 });

    const submissionRow = page.locator(`tr:has-text("#${submissionId.substring(0, 8)}")`);
    await expect(submissionRow).toBeVisible({ timeout: 8000 });
    await expect(submissionRow.locator('text=/9.5|Chờ chấm|Pending/').first()).toBeVisible();
    await expect(submissionRow.getByRole('link', { name: /Xem kết quả|View Result/i })).toBeVisible();
  });
});
