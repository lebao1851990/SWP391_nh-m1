import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe('Role Boundary, Teacher Feedback, and ZIP Submission Regression Tests', () => {

  test('Priority 1: Unauthenticated request to protected route redirects to /login, not /teacher/dashboard', async ({ page }) => {
    // Clear cookies
    await page.context().clearCookies();

    // Directly navigate to student dashboard
    await page.goto('/student/dashboard');
    await expect(page).toHaveURL(/\/login/);

    // Directly navigate to teacher dashboard
    await page.goto('/teacher/dashboard');
    await expect(page).toHaveURL(/\/login/);

    // Directly navigate to admin dashboard
    await page.goto('/admin/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('Priority 1: Student role cannot access Teacher or Admin routes and is never promoted to Teacher', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Verify session store and header displays Student role
    await expect(page.getByText('STUDENT', { exact: true })).toBeVisible();

    // Attempt direct navigation to Teacher dashboard
    await page.goto('/teacher/dashboard');
    // Must be blocked and redirected back to student dashboard or login
    await expect(page).not.toHaveURL(/\/teacher\/dashboard/);
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Attempt direct navigation to Admin dashboard
    await page.goto('/admin/dashboard');
    await expect(page).not.toHaveURL(/\/admin\/dashboard/);
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Ensure role cookie was not tampered or converted to TEACHER
    const cookies = await page.context().cookies();
    const roleCookie = cookies.find(c => c.name === 'codeassess_role');
    expect(roleCookie?.value).toBe('STUDENT');
  });

  test('Priority 2: Student can view Instructor Evaluation Feedback on submission result page', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Navigate to Submissions tab
    await page.click('a[href="/student/submissions"]');
    await page.waitForURL(/\/student\/submissions/);

    // Check if there are any submissions listed
    const viewButtons = page.locator('a:has-text("View")');
    const count = await viewButtons.count();

    if (count > 0) {
      // Click first submission
      await viewButtons.first().click();
      await page.waitForURL(/\/student\/submissions\/[a-zA-Z0-9-]+/);

      // Verify the instructor feedback card is rendered
      const feedbackCard = page.locator('[data-testid="instructor-feedback-card"]');
      await expect(feedbackCard).toBeVisible();

      // Ensure internal-only strings like "Override Rationale:" are NOT rendered
      await expect(page.locator('text=Override Rationale:')).toHaveCount(0);
    }
  });

  test('Priority 3: Student exam page presents ZIP upload tab as the primary submission method', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Navigate to Exams list
    await page.click('a[href="/student/exams"]');
    await page.waitForURL(/\/student\/exams/);

    // Check for an available exam
    const takeExamButtons = page.locator('a:has-text("Take Exam"), a:has-text("View Exam")');
    const examCount = await takeExamButtons.count();

    if (examCount > 0) {
      await takeExamButtons.first().click();
      await page.waitForURL(/\/student\/exams\/[a-zA-Z0-9-]+/);

      // Verify Submission Workspace tabs exist
      const zipTab = page.locator('button:has-text("ZIP Archive (Official)")');
      const directCodeTab = page.locator('button:has-text("Direct Code")');

      await expect(zipTab).toBeVisible();
      await expect(directCodeTab).toBeVisible();

      // Verify ZIP dropzone is active by default
      await expect(page.locator('text=Drop your .zip file here or click to browse')).toBeVisible();
      await expect(page.locator('text=Maximum size: 10 MB. Root entry point must be')).toBeVisible();

      // Verify switching to Direct Code tab displays code editor
      await directCodeTab.click();
      await expect(page.locator('text=Write or paste your code directly into the editor')).toBeVisible();

      // Switch back to ZIP tab
      await zipTab.click();
      await expect(page.locator('text=Drop your .zip file here or click to browse')).toBeVisible();
    }
  });

});
