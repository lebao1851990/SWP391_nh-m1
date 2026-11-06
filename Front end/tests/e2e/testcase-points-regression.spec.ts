import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('Test Case Points Numeric Input Regression (TASK-FE-VALIDATION-01B)', () => {
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

    // Wait for empty state and click "+ Thêm câu hỏi thủ công"
    const addQuestionBtn = page.locator('button:has-text("+ Thêm câu hỏi thủ công")').first();
    await addQuestionBtn.waitFor({ state: 'visible', timeout: 10000 });
    await addQuestionBtn.click();

    // Set Question Max Score to 2.50
    const qScoreInput = page.locator('[data-testid="question-max-score-input"]');
    await qScoreInput.waitFor({ state: 'visible', timeout: 5000 });
    await qScoreInput.fill('2.50');

    // Switch to 'Bộ Test Cases' tab
    const tcTab = page.locator('button:has-text("Bộ Test Cases")').first();
    await tcTab.waitFor({ state: 'visible', timeout: 5000 });
    await tcTab.click();
  });

  async function openTestCaseModal(page: any) {
    const addTcBtn = page.locator('button:has-text("Thêm Test Case")').first();
    await addTcBtn.waitFor({ state: 'visible', timeout: 5000 });
    await addTcBtn.click();
    const modal = page.locator('div[role="dialog"]');
    await expect(modal).toBeVisible();
    return modal;
  }

  test('Case 1 & 2: 2.50 accepted when remaining score is 2.50; 2.51 rejected with readable error', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    // Test 2.51 exceeds remaining score of 2.50
    await pointsInput.fill('2.51');
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('output');
    await submitBtn.click();

    // Verify rejected with readable constraint message
    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('vượt quá điểm tối đa của câu hỏi');

    // Verify 2.50 is accepted
    await pointsInput.fill('2.50');
    await submitBtn.click();

    // Modal should close upon successful addition
    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="tc-total-allocated"]')).toContainText('2.50đ');
  });

  test('Case 3: 0.01 is accepted when remaining score permits', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    await pointsInput.fill('0.01');
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="tc-total-allocated"]')).toContainText('0.01đ');
  });

  test('Case 4: 0 is rejected with readable error', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    await pointsInput.fill('0');
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('phải lớn hơn 0 (tối thiểu 0.01đ)');
  });

  test('Case 5: -1 is rejected without UI corruption', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    await pointsInput.fill('-1');
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('không được là số âm');
  });

  test('Case 6: 1.234 (>2 decimal places) is rejected', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    await pointsInput.fill('1.234');
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('tối đa 2 chữ số thập phân');
  });

  test('Case 7: 1e+21 (scientific notation) is rejected with readable error and does not corrupt UI', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    await pointsInput.fill('1e+21');
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    // Verify input value did not transform or corrupt into exponential display
    await expect(pointsInput).toHaveValue('1e+21');

    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('Không chấp nhận định dạng ký hiệu khoa học (scientific notation)');
  });

  test('Case 8: Very long numeric string is rejected without UI corruption', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    const veryLongStr = '99999999999999999999';
    await pointsInput.fill(veryLongStr);
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    await expect(pointsInput).toHaveValue(veryLongStr);

    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('vượt quá giới hạn tối đa cho phép');
  });

  test('Case 9 & 10: NaN and Infinity are rejected', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    // Test NaN
    await pointsInput.fill('NaN');
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    let errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('Định dạng điểm không hợp lệ');

    // Test Infinity
    await pointsInput.fill('Infinity');
    await submitBtn.click();

    errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('Định dạng điểm không hợp lệ');
  });

  test('Case 11: Empty intermediate input is allowed while editing, rejected on submit', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    // User clears the field during editing
    await pointsInput.fill('');
    await expect(pointsInput).toHaveValue('');

    // Attempting to submit while empty triggers validation error
    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('Vui lòng nhập điểm cho test case');
  });

  test('Paste operations: pasting scientific notation or invalid text is rejected without silent clamping', async ({ page }) => {
    await openTestCaseModal(page);

    const pointsInput = page.locator('[data-testid="tc-points-input"]');
    const submitBtn = page.locator('[data-testid="tc-submit-button"]');

    // Simulate paste of scientific notation
    await pointsInput.focus();
    await page.evaluate(() => {
      const input = document.querySelector('[data-testid="tc-points-input"]') as HTMLInputElement;
      if (input) {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set;
        setter!.call(input, '2.5e+10');
        input.dispatchEvent(new Event('input', { bubbles: true }));
      }
    });

    await page.locator('textarea[placeholder*="Nhập kết quả in ra màn hình"]').fill('sample');
    await submitBtn.click();

    const errorMsg = page.locator('[data-testid="tc-points-error"]');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('scientific notation');
  });
});
