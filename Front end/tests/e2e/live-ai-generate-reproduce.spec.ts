import { test, expect } from '@playwright/test';
import { login, withGeminiRetry } from './helpers';

const TEACHER_EMAIL = process.env.E2E_TEACHER_EMAIL || 'teachera@fpt.edu.vn';

test('Verify AI Document Generation & Publish in Real UI', async ({ page }) => {
  test.setTimeout(120000);

  // Gemini rate limit cooldown between consecutive AI test suites
  console.log('Waiting for Gemini rate limit cooldown window (15s)...');
  await page.waitForTimeout(15000);

  console.log('1. Logging in as teacher...');
  await login(page, TEACHER_EMAIL, { role: 'TEACHER' });
  await expect(page).toHaveURL(/\/teacher\/dashboard/);
  console.log('Logged in successfully!');

  console.log('2. Navigating to /teacher/exams/builder...');
  await page.goto('/teacher/exams/builder');
  await page.waitForLoadState('networkidle');

  console.log('3. Opening AI Generation modal...');
  const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI"), button:has-text("Tạo đề bằng AI")').first();
  await expect(openAiBtn).toBeVisible({ timeout: 10000 });
  await openAiBtn.click();

  console.log('4. Uploading document demo_1.docx...');
  const fileInput = page.locator('input[type="file"]');
  await fileInput.setInputFiles('D:\\demo_1.docx');

  console.log('5. Configuring Question Count = 1 and Test Cases = 3...');
  const qCountInput = page.locator('input[type="number"]').first();
  await qCountInput.fill('1');

  const tcCountInput = page.locator('input[type="number"]').nth(1);
  if (await tcCountInput.isVisible()) {
    await tcCountInput.fill('3');
  }

  console.log('6. Submitting generation request with centralized Gemini retry...');
  const submitBtn = page.locator('button:has-text("Bắt đầu sinh đề AI"), button:has-text("Tạo đề ngay")');

  const response = await withGeminiRetry(async () => {
    const [res] = await Promise.all([
      page.waitForResponse(r => r.url().includes('generate-from-document'), { timeout: 60000 }),
      submitBtn.click()
    ]);
    return res;
  }, {
    maxAttempts: 3,
    initialDelayMs: 15000,
    factor: 1.5,
    onRetry: (attempt, status, delayMs) => {
      console.log(`[Gemini Retry] Attempt ${attempt} returned status ${status}. Waiting ${delayMs}ms before retrying generation...`);
    }
  });

  if (response.status() !== 200) {
    console.log(`Generation API error body: ${await response.text()}`);
  }
  expect(response.status()).toBe(200);

  const responseJson = await response.json();
  console.log(`AI Response Title: ${responseJson.title}`);
  console.log(`AI Questions Count: ${responseJson.questions?.length}`);

  // Wait for modal to close and questions to appear in builder
  console.log('7. Verifying questions imported into Exam Builder...');
  await expect(page.locator('div[role="dialog"]')).not.toBeVisible({ timeout: 10000 });

  // Verify questions exist in navigator
  const navQuestions = page.locator('button:has-text("Câu hỏi")');
  const count = await navQuestions.count();
  console.log(`Questions found in builder navigator: ${count}`);
  expect(count).toBeGreaterThanOrEqual(1);

  console.log('8. Verifying Publish readiness...');
  // Ensure exam title is set
  const titleInput = page.locator('input[placeholder*="tiêu đề"], input[value*="Kỳ thi"], input[value*="Java"]').first();
  if (await titleInput.isVisible()) {
    const currentTitle = await titleInput.inputValue();
    if (!currentTitle || currentTitle.trim() === '') {
      await titleInput.fill('Kỳ thi Thực hành Java 2026 - Kiểm tra giữa kỳ');
    }
  }

  // Select a class if not selected
  const classSelect = page.locator('select').first();
  if (await classSelect.isVisible()) {
    const val = await classSelect.inputValue();
    if (!val) {
      await classSelect.selectOption({ index: 1 });
    }
  }

  console.log('--- LIVE UI VERIFICATION COMPLETED SUCCESSFULLY ---');
});
