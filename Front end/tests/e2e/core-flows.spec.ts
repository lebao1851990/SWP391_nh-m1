import { test, expect } from '@playwright/test';
import { login } from './helpers';

test.describe.serial('Core Business Flows E2E', () => {

  const examTitle = 'Exam A';
  let currentSubAId: string;
  let currentSubBId: string;

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
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    await page.locator('nav a[href="/student/exams"], a[href="/student/exams"]').first().click();
    await expect(page).toHaveURL(/\/student\/exams/);

    const openLink = page.locator(`tr:has-text("${examTitle}") a:has-text("Open")`).first();
    await openLink.waitFor({ state: 'visible', timeout: 8000 });
    await openLink.click();

    await expect(page.locator('h1')).toBeVisible({ timeout: 8000 });

    await page.fill('textarea', javaCodeA);
    await page.click('button:has-text("Submit Code Artifact")');

    // Confirm submission in modal dialog and capture backend response with opaque ID
    const confirmBtn = page.locator('button:has-text("Confirm & Grade")');
    await confirmBtn.waitFor({ state: 'visible', timeout: 5000 });

    const submitResponsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/student/exams/') && res.url().includes('/submissions') && res.request().method() === 'POST'
    );
    await confirmBtn.click();
    const submitResponse = await submitResponsePromise;
    expect(submitResponse.status()).toBe(201);
    const subJson = await submitResponse.json();
    expect(subJson.id).toBeTruthy();
    currentSubAId = subJson.id;

    await page.waitForURL(/\/student\/submissions/, { timeout: 15000 });
    // Allow Spring Boot async analysis thread for submission A to settle before submission B
    await page.waitForTimeout(2500);
  });

  test('Flow 2 — Student B Submission (Plagiarism Pipeline)', async ({ page }) => {
    await login(page, 'studentb@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    await page.locator('nav a[href="/student/exams"], a[href="/student/exams"]').first().click();
    await expect(page).toHaveURL(/\/student\/exams/);

    const openLink = page.locator(`tr:has-text("${examTitle}") a:has-text("Open")`).first();
    await openLink.waitFor({ state: 'visible', timeout: 8000 });
    await openLink.click();

    await expect(page.locator('h1')).toBeVisible({ timeout: 8000 });

    await page.fill('textarea', javaCodeB);
    await page.click('button:has-text("Submit Code Artifact")');

    // Confirm submission in modal dialog and capture backend response with opaque ID
    const confirmBtn = page.locator('button:has-text("Confirm & Grade")');
    await confirmBtn.waitFor({ state: 'visible', timeout: 5000 });

    const submitResponsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/student/exams/') && res.url().includes('/submissions') && res.request().method() === 'POST'
    );
    await confirmBtn.click();
    const submitResponse = await submitResponsePromise;
    expect(submitResponse.status()).toBe(201);
    const subJson = await submitResponse.json();
    expect(subJson.id).toBeTruthy();
    currentSubBId = subJson.id;

    await page.waitForURL(/\/student\/submissions/, { timeout: 15000 });
    await expect(page.locator('h1')).toBeVisible({ timeout: 10000 });
  });

  test('Flow 3 — Teacher Review & Plagiarism Dashboard', async ({ page }) => {
    expect(currentSubAId).toBeTruthy();
    expect(currentSubBId).toBeTruthy();

    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    // Navigate to plagiarism dashboard and await initial list fetch
    const initialFetchPromise = page.waitForResponse(
      (res) => res.url().includes('/api/teacher/plagiarism') && res.request().method() === 'GET'
    );
    await page.locator('nav a[href="/teacher/plagiarism"], a[href="/teacher/plagiarism"]').first().click();
    await expect(page).toHaveURL(/\/teacher\/plagiarism/);

    // Verify AST Plagiarism Detection matrix view
    await expect(page.locator('h1:has-text("AST Code Plagiarism Detection")')).toBeVisible({ timeout: 8000 });
    const syncBtn = page.locator('button:has-text("Sync Similarity Matrix")');
    await expect(syncBtn).toBeVisible({ timeout: 5000 });

    interface PairResponse {
      pairId?: string;
      submissionAId?: string;
      submissionBId?: string;
      similarityRate?: number;
      flagged?: boolean;
      isFlagged?: boolean;
    }

    const initialResp = await initialFetchPromise;
    let initialPairs: PairResponse[] = [];
    if (initialResp.status() === 200) {
      initialPairs = (await initialResp.json()) as PairResponse[];
    }

    // Verify specifically that the similarity pair for (currentSubAId, currentSubBId) exists
    let matchedPair: PairResponse | undefined = initialPairs.find((p: PairResponse) =>
      (p.submissionAId === currentSubAId && p.submissionBId === currentSubBId) ||
      (p.submissionAId === currentSubBId && p.submissionBId === currentSubAId)
    );

    // Bounded polling via UI Sync button if async pipeline is completing
    if (!matchedPair) {
      await expect.poll(async () => {
        const [resp] = await Promise.all([
          page.waitForResponse((res) => res.url().includes('/api/teacher/plagiarism') && res.request().method() === 'GET'),
          syncBtn.click()
        ]);
        const pairs = (await resp.json()) as PairResponse[];
        matchedPair = pairs.find((p: PairResponse) =>
          (p.submissionAId === currentSubAId && p.submissionBId === currentSubBId) ||
          (p.submissionAId === currentSubBId && p.submissionBId === currentSubAId)
        );
        return Boolean(matchedPair);
      }, {
        message: `AST similarity pair connecting submission ${currentSubAId} and ${currentSubBId} must be produced`,
        timeout: 15000,
        intervals: [1000, 2000]
      }).toBe(true);
    }

    // Explicit assertions on the verified similarity pair connecting current test submissions
    expect(matchedPair).toBeDefined();
    if (!matchedPair) {
      throw new Error(`Expected similarity pair for ${currentSubAId} and ${currentSubBId} to be defined`);
    }
    expect(matchedPair.similarityRate).toBeGreaterThanOrEqual(70);
    const isAuthoritativeFlag = matchedPair.flagged ?? matchedPair.isFlagged;
    expect(isAuthoritativeFlag).toBe(true);

    // Observable UI state: Verify rendered similarity pair row and diff review action
    const pairRow = page.locator('table tbody tr').first();
    await expect(pairRow).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=Review Diff').first()).toBeVisible({ timeout: 10000 });
  });

  test('Flow 5 — Teacher Manual Scoring', async ({ page }) => {
    await login(page, 'teachera@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);

    await page.locator('nav a[href="/teacher/exams"], a[href="/teacher/exams"]').first().click();
    await expect(page).toHaveURL(/\/teacher\/exams/);

    const manageLink = page.locator(`tr:has-text("${examTitle}") a:has-text("Manage")`).first();
    await manageLink.waitFor({ state: 'visible', timeout: 8000 });
    await manageLink.click();

    // Now we are at /teacher/exams/[id]
    // Click Review & Grade for Student A (by student name or email)
    const subLink = page.locator('tr:has-text("studenta@fpt.edu.vn") a:has-text("Review & Grade"), tr:has-text("Student A") a:has-text("Review & Grade")').first();
    await subLink.waitFor({ state: 'visible', timeout: 8000 });
    await subLink.click();

    // Now we are at /teacher/submissions/[id]
    await expect(page).toHaveURL(/\/teacher\/submissions/);
    
    // Click "Grade Submission" to navigate to scoring rubric
    const gradeLink = page.locator('a:has-text("Grade Submission")').first();
    await gradeLink.waitFor({ state: 'visible', timeout: 8000 });
    await gradeLink.click();

    // Now we are at /teacher/scoring/[id]
    await expect(page).toHaveURL(/\/teacher\/scoring/);
    await expect(page.locator('h1:has-text("Evaluation Rubric")')).toBeVisible({ timeout: 8000 });

    // Enable manual override
    const overrideBtn = page.locator('button:has-text("Manual Score Override")');
    if (await overrideBtn.isVisible()) {
      await overrideBtn.click();
      await page.fill('input[type="number"][name="overrideTotalScore"], input[name="overrideTotalScore"]', '95');
      await page.fill('textarea[name="overrideRationale"]', 'Edge case test suite resolution confirmed');
      await page.click('button:has-text("Apply to Form")');
    }

    // Save rubric changes
    await page.click('button:has-text("Commit Rubric Scores")');

    // Verify success notification
    await expect(page.locator('text=Evaluation saved successfully!')).toBeVisible({ timeout: 8000 });
  });

  test('Flow 4 & 6 — Student Result & Appeals', async ({ page }) => {
    await login(page, 'studenta@fpt.edu.vn', 'testpass123');
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Check appeals list
    await page.locator('nav a[href="/student/appeals"], a[href="/student/appeals"]').first().click();
    await expect(page).toHaveURL(/\/student\/appeals/);

    await expect(page.locator('h1:has-text("My Appeals")')).toBeVisible({ timeout: 8000 });
    await expect(page.locator('button:has-text("Export Statement")')).toBeVisible({ timeout: 5000 });
  });
});
