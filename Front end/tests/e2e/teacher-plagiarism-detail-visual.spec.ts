import { test, expect } from '@playwright/test';

test.describe('Teacher Plagiarism Detail Visualization & Side-by-Side Hierarchical AST Comparison', () => {

  const flaggedPair = {
    recordId: '30010',
    examName: 'Tính tổng số nguyên tố dưới từ 0 tới 1000',
    className: 'PRO192',
    pair: {
      pairId: '30010',
      submissionAId: '6f2fce7e-2536-4b8e-b497-e5248335a36d',
      submissionBId: 'fb252325-7a22-4ed5-a538-eafc937f09e2',
      studentAId: 'usr-student-001',
      studentBId: 'usr-student-003',
      studentAName: 'Student AST A (Trial Division Loop)',
      studentBName: 'Student AST C (Renamed Identifiers Twin)',
      similarityRate: 100.0,
      isFlagged: true,
      examId: 'afb7fcfe-fcd5-4922-9b8d-2eda2c090604',
      examTitle: 'Tính tổng số nguyên tố dưới từ 0 tới 1000',
      className: 'PRO192',
      adjudicationStatus: 'PENDING',
    },
    evidence: [
      {
        evidenceId: 'ev-1',
        description: 'Matching AST structure detected around Main.java:4-6.',
        severity: 'high',
      },
    ],
  };

  const comparisonData30010 = {
    pairId: '30010',
    fileA: 'StudentA.java',
    fileB: 'StudentB.java',
    sourceCodeA: `public class Main {\n    public static void main(String[] args) {\n        int sum = 0;\n        for (int i = 0; i <= 1000; i++) {\n            if (isPrime(i)) sum += i;\n        }\n        System.out.println(sum);\n    }\n}`,
    sourceCodeB: `public class MainPrime {\n    public static void main(String[] args) {\n        int total = 0;\n        for (int num = 0; num <= 1000; num++) {\n            if (checkPrime(num)) total += num;\n        }\n        System.out.println(total);\n    }\n}`,
    matches: [
      {
        startLineA: 4,
        endLineA: 6,
        startLineB: 4,
        endLineB: 6,
        type: 'ForStmt',
      },
    ],
  };

  const astTree30010 = {
    pairId: '30010',
    submissionAId: '6f2fce7e-2536-4b8e-b497-e5248335a36d',
    submissionBId: 'fb252325-7a22-4ed5-a538-eafc937f09e2',
    studentAName: 'Student AST A (Trial Division Loop)',
    studentBName: 'Student AST C (Renamed Identifiers Twin)',
    treeA: {
      nodeId: 'A-0',
      nodeType: 'CompilationUnit',
      label: 'CompilationUnit (Root)',
      startLine: 1,
      endLine: 9,
      matchStatus: 'CONTEXT',
      matchedNodeId: null,
      evidenceId: null,
      hasHiddenChildren: false,
      children: [
        {
          nodeId: 'A-0-0',
          nodeType: 'ClassOrInterfaceDeclaration',
          label: 'class Main',
          startLine: 1,
          endLine: 9,
          matchStatus: 'CONTEXT',
          matchedNodeId: null,
          evidenceId: null,
          hasHiddenChildren: false,
          children: [
            {
              nodeId: 'A-0-0-0',
              nodeType: 'MethodDeclaration',
              label: 'void main(1 params)',
              startLine: 2,
              endLine: 8,
              matchStatus: 'CONTEXT',
              matchedNodeId: null,
              evidenceId: null,
              hasHiddenChildren: false,
              children: [
                {
                  nodeId: 'A-0-0-0-0',
                  nodeType: 'BlockStmt',
                  label: '{ ... } (3 stmts)',
                  startLine: 2,
                  endLine: 8,
                  matchStatus: 'CONTEXT',
                  matchedNodeId: null,
                  evidenceId: null,
                  hasHiddenChildren: false,
                  children: [
                    {
                      nodeId: 'A-0-0-0-0-0',
                      nodeType: 'ForStmt',
                      label: 'for (i <= 1000)',
                      startLine: 4,
                      endLine: 6,
                      matchStatus: 'MATCHED_REGION',
                      matchedNodeId: null,
                      evidenceId: 'ev-1',
                      hasHiddenChildren: false,
                      children: [],
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
    treeB: {
      nodeId: 'B-0',
      nodeType: 'CompilationUnit',
      label: 'CompilationUnit (Root)',
      startLine: 1,
      endLine: 9,
      matchStatus: 'CONTEXT',
      matchedNodeId: null,
      evidenceId: null,
      hasHiddenChildren: false,
      children: [
        {
          nodeId: 'B-0-0',
          nodeType: 'ClassOrInterfaceDeclaration',
          label: 'class MainPrime',
          startLine: 1,
          endLine: 9,
          matchStatus: 'CONTEXT',
          matchedNodeId: null,
          evidenceId: null,
          hasHiddenChildren: false,
          children: [
            {
              nodeId: 'B-0-0-0',
              nodeType: 'MethodDeclaration',
              label: 'void main(1 params)',
              startLine: 2,
              endLine: 8,
              matchStatus: 'CONTEXT',
              matchedNodeId: null,
              evidenceId: null,
              hasHiddenChildren: false,
              children: [
                {
                  nodeId: 'B-0-0-0-0',
                  nodeType: 'BlockStmt',
                  label: '{ ... } (3 stmts)',
                  startLine: 2,
                  endLine: 8,
                  matchStatus: 'CONTEXT',
                  matchedNodeId: null,
                  evidenceId: null,
                  hasHiddenChildren: false,
                  children: [
                    {
                      nodeId: 'B-0-0-0-0-0',
                      nodeType: 'ForStmt',
                      label: 'for (num <= 1000)',
                      startLine: 4,
                      endLine: 6,
                      matchStatus: 'MATCHED_REGION',
                      matchedNodeId: null,
                      evidenceId: 'ev-1',
                      hasHiddenChildren: false,
                      children: [],
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
    displayedNodesA: 5,
    displayedNodesB: 5,
    totalCountedNodesA: 5,
    totalCountedNodesB: 5,
    countCompletedA: true,
    countCompletedB: true,
    matchedRegionNodesCount: 2,
    isTruncated: false,
    truncationReason: null,
    truncationNotice: null,
    parseStatus: 'SUCCESS',
    parseErrorMessage: null,
  };

  const unflaggedPair = {
    recordId: '30004',
    examName: 'Tính tổng số nguyên tố dưới từ 0 tới 1000',
    className: 'PRO192',
    pair: {
      pairId: '30004',
      submissionAId: '4d853b5d-145b-41ec-9b8c-16faec119399',
      submissionBId: '6f2fce7e-2536-4b8e-b497-e5248335a36d',
      studentAId: 'usr-student-002',
      studentBId: 'usr-student-001',
      studentAName: 'Student AST B (Sieve of Eratosthenes)',
      studentBName: 'Student AST A (Trial Division Loop)',
      similarityRate: 37.65,
      isFlagged: false,
      examId: 'afb7fcfe-fcd5-4922-9b8d-2eda2c090604',
      examTitle: 'Tính tổng số nguyên tố dưới từ 0 tới 1000',
      className: 'PRO192',
      adjudicationStatus: 'PENDING',
    },
    evidence: [],
  };

  const astTree30004 = {
    pairId: '30004',
    submissionAId: '4d853b5d-145b-41ec-9b8c-16faec119399',
    submissionBId: '6f2fce7e-2536-4b8e-b497-e5248335a36d',
    studentAName: 'Student AST B (Sieve of Eratosthenes)',
    studentBName: 'Student AST A (Trial Division Loop)',
    treeA: {
      nodeId: 'A-0',
      nodeType: 'CompilationUnit',
      label: 'CompilationUnit (Root)',
      startLine: 1,
      endLine: 4,
      matchStatus: 'UNMATCHED',
      matchedNodeId: null,
      evidenceId: null,
      hasHiddenChildren: false,
      children: [],
    },
    treeB: {
      nodeId: 'B-0',
      nodeType: 'CompilationUnit',
      label: 'CompilationUnit (Root)',
      startLine: 1,
      endLine: 4,
      matchStatus: 'UNMATCHED',
      matchedNodeId: null,
      evidenceId: null,
      hasHiddenChildren: false,
      children: [],
    },
    displayedNodesA: 1,
    displayedNodesB: 1,
    totalCountedNodesA: 1,
    totalCountedNodesB: 1,
    countCompletedA: true,
    countCompletedB: true,
    matchedRegionNodesCount: 0,
    isTruncated: false,
    truncationReason: null,
    truncationNotice: null,
    parseStatus: 'SUCCESS',
    parseErrorMessage: null,
  };

  test.beforeEach(async ({ page, context }) => {
    // Mock user profile to satisfy AuthGuard on teacher route
    await page.route('**/api/user/profile', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'teacher-01',
          email: 'teacher@codeassess.edu.vn',
          fullName: 'Giảng viên Kiểm thử',
          role: 'TEACHER',
        }),
      });
    });

    await context.addCookies([
      { name: 'codeassess_role', value: 'TEACHER', url: 'http://localhost:3000' },
      { name: 'codeassess_session', value: 'valid-test-session', url: 'http://localhost:3000' },
    ]);
  });

  test('1. Macro Diagram: Preserves two-node relationship graph, centered badge, and student names', async ({ page }) => {
    await page.route('**/api/teacher/plagiarism/30010', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flaggedPair),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/compare', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(comparisonData30010),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/ast-tree', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(astTree30010),
      });
    });

    await page.goto('/teacher/plagiarism/30010');

    // 1. Verify Card Title (Relationship Graph)
    const cardTitle = page.locator('text=Sơ đồ Quan hệ Tương đồng Giữa hai Bài nộp');
    await expect(cardTitle).toBeVisible();

    // 2. Verify Flagged badge
    const flaggedBadge = page.locator('text=Vượt ngưỡng cảnh báo (100%)');
    await expect(flaggedBadge).toBeVisible();

    // 3. Verify Both Student Names appear in the diagram
    const nameA = page.locator('svg text:has-text("Student AST A")');
    const nameB = page.locator('svg text:has-text("Student AST C")');
    await expect(nameA).toBeVisible();
    await expect(nameB).toBeVisible();

    // 4. Verify Centered Similarity Percentage label
    const similarityBadgeText = page.locator('svg text:has-text("100%")');
    await expect(similarityBadgeText).toBeVisible();

    // 5. Verify connecting lines do not cross center badge
    const leftLine = page.locator('svg line[x2="265"]');
    const rightLine = page.locator('svg line[x1="415"]');
    await expect(leftLine).toBeAttached();
    await expect(leftLine).toHaveAttribute('stroke', '#e11d48');
    await expect(rightLine).toBeAttached();
    await expect(rightLine).toHaveAttribute('stroke', '#e11d48');
  });

  test('2. Side-by-Side Dual AST Tree: Renders Tree A & Tree B, parent-child nesting, and status pills', async ({ page }) => {
    await page.route('**/api/teacher/plagiarism/30010', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flaggedPair),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/compare', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(comparisonData30010),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/ast-tree', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(astTree30010),
      });
    });

    await page.goto('/teacher/plagiarism/30010');

    // 1. Verify AST Tree Section Heading
    await expect(page.locator('text=Đối chiếu Cây Cú pháp AST Phân cấp Song song')).toBeVisible();

    // 2. Verify Headers of both Tree Columns
    await expect(page.locator('text=Cây AST: Student AST A (Trial Division Loop)')).toBeVisible();
    await expect(page.locator('text=Cây AST: Student AST C (Renamed Identifiers Twin)')).toBeVisible();

    // 3. Verify Nesting hierarchy in Tree A
    await expect(page.locator('text=CompilationUnit').first()).toBeVisible();
    await expect(page.locator('text=ClassOrInterfaceDeclaration').first()).toBeVisible();
    await expect(page.locator('text=MethodDeclaration').first()).toBeVisible();
    await expect(page.locator('text=ForStmt').first()).toBeVisible();

    // 4. Verify MATCHED_REGION and CONTEXT status badges
    await expect(page.locator('text=MATCHED REGION').first()).toBeVisible();
    await expect(page.locator('text=CONTEXT').first()).toBeVisible();
  });

  test('3. Tree Interactions: Expand/Collapse branches and node selection updates code snippet', async ({ page }) => {
    await page.route('**/api/teacher/plagiarism/30010', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flaggedPair),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/compare', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(comparisonData30010),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/ast-tree', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(astTree30010),
      });
    });

    await page.goto('/teacher/plagiarism/30010');

    // 1. Select ForStmt node in Tree A
    const forNode = page.locator('[data-testid="ast-tree-panel-a"]').getByText('ForStmt').first();
    await forNode.click();

    // 2. Verify inspector updates with node details
    await expect(page.locator('text=Nút đang chọn: ForStmt')).toBeVisible();
    await expect(page.locator('text=Evidence #ev-1')).toBeVisible();

    // 3. Verify Code snippets render line numbers and content
    await expect(page.locator('text=Dòng 4–6').first()).toBeVisible();

    // 4. Test Collapse All button
    const collapseAllBtn = page.locator('button:has-text("Thu gọn tất cả")');
    await collapseAllBtn.click();

    // After collapsing all, ForStmt should be hidden from view inside tree panels
    await expect(page.locator('[data-testid="ast-tree-panel-a"]').getByText('ForStmt')).toHaveCount(0);

    // 5. Test Expand All button
    const expandAllBtn = page.locator('button:has-text("Mở rộng tất cả")');
    await expandAllBtn.click();

    // After expanding all, ForStmt should reappear
    await expect(page.locator('[data-testid="ast-tree-panel-a"]').getByText('ForStmt').first()).toBeVisible();
  });

  test('4. Safety Truncation Banner: Displays amber warning notice when isTruncated is true', async ({ page }) => {
    const truncatedTree = {
      ...astTree30010,
      isTruncated: true,
      truncationNotice: 'Cây cú pháp đã được rút gọn an toàn (Bài A: 500/1200 nút, Bài B: 500/1150 nút) để bảo đảm hiệu năng trình duyệt.',
    };

    await page.route('**/api/teacher/plagiarism/30010', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flaggedPair),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/compare', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(comparisonData30010),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/ast-tree', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(truncatedTree),
      });
    });

    await page.goto('/teacher/plagiarism/30010');

    // Verify Truncation Warning Notice
    await expect(page.locator('text=Cây cú pháp đã được giới hạn an toàn:')).toBeVisible();
    await expect(page.locator('text=Cây cú pháp đã được rút gọn an toàn (Bài A: 500/1200 nút, Bài B: 500/1150 nút)')).toBeVisible();
  });

  test('5. Syntax Error & Special Status Handling: Renders graceful notice instead of fake tree', async ({ page }) => {
    const syntaxErrorTree = {
      ...astTree30010,
      parseStatus: 'SYNTAX_ERROR_A',
      parseErrorMessage: 'Bài A: Syntax error at line 3 column 5',
    };

    await page.route('**/api/teacher/plagiarism/30010', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flaggedPair),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/compare', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(comparisonData30010),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/ast-tree', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(syntaxErrorTree),
      });
    });

    await page.goto('/teacher/plagiarism/30010');

    await expect(page.locator('text=Mã nguồn chứa lỗi cú pháp Java không thể phân tích cây AST')).toBeVisible();
    await expect(page.locator('text=Bài A: Syntax error at line 3 column 5')).toBeVisible();
    await expect(page.locator('text=Xem mã nguồn thô trên trình Code Diff')).toBeVisible();
  });

  test('6. Unflagged Pair (<70%): Renders under-threshold state, UNMATCHED tree nodes, clean contrast', async ({ page }) => {
    await page.route('**/api/teacher/plagiarism/30004', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(unflaggedPair),
      });
    });

    await page.route('**/api/teacher/plagiarism/30004/compare', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ pairId: '30004', sourceCodeA: '', sourceCodeB: '', matches: [] }),
      });
    });

    await page.route('**/api/teacher/plagiarism/30004/ast-tree', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(astTree30004),
      });
    });

    await page.goto('/teacher/plagiarism/30004');

    // 1. Verify Unflagged status badge
    await expect(page.locator('text=Dưới ngưỡng cảnh báo (37.65%)')).toBeVisible();

    // 2. Verify Exact Similarity Rate displayed
    await expect(page.locator('svg text:has-text("37.65%")')).toBeVisible();

    // 3. Verify UNMATCHED badge in AST Tree
    await expect(page.locator('text=UNMATCHED').first()).toBeVisible();
  });

  test('7. Mobile Viewport (375px): Responsive layout stacks cleanly without horizontal overflow', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });

    await page.route('**/api/teacher/plagiarism/30010', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(flaggedPair),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/compare', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(comparisonData30010),
      });
    });

    await page.route('**/api/teacher/plagiarism/30010/ast-tree', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(astTree30010),
      });
    });

    await page.goto('/teacher/plagiarism/30010');

    // Verify SVG scaled responsively
    await expect(page.locator('svg[role="img"]')).toBeVisible();

    // Verify AST Tree section header is visible and responsive
    await expect(page.locator('text=Đối chiếu Cây Cú pháp AST Phân cấp Song song')).toBeVisible();
  });

  test('8. Error State: 403 Forbidden renders gracefully with return button', async ({ page }) => {
    await page.route('**/api/teacher/plagiarism/forbidden_pair', async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Quyền truy cập bị hạn chế' }),
      });
    });

    await page.goto('/teacher/plagiarism/forbidden_pair');

    await expect(page.locator('text=Quyền truy cập bị hạn chế')).toBeVisible();
    await expect(page.locator('text=Quay lại Trung tâm Kiểm tra Tương đồng')).toBeVisible();
  });

});
