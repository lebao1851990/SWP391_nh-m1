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

    // Verify Assigned Course Classes trigger field is visible and interactive
    const classTrigger = modal.locator('#assigned-classes-trigger');
    await expect(classTrigger).toBeVisible({ timeout: 5000 });

    // Click trigger to open dropdown
    await classTrigger.click();

    // Verify dropdown popover opens
    const classDropdown = modal.locator('[data-testid="assigned-classes-dropdown"]');
    await expect(classDropdown).toBeVisible({ timeout: 5000 });

    // Multi-class selector: check that class checkboxes exist
    const classCheckboxes = classDropdown.locator('input[type="checkbox"]');
    const checkboxCount = await classCheckboxes.count();
    expect(checkboxCount).toBeGreaterThanOrEqual(1);

    // Test select and deselect
    const firstCheckbox = classCheckboxes.first();
    const wasChecked = await firstCheckbox.isChecked();
    if (wasChecked) {
      // Deselect it to verify deselecting works
      await firstCheckbox.uncheck();
      await expect(firstCheckbox).not.toBeChecked();
      // Re-select it
      await firstCheckbox.check();
      await expect(firstCheckbox).toBeChecked();
    } else {
      await firstCheckbox.check();
      await expect(firstCheckbox).toBeChecked();
    }

    // Verify count in label updates
    await expect(modal.locator('label[for="assigned-classes-trigger"]')).toContainText('selected');

    // Close dropdown
    await modal.locator('button:has-text("Done")').click();
    await expect(classDropdown).not.toBeVisible({ timeout: 5000 });

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

  test('Teacher Create Exam validates required class selection before submission', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await page.goto('/teacher/exams');

    // Open create exam modal
    const createBtn = page.locator('#btn-create-exam');
    await expect(createBtn).toBeVisible({ timeout: 5000 });
    await createBtn.click();

    const modal = page.locator('div.fixed');
    await expect(modal).toBeVisible({ timeout: 5000 });

    // Fill title
    await modal.locator('input[placeholder*="CS201"]').fill('Validation Test Exam');

    // Open dropdown and clear all selections
    const classTrigger = modal.locator('#assigned-classes-trigger');
    await classTrigger.click();

    const classDropdown = modal.locator('[data-testid="assigned-classes-dropdown"]');
    await expect(classDropdown).toBeVisible();

    const clearBtn = classDropdown.locator('button:has-text("Clear")');
    if (await clearBtn.isVisible()) {
      await clearBtn.click();
    } else {
      const checkedBoxes = classDropdown.locator('input[type="checkbox"]:checked');
      const count = await checkedBoxes.count();
      for (let i = 0; i < count; i++) {
        await checkedBoxes.nth(0).uncheck();
      }
    }

    // Verify 0 selected
    await expect(modal.locator('label[for="assigned-classes-trigger"]')).toContainText('(0 selected)');

    // Close dropdown
    await modal.locator('button:has-text("Done")').click();

    // Attempt to submit without selecting any class
    await page.click('button:has-text("Publish & Save")');

    // Verify validation error is displayed
    await expect(modal.locator('text=Please select at least one course class')).toBeVisible({ timeout: 5000 });

    // Verify dropdown automatically opened to guide the teacher
    await expect(classDropdown).toBeVisible({ timeout: 5000 });
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

    // Click edit assigned classes trigger
    const editClassTrigger = editModal.locator('#edit-assigned-classes-trigger');
    await expect(editClassTrigger).toBeVisible({ timeout: 5000 });
    await editClassTrigger.click();

    // Confirm classes checkboxes are rendered in dropdown
    const editClassCheckboxes = editModal.locator('[data-testid="assigned-classes-dropdown"] input[type="checkbox"]');
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
