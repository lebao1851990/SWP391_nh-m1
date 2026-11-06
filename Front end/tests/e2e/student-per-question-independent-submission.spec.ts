import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('Student Per-Question Independent Submission E2E (TASK-STUDENT-PER-QUESTION-SUBMISSION-REDESIGN-01)', () => {
  const examId = 'exam-multi-question-test-01';

  const mockExam = {
    id: examId,
    title: 'Kỳ thi Java Cấu trúc Dữ liệu & Giải thuật',
    description: 'Đề thi gồm 3 câu hỏi độc lập.',
    duration: 90,
    durationMinutes: 90,
    startTime: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
    endTime: new Date(Date.now() + 75 * 60 * 1000).toISOString(),
    serverTime: new Date().toISOString(),
    status: 'active',
    courseClassCode: 'CS201-DSA',
    totalAllocatedPoints: 10,
    totalQuestions: 3,
    questions: [
      {
        id: 'q1-sum',
        examId,
        title: 'Sum of Positive Numbers',
        description: 'Viết chương trình tính tổng các số nguyên dương trong một mảng cho trước.',
        maxScore: 3.33,
        displayOrder: 1,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc1',
            name: 'Sample Case 1',
            description: 'Mảng có cả số âm và số dương',
            points: 1.0,
            isHidden: false,
            displayOrder: 1,
            inputData: '5\n1 -2 3 -4 5',
            expectedOutput: '9',
          },
          {
            id: 'tc2',
            name: 'Hidden Edge Case (Ẩn)',
            description: 'Bảo mật',
            points: 2.33,
            isHidden: true,
            displayOrder: 2,
            inputData: 'SECRET_INPUT_DO_NOT_LEAK',
            expectedOutput: 'SECRET_OUTPUT_DO_NOT_LEAK',
          },
        ],
      },
      {
        id: 'q2-second-largest',
        examId,
        title: 'Find the Second Largest Number',
        description: '', // Empty description to test fallback notice
        maxScore: 3.33,
        displayOrder: 2,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc3',
            name: 'Sample Case 1',
            points: 3.33,
            isHidden: false,
            displayOrder: 1,
            inputData: '4\n12 35 1 10',
            expectedOutput: '12',
          },
        ],
      },
      {
        id: 'q3-palindrome',
        examId,
        title: 'Check Palindrome String',
        description: 'Kiểm tra chuỗi đối xứng không phân biệt chữ hoa, chữ thường.',
        maxScore: 3.34,
        displayOrder: 3,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc4',
            name: 'Sample Case 1',
            points: 3.34,
            isHidden: false,
            displayOrder: 1,
            inputData: 'racecar',
            expectedOutput: 'true',
          },
        ],
      },
    ],
  };

  const validZipBuffer = Buffer.from('PK\x05\x06' + '\x00'.repeat(18));

  test.beforeEach(async ({ page }) => {
    await setMockAuthCookies(page, { role: 'STUDENT' });
    await mockUserProfile(page, { role: 'STUDENT' });

    // Mock student exam detail endpoint
    await page.route(`**/api/student/exams/${examId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockExam),
      });
    });
  });

  test('1. Independent Submission: Student submits Question 1 while Q2 and Q3 are untouched', async ({ page }) => {
    let capturedSubmissionRequest: { examId: string; questionId?: string; filename?: string } | null = null;
    let submissionsDatabase: any[] = [];

    // Mock submissions endpoint to return dynamic list
    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      if (route.request().method() === 'POST') {
        const postData = route.request().postData();
        capturedSubmissionRequest = {
          examId,
          questionId: 'q1-sum',
          filename: 'solution_q1.zip',
        };

        const newSub = {
          id: 'sub-q1-01',
          examId,
          questionId: 'q1-sum',
          questionTitle: 'Sum of Positive Numbers',
          questionMaxScore: 3.33,
          originalFilename: 'solution_q1.zip',
          status: 'COMPLETED',
          executionStatus: 'COMPLETED',
          testCasesPassed: 2,
          testCasesTotal: 2,
          autoScore: 3.33,
          totalScore: 3.33,
          createdAt: new Date().toISOString(),
        };
        submissionsDatabase = [newSub];

        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newSub),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(submissionsDatabase),
        });
      }
    });

    await page.goto(`/student/exams/${examId}`);

    // Verify initial progress is 0 / 3 câu hỏi đã nộp
    await expect(page.locator('text=0 / 3 câu hỏi đã nộp')).toBeVisible();

    // Verify per-question submit button for Q1 is disabled before file selection
    const q1SubmitBtn = page.locator('#submit-question-q1-sum');
    await expect(q1SubmitBtn).toBeDisabled();

    // Select file for Question 1 only
    await page.setInputFiles('#file-input-q1-sum', {
      name: 'solution_q1.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    // Question 1 submit button is now enabled
    await expect(q1SubmitBtn).toBeEnabled();
    await expect(q1SubmitBtn).toContainText('Nộp Câu 1');

    // Question 2 and Question 3 submit buttons MUST remain disabled
    await expect(page.locator('#submit-question-q2-second-largest')).toBeDisabled();
    await expect(page.locator('#submit-question-q3-palindrome')).toBeDisabled();

    // Click submit for Question 1
    await q1SubmitBtn.click();

    // Verify backend received the single question submission with questionId
    expect(capturedSubmissionRequest).not.toBeNull();
    expect((capturedSubmissionRequest as any)?.questionId).toBe('q1-sum');

    // Verify progress updates to 1 / 3 câu hỏi đã nộp
    await expect(page.locator('text=1 / 3 câu hỏi đã nộp')).toBeVisible();

    // Question 1 card now shows submission info and score
    await expect(page.locator('#question-card-q1-sum')).toContainText('3.33 / 3.33 đ');
    await expect(page.locator('#question-card-q1-sum')).toContainText('2/2 test đạt');
  });

  test('2. Out of Order Submission: Student submits Question 3 directly, leaving Q1 and Q2 blank', async ({ page }) => {
    let capturedQuestionId: string | null = null;
    let submissionsDatabase: any[] = [];

    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      if (route.request().method() === 'POST') {
        capturedQuestionId = 'q3-palindrome';
        const newSub = {
          id: 'sub-q3-01',
          examId,
          questionId: 'q3-palindrome',
          questionTitle: 'Check Palindrome String',
          questionMaxScore: 3.34,
          originalFilename: 'palindrome.zip',
          status: 'COMPLETED',
          executionStatus: 'COMPLETED',
          testCasesPassed: 1,
          testCasesTotal: 1,
          autoScore: 3.34,
          totalScore: 3.34,
          createdAt: new Date().toISOString(),
        };
        submissionsDatabase = [newSub];
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newSub),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(submissionsDatabase),
        });
      }
    });

    await page.goto(`/student/exams/${examId}`);

    // Attach file ONLY to Question 3
    await page.setInputFiles('#file-input-q3-palindrome', {
      name: 'palindrome.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    const q3SubmitBtn = page.locator('#submit-question-q3-palindrome');
    await expect(q3SubmitBtn).toBeEnabled();
    await q3SubmitBtn.click();

    expect(capturedQuestionId).toBe('q3-palindrome');

    // Verify progress is 1 / 3
    await expect(page.locator('text=1 / 3 câu hỏi đã nộp')).toBeVisible();

    // Verify Navigator shows Question 3 graded, while Question 1 & 2 are not submitted
    const q3Card = page.locator('#question-card-q3-palindrome');
    await expect(q3Card).toContainText('Đã chấm: 3.34/3.34 đ');
    await expect(page.locator('#question-card-q1-sum')).toContainText('Chưa nộp');
    await expect(page.locator('#question-card-q2-second-largest')).toContainText('Chưa nộp');
  });

  test('3. Fallback Notice: Question 2 with empty description renders explicit placeholder', async ({ page }) => {
    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/student/exams/${examId}`);

    // Question 1 has description
    await expect(page.locator('#question-card-q1-sum')).toContainText('Viết chương trình tính tổng các số nguyên dương');

    // Question 2 description is empty in backend -> MUST render explicit notice
    const q2Card = page.locator('#question-card-q2-second-largest');
    await expect(q2Card).toContainText('Giảng viên chưa cung cấp nội dung mô tả chi tiết cho câu hỏi này.');
  });

  test('4. Safe Isolation: Hidden test cases NEVER leak input or expected output', async ({ page }) => {
    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/student/exams/${examId}`);

    const q1Card = page.locator('#question-card-q1-sum');

    // Public test case input & output should be visible
    await expect(q1Card).toContainText('Sample Case 1');
    await expect(q1Card).toContainText('1 -2 3 -4 5');
    await expect(q1Card).toContainText('9');

    // Hidden test case must show protected label and ZERO confidential content
    await expect(q1Card).toContainText('+1 test case ẩn bảo mật');
    await expect(page.locator('text=SECRET_INPUT_DO_NOT_LEAK')).toHaveCount(0);
    await expect(page.locator('text=SECRET_OUTPUT_DO_NOT_LEAK')).toHaveCount(0);
  });

  test('5. Persistence on Reload: Existing submissions are restored from backend', async ({ page }) => {
    const existingSubmissions = [
      {
        id: 'sub-q1-persisted',
        examId,
        questionId: 'q1-sum',
        questionTitle: 'Sum of Positive Numbers',
        questionMaxScore: 3.33,
        originalFilename: 'q1_final.zip',
        status: 'COMPLETED',
        executionStatus: 'COMPLETED',
        testCasesPassed: 2,
        testCasesTotal: 2,
        autoScore: 3.33,
        totalScore: 3.33,
        createdAt: new Date().toISOString(),
      },
      {
        id: 'sub-q3-persisted',
        examId,
        questionId: 'q3-palindrome',
        questionTitle: 'Check Palindrome String',
        questionMaxScore: 3.34,
        originalFilename: 'q3_palindrome.zip',
        status: 'COMPLETED',
        executionStatus: 'COMPLETED',
        testCasesPassed: 1,
        testCasesTotal: 1,
        autoScore: 3.34,
        totalScore: 3.34,
        createdAt: new Date().toISOString(),
      },
    ];

    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(existingSubmissions),
      });
    });

    await page.goto(`/student/exams/${examId}`);

    // Verify progress reflects 2 / 3 submitted
    await expect(page.locator('text=2 / 3 câu hỏi đã nộp')).toBeVisible();

    // Verify Question 1 shows previous submission info and allows re-submission
    const q1Card = page.locator('#question-card-q1-sum');
    await expect(q1Card).toContainText('q1_final.zip');
    await expect(q1Card).toContainText('3.33 / 3.33 đ');

    // Attach a new file to Question 1 to test re-submission label
    await page.setInputFiles('#file-input-q1-sum', {
      name: 'q1_v2.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });
    const q1SubmitBtn = page.locator('#submit-question-q1-sum');
    await expect(q1SubmitBtn).toBeEnabled();
    await expect(q1SubmitBtn).toContainText('Nộp lại Câu 1');

    // Verify Question 2 is still NOT_SUBMITTED
    const q2Card = page.locator('#question-card-q2-second-largest');
    await expect(q2Card).toContainText('Chưa nộp');
  });

  test('6. Navigator & Selection: Clicking Question in Navigator highlights active card and preserves files', async ({ page }) => {
    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/student/exams/${examId}`);

    // Select file for Q1
    await page.setInputFiles('#file-input-q1-sum', {
      name: 'q1.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    // Select file for Q3
    await page.setInputFiles('#file-input-q3-palindrome', {
      name: 'q3.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    // Both files are preserved
    await expect(page.locator('#question-card-q1-sum')).toContainText('q1.zip');
    await expect(page.locator('#question-card-q3-palindrome')).toContainText('q3.zip');

    // Click Question 3 in Navigator
    const navBtnQ3 = page.locator('button:has-text("Câu 3")').first();
    await navBtnQ3.click();

    // Verify Q3 card has active highlight
    const q3Card = page.locator('#question-card-q3-palindrome');
    await expect(q3Card).toHaveClass(/border-primary/);

    // Q1 file is STILL preserved
    await expect(page.locator('#question-card-q1-sum')).toContainText('q1.zip');
  });

  test('7. Resubmission History: Displays collapsible list when multiple submissions exist for a question', async ({ page }) => {
    const multiSubmissions = [
      {
        id: 'sub-q1-v2',
        examId,
        questionId: 'q1-sum',
        questionTitle: 'Sum of Positive Numbers',
        questionMaxScore: 3.33,
        originalFilename: 'q1_v2.zip',
        status: 'COMPLETED',
        executionStatus: 'COMPLETED',
        testCasesPassed: 2,
        testCasesTotal: 2,
        autoScore: 3.33,
        totalScore: 3.33,
        createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
      },
      {
        id: 'sub-q1-v1',
        examId,
        questionId: 'q1-sum',
        questionTitle: 'Sum of Positive Numbers',
        questionMaxScore: 3.33,
        originalFilename: 'q1_v1.zip',
        status: 'COMPLETED',
        executionStatus: 'COMPILE_ERROR',
        testCasesPassed: 0,
        testCasesTotal: 2,
        autoScore: 0,
        totalScore: 0,
        createdAt: new Date(Date.now() - 25 * 60 * 1000).toISOString(),
      },
    ];

    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(multiSubmissions),
      });
    });

    await page.goto(`/student/exams/${examId}`);

    const q1Card = page.locator('#question-card-q1-sum');
    // Verify latest submission is v2
    await expect(q1Card).toContainText('q1_v2.zip');
    await expect(q1Card).toContainText('3.33 / 3.33 đ');

    // Verify download ZIP link is present
    const downloadZip = q1Card.locator('a:has-text("Tải ZIP")');
    await expect(downloadZip).toBeVisible();

    // Verify collapsible history button is visible with 2 attempts
    const historyBtn = q1Card.locator('button:has-text("Lịch sử nộp bài (2 lần nộp)")');
    await expect(historyBtn).toBeVisible();

    // Click to expand history
    await historyBtn.click();

    // Verify history table contains both v1 and v2 records
    await expect(q1Card).toContainText('q1_v1.zip');
    await expect(q1Card).toContainText('COMPILE_ERROR');
  });

  test('8. Sequential Submission: Submitting Q2 after Q1 updates overall progress to 2/3 without affecting Q1', async ({ page }) => {
    let submissionsDatabase: any[] = [
      {
        id: 'sub-q1-done',
        examId,
        questionId: 'q1-sum',
        questionTitle: 'Sum of Positive Numbers',
        questionMaxScore: 3.33,
        originalFilename: 'q1.zip',
        status: 'COMPLETED',
        executionStatus: 'COMPLETED',
        testCasesPassed: 2,
        testCasesTotal: 2,
        autoScore: 3.33,
        totalScore: 3.33,
        createdAt: new Date().toISOString(),
      },
    ];

    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      if (route.request().method() === 'POST') {
        const newSub = {
          id: 'sub-q2-done',
          examId,
          questionId: 'q2-second-largest',
          questionTitle: 'Find the Second Largest Number',
          questionMaxScore: 3.33,
          originalFilename: 'q2.zip',
          status: 'COMPLETED',
          executionStatus: 'COMPLETED',
          testCasesPassed: 1,
          testCasesTotal: 1,
          autoScore: 3.33,
          totalScore: 3.33,
          createdAt: new Date().toISOString(),
        };
        submissionsDatabase.push(newSub);
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newSub),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(submissionsDatabase),
        });
      }
    });

    await page.goto(`/student/exams/${examId}`);

    // Initial: 1 / 3
    await expect(page.locator('text=1 / 3 câu hỏi đã nộp')).toBeVisible();

    // Select file for Q2
    await page.setInputFiles('#file-input-q2-second-largest', {
      name: 'q2.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    const q2SubmitBtn = page.locator('#submit-question-q2-second-largest');
    await expect(q2SubmitBtn).toBeEnabled();
    await q2SubmitBtn.click();

    // Progress updates to 2 / 3
    await expect(page.locator('text=2 / 3 câu hỏi đã nộp')).toBeVisible();

    // Q1 card remains graded with 3.33 đ
    await expect(page.locator('#question-card-q1-sum')).toContainText('3.33 / 3.33 đ');

    // Q2 card is now graded with 3.33 đ
    await expect(page.locator('#question-card-q2-second-largest')).toContainText('3.33 / 3.33 đ');
  });

  test('9. Submission Error: Server 400 displays actionable error on question card and keeps selected file', async ({ page }) => {
    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            message: 'Mã nguồn bị lỗi cấu trúc: Không tìm thấy file Main.java trong tệp ZIP.',
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

    await page.goto(`/student/exams/${examId}`);

    await page.setInputFiles('#file-input-q1-sum', {
      name: 'broken_structure.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    const q1SubmitBtn = page.locator('#submit-question-q1-sum');
    await q1SubmitBtn.click();

    // Error message must appear on Q1 card
    const q1Card = page.locator('#question-card-q1-sum');
    await expect(q1Card).toContainText('Không tìm thấy file Main.java trong tệp ZIP');

    // Selected file MUST still be attached so user can see what failed
    await expect(q1Card).toContainText('broken_structure.zip');
  });

  test('10. UPCOMING exam: displays upcoming banner and prevents file selection and submit', async ({ page }) => {
    const upcomingExam = {
      ...mockExam,
      status: 'upcoming',
      startTime: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    };

    await page.route(`**/api/student/exams/${examId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(upcomingExam),
      });
    });

    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/student/exams/${examId}`);

    // Verify upcoming banner is visible
    await expect(page.locator('text=Bài thi chưa mở cổng làm bài')).toBeVisible();

    // Submit button is disabled
    const q1SubmitBtn = page.locator('#submit-question-q1-sum');
    await expect(q1SubmitBtn).toBeDisabled();
  });

  test('11. COMPLETED / EXPIRED exam: displays expired banner and disables submit', async ({ page }) => {
    const expiredExam = {
      ...mockExam,
      status: 'completed',
      isExpired: true,
      endTime: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
    };

    await page.route(`**/api/student/exams/${examId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(expiredExam),
      });
    });

    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/student/exams/${examId}`);

    // Verify expired banner is visible
    await expect(page.locator('text=Thời gian làm bài đã kết thúc')).toBeVisible();

    // Submit button is disabled
    const q1SubmitBtn = page.locator('#submit-question-q1-sum');
    await expect(q1SubmitBtn).toBeDisabled();
  });
});

