import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe('ClassMultiSelectDropdown & Create Exam Validation', () => {

  test('Dropdown opens on click, supports select/deselect, and updates selected count', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await page.goto('/teacher/exams');

    // Open create exam modal
    const createBtn = page.locator('#btn-create-exam');
    await expect(createBtn).toBeVisible({ timeout: 5000 });
    await createBtn.click();

    const modal = page.locator('div.fixed');
    await expect(modal).toBeVisible({ timeout: 5000 });

    const trigger = modal.locator('#assigned-classes-trigger');
    await expect(trigger).toBeVisible({ timeout: 5000 });

    // Dropdown is initially closed
    const dropdown = modal.locator('[data-testid="assigned-classes-dropdown"]');
    await expect(dropdown).not.toBeVisible();

    // 1. Dropdown opens when clicked
    await trigger.click();
    await expect(dropdown).toBeVisible({ timeout: 5000 });
    await expect(trigger).toHaveAttribute('aria-expanded', 'true');

    // 2. Classes load and render with code, name, students count
    const classOption = dropdown.locator('[data-testid^="class-option-"]').first();
    await expect(classOption).toBeVisible();

    // 3. Teacher can select and deselect classes
    const checkbox = classOption.locator('input[type="checkbox"]');
    const labelText = modal.locator('label[for="assigned-classes-trigger"]');

    // Deselect if already selected
    if (await checkbox.isChecked()) {
      await classOption.click();
      await expect(checkbox).not.toBeChecked();
      await expect(labelText).toContainText('(0 selected)');
      // Select it back
      await classOption.click();
      await expect(checkbox).toBeChecked();
      await expect(labelText).toContainText('(1 selected)');
    } else {
      await classOption.click();
      await expect(checkbox).toBeChecked();
      await expect(labelText).toContainText('(1 selected)');
      // Deselect
      await classOption.click();
      await expect(checkbox).not.toBeChecked();
      await expect(labelText).toContainText('(0 selected)');
    }

    // 4. Test keyboard interaction: Escape closes dropdown
    await trigger.press('Escape');
    await expect(dropdown).not.toBeVisible();
    await expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  test('Displays clear empty state when teacher has no authorized classes', async ({ page }) => {
    // Intercept /api/teacher/classes to return empty array
    await page.route('**/api/teacher/classes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await page.goto('/teacher/exams');

    const createBtn = page.locator('#btn-create-exam');
    await createBtn.click();

    const modal = page.locator('div.fixed');
    await expect(modal).toBeVisible();

    const trigger = modal.locator('#assigned-classes-trigger');
    await expect(trigger).toBeVisible();
    await expect(trigger).toContainText('No authorized classes assigned');

    // Open dropdown to view empty state
    await trigger.click();
    const dropdown = modal.locator('[data-testid="assigned-classes-dropdown"]');
    await expect(dropdown).toBeVisible();
    await expect(dropdown.locator('text=No authorized course classes found')).toBeVisible();
    await expect(dropdown.locator('text=Contact your administrator')).toBeVisible();
  });

  test('Displays actionable error state and retry option when API fails', async ({ page }) => {
    let shouldFail = true;
    // Intercept to fail initially (including query retries), succeed only when shouldFail is flipped
    await page.route('**/api/teacher/classes', async (route) => {
      if (shouldFail) {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Internal Server Error' }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: 'mock-class-1', name: 'Mock Data Structures', code: 'CS201', studentCount: 25 },
          ]),
        });
      }
    });

    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await page.goto('/teacher/exams');

    const createBtn = page.locator('#btn-create-exam');
    await createBtn.click();

    const modal = page.locator('div.fixed');
    const trigger = modal.locator('#assigned-classes-trigger');
    await expect(trigger).toBeVisible();

    // Trigger shows error
    await expect(trigger).toContainText('Failed to load classes', { timeout: 10000 });

    // Open dropdown to see error details and retry button
    await trigger.click();
    const dropdown = modal.locator('[data-testid="assigned-classes-dropdown"]');
    await expect(dropdown.locator('text=Unable to load classes')).toBeVisible();

    const retryBtn = dropdown.locator('button:has-text("Retry")');
    await expect(retryBtn).toBeVisible();

    // Now permit the backend to succeed
    shouldFail = false;

    // Click retry
    await retryBtn.click();

    // After retry, mock class is loaded
    await expect(dropdown.locator('text=CS201')).toBeVisible({ timeout: 5000 });
  });

  test('Required-field validation prevents submission without classes and opens dropdown', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await page.goto('/teacher/exams');

    const createBtn = page.locator('#btn-create-exam');
    await createBtn.click();

    const modal = page.locator('div.fixed');
    await modal.locator('input[placeholder*="CS201"]').fill('Validation Test Exam');

    // Ensure 0 classes selected
    const trigger = modal.locator('#assigned-classes-trigger');
    await trigger.click();
    const dropdown = modal.locator('[data-testid="assigned-classes-dropdown"]');
    await expect(dropdown).toBeVisible();

    const clearBtn = dropdown.locator('button:has-text("Clear")');
    if (await clearBtn.isVisible()) {
      await clearBtn.click();
    } else {
      const checked = dropdown.locator('input[type="checkbox"]:checked');
      const count = await checked.count();
      for (let i = 0; i < count; i++) {
        await checked.nth(0).uncheck();
      }
    }

    // Close dropdown
    await modal.locator('button:has-text("Done")').click();
    await expect(dropdown).not.toBeVisible();

    // Submit form
    await page.click('button:has-text("Publish & Save")');

    // Validation error shown
    await expect(modal.locator('text=Please select at least one course class')).toBeVisible();

    // Dropdown is automatically opened for user
    await expect(dropdown).toBeVisible();
    await expect(trigger).toHaveAttribute('aria-invalid', 'true');
  });
});
