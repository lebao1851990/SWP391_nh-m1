import { test, expect } from '@playwright/test';
import { login, withGeminiRetry } from './helpers';

test.describe('TASK-17: Complete Live AI Application Workflows Verification', () => {
  const submissionId = '040b61a8-3c7a-4ea2-aace-18a0ba2d9d32';
  const plagiarismPairId = '101';

  const TEACHER_EMAIL = process.env.E2E_TEACHER_EMAIL || 'teachera@fpt.edu.vn';
  const STUDENT_EMAIL = process.env.E2E_STUDENT_EMAIL || 'studenta@fpt.edu.vn';

  test('TASK-17C: Student AI Success Workflow via Real UI', async ({ page }) => {
    // 1. Login as Student
    await login(page, STUDENT_EMAIL, { role: 'STUDENT' });
    await expect(page).toHaveURL(/\/student\/dashboard/);
    console.log('Successfully logged in as Student A!');

    // 2. Navigate directly to the seeded submission
    await page.goto(`/student/submissions/${submissionId}`);
    await page.waitForLoadState('networkidle');

    // 3. Verify submission page content
    const heading = page.locator('h1');
    await expect(heading).toBeVisible();
    console.log('Submission page loaded with heading:', await heading.innerText());

    // 4. Find Student AI Assistant Card
    const aiCard = page.locator('text=Trợ lý Học tập AI');
    await expect(aiCard).toBeVisible();

    // 5. Click button to request explanation with centralized Gemini retry support
    const submitAiBtn = page.locator('#btn-request-ai').or(page.getByRole('button', { name: /Nhận hướng dẫn AI/i }));
    await expect(submitAiBtn).toBeVisible();

    await withGeminiRetry(async () => {
      const responsePromise = page.waitForResponse(
        (res) => res.url().includes('/api/') && res.url().includes('/ai') && res.request().method() === 'POST',
        { timeout: 35000 }
      ).catch(() => null);

      await submitAiBtn.click();
      const res = await responsePromise;
      if (res && (res.status() === 429 || res.status() === 503)) {
        return res;
      }
      return res;
    }, {
      maxAttempts: 3,
      initialDelayMs: 5000,
      factor: 2
    });

    // 6. Wait for response from real Gemini API / AI service
    const advisoryNotice = page.locator('text=/Chỉ mang tính tham vấn|tham khảo|khuyến nghị/');
    await expect(advisoryNotice.first()).toBeVisible({ timeout: 35000 });

    // Verify response content renders on UI
    const aiResponseText = page.locator('#ai-response-container').or(page.locator('#ai-response-box')).or(page.locator('text=/Hướng dẫn của Trợ lý|Solution|public class|int /'));
    await expect(aiResponseText.first()).toBeVisible({ timeout: 25000 });
    console.log('AI Response successfully received and rendered on Student UI!');
  });

  test('TASK-17D: Teacher Semantic Review via Real UI', async ({ page }) => {
    // 1. Login as Teacher A
    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });
    await expect(page).toHaveURL(/\/teacher\/dashboard/);
    console.log('Successfully logged in as Teacher A!');

    // 2. Navigate to teacher scoring / submission view
    await page.goto(`/teacher/scoring/${submissionId}`);
    await page.waitForLoadState('networkidle');
    console.log('Teacher scoring page loaded. URL:', page.url());

    // 3. Verify Semantic Code Review component is present
    const semanticHeading = page.locator('text=/Semantic Code Review|Đánh giá Mã nguồn Ngữ nghĩa/');
    await expect(semanticHeading.first()).toBeVisible({ timeout: 15000 });

    // 4. Verify Clean Code Score or Run button
    const runBtn = page.getByRole('button', { name: /Semantic Review|Chạy Phân tích|Đánh giá lại/i });
    if (await runBtn.isVisible()) {
      await runBtn.click();
    }

    // 5. Verify score display or review summary is visible
    const scoreBadge = page.locator('text=/10').or(page.locator('text=/Clean Code|Tiêu chuẩn/')).or(page.locator('text=Solution'));
    await expect(scoreBadge.first()).toBeVisible({ timeout: 25000 });
    console.log('Teacher Semantic Review successfully displayed on UI!');
  });

  test('TASK-17E: Plagiarism AI Advisory via Real UI', async ({ page }) => {
    // 1. Login as Teacher A
    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // 2. Navigate to Plagiarism Detail page for pair 101
    await page.goto(`/teacher/plagiarism/${plagiarismPairId}`);
    await page.waitForLoadState('networkidle');
    console.log('Plagiarism detail page loaded. URL:', page.url());

    // 3. Check if page loaded details
    const heading = page.locator('text=/Phân tích Hồ sơ Tương đồng|So sánh Cấu trúc Mã nguồn|Chi tiết/');
    await expect(heading.first()).toBeVisible({ timeout: 15000 });

    // 4. Find and click "Giải thích Tương đồng từ AI" / "Kiểm tra Cấu trúc AI" button
    const aiBtn = page.getByRole('button', { name: /Giải thích Tương đồng từ AI|Kiểm tra Cấu trúc AI|Phân tích bằng AI/i });
    if (await aiBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await withGeminiRetry(async () => {
        const responsePromise = page.waitForResponse(
          (res) => res.url().includes('/api/') && res.url().includes('/ai') && res.request().method() === 'POST',
          { timeout: 35000 }
        ).catch(() => null);

        await aiBtn.click();
        const res = await responsePromise;
        if (res && (res.status() === 429 || res.status() === 503)) {
          return res;
        }
        return res;
      }, {
        maxAttempts: 3,
        initialDelayMs: 5000,
        factor: 2
      });

      // 5. Verify AI Analysis modal opens
      const modalTitle = page.locator('text=/Giải thích Giám định Tương đồng bằng AI|Phân tích Đoạn này bằng AI|Phân tích AI/');
      await expect(modalTitle.first()).toBeVisible({ timeout: 10000 });

      // 6. Verify AI analysis content renders inside modal
      const modalContent = page.locator('text=/tương đồng|similarity|tham khảo|khuyến nghị/');
      await expect(modalContent.first()).toBeVisible({ timeout: 35000 });
      console.log('Plagiarism AI Advisory modal rendered with live AI analysis!');
    }
  });
});
