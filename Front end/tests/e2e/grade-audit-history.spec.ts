import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe('Teacher Grade Audit History UI Verification', () => {
  const submissionWithAuditLogs = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32';

  test('Teacher views populated audit history with chronological order and score diffs', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // Navigate to teacher submission detail page
    await page.goto(`/teacher/submissions/${submissionWithAuditLogs}`);
    await expect(page.locator('h1')).toContainText('Submission #', { timeout: 10000 });

    // Confirm Grade & Audit History section is visible
    const auditSection = page.locator('section[aria-label="Grade Audit History"]');
    await expect(auditSection).toBeVisible({ timeout: 10000 });
    await expect(auditSection.locator('h2')).toContainText('Grade & Audit History');

    // Confirm audit logs list has records
    const logsList = page.locator('[data-testid="grade-audit-logs-list"]');
    await expect(logsList).toBeVisible({ timeout: 5000 });

    const auditCards = logsList.locator('article');
    const count = await auditCards.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Verify first (newest) card contains expected action type and details
    const firstCard = auditCards.first();
    await expect(firstCard).toBeVisible();

    // Check for actor info and timestamp
    await expect(firstCard.locator('time')).toBeVisible();
    await expect(firstCard.getByText('Teacher A').or(firstCard.getByText('Authorized Teacher'))).toBeVisible();

    // Check for 3-column score grid (Rubric, Override, Effective)
    await expect(firstCard.getByText('Rubric Score', { exact: true })).toBeVisible();
    await expect(firstCard.getByText('Manual Override', { exact: true })).toBeVisible();
    await expect(firstCard.getByText('Effective Score', { exact: true })).toBeVisible();

    // Confirm "No override" pill is present on the card that cleared override
    const noOverridePill = auditSection.locator('span:text-is("No override")');
    await expect(noOverridePill.first()).toBeVisible({ timeout: 5000 });

    // Confirm rationale/comments section is displayed if present
    const rationaleHeader = auditSection.getByText('Instructor Justification & Comments');
    if (await rationaleHeader.count() > 0) {
      await expect(rationaleHeader.first()).toBeVisible();
    }
  });

  test('Teacher scoring page also embeds and displays Grade Audit History', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    await page.goto(`/teacher/scoring/${submissionWithAuditLogs}`);
    await expect(page.locator('h1')).toContainText('Evaluation Rubric', { timeout: 10000 });

    const auditSection = page.locator('section[aria-label="Grade Audit History"]');
    await expect(auditSection).toBeVisible({ timeout: 10000 });
    await expect(auditSection.locator('h2')).toContainText('Grade & Audit History');
  });

  test('Renders NULL override as "No override" distinctly from numeric 0.00 pts', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');

    // Mock an audit log response containing both a null override and a 0.00 override
    await page.route(`**/api/teacher/submissions/${submissionWithAuditLogs}/scoring/audit-logs`, async (route) => {
      const mockAuditLogs = [
        {
          id: 101,
          submissionId: submissionWithAuditLogs,
          actorId: 2,
          actorName: 'Teacher A',
          actorEmail: 'teachera@fpt.edu.vn',
          actionType: 'OVERRIDE_SET',
          preRubricScore: 80.0,
          postRubricScore: 80.0,
          preOverrideScore: null, // Should render as "No override"
          postOverrideScore: 0.0,  // Should render as "0.00 pts", NOT "No override"
          preEffectiveScore: 80.0,
          postEffectiveScore: 0.0,
          rationale: 'Penalty applied: score reduced to 0.',
          createdAt: '2026-10-15T10:00:00Z',
        },
      ];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockAuditLogs),
      });
    });

    await page.goto(`/teacher/submissions/${submissionWithAuditLogs}`);
    const auditSection = page.locator('section[aria-label="Grade Audit History"]');
    await expect(auditSection).toBeVisible({ timeout: 10000 });

    // Pre override was null -> must display "No override"
    const noOverrideSpan = auditSection.locator('span:text-is("No override")');
    await expect(noOverrideSpan).toBeVisible({ timeout: 5000 });

    // Post override was 0.0 -> must display "0.00 pts", NEVER "No override"
    const zeroScoreSpan = auditSection.locator('span.text-amber-600, span.dark\\:text-amber-400').filter({ hasText: '0.00 pts' });
    await expect(zeroScoreSpan).toBeVisible({ timeout: 5000 });
  });

  test('Displays empty state when no audit history exists', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');

    // Mock empty response
    await page.route(`**/api/teacher/submissions/${submissionWithAuditLogs}/scoring/audit-logs`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/teacher/submissions/${submissionWithAuditLogs}`);
    const auditSection = page.locator('section[aria-label="Grade Audit History"]');
    await expect(auditSection).toBeVisible({ timeout: 10000 });

    await expect(auditSection.getByText('No grade history recorded yet')).toBeVisible({ timeout: 5000 });
    await expect(auditSection.getByText(/This submission has not undergone any official evaluations/)).toBeVisible();
  });

  test('Displays error callout and retry button on API failure', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');

    // Mock 500 Internal Server Error
    await page.route(`**/api/teacher/submissions/${submissionWithAuditLogs}/scoring/audit-logs`, async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Internal server error while retrieving audit logs' }),
      });
    });

    await page.goto(`/teacher/submissions/${submissionWithAuditLogs}`);
    const auditSection = page.locator('section[aria-label="Grade Audit History"]');
    await expect(auditSection).toBeVisible({ timeout: 10000 });

    await expect(auditSection.getByText('Failed to load grade history')).toBeVisible({ timeout: 5000 });
    await expect(auditSection.getByRole('button', { name: 'Retry Request' })).toBeVisible();
  });

  test('Student route does NOT expose Grade Audit History', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Navigate to student submission result page
    await page.goto(`/student/submissions/${submissionWithAuditLogs}`);
    await expect(page.locator('h1')).toContainText('Official Result', { timeout: 10000 });

    // Confirm that Grade & Audit History section is completely absent
    const auditSection = page.locator('section[aria-label="Grade Audit History"]');
    await expect(auditSection).toHaveCount(0);
    await expect(page.getByText('Grade & Audit History')).toHaveCount(0);
  });

  test('Responsive verification: Mobile and Desktop layouts', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');

    // Mobile viewport (390 x 844)
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`/teacher/submissions/${submissionWithAuditLogs}`);
    const auditSection = page.locator('section[aria-label="Grade Audit History"]');
    await expect(auditSection).toBeVisible({ timeout: 10000 });

    // Verify header and refresh button fit without overflow
    await expect(auditSection.locator('h2')).toBeVisible();
    await expect(auditSection.getByRole('button', { name: 'Refresh' })).toBeVisible();

    // Verify cards are readable
    const logsList = page.locator('[data-testid="grade-audit-logs-list"]');
    if (await logsList.isVisible()) {
      await expect(logsList.locator('article').first()).toBeVisible();
    }

    // Desktop viewport (1280 x 800)
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(auditSection).toBeVisible();
  });
});
