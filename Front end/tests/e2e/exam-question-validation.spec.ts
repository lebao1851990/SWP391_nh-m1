import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('Exam and Question Form Validation (TASK-FE-VALIDATION-01C)', () => {
  test.beforeEach(async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, { role: 'TEACHER' });

    await page.route('**/api/teacher/classes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'class-1', name: 'Lớp Lập trình Java - CS101', code: 'CS101' },
        ]),
      });
    });

    await page.goto('/teacher/exams/builder');

    // Add 1 question
    const addQuestionBtn = page.locator('button:has-text("+ Thêm câu hỏi thủ công")').first();
    await addQuestionBtn.waitFor({ state: 'visible', timeout: 10000 });
    await addQuestionBtn.click();
  });

  test('Exam Title validation: rejects whitespace, displays inline error, blocks Save Draft', async ({ page }) => {
    const titleInput = page.locator('[data-testid="exam-title-input"]');
    await titleInput.waitFor({ state: 'visible', timeout: 5000 });

    // Clear title with whitespace
    await titleInput.fill('    ');
    
    // Inline error should appear
    const titleError = page.locator('[data-testid="exam-title-error"]');
    await expect(titleError).toBeVisible();
    await expect(titleError).toContainText('Tiêu đề bài thi không được để trống');

    // Attempt to Save Draft
    const saveDraftBtn = page.locator('button:has-text("Lưu bản nháp")');
    await saveDraftBtn.click();

    // Verify banner shows error and title input is focused
    await expect(page.locator('text=Vui lòng nhập tiêu đề bài thi hợp lệ')).toBeVisible();
    await expect(titleInput).toBeFocused();

    // Fill valid title
    await titleInput.fill('Bài kiểm tra Giữa kỳ Thuật toán');
    await expect(titleError).not.toBeVisible();
  });

  test('Exam Duration validation: rejects < 1 or > 1440, displays inline error', async ({ page }) => {
    const durationInput = page.locator('[data-testid="exam-duration-input"]');
    await durationInput.waitFor({ state: 'visible', timeout: 5000 });

    // Set duration to 0
    await durationInput.fill('0');
    const durationError = page.locator('[data-testid="exam-duration-error"]');
    await expect(durationError).toBeVisible();
    await expect(durationError).toContainText('1-1440 phút');

    // Attempt Save Draft
    const saveDraftBtn = page.locator('button:has-text("Lưu bản nháp")');
    await saveDraftBtn.click();
    await expect(page.locator('text=Thời lượng bài thi phải từ 1 đến 1440 phút')).toBeVisible();

    // Fix duration
    await durationInput.fill('90');
    await expect(durationError).not.toBeVisible();
  });

  test('Question Title & Navigator Warning validation', async ({ page }) => {
    const qTitleInput = page.locator('[data-testid="question-title-input"]');
    await qTitleInput.waitFor({ state: 'visible', timeout: 5000 });

    // Clear question title
    await qTitleInput.fill('   ');
    const qTitleError = page.locator('[data-testid="question-title-error"]');
    await expect(qTitleError).toBeVisible();
    await expect(qTitleError).toContainText('không được để trống');

    // Check navigator shows warning icon
    const navWarning = page.locator('[data-testid="question-nav-warning-0"]');
    await expect(navWarning).toBeVisible();

    // Fill valid question title
    await qTitleInput.fill('Tìm phần tử nhỏ thứ k trong mảng');
    await expect(qTitleError).not.toBeVisible();
  });

  test('Question Max Score validation: rejects <= 0 or > 2 decimal places', async ({ page }) => {
    const qScoreInput = page.locator('[data-testid="question-max-score-input"]');
    await qScoreInput.waitFor({ state: 'visible', timeout: 5000 });

    // Set score to 0
    await qScoreInput.fill('0');
    const scoreError = page.locator('[data-testid="question-max-score-error"]');
    await expect(scoreError).toBeVisible();
    await expect(scoreError).toContainText('Điểm phải > 0');

    // Set score to 3 decimal places
    await qScoreInput.fill('3.333');
    await expect(scoreError).toBeVisible();
    await expect(scoreError).toContainText('Tối đa 2 số thập phân');

    // Set valid score
    await qScoreInput.fill('10.00');
    await expect(scoreError).not.toBeVisible();
  });

  test('Question Problem Statement validation: warning displayed when empty, preserved with Markdown', async ({ page }) => {
    const statementTextarea = page.locator('[data-testid="problem-statement-textarea"]');
    await statementTextarea.waitFor({ state: 'visible', timeout: 5000 });

    // Initially empty -> warning displayed
    const warning = page.locator('[data-testid="problem-statement-warning"]');
    await expect(warning).toBeVisible();
    await expect(warning).toContainText('Nội dung yêu cầu đề bài là bắt buộc khi xuất bản');

    // Fill markdown description
    const markdownContent = '### Đề bài\nCho mảng số nguyên `A` gồm $n$ phần tử.\n**Yêu cầu:** Tìm số lớn nhất.\n```python\nprint("Hello")\n```';
    await statementTextarea.fill(markdownContent);

    // Warning cleared and content preserved exactly
    await expect(warning).not.toBeVisible();
    await expect(statementTextarea).toHaveValue(markdownContent);
  });

  test('Publish Workflow: blocks publish if required fields missing and focuses invalid field', async ({ page }) => {
    // Fill title
    const titleInput = page.locator('[data-testid="exam-title-input"]');
    await titleInput.fill('Đề thi thử nghiệm Kiểm thử 01C');

    // Clear exam date to test date validation
    const dateInput = page.locator('[data-testid="exam-date-input"]');
    await dateInput.fill('');

    // Attempt to publish without examDate
    const publishBtn = page.locator('button:has-text("Xuất bản bài thi")');
    await publishBtn.click();

    // Error for missing date
    await expect(page.locator('text=Vui lòng chọn thời gian thi trước khi xuất bản')).toBeVisible();
    await expect(dateInput).toBeFocused();

    // Set exam date
    await dateInput.fill('2026-12-01T08:00');

    // Attempt publish again -> problem statement empty
    await publishBtn.click();
    await expect(page.locator('text=chưa có nội dung mô tả đề bài')).toBeVisible();
    const statementTextarea = page.locator('[data-testid="problem-statement-textarea"]');
    await expect(statementTextarea).toBeFocused();
  });
});
