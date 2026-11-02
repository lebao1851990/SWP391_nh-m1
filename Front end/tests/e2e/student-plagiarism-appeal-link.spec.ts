import { test, expect } from '@playwright/test';
import { login } from './helpers';

const studentEmail = process.env.E2E_STUDENT_EMAIL || 'student.test@codeassess.edu.vn';

test.describe('Student Plagiarism Appeal Navigation ID Contract Tests', () => {

  test('1. Appeal link uses authentic submission UUID, not similarity-pair recordId', async ({ page }) => {
    await login(page, studentEmail);

    // Mock student plagiarism detail response with both pair recordId "1" and own submissionId UUID
    await page.route('**/api/student/plagiarism/1', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recordId: '1',
          submissionId: 'd6602b62-4fce-445f-a407-bce270ad99ef',
          examName: 'PRO192 Practical Exam 1 - Java Core',
          similarityRate: 89.29,
          isFlagged: true,
          evidence: []
        }),
      });
    });

    await page.goto('/student/plagiarism/1');

    const appealLink = page.locator('a:has-text("Tạo đơn khiếu nại bài nộp này")');
    await expect(appealLink).toBeVisible({ timeout: 8000 });

    const href = await appealLink.getAttribute('href');
    expect(href).not.toBeNull();

    // MUST contain the submission UUID
    expect(href).toContain('submissionId=d6602b62-4fce-445f-a407-bce270ad99ef');
    // MUST NOT use the numeric recordId "1" as submissionId
    expect(href).not.toBe('/student/appeals/new?submissionId=1');
    expect(href).not.toContain('submissionId=1&');
  });

  test('2. Missing submissionId renders a disabled button safely without navigating', async ({ page }) => {
    await login(page, studentEmail);

    // Mock student plagiarism detail response with missing/null submissionId
    await page.route('**/api/student/plagiarism/1', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recordId: '1',
          submissionId: null,
          examName: 'PRO192 Practical Exam 1 - Java Core',
          similarityRate: 89.29,
          isFlagged: true,
          evidence: []
        }),
      });
    });

    await page.goto('/student/plagiarism/1');

    // Should render disabled button, NOT an active link
    const disabledBtn = page.locator('button:has-text("Tạo đơn khiếu nại bài nộp này")');
    await expect(disabledBtn).toBeVisible({ timeout: 8000 });
    await expect(disabledBtn).toBeDisabled();

    // Verify no active link exists with submissionId=1
    const activeLink = page.locator('a:has-text("Tạo đơn khiếu nại bài nộp này")');
    await expect(activeLink).not.toBeVisible();
  });

  test('3. Non-flagged report does not render the appeal creation action', async ({ page }) => {
    await login(page, studentEmail);

    await page.route('**/api/student/plagiarism/1', route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recordId: '1',
          submissionId: 'd6602b62-4fce-445f-a407-bce270ad99ef',
          examName: 'PRO192 Practical Exam 1 - Java Core',
          similarityRate: 15.0,
          isFlagged: false,
          evidence: []
        }),
      });
    });

    await page.goto('/student/plagiarism/1');

    const appealButton = page.locator('text=Tạo đơn khiếu nại bài nộp này');
    await expect(appealButton).not.toBeVisible();
  });
});
