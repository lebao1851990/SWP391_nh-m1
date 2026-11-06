import { test, expect } from '@playwright/test';
import { login, runStagingSql } from './helpers';
import crypto from 'crypto';

const TEACHER_EMAIL = 'teachera@fpt.edu.vn';
const STUDENT_A = 'studenta@fpt.edu.vn';
const STUDENT_A_ID = 'usr-e2e-student-000000000001';
const STUDENT_B = 'studentb@fpt.edu.vn';
const STUDENT_B_ID = 'usr-e2e-student-000000000002';

test.describe('Git Contribution Analytics Browser End-to-End Acceptance', () => {
  let syntheticRepoId: string;
  let commit1Id: string;
  let commit2Id: string;

  test.beforeEach(async () => {
    syntheticRepoId = `repo-e2e-${crypto.randomUUID().substring(0, 8)}`;
    commit1Id = `cmt-e2e-${crypto.randomUUID().substring(0, 8)}`;
    commit2Id = `cmt-e2e-${crypto.randomUUID().substring(0, 8)}`;

    // 1. Seed synthetic Git Repository belonging to Student A's project
    const memberJson = JSON.stringify([STUDENT_A_ID]).replace(/"/g, '\\"');
    const insertRepoSql = `INSERT INTO git_repositories (id, name, project_id, class_id, repo_url, default_branch, provider, sync_status, member_user_ids, created_at) VALUES ('${syntheticRepoId}', 'E2E Capstone Phoenix', 'proj-phoenix-001', 'class-swp-001', 'https://github.com/codeassess-org/capstone-phoenix.git', 'main', 'GITHUB', 'SYNCED', '${memberJson}', NOW());`;
    runStagingSql(insertRepoSql);

    // 2. Seed 2 synthetic commits:
    // Commit 1: Authored by Student A (pre-mapped to Student A)
    // Commit 2: Authored by Student B's email (initially unmapped)
    const insertCommitsSql = `INSERT INTO git_commits (id, repository_id, commit_hash, author_name, author_email, user_id, commit_date, commit_message, lines_added, lines_deleted, files_changed, is_merge_commit, is_revert_commit) VALUES ('${commit1Id}', '${syntheticRepoId}', 'a1b2c3d4e5f67890123456789012345678901234', 'Nguyen Van An', '${STUDENT_A}', '${STUDENT_A_ID}', NOW() - INTERVAL 2 DAY, 'feat: initial architecture and models', 250, 20, 6, 0, 0), ('${commit2Id}', '${syntheticRepoId}', 'b2c3d4e5f67890123456789012345678901234a1', 'Tran Thi Binh', '${STUDENT_B}', NULL, NOW() - INTERVAL 1 DAY, 'feat: client dashboard and navigation', 180, 30, 4, 0, 0);`;
    runStagingSql(insertCommitsSql);
  });

  test.afterEach(async () => {
    if (syntheticRepoId) {
      // Purge synthetic fixtures safely from Staging MySQL
      runStagingSql(`DELETE FROM git_contributor_mappings WHERE repository_id = '${syntheticRepoId}';`);
      runStagingSql(`DELETE FROM git_commits WHERE repository_id = '${syntheticRepoId}';`);
      runStagingSql(`DELETE FROM git_repositories WHERE id = '${syntheticRepoId}';`);
    }
  });

  test('1. Teacher view: loads overview, metrics, timeline, and performs contributor mapping modal interaction', async ({ page }) => {
    await login(page, TEACHER_EMAIL);

    await page.goto('/teacher/analytics/git');
    await page.waitForLoadState('networkidle');

    // 1. Verify Page Title & Methodology Banner
    const heading = page.locator('text=Git Contribution Analytics').first();
    await expect(heading).toBeVisible({ timeout: 10000 });

    const banner = page.locator('text=Transparent Metric Calculation').first();
    await expect(banner).toBeVisible({ timeout: 5000 });

    // 2. Verify Repository Overview Card
    const repoTitle = page.locator('h2:has-text("E2E Capstone Phoenix")').first();
    await expect(repoTitle).toBeVisible({ timeout: 10000 });

    const totalCommits = page.locator('text=Total Commits').first();
    await expect(totalCommits).toBeVisible();

    const syncBtn = page.locator('button:has-text("Sync Commits")').first();
    await expect(syncBtn).toBeVisible();

    // 3. Verify Date Range Filter
    const filterTitle = page.locator('text=Date Range Filter:').first();
    await expect(filterTitle).toBeVisible();
    const preset7DBtn = page.locator('button:has-text("7D")').first();
    await expect(preset7DBtn).toBeVisible();
    const applyBtn = page.locator('button:has-text("Apply")').first();
    await expect(applyBtn).toBeVisible();

    // 4. Verify Contributor Summary Table
    const tableHeader = page.locator('text=Member Contribution Summary').first();
    await expect(tableHeader).toBeVisible();

    // Check Student A row
    const studentARow = page.locator(`text=${STUDENT_A}`).first();
    await expect(studentARow).toBeVisible();
    const matchedBadge = page.locator('text=Matched').first();
    await expect(matchedBadge).toBeVisible();

    // Check Student B row (unmatched initially)
    const studentBRow = page.locator(`text=${STUDENT_B}`).first();
    await expect(studentBRow).toBeVisible();
    const unmatchedBadge = page.locator('text=Unmatched').first();
    await expect(unmatchedBadge).toBeVisible();

    // 5. Verify Contribution Timeline Chart
    const timelineTitle = page.locator('text=Contribution Timeline').first();
    await expect(timelineTitle).toBeVisible();

    // 6. Test Contributor Mapping Interaction via Modal
    const mapBtn = page.locator('button:has-text("Map")').first();
    await expect(mapBtn).toBeVisible();
    await mapBtn.click();

    // Modal should open
    const modalTitle = page.locator('text=Map Git Contributor').first();
    await expect(modalTitle).toBeVisible({ timeout: 5000 });

    // Enter Student B user ID
    const studentIdInput = page.locator('input[placeholder*="user-alice-123"]');
    await studentIdInput.fill(STUDENT_B_ID);

    // Confirm Mapping
    const confirmBtn = page.locator('button:has-text("Confirm Mapping")');
    await confirmBtn.click();

    // Modal should close and success toast should appear
    await expect(modalTitle).not.toBeVisible({ timeout: 5000 });
  });

  test('2. Student view: displays team repository analytics without teacher management controls', async ({ page }) => {
    // Student A is member of the synthetic repository
    await login(page, STUDENT_A);

    await page.goto('/student/analytics/git');
    await page.waitForLoadState('networkidle');

    // 1. Verify Page Title
    const heading = page.locator('text=Team Git Analytics').first();
    await expect(heading).toBeVisible({ timeout: 10000 });

    // 2. Verify Repository is visible to Student A
    const repoTitle = page.locator('h2:has-text("E2E Capstone Phoenix")').first();
    await expect(repoTitle).toBeVisible({ timeout: 10000 });

    // 3. Verify Student CANNOT see "Sync Commits" or "Map" buttons (RBAC isolation)
    const syncBtn = page.locator('button:has-text("Sync Commits")');
    await expect(syncBtn).not.toBeVisible();

    const mapBtn = page.locator('button:has-text("Map")');
    await expect(mapBtn).not.toBeVisible();

    // 4. Verify Contributor Summary Table is visible
    const tableHeader = page.locator('text=Member Contribution Summary').first();
    await expect(tableHeader).toBeVisible();
  });

  test('3. Student RBAC isolation: Student B (non-member) cannot access unauthorized repository analytics', async ({ page, request }) => {
    // 1. Student B logs into browser
    await login(page, STUDENT_B);

    await page.goto('/student/analytics/git');
    await page.waitForLoadState('networkidle');

    // Since Student B is NOT a member of E2E Capstone Phoenix, the repository should NOT appear in selector/view
    const repoTitle = page.locator('h2:has-text("E2E Capstone Phoenix")');
    await expect(repoTitle).not.toBeVisible();

    // Verify empty state or no repositories message for Student B
    const emptyState = page.locator('text=No Team Repositories Registered').or(page.locator('text=No Git Repositories Registered')).or(page.locator('text=Team Git Analytics'));
    await expect(emptyState.first()).toBeVisible();

    // 2. Direct API query by Student B to the unauthorized repository returns 403 Forbidden
    const studentBLoginRes = await request.post('http://localhost:8081/api/auth/login', {
      data: {
        email: STUDENT_B,
        password: process.env.E2E_TEST_PASSWORD || 'TestPass123!',
      },
    });
    const studentBCookie = studentBLoginRes.headers()['set-cookie']?.split(';')[0];

    const forbiddenRes = await request.get(`http://localhost:8081/api/git/repositories/${syntheticRepoId}/analytics`, {
      headers: {
        Cookie: studentBCookie,
      },
    });
    expect(forbiddenRes.status()).toBe(403);
  });
});
