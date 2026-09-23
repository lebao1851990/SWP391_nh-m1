import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe.serial('Core Business Flows E2E', () => {

  const examTitle = 'Exam A';
  const javaCodeA = `
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from Student A!");
    }
}
  `;
  const javaCodeB = `
public class Main {
    public static void main(String[] args) {
        // Renamed variable, but same structure
        System.out.println("Hello from Student A!");
    }
}
  `;

  test('Flow 1 — Student A Submission', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', '123456');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    await page.click('text=My Exams');
    await expect(page).toHaveURL(/\/student\/exams/);

    const examLink = page.locator(`tr:has-text("${examTitle}") >> text=View`).first();
    await examLink.waitFor({ state: 'visible', timeout: 5000 });
    const href = await examLink.getAttribute('href');
    await page.goto(href as string);

    await expect(page.locator('h1')).toBeVisible({ timeout: 5000 });

    await page.fill('textarea', javaCodeA);
    await page.click('button:has-text("Submit")');

    await page.waitForURL(/\/student\/exams$/);
  });

  test('Flow 2 — Student B Submission (Plagiarism Pipeline)', async ({ page }) => {
    await login(page, 'studentb@fpt.edu.vn', '123456');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    await page.click('text=My Exams');
    await expect(page).toHaveURL(/\/student\/exams/);

    const examLink = page.locator(`tr:has-text("${examTitle}") >> text=View`).first();
    await examLink.waitFor({ state: 'visible', timeout: 5000 });
    const href = await examLink.getAttribute('href');
    await page.goto(href as string);

    await expect(page.locator('h1')).toBeVisible({ timeout: 5000 });

    await page.fill('textarea', javaCodeB);
    await page.click('button:has-text("Submit")');

    await page.waitForURL(/\/student\/exams$/);

    // Wait a few seconds for AST pipeline to finish processing
    await page.waitForTimeout(5000);
  });

  test('Flow 3 — Teacher Review & Plagiarism Dashboard', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', '123456');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // Navigate to plagiarism dashboard
    await page.click('text=Plagiarism Check');
    await expect(page).toHaveURL(/\/teacher\/plagiarism/);

    // Ensure we see similarity 100%
    await expect(page.locator('text=100%')).toBeVisible();
  });

  test('Flow 5 — Teacher Manual Scoring', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', '123456');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    await page.click('text=Exams');
    await expect(page).toHaveURL(/\/teacher\/exams/);

    const examLink = page.locator(`tr:has-text("${examTitle}") >> text=View`).first();
    await examLink.waitFor({ state: 'visible', timeout: 5000 });
    const href = await examLink.getAttribute('href');
    await page.goto(href as string);

    // Now we are at /teacher/exams/[id]
    // Click View Code for Student A
    const subLink = page.locator('tr:has-text("Student A") >> text=View Code').first();
    await subLink.waitFor({ state: 'visible', timeout: 5000 });
    const subHref = await subLink.getAttribute('href');
    await page.goto(subHref as string);

    // Now we are at /teacher/submissions/[id]
    await page.click('text=Grade Submission');
    await expect(page).toHaveURL(/\/teacher\/scoring/);

    // Use Manual Override since criteria table is empty
    await page.click('text=Override Official Score');
    await page.locator('input[name="overrideTotalScore"]').fill('95');
    await page.locator('textarea[name="overrideRationale"]').fill('LGTM');
    await page.click('button:has-text("Done")');

    await page.click('text=Save Rubric Changes');

    // Verify success message
    await expect(page.locator('text=Scoring saved successfully!')).toBeVisible({ timeout: 5000 });
  });

  test('Flow 4 & 6 — Student Result & Appeal', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', '123456');
    
    // Let's get the submission ID from My Exams -> View Result
    await page.click('text=My Exams');
    
    const resultLink = page.locator(`tr:has-text("${examTitle}") >> text=View Result`).first();
    await resultLink.waitFor({ state: 'visible', timeout: 5000 });
    const href = await resultLink.getAttribute('href');
    await page.goto(href as string);

    // Verify GRADED status
    await expect(page.locator('text=GRADED')).toBeVisible({ timeout: 5000 });

    // Get submission ID from URL
    await page.click('text=Submit Appeal');
    
    // Fill the appeal form
    await page.click('text=Code Similarity Clarification');
    await page.fill('textarea', 'This is my original code and I can explain it.');
    await page.click('button:has-text("Submit Appeal")');
    
    // Click confirm dialog
    await page.click('button:has-text("Confirm & Submit")');

    // Redirect to appeals list
    await expect(page).toHaveURL(/\/student\/appeals/);
    
    // Verify appeal is listed and pending
    await expect(page.locator('td >> text=Pending Review').first()).toBeVisible({ timeout: 5000 });
  });
});
