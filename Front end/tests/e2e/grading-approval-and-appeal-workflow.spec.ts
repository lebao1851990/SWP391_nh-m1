import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('E2E: Grading Approval, Teacher Publication & Admin Appeal Workflow (TASK-GRADING-APPROVAL-AND-ADMIN-APPEAL-WORKFLOW-01)', () => {
  const submissionId = 'sub-test-workflow-99';
  const examId = 'exam-workflow-01';
  const questionId = 'q-workflow-01';

  test.beforeEach(async ({ page }) => {
    page.on('pageerror', (err) => console.log('Page error:', err.message));

    // Safe fallbacks for secondary endpoints so unmocked auxiliary calls never trigger 401
    await page.route('**/api/teacher/exams*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.route('**/api/teacher/classes*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.route('**/api/teacher/submissions/**/scoring/audit-logs*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.route('**/api/student/submissions/**/files*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.route('**/api/student/submissions/**/appeals*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.route('**/api/submissions/**/audit-logs*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
    await page.route('**/api/rubrics/**', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
  });

  test('1. Student cannot see unpublished provisional grade; receives clear waiting message', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'STUDENT' });
    await mockUserProfile(page, {
      id: 'student-01',
      role: 'STUDENT',
      email: 'student@codeassess.edu.vn',
      fullName: 'Nguyễn Văn Sinh Viên',
    });

    // Mock unpublished provisional grade: Student API returns 403 Forbidden
    await page.route(`**/api/student/submissions/${submissionId}/result`, async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Điểm số đang chờ giảng viên xem xét và phê duyệt. Kết quả tạm thời chưa được công bố.',
        }),
      });
    });

    await page.goto(`/student/submissions/${submissionId}`);

    // Verify awaiting approval banner
    await expect(page.getByText('Kết quả đang chờ Phê duyệt & Công bố')).toBeVisible();
    await expect(page.getByText(/Điểm số hiện được bảo lưu ở trạng thái tạm tính nội bộ/)).toBeVisible();
    await expect(page.getByText('Quay lại danh sách bài nộp')).toBeVisible();
  });

  test('2. Teacher views grading queue, inspects submission, approves and publishes grade', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, {
      id: 'teacher-01',
      role: 'TEACHER',
      email: 'teacher@codeassess.edu.vn',
      fullName: 'TS. Nguyễn Văn Giảng Viên',
    });

    const mockGradingQueue = [
      {
        submissionId,
        examId,
        examTitle: 'Kỳ thi Java Cấu trúc Dữ liệu Kỳ 1',
        questionId,
        questionTitle: 'Câu 1: Thuật toán QuickSort',
        questionOrder: 1,
        studentId: 'student-01',
        studentName: 'Nguyễn Văn Sinh Viên',
        studentEmail: 'student@codeassess.edu.vn',
        submissionTime: new Date().toISOString(),
        attemptNumber: 1,
        provisionalScore: 8.5,
        maxScore: 10.0,
        reviewStatus: 'PENDING_REVIEW',
        publicationStatus: 'UNPUBLISHED',
        executionStatus: 'COMPLETED',
        approvedScore: null,
        publishedScore: null,
        testCasesPassed: 4,
        testCasesTotal: 5,
      },
    ];

    let currentReviewStatus = 'PENDING_REVIEW';
    let currentPubStatus = 'UNPUBLISHED';
    let currentApprovedScore: number | null = null;
    let currentPublishedScore: number | null = null;

    const getDetailResponse = () => ({
      submissionId,
      examId,
      examTitle: 'Kỳ thi Java Cấu trúc Dữ liệu Kỳ 1',
      className: 'CS201 - CTDL & GT',
      questionId,
      questionTitle: 'Câu 1: Thuật toán QuickSort',
      questionDescription: 'Cài đặt thuật toán QuickSort tối ưu bộ nhớ.',
      questionMaxScore: 10.0,
      studentId: 'student-01',
      studentName: 'Nguyễn Văn Sinh Viên',
      studentEmail: 'student@codeassess.edu.vn',
      submissionTime: new Date().toISOString(),
      attemptNumber: 1,
      submissionType: 'ZIP',
      originalFilename: 'quicksort-student.zip',
      archiveHash: 'sha256-abc123mock',
      executionStatus: 'COMPLETED',
      exitCode: 0,
      compileOutput: 'Build success. 0 warnings.',
      stdout: 'All tests finished.',
      stderr: '',
      executionDurationMs: 412,
      provisionalScore: 8.5,
      autoScore: 8.5,
      overrideScore: null,
      effectiveScore: 8.5,
      reviewStatus: currentReviewStatus,
      publicationStatus: currentPubStatus,
      reviewedById: currentReviewStatus === 'APPROVED' ? 'teacher-01' : null,
      reviewedByName: currentReviewStatus === 'APPROVED' ? 'TS. Nguyễn Văn Giảng Viên' : null,
      reviewedAt: currentReviewStatus === 'APPROVED' ? new Date().toISOString() : null,
      reviewNotes: currentReviewStatus === 'APPROVED' ? 'Code chuẩn, pass 4/5 test cases.' : null,
      approvedScore: currentApprovedScore,
      publishedById: currentPubStatus === 'PUBLISHED' ? 'teacher-01' : null,
      publishedByName: currentPubStatus === 'PUBLISHED' ? 'TS. Nguyễn Văn Giảng Viên' : null,
      publishedAt: currentPubStatus === 'PUBLISHED' ? new Date().toISOString() : null,
      publishedScore: currentPublishedScore,
      scoreVersion: 1,
      testCasesPassed: 4,
      testCasesTotal: 5,
      testResults: [
        {
          id: 'tr-1',
          testCaseId: 'tc-1',
          testCaseName: 'Mảng rỗng hoặc 1 phần tử',
          status: 'PASSED',
          score: 2.0,
          maxScore: 2.0,
          isHidden: false,
          executionTimeMs: 12,
        },
        {
          id: 'tr-2',
          testCaseId: 'tc-2',
          testCaseName: 'Mảng đã sắp xếp',
          status: 'PASSED',
          score: 2.0,
          maxScore: 2.0,
          isHidden: false,
          executionTimeMs: 15,
        },
        {
          id: 'tr-3',
          testCaseId: 'tc-3',
          testCaseName: 'Mảng ngẫu nhiên 10,000 số',
          status: 'PASSED',
          score: 2.5,
          maxScore: 2.5,
          isHidden: true,
          executionTimeMs: 45,
        },
        {
          id: 'tr-4',
          testCaseId: 'tc-4',
          testCaseName: 'Mảng toàn phần tử trùng lặp',
          status: 'PASSED',
          score: 2.0,
          maxScore: 2.0,
          isHidden: true,
          executionTimeMs: 20,
        },
        {
          id: 'tr-5',
          testCaseId: 'tc-5',
          testCaseName: 'Mảng cực lớn 1,000,000 số (Memory Limit)',
          status: 'FAILED',
          score: 0.0,
          maxScore: 1.5,
          isHidden: true,
          executionTimeMs: 320,
          errorMessage: 'Time Limit Exceeded (>300ms)',
        },
      ],
      criteria: [],
      auditLogs: [
        {
          id: 'audit-1',
          actorId: 'system',
          actorName: 'Automated Grader',
          actorEmail: 'system@codeassess.internal',
          actionType: 'AUTO_EVALUATED',
          preEffectiveScore: null,
          postEffectiveScore: 8.5,
          rationale: 'Chấm tự động: đạt 4/5 test cases.',
          createdAt: new Date().toISOString(),
        },
      ],
      previousAttempts: [],
    });

    // Mock API list: /api/teacher/grading
    await page.route('**/api/teacher/grading?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockGradingQueue),
      });
    });
    await page.route('**/api/teacher/grading', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockGradingQueue),
      });
    });

    // Mock API detail: /api/teacher/grading/{id}
    await page.route(`**/api/teacher/grading/${submissionId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(getDetailResponse()),
      });
    });

    // Mock API files: /api/teacher/submissions/{id}/files
    await page.route(`**/api/teacher/submissions/${submissionId}/files`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            filename: 'QuickSort.java',
            size: 1024,
            content: 'public class QuickSort {\n    public static void sort(int[] a) {\n        // Implementation\n    }\n}',
          },
        ]),
      });
    });

    // Mock Approve API: /api/teacher/grading/{id}/approve
    await page.route(`**/api/teacher/grading/${submissionId}/approve`, async (route) => {
      currentReviewStatus = 'APPROVED';
      currentApprovedScore = 8.5;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(getDetailResponse()),
      });
    });

    // Mock Publish API: /api/teacher/grading/{id}/publish
    await page.route(`**/api/teacher/grading/${submissionId}/publish`, async (route) => {
      currentPubStatus = 'PUBLISHED';
      currentPublishedScore = 8.5;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(getDetailResponse()),
      });
    });

    // 1. Visit /teacher/grading queue
    await page.goto('/teacher/grading');
    await expect(page.getByText('Thẩm định & Phê duyệt Điểm bài thi')).toBeVisible();
    await expect(page.getByText('Kỳ thi Java Cấu trúc Dữ liệu Kỳ 1')).toBeVisible();
    await expect(page.getByText('Chờ duyệt điểm')).toBeVisible();

    // 2. Open detail page
    await page.click(`a[href="/teacher/grading/${submissionId}"]`);
    await page.waitForURL(`**/teacher/grading/${submissionId}`);

    // Verify grading detail contents
    await expect(page.getByText('Điểm Đánh Giá & Thẩm Định')).toBeVisible();
    await expect(page.getByText('8.5', { exact: true })).toBeVisible();
    await expect(page.getByText('PENDING_REVIEW')).toBeVisible();
    await expect(page.getByText('CHƯA CÔNG BỐ', { exact: true })).toBeVisible();

    // Verify testcase results (both public and hidden test cases rendered for teacher)
    await expect(page.getByText('Mảng rỗng hoặc 1 phần tử')).toBeVisible();
    await expect(page.getByText('Mảng cực lớn 1,000,000 số (Memory Limit)')).toBeVisible();

    // Verify source code browser
    await expect(page.getByRole('button', { name: 'QuickSort.java' })).toBeVisible();

    // 3. Click "Phê duyệt điểm số"
    await page.click('button:has-text("Phê duyệt điểm")');
    await expect(page.getByText('Xác nhận Phê duyệt Điểm')).toBeVisible();
    await page.click('button:has-text("Xác nhận Phê duyệt")');

    // Verify approved state
    await expect(page.getByText('APPROVED')).toBeVisible();

    // 4. Click "Công bố điểm cho SV"
    await page.click('button:has-text("Công bố điểm cho SV")');
    await expect(page.getByText('Xác nhận Công bố Điểm Chính thức')).toBeVisible();
    await page.click('button:has-text("Công bố Điểm Ngay")');

    // Verify published state
    await expect(page.getByText('ĐÃ CÔNG BỐ (8.5 đ)')).toBeVisible();
  });

  test('3. Student views published grade and creates appeal', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'STUDENT' });
    await mockUserProfile(page, {
      id: 'student-01',
      role: 'STUDENT',
      email: 'student@codeassess.edu.vn',
      fullName: 'Nguyễn Văn Sinh Viên',
    });

    // Mock published scoring response: /api/student/submissions/{id}/result
    await page.route(`**/api/student/submissions/${submissionId}/result`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          submissionId,
          studentId: 'student-01',
          studentName: 'Nguyễn Văn Sinh Viên',
          examName: 'Kỳ thi Java Cấu trúc Dữ liệu Kỳ 1',
          className: 'CS201',
          questionId,
          questionTitle: 'Câu 1: Thuật toán QuickSort',
          questionMaxScore: 10.0,
          status: 'COMPLETED',
          totalScore: 8.5,
          overrideTotalScore: null,
          effectiveScore: 8.5,
          maxTotalScore: 10.0,
          autoScore: 8.5,
          testCasesPassed: 4,
          testCasesTotal: 5,
          criteria: [],
          testResults: [
            {
              id: 'tr-1',
              testCaseId: 'tc-1',
              testCaseName: 'Sample Case 1',
              score: 2.0,
              maxScore: 2.0,
              passed: true,
              isHidden: false,
            },
          ],
        }),
      });
    });

    // Mock existing student appeals for this submission: empty
    await page.route(`**/api/student/submissions/${submissionId}/appeals`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    // Mock student appeals list & creation
    await page.route('**/api/student/appeals', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'appeal-mock-01',
            submissionId,
            examName: 'Kỳ thi Java Cấu trúc Dữ liệu Kỳ 1',
            basis: 'grading_dispute',
            explanation: 'Em xin phúc khảo testcase số 5 bị time limit exceeded do server bận.',
            status: 'PENDING',
            createdAt: new Date().toISOString(),
          }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        });
      }
    });

    await page.goto(`/student/submissions/${submissionId}`);

    // Verify published score is visible
    await expect(page.getByText('8.5', { exact: true })).toBeVisible();

    // Verify "Gửi đơn khiếu nại điểm" button is available
    const appealBtn = page.getByRole('link', { name: /Gửi đơn khiếu nại điểm/i });
    await expect(appealBtn).toBeVisible();
    await appealBtn.click();
    await page.waitForURL(`**/student/appeals/new?submissionId=${submissionId}*`);

    // Verify appeal creation page
    await expect(page.getByRole('heading', { name: 'Gửi đơn khiếu nại Điểm số & Liêm chính' })).toBeVisible();
    await page.check('input[value="grading_dispute"]', { force: true });
    await page.fill('textarea[name="explanation"]', 'Em xin phúc khảo testcase số 5 bị time limit exceeded do server bận.');
    await page.click('button:has-text("Gửi đơn khiếu nại chính thức")');

    // Confirm submission in modal
    await expect(page.getByText('Xác nhận gửi đơn khiếu nại')).toBeVisible();
    await page.click('button:has-text("Xác nhận & Gửi đơn")');

    // Should redirect to student appeals list
    await page.waitForURL(/\/student\/appeals/);
  });

  test('4. Admin manages appeals: starts review, downloads ZIP archive, approves with controlled resolution', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'ADMIN' });
    await mockUserProfile(page, {
      id: 'admin-01',
      role: 'ADMIN',
      email: 'admin@codeassess.edu.vn',
      fullName: 'Quản trị viên Khảo thí',
    });

    const appealId = 'appeal-mock-01';
    let currentAppealStatus = 'PENDING';
    let currentResolutionAction: string | null = null;
    let currentUpdatedScore: number | null = null;

    const getAppealDetail = () => ({
      id: appealId,
      submissionId,
      examName: 'Kỳ thi Java Cấu trúc Dữ liệu Kỳ 1',
      studentName: 'Nguyễn Văn Sinh Viên',
      studentEmail: 'student@codeassess.edu.vn',
      basis: 'TEST_CASE_MISMATCH',
      explanation: 'Em xin phúc khảo testcase số 5 bị time limit exceeded do server bận.',
      status: currentAppealStatus,
      resolutionAction: currentResolutionAction,
      createdAt: new Date().toISOString(),
      submittedAt: new Date().toISOString(),
      autoScore: 8.5,
      originalScore: 8.5,
      updatedScore: currentUpdatedScore,
      currentScore: currentUpdatedScore ?? 8.5,
      testCasesPassed: 4,
      testCasesTotal: 5,
      hasArchive: true,
      originalFilename: 'quicksort-student.zip',
      fileSize: 4096,
      sourceCode: 'public class QuickSort { ... }',
      reviewerNotes: currentAppealStatus === 'APPROVED' ? 'Chấp thuận do server quá tải.' : null,
      resolvedAt: currentAppealStatus === 'APPROVED' ? new Date().toISOString() : null,
    });

    // Mock admin appeal list
    await page.route('**/api/admin/appeals*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([getAppealDetail()]),
      });
    });

    // Mock admin appeal detail
    await page.route(`**/api/admin/appeals/${appealId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(getAppealDetail()),
      });
    });

    // Mock start review API
    await page.route(`**/api/admin/appeals/${appealId}/start-review`, async (route) => {
      currentAppealStatus = 'UNDER_REVIEW';
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(getAppealDetail()),
      });
    });

    // Mock download archive API
    await page.route(`**/api/admin/appeals/${appealId}/submission/archive`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/zip',
        headers: {
          'Content-Disposition': 'attachment; filename="quicksort-student.zip"',
        },
        body: Buffer.from('mock-zip-content'),
      });
    });

    // Mock resolve appeal API
    await page.route(`**/api/admin/appeals/${appealId}/resolve`, async (route) => {
      const payload = JSON.parse(route.request().postData() || '{}');
      currentAppealStatus = payload.decision;
      currentResolutionAction = payload.resolutionAction;
      currentUpdatedScore = payload.adjustedScore || null;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(getAppealDetail()),
      });
    });

    // 1. Visit /admin/appeals
    await page.goto('/admin/appeals');
    await expect(page.getByText('Quản lý Khiếu nại Điểm số')).toBeVisible();
    await expect(page.getByText('Chờ Admin')).toBeVisible();

    // 2. Open appeal detail
    await page.click(`a[href="/admin/appeals/${appealId}"]`);
    await page.waitForURL(`**/admin/appeals/${appealId}`);

    // Verify appeal details
    await expect(page.getByText('Xem xét & Phán quyết Khiếu nại')).toBeVisible();
    await expect(page.getByText('quicksort-student.zip')).toBeVisible();

    // 3. Start review
    const startReviewBtn = page.getByRole('button', { name: /Bắt đầu thẩm định/i });
    await expect(startReviewBtn).toBeVisible();
    await startReviewBtn.click();

    // Verify status changes to UNDER_REVIEW
    await expect(page.getByText('Đang thẩm định (Under Review)')).toBeVisible();

    // 4. Download archive
    const downloadPromise = page.waitForEvent('download');
    await page.click('button:has-text("Tải file ZIP")');
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toBe('quicksort-student.zip');

    // 5. Select resolution action & approve with adjusted score
    await page.click('button:has-text("Chấp thuận khiếu nại (APPROVED)")');
    await page.click('button:has-text("Điều chỉnh điểm trực tiếp")');
    await page.fill('input[placeholder="VD: 9.0"]', '9.5');
    await page.fill('textarea[placeholder*="Nhập căn cứ pháp lý"]', 'Server ghi nhận độ trễ vượt ngưỡng. Đã thẩm định lại code đạt chuẩn.');
    await page.click('button:has-text("Lưu Phán quyết Khiếu nại")');

    // Confirm in modal
    await expect(page.getByText('Xác nhận phán quyết khiếu nại?')).toBeVisible();
    await page.click('button:has-text("Xác nhận thực hiện")');

    // Verify resolved state
    await expect(page.getByText('Đã chấp thuận')).toBeVisible();
    await expect(page.getByText('9.5', { exact: true })).toBeVisible();
  });
});
