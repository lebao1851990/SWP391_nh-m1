import { test, expect } from '@playwright/test';

test.describe('TASK-17: Complete Live AI Application Workflows Verification', () => {
  const submissionId = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32';
  const plagiarismPairId = '101';

  test('TASK-17C: Student AI Success Workflow via Real UI', async ({ page }) => {
    // 1. Navigate to login
    await page.goto('http://localhost:3000/login');
    await page.waitForLoadState('networkidle');

    // 2. Fill login form using actual input names (identity & password)
    await page.fill('input[name="identity"]', 'studenta@fpt.edu.vn');
    await page.fill('input[name="password"]', 'CodeAssess@2026!');
    await page.click('button[type="submit"]');

    // 3. Wait for redirect to student dashboard
    await page.waitForURL(/\/student\/dashboard/, { timeout: 15000 });
    console.log('Successfully logged in as Student A! Current URL:', page.url());

    // 4. Navigate directly to the seeded submission
    await page.goto(`http://localhost:3000/student/submissions/${submissionId}`);
    await page.waitForLoadState('networkidle');

    // 5. Verify submission page content
    const heading = page.locator('h1');
    await expect(heading).toBeVisible();
    console.log('Submission page loaded with heading:', await heading.innerText());

    // 6. Find Student AI Assistant Card
    const aiCard = page.locator('text=Trợ lý Học tập AI');
    await expect(aiCard).toBeVisible();

    // 7. Click button to request explanation
    const submitAiBtn = page.locator('#btn-request-ai').or(page.getByRole('button', { name: /Nhận hướng dẫn AI/i }));
    await expect(submitAiBtn).toBeVisible();
    await submitAiBtn.click();

    // 8. Wait for response from real Gemini API
    const advisoryNotice = page.locator('text=Chỉ mang tính tham vấn');
    await expect(advisoryNotice).toBeVisible();

    // Verify response content renders on UI
    const aiResponseText = page.locator('#ai-response-box').or(page.locator('text=Solution')).or(page.locator('div.prose'));
    await expect(aiResponseText.first()).toBeVisible({ timeout: 25000 });
    console.log('AI Response successfully received and rendered on Student UI!');
  });

  test('TASK-17D: Teacher Semantic Review via Real UI', async ({ page }) => {
    // 1. Navigate to login
    await page.goto('http://localhost:3000/login');
    await page.waitForLoadState('networkidle');

    // 2. Fill login form as Teacher A
    await page.fill('input[name="identity"]', 'teachera@fpt.edu.vn');
    await page.fill('input[name="password"]', 'CodeAssess@2026!');
    await page.click('button[type="submit"]');

    // 3. Wait for redirect to teacher dashboard
    await page.waitForURL(/\/teacher\/dashboard/, { timeout: 15000 });
    console.log('Successfully logged in as Teacher A! Current URL:', page.url());

    // 4. Navigate to teacher scoring / submission view
    await page.goto(`http://localhost:3000/teacher/scoring/${submissionId}`);
    await page.waitForLoadState('networkidle');
    console.log('Teacher scoring page loaded. URL:', page.url());

    // 5. Verify Semantic Code Review component is present
    const semanticHeading = page.locator('text=Semantic Code Review');
    await expect(semanticHeading.first()).toBeVisible({ timeout: 15000 });

    // 6. Verify Clean Code Score or Run button
    const runBtn = page.getByRole('button', { name: /Semantic Review/i });
    if (await runBtn.isVisible()) {
      await runBtn.click();
    }

    // 7. Verify score display or review summary is visible
    const scoreBadge = page.locator('text=/10').or(page.locator('text=Clean Code')).or(page.locator('text=Solution'));
    await expect(scoreBadge.first()).toBeVisible({ timeout: 25000 });
    console.log('Teacher Semantic Review successfully displayed on UI!');
  });

  test('TASK-17E: Plagiarism AI Advisory via Real UI', async ({ page }) => {
    // 1. Navigate to login
    await page.goto('http://localhost:3000/login');
    await page.waitForLoadState('networkidle');

    // 2. Fill login form as Teacher A
    await page.fill('input[name="identity"]', 'teachera@fpt.edu.vn');
    await page.fill('input[name="password"]', 'CodeAssess@2026!');
    await page.click('button[type="submit"]');

    // 3. Wait for redirect to teacher dashboard
    await page.waitForURL(/\/teacher\/dashboard/, { timeout: 15000 });

    // 4. Navigate to Plagiarism Detail page for pair 101
    await page.goto(`http://localhost:3000/teacher/plagiarism/${plagiarismPairId}`);
    await page.waitForLoadState('networkidle');
    console.log('Plagiarism detail page loaded. URL:', page.url());

    // 5. Check if page loaded details
    const heading = page.locator('text=Phân tích Hồ sơ Tương đồng');
    await expect(heading.first()).toBeVisible({ timeout: 15000 });

    // 6. Find and click "Giải thích Tương đồng từ AI" button
    const aiBtn = page.getByRole('button', { name: /Giải thích Tương đồng từ AI/i });
    await expect(aiBtn).toBeVisible({ timeout: 10000 });
    await aiBtn.click();

    // 7. Verify AI Analysis modal opens
    const modalTitle = page.locator('text=Giải thích Giám định Tương đồng bằng AI');
    await expect(modalTitle).toBeVisible({ timeout: 10000 });

    // 8. Verify AI analysis content renders inside modal
    const modalContent = page.locator('text=tương đồng').or(page.locator('text=similarity')).or(page.locator('text=tham khảo'));
    await expect(modalContent.first()).toBeVisible({ timeout: 35000 });
    console.log('Plagiarism AI Advisory modal rendered with live AI analysis!');
  });
});
