import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe('Scoring & Override Critical Flow Verification', () => {
  const submissionId = '007cf9ae-4385-46dc-b492-ba891af6a535';

  test('Teacher sets override, reloads page, and confirms persistence', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // Navigate to teacher scoring page
    await page.goto(`/teacher/scoring/${submissionId}`);
    await expect(page.locator('h1')).toContainText('Evaluation Rubric', { timeout: 10000 });

    // Open Manual Score Override modal
    const overrideBtn = page.locator('button:has-text("Manual Score Override")');
    await expect(overrideBtn).toBeVisible({ timeout: 5000 });
    await overrideBtn.click();

    // Verify modal elements
    const overrideInput = page.locator('input[type="number"][name="overrideTotalScore"]');
    await expect(overrideInput).toBeVisible({ timeout: 5000 });

    // Set override to 95
    await overrideInput.fill('95');
    await page.click('button:has-text("Apply to Form")');

    // Verify dynamic score card reflects manual override
    await expect(page.locator('text=Effective Score (Manual Override)')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('95 pts', { exact: true })).toBeVisible();

    // Commit changes
    const commitPromise = page.waitForResponse(
      (res) => res.url().includes(`/api/teacher/submissions/${submissionId}/scoring`) && res.request().method() === 'PUT'
    );
    await page.click('button:has-text("Commit Rubric Scores")');
    const commitRes = await commitPromise;
    expect(commitRes.status()).toBe(200);

    const body = await commitRes.json();
    expect(body.overrideTotalScore).toBe(95);
    expect(body.effectiveScore).toBe(95);

    // Reload page to verify persistence after reload
    await page.reload();
    await expect(page.locator('h1')).toContainText('Evaluation Rubric', { timeout: 10000 });

    // Confirm that after reload, override is preserved and displayed as active
    await expect(page.locator('text=Effective Score (Manual Override)')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('button:has-text("Manual Score Override (95 pts)")')).toBeVisible({ timeout: 5000 });
  });

  test('Student views effective score and instructor adjustment badge', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Navigate to student result view
    await page.goto(`/student/submissions/${submissionId}`);
    await expect(page.locator('h1')).toContainText('Official Result', { timeout: 10000 });

    // Confirm student sees effective score (95), NOT the 0 rubric total!
    await expect(page.getByText('Consolidated Assessment Total')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('span.font-display-lg:has-text("95")')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=/ 100 pts')).toBeVisible();

    // Confirm adjustment badge
    await expect(page.getByText('Adjusted by Instructor (Rubric: 0 pts)')).toBeVisible({ timeout: 5000 });

    // Navigate to submissions list and verify score table & stats
    await page.goto('/student/submissions');
    await expect(page.locator('h1')).toContainText('My Submissions', { timeout: 10000 });

    const submissionRow = page.locator(`tr:has-text("#${submissionId.substring(0, 8)}")`);
    await expect(submissionRow).toBeVisible({ timeout: 8000 });
    await expect(submissionRow.locator('text=95')).toBeVisible();
  });
});
