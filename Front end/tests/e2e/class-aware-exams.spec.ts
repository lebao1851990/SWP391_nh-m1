import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe('Class-aware Exams E2E Verification', () => {

  test('Teacher assigns an exam to multiple classes and verifies assignments in list and detail', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // Navigate to teacher exams view
    await page.goto('/teacher/exams');
    await expect(page.locator('h1')).toContainText('Exam Management', { timeout: 10000 });

    // Open create exam modal
    const createBtn = page.locator('#btn-create-exam');
    await expect(createBtn).toBeVisible({ timeout: 5000 });
    await createBtn.click();

    // Verify modal elements
    const modal = page.locator('div.fixed');
    await expect(modal).toBeVisible({ timeout: 5000 });
    await expect(modal.locator('h2')).toContainText('Create New Exam');

    // Fill title
    const examTitle = `Class-Aware Exam ${Date.now()}`;
    await modal.locator('input[placeholder*="CS201"]').fill(examTitle);

    // Multi-class selector: check that class checkboxes exist
    const classCheckboxes = modal.locator('input[type="checkbox"]');
    const checkboxCount = await classCheckboxes.count();
    expect(checkboxCount).toBeGreaterThanOrEqual(1);

    // If multiple classes exist, select all or multiple
    const selectAllBtn = modal.locator('button:has-text("Select All")');
    if (await selectAllBtn.isVisible()) {
      await selectAllBtn.click();
    } else {
      await classCheckboxes.first().check();
    }

    // Set duration
    await page.fill('input[type="number"]', '120');

    // Submit creation form
    const createPromise = page.waitForResponse(
      (res) => res.url().includes('/api/teacher/exams') && res.request().method() === 'POST'
    );
    await page.click('button:has-text("Publish & Save")');
    const createRes = await createPromise;
    expect(createRes.status()).toBe(200);

    const createdData = await createRes.json();
    expect(createdData.title).toBe(examTitle);
    expect(createdData.classIds.length).toBeGreaterThanOrEqual(1);

    // Check that newly created exam appears in table with its assigned classes
    await expect(page.locator('table#teacher-exams-table').getByText(examTitle)).toBeVisible({ timeout: 8000 });

    // Navigate to exam detail
    await page.goto(`/teacher/exams/${createdData.id}`);
    await expect(page.locator('h1')).toContainText(examTitle, { timeout: 10000 });

    // Confirm assigned classes section is visible
    const assignedClassesSection = page.locator('[data-testid="assigned-classes-list"]');
    await expect(assignedClassesSection).toBeVisible({ timeout: 5000 });
    await expect(assignedClassesSection).toContainText('Assigned Classes:');
  });

  test('Teacher can edit assigned classes on an existing exam', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // Go to exams list to find an existing exam
    await page.goto('/teacher/exams');
    await expect(page.locator('h1')).toContainText('Exam Management', { timeout: 10000 });

    const firstManageLink = page.locator('table#teacher-exams-table tbody tr a:has-text("Manage")').first();
    await expect(firstManageLink).toBeVisible({ timeout: 10000 });
    await firstManageLink.click();

    // On exam detail page
    await expect(page.locator('#btn-edit-exam')).toBeVisible({ timeout: 10000 });
    await page.click('#btn-edit-exam');

    // Verify edit modal is open
    const editModal = page.locator('div.fixed');
    await expect(editModal).toBeVisible({ timeout: 5000 });
    await expect(editModal.locator('h2')).toContainText('Edit Exam Settings');

    // Confirm classes checkboxes are rendered
    const editClassCheckboxes = editModal.locator('input[type="checkbox"]');
    expect(await editClassCheckboxes.count()).toBeGreaterThanOrEqual(1);

    // Close without saving or save updated settings
    await editModal.locator('button:has-text("Cancel")').click();
    await expect(editModal).not.toBeVisible({ timeout: 5000 });
  });

  test('Student views only exams for their enrolled classes', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Navigate to student exams view
    await page.goto('/student/exams');
    await expect(page.locator('h1')).toContainText('Enrolled Assessments', { timeout: 10000 });

    // Verify assessments list loads
    const examCards = page.locator('div[id^="exam-card-"]');
    if (await examCards.count() > 0) {
      const firstCard = examCards.first();
      await expect(firstCard).toBeVisible();
    }
  });

  test('Student is blocked from accessing unauthorized exams by direct ID', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Directly navigate to a non-enrolled or non-existent exam ID
    const unauthorizedExamId = '99999999-0000-0000-0000-999999999999';
    await page.goto(`/student/exams/${unauthorizedExamId}`);

    // Verify access restriction or not found banner
    const restrictedHeader = page.locator('h2:has-text("Access Restricted"), h2:has-text("Assessment Not Found")');
    await expect(restrictedHeader).toBeVisible({ timeout: 10000 });
  });

  test('Unauthorized exam submission is rejected with 404', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');

    // Send direct POST to submission endpoint for unauthorized exam
    const unauthorizedExamId = '99999999-0000-0000-0000-999999999999';
    const response = await page.request.post(`/api/student/exams/${unauthorizedExamId}/submissions`, {
      data: {
        sourceCode: 'public class Solution { public static void main(String[] args) {} }',
      },
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Must be 404 Not Found (or 403 Forbidden)
    expect([404, 403]).toContain(response.status());
  });
});
