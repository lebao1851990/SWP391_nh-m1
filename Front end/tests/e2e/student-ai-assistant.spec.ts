import { test, expect } from '@playwright/test';

test.describe('Student AI Learning Assistant UI Verification', () => {
  const submissionId = 'test-sub-12345';

  test.beforeEach(async ({ page }) => {
    // Set student authentication cookies to satisfy proxy/middleware
    await page.context().addCookies([
      { name: 'codeassess_session', value: 'mock-session-token', url: 'http://localhost:3000' },
      { name: 'codeassess_role', value: 'STUDENT', url: 'http://localhost:3000' },
    ]);

    // Mock user profile to satisfy AuthGuard on student route
    await page.route('**/api/user/profile', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'student-01',
          email: 'student@codeassess.edu.vn',
          fullName: 'Sinh viên Kiểm thử',
          role: 'STUDENT',
        }),
      });
    });

    // Mock student result API
    await page.route(`**/api/student/submissions/${submissionId}/result`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          submissionId,
          examName: 'Midterm Java Assessment',
          status: 'graded',
          evaluatedAt: '2026-10-15T10:00:00Z',
          totalScore: 88,
          maxTotalScore: 100,
          criteria: [
            {
              criterionId: 'c1',
              name: 'Code Correctness',
              description: 'Passes core test cases',
              score: 40,
              maxScore: 50,
              rules: [],
            },
          ],
        }),
      });
    });
  });

  test('Renders AI assistant card, toggles modes, and receives successful feedback', async ({ page }) => {
    // Mock the student assist endpoint
    await page.route(`**/api/student/submissions/${submissionId}/ai/assist`, async (route) => {
      const request = route.request();
      const postData = JSON.parse(request.postData() || '{}');
      expect(postData.mode).toBe('CODE_EXPLANATION');

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          mode: 'CODE_EXPLANATION',
          explanation: 'Your code declares a Main class with an entrypoint. It executes sequentially without loops.',
          advisoryNotice:
            'Advisory Notice: AI-generated learning guidance is educational only and may be incomplete or inaccurate. It does not replace teacher assessment, alter test suite outcomes, or modify assignment scores.',
        }),
      });
    });

    await page.goto(`/student/submissions/${submissionId}`);

    // Verify header and rubric
    await expect(page.locator('h1')).toContainText('Kết quả Chính thức');

    // Verify AI Assistant Card
    await expect(page.getByText('Trợ lý Học tập AI', { exact: true })).toBeVisible();
    await expect(page.getByText('Chỉ mang tính tham vấn', { exact: true })).toBeVisible();

    // Switch tab to Explain Code
    const explainCodeTab = page.locator('#tab-code-explanation');
    await explainCodeTab.click();
    await expect(page.getByText('Chế độ đã chọn: Giải thích mã nguồn')).toBeVisible();

    // Click Get AI Guidance
    const getGuidanceBtn = page.locator('#btn-request-ai');
    await getGuidanceBtn.click();

    // Confirm AI response is displayed
    const responseContainer = page.locator('#ai-response-container');
    await expect(responseContainer).toBeVisible({ timeout: 5000 });
    await expect(responseContainer).toContainText('Your code declares a Main class with an entrypoint.');

    // Confirm Advisory notice is prominently rendered
    await expect(responseContainer).toContainText('Lưu ý tham vấn');

    // Confirm official score remains unaffected
    await expect(page.locator('span:text-is("88")')).toBeVisible();
  });

  test('Displays 403 Forbidden message during active examination', async ({ page }) => {
    await page.route(`**/api/student/submissions/${submissionId}/ai/assist`, async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'AI Learning Assistant is not permitted during an active examination. Please wait until the assessment has concluded.',
        }),
      });
    });

    await page.goto(`/student/submissions/${submissionId}`);
    const getGuidanceBtn = page.locator('#btn-request-ai');
    await getGuidanceBtn.click();

    const errorBanner = page.locator('#ai-error-banner');
    await expect(errorBanner).toBeVisible({ timeout: 5000 });
    await expect(errorBanner).toContainText('Trợ lý học tập AI bị khóa trong thời gian diễn ra bài thi trực tiếp');
  });

  test('Displays 429 Rate Limit Exceeded message when limit is hit', async ({ page }) => {
    await page.route(`**/api/student/submissions/${submissionId}/ai/assist`, async (route) => {
      await route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'AI assistance rate limit exceeded (maximum 10 requests per minute). Please wait a moment before trying again.',
        }),
      });
    });

    await page.goto(`/student/submissions/${submissionId}`);
    const getGuidanceBtn = page.locator('#btn-request-ai');
    await getGuidanceBtn.click();

    const errorBanner = page.locator('#ai-error-banner');
    await expect(errorBanner).toBeVisible({ timeout: 5000 });
    await expect(errorBanner).toContainText('Đã đạt giới hạn yêu cầu');
  });
});
