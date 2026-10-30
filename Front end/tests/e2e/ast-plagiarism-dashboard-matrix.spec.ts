import { test, expect } from '@playwright/test';
import { login } from './helpers';

const TEACHER_EMAIL = process.env.E2E_TEACHER_EMAIL || 'teachera@fpt.edu.vn';
const STUDENT_EMAIL = process.env.E2E_STUDENT_EMAIL || 'studenta@fpt.edu.vn';

test.describe('AST Plagiarism Dashboard, Similarity Matrix & Comparison E2E', () => {

  const mockPairs = [
    {
      pairId: 'pair-101',
      submissionAId: 'sub-001',
      submissionBId: 'sub-002',
      studentAId: 'SE16001',
      studentBId: 'SE16002',
      studentAName: 'Alice Nguyen',
      studentBName: 'Bob Tran',
      similarityRate: 88.5,
      isFlagged: true,
      examId: 'exam-1',
      examTitle: 'Java OOP Midterm Exam',
      className: 'SE1701-PRO192',
    },
    {
      pairId: 'pair-102',
      submissionAId: 'sub-001',
      submissionBId: 'sub-003',
      studentAId: 'SE16001',
      studentBId: 'SE16003',
      studentAName: 'Alice Nguyen',
      studentBName: 'Charlie Le',
      similarityRate: 52.0,
      isFlagged: false,
      examId: 'exam-1',
      examTitle: 'Java OOP Midterm Exam',
      className: 'SE1701-PRO192',
    },
    {
      pairId: 'pair-103',
      submissionAId: 'sub-002',
      submissionBId: 'sub-003',
      studentAId: 'SE16002',
      studentBId: 'SE16003',
      studentAName: 'Bob Tran',
      studentBName: 'Charlie Le',
      similarityRate: 24.0,
      isFlagged: false,
      examId: 'exam-2',
      examTitle: 'Data Structures Final Exam',
      className: 'SE1702-CSD201',
    },
  ];

  test('1. Dashboard renders 4 telemetry cards, neutral notice, and pairwise table', async ({ page }) => {
    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });

    await page.route('**/api/teacher/plagiarism', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPairs),
      });
    });

    await page.goto('/teacher/plagiarism');

    // 1. Verify telemetry metric cards
    await expect(page.locator('text=Pairwise Comparisons')).toBeVisible();
    await expect(page.locator('text=Critical Flagged (≥70%)')).toBeVisible();
    await expect(page.locator('text=Moderate (40–69%)')).toBeVisible();
    await expect(page.locator('text=Normal (<40%)')).toBeVisible();

    // 2. Verify Neutral Evidentiary Notice
    await expect(page.locator('text=Academic Rigor & Neutral Interpretation Notice')).toBeVisible();
    await expect(page.locator('text=Similarity indicates structural or textual overlap between submissions. It is a review signal, not proof of plagiarism.')).toBeVisible();
    await expect(page.locator('text=Độ tương đồng phản ánh sự trùng lặp cấu trúc')).toBeVisible();

    // 3. Verify Table Rows
    await expect(page.locator('text=Alice Nguyen').first()).toBeVisible();
    await expect(page.locator('text=Bob Tran').first()).toBeVisible();
    await expect(page.locator('text=88.5%').first()).toBeVisible();
    await expect(page.locator('text=CRITICAL FLAG').first()).toBeVisible();
  });

  test('2. Toggles to Similarity Matrix Heatmap view and allows cell inspection', async ({ page }) => {
    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });

    await page.route('**/api/teacher/plagiarism', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPairs),
      });
    });

    await page.goto('/teacher/plagiarism');

    // Click Matrix Heatmap toggle
    const matrixBtn = page.locator('button:has-text("Matrix Heatmap")');
    await matrixBtn.click();

    // Verify Matrix Grid Header & Legend
    await expect(page.locator('text=All-Pairs AST Similarity Matrix (3 × 3)')).toBeVisible();
    await expect(page.locator('text=Similarity Heatmap:')).toBeVisible();
    await expect(page.locator('text=≥ 70% Critical (Review Flagged)')).toBeVisible();

    // Verify matrix cells contain similarity rates
    const cell88 = page.locator('td a:has-text("88.5%")').first();
    await expect(cell88).toBeVisible();

    // Verify clicking cell navigates to comparison view
    await cell88.click();
    await expect(page).toHaveURL(/\/teacher\/plagiarism\/pair-101/);
  });

  test('3. Filters by Assessment (Exam) and Cohort (Class)', async ({ page }) => {
    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });

    await page.route('**/api/teacher/plagiarism', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockPairs),
      });
    });

    await page.goto('/teacher/plagiarism');

    // Select Exam 2
    const examSelect = page.locator('select').first();
    await examSelect.selectOption('Data Structures Final Exam');

    // Should only show Bob Tran x Charlie Le
    await expect(page.locator('text=24%')).toBeVisible();
    await expect(page.locator('text=88.5%')).not.toBeVisible();
  });

  test('4. Pair Comparison View displays side-by-side code diff, matches, and faculty adjudication', async ({ page }) => {
    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });

    const pairId = 'pair-101';
    await page.route(`**/api/teacher/plagiarism/${pairId}/compare`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          pairId,
          fileA: 'StudentA_Main.java',
          fileB: 'StudentB_Main.java',
          sourceCodeA: 'public class Main {\n  public static void main(String[] args) {\n    int sum = 0;\n    for(int i = 0; i < 10; i++) sum += i;\n    System.out.println(sum);\n  }\n}',
          sourceCodeB: 'public class Solution {\n  public static void main(String[] args) {\n    int total = 0;\n    for(int k = 0; k < 10; k++) total += k;\n    System.out.println(total);\n  }\n}',
          matches: [
            {
              startLineA: 4,
              endLineA: 4,
              startLineB: 4,
              endLineB: 4,
              type: 'ForLoopSubtreeCongruence',
            },
          ],
        }),
      });
    });

    await page.route(`**/api/teacher/plagiarism/${pairId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recordId: pairId,
          examName: 'Java OOP Midterm Exam',
          className: 'SE1701-PRO192',
          pair: mockPairs[0],
          evidence: [
            {
              evidenceId: 'ev-1',
              description: 'Isomorphic ForLoop node sequence at line 4',
              severity: 'high',
            },
          ],
        }),
      });
    });

    await page.route(`**/api/teacher/plagiarism/${pairId}/adjudicate`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          pairId,
          status: 'PENDING',
          notes: 'Reviewed: isomorphic loop is standard course accumulator pattern.',
          adjudicatedBy: 'teachera@fpt.edu.vn',
          adjudicatedAt: '2026-10-16T19:00:00',
        }),
      });
    });

    await page.goto(`/teacher/plagiarism/${pairId}/compare`);

    // 1. Verify Neutral Evidentiary Notice
    await expect(page.locator('text=Evidentiary Standard Notice (Thông báo Tham vấn Học thuật)')).toBeVisible();

    // 2. Verify Matching Sub-tree Evidence
    await expect(page.locator('text=ForLoopSubtreeCongruence')).toBeVisible();
    await expect(page.locator('text=Student A: Lines 4–4')).toBeVisible();
    await expect(page.locator('text=Student B: Lines 4–4')).toBeVisible();

    // 3. Verify Dual Code Viewer Windows
    await expect(page.locator('text=StudentA_Main.java')).toBeVisible();
    await expect(page.locator('text=StudentB_Main.java')).toBeVisible();

    // 4. Verify Faculty Adjudication Protocol
    await expect(page.locator('text=Faculty Adjudication Protocol')).toBeVisible();
    const notesInput = page.locator('input[placeholder*="Scrutinized loop structures"]');
    await notesInput.fill('Reviewed: isomorphic loop is standard course accumulator pattern.');
    await page.locator('button:has-text("Record Review")').click();
    await expect(page.locator('text=Adjudication Recorded')).toBeVisible();
  });

  test('5. Student Academic Integrity Audit preserves peer confidentiality and shows neutral notice', async ({ page }) => {
    await login(page, STUDENT_EMAIL, { role: 'STUDENT' });

    const subId = 'sub-test-001';
    await page.route(`**/api/student/plagiarism/${subId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recordId: subId,
          examName: 'Java OOP Midterm Exam',
          similarityRate: 75.0,
          isFlagged: true,
          evidence: [
            {
              evidenceId: 'ev-sub-1',
              description: 'High syntactic correspondence in algorithm body',
              severity: 'high',
            },
          ],
        }),
      });
    });

    await page.goto(`/student/plagiarism/${subId}`);

    // Verify Title & Status
    await expect(page.locator('text=Academic Integrity Audit')).toBeVisible();
    await expect(page.locator('text=High Similarity Flagged')).toBeVisible();
    await expect(page.locator('text=75%')).toBeVisible();

    // Verify Neutral Notice
    await expect(page.locator('text=Academic Rigor Notice (Thông báo Tham vấn Học thuật)')).toBeVisible();
    await expect(page.locator('text=Similarity indicates structural or textual overlap')).toBeVisible();

    // Verify Student Dispute Button
    await expect(page.locator('text=Dispute with Appeal Ticket')).toBeVisible();

    // Verify PEER CONFIDENTIALITY: no peer name or raw peer submission ID shown
    await expect(page.locator('text=Bob Tran')).not.toBeVisible();
    await expect(page.locator('text=SE16002')).not.toBeVisible();
  });

  test('6. Adjudication API failure retains user input and does not display fake recorded state', async ({ page }) => {
    await login(page, TEACHER_EMAIL, { role: 'TEACHER' });

    const pairId = 'pair-101';
    await page.route(`**/api/teacher/plagiarism/${pairId}/compare`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          pairId,
          fileA: 'StudentA_Main.java',
          fileB: 'StudentB_Main.java',
          sourceCodeA: 'public class Main {}',
          sourceCodeB: 'public class Solution {}',
          matches: [],
        }),
      });
    });

    await page.route(`**/api/teacher/plagiarism/${pairId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recordId: pairId,
          examName: 'Java OOP Midterm Exam',
          className: 'SE1701-PRO192',
          pair: mockPairs[0],
          evidence: [],
        }),
      });
    });

    // Mock API failure (500 Internal Server Error)
    await page.route(`**/api/teacher/plagiarism/${pairId}/adjudicate`, async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Database connection timeout during adjudication persistence',
        }),
      });
    });

    await page.goto(`/teacher/plagiarism/${pairId}/compare`);

    const notesInput = page.locator('input[placeholder*="Scrutinized loop structures"]');
    await notesInput.fill('Notes entered by instructor during network instability');
    
    // Select status
    const statusSelect = page.locator('select').filter({ hasText: 'Under Faculty Review' });
    await statusSelect.selectOption('FALSE_POSITIVE');

    // Click Record Review
    await page.locator('button:has-text("Record Review")').click();

    // 1. Must NOT display fake recorded state
    await expect(page.locator('text=Adjudication Recorded')).not.toBeVisible();

    // 2. Must RETAIN entered text and selection in input
    await expect(notesInput).toHaveValue('Notes entered by instructor during network instability');
    await expect(statusSelect).toHaveValue('FALSE_POSITIVE');
  });

});
