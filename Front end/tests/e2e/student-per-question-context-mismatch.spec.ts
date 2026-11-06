import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('BUG-FIX-STUDENT-PER-QUESTION-CONTEXT-MISMATCH-01 — Question Context Synchronization E2E', () => {
  const examId = 'exam-context-sync-4q-test';

  // 4-Question Exam with 0-indexed displayOrder (0, 1, 2, 3) matching real database structure
  const mockExam = {
    id: examId,
    title: 'Kỳ thi Java Nâng cao: 4 Câu hỏi Độc lập',
    description: 'Đề thi 4 câu hỏi kiểm tra tính đồng bộ context câu hỏi.',
    duration: 120,
    durationMinutes: 120,
    startTime: new Date(Date.now() - 20 * 60 * 1000).toISOString(),
    endTime: new Date(Date.now() + 100 * 60 * 1000).toISOString(),
    serverTime: new Date().toISOString(),
    status: 'active',
    courseClassCode: 'CS301-JAVA-ADV',
    totalAllocatedPoints: 10,
    totalQuestions: 4,
    questions: [
      {
        id: 'q1-spiral',
        examId,
        title: 'Spiral Matrix Traversal',
        description: 'Duyệt ma trận theo hình xoắn ốc ngược chiều kim đồng hồ.',
        maxScore: 2.5,
        displayOrder: 0, // 0-indexed in DB!
        status: 'PUBLISHED',
        testCases: [
          { id: 'tc1-1', name: 'Sample 3x3', points: 1.0, isHidden: false, displayOrder: 0, inputData: '3 3', expectedOutput: '1 2 3' },
          { id: 'tc1-2', name: 'Hidden 5x5', points: 1.5, isHidden: true, displayOrder: 1 },
        ],
      },
      {
        id: 'q2-palindrome',
        examId,
        title: 'Longest Palindromic Substring',
        description: 'Tìm chuỗi con đối xứng dài nhất với độ phức tạp tối ưu O(N).',
        maxScore: 2.5,
        displayOrder: 1, // 0-indexed in DB!
        status: 'PUBLISHED',
        testCases: [
          { id: 'tc2-1', name: 'Sample abaxyzzyxf', points: 1.0, isHidden: false, displayOrder: 0, inputData: 'abaxyzzyxf', expectedOutput: 'xyzzyx' },
          { id: 'tc2-2', name: 'Hidden Edge Case', points: 1.5, isHidden: true, displayOrder: 1 },
        ],
      },
      {
        id: 'q3-prefix-tree',
        examId,
        title: 'Trie Prefix Search and Autocomplete',
        description: 'Cài đặt cấu trúc dữ liệu Trie hỗ trợ insert, search và startsWith.',
        maxScore: 2.5,
        displayOrder: 2, // 0-indexed in DB!
        status: 'PUBLISHED',
        testCases: [
          { id: 'tc3-1', name: 'Sample Insert Search', points: 2.5, isHidden: false, displayOrder: 0, inputData: 'apple app', expectedOutput: 'true false' },
        ],
      },
      {
        id: 'q4-graph-cycle',
        examId,
        title: 'Detect Cycle in Directed Graph',
        description: 'Sử dụng thuật toán DFS hoặc Kahn để phát hiện chu trình trong đồ thị có hướng.',
        maxScore: 2.5,
        displayOrder: 3, // 0-indexed in DB!
        status: 'PUBLISHED',
        testCases: [
          { id: 'tc4-1', name: 'Sample DAG', points: 2.5, isHidden: false, displayOrder: 0, inputData: '4 3\n0 1\n1 2\n2 3', expectedOutput: 'false' },
        ],
      },
    ],
  };

  const validZipBuffer = Buffer.from('PK\x05\x06' + '\x00'.repeat(18));

  test('Complete Flow: Context Synchronization, File Preservation, and Correct questionId Submission', async ({ page }) => {
    let capturedSubmissionRequest: any = null;
    let submissionsState: any[] = [];

    // Intercept student exam endpoint
    await setMockAuthCookies(page, { role: 'STUDENT' });
    await mockUserProfile(page, { role: 'STUDENT' });

    await page.route(`**/api/student/exams/${examId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockExam),
      });
    });

    await page.route(`**/api/student/exams/${examId}/submissions*`, async (route) => {
      const method = route.request().method();
      const url = route.request().url();

      if (method === 'POST') {
        const postData = route.request().postDataBuffer();
        const postDataStr = postData?.toString('utf-8') || '';
        const urlObj = new URL(url);
        let extractedQuestionId = urlObj.searchParams.get('questionId');
        if (!extractedQuestionId) {
          const match = postDataStr.match(/name="questionId"\s*\r?\n\r?\n([^\r\n]+)/);
          if (match) extractedQuestionId = match[1].trim();
        }

        capturedSubmissionRequest = {
          url,
          queryQuestionId: extractedQuestionId,
          contentLength: postData?.length,
        };

        const targetQ = mockExam.questions.find((q) => q.id === extractedQuestionId);
        const newSubmission = {
          id: `sub-${extractedQuestionId}-${Date.now()}`,
          examId,
          questionId: extractedQuestionId,
          questionTitle: targetQ?.title || 'Unknown',
          questionMaxScore: targetQ?.maxScore || 2.5,
          originalFilename: 'solution_q2.zip',
          executionStatus: 'COMPLETED',
          testCasesPassed: 2,
          testCasesTotal: 2,
          autoScore: 2.5,
          totalScore: 2.5,
          createdAt: new Date().toISOString(),
        };

        submissionsState.push(newSubmission);

        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newSubmission),
        });
      } else if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(submissionsState),
        });
      } else {
        await route.continue();
      }
    });

    // 1. Mở bài thi có ít nhất 4 câu
    await page.goto(`/student/exams/${examId}`);
    await expect(page.locator('h1')).toContainText(mockExam.title);

    // Verify all 4 questions exist in Navigator and Cards
    for (let i = 1; i <= 4; i++) {
      await expect(page.locator(`text=Câu ${i}`).first()).toBeVisible();
    }

    // 2. Chọn Câu 1 và xác nhận upload label + submit button đều là Câu 1
    const navQ1 = page.locator('[data-testid="nav-question-q1-spiral"]');
    await navQ1.click();

    // Check Question 1 Card
    const q1UploadLabel = page.locator('[data-testid="upload-label-q1-spiral"]');
    const q1SubmitBtn = page.locator('#submit-question-q1-spiral');
    await expect(q1UploadLabel).toContainText('Đính kèm tệp ZIP cho Câu 1:');
    await expect(q1SubmitBtn).toContainText('Nộp Câu 1');

    // Check Sticky Footer
    const stickyUploadLabel = page.locator('[data-testid="sticky-active-upload-label"]');
    const stickySubmitBtn = page.locator('#sticky-submit-question-btn');
    await expect(stickyUploadLabel).toContainText('Đính kèm tệp ZIP cho Câu 1');
    await expect(stickySubmitBtn).toContainText('Nộp Câu 1');

    // Attach ZIP to Question 1
    await page.setInputFiles('#file-input-q1-spiral', {
      name: 'solution_q1.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });
    await expect(page.locator('text=solution_q1.zip').first()).toBeVisible();

    // 3. Chọn Câu 2 và xác nhận cả hai đều chuyển thành Câu 2
    const navQ2 = page.locator('[data-testid="nav-question-q2-palindrome"]');
    await navQ2.click();

    // Verify Question 2 Card context
    const q2UploadLabel = page.locator('[data-testid="upload-label-q2-palindrome"]');
    const q2SubmitBtn = page.locator('#submit-question-q2-palindrome');
    await expect(q2UploadLabel).toContainText('Đính kèm tệp ZIP cho Câu 2:');
    await expect(q2SubmitBtn).toContainText('Nộp Câu 2');

    // Verify Sticky Footer context switched to Câu 2
    await expect(page.locator('[data-testid="sticky-active-question-label"]')).toContainText('Câu 2: Longest Palindromic Substring');
    await expect(stickyUploadLabel).toContainText('Đính kèm tệp ZIP cho Câu 2');
    await expect(stickySubmitBtn).toContainText('Nộp Câu 2');

    // Attach ZIP to Question 2
    await page.setInputFiles('#file-input-q2-palindrome', {
      name: 'solution_q2.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });
    await expect(page.locator('text=solution_q2.zip').first()).toBeVisible();

    // 4. Chọn Câu 3 và xác nhận cả hai đều chuyển thành Câu 3
    const navQ3 = page.locator('[data-testid="nav-question-q3-prefix-tree"]');
    await navQ3.click();

    // Verify Question 3 Card context
    const q3UploadLabel = page.locator('[data-testid="upload-label-q3-prefix-tree"]');
    const q3SubmitBtn = page.locator('#submit-question-q3-prefix-tree');
    await expect(q3UploadLabel).toContainText('Đính kèm tệp ZIP cho Câu 3:');
    await expect(q3SubmitBtn).toContainText('Nộp Câu 3');

    // Verify Sticky Footer context switched to Câu 3
    await expect(page.locator('[data-testid="sticky-active-question-label"]')).toContainText('Câu 3: Trie Prefix Search');
    await expect(stickyUploadLabel).toContainText('Đính kèm tệp ZIP cho Câu 3');
    await expect(stickySubmitBtn).toContainText('Nộp Câu 3');

    // 5. Chọn lại Câu 1, kiểm tra ZIP đã chọn trước đó vẫn được giữ
    await navQ1.click();
    await expect(page.locator('[data-testid="sticky-active-question-label"]')).toContainText('Câu 1: Spiral Matrix Traversal');
    // Confirm solution_q1.zip is still attached to Question 1
    const q1Card = page.locator('[data-testid="question-card-q1-spiral"]');
    await expect(q1Card.locator('text=solution_q1.zip')).toBeVisible();
    await expect(q1SubmitBtn).toBeEnabled();

    // Confirm solution_q2.zip is also still preserved on Question 2
    const q2Card = page.locator('[data-testid="question-card-q2-palindrome"]');
    await expect(q2Card.locator('text=solution_q2.zip')).toBeVisible();

    // 6. Submit Câu 2 và xác nhận request chứa đúng questionId của Câu 2
    await navQ2.click();
    await expect(page.locator('[data-testid="sticky-active-question-label"]')).toContainText('Câu 2');
    await expect(q2SubmitBtn).toBeEnabled();
    await expect(q2SubmitBtn).toContainText('Nộp Câu 2');

    // Click submit for Question 2
    await q2SubmitBtn.click();

    // Verify request payload contains EXACTLY q2-palindrome
    expect(capturedSubmissionRequest).not.toBeNull();
    expect(capturedSubmissionRequest.queryQuestionId).toBe('q2-palindrome');

    // 7. Kiểm tra submission chỉ được tạo cho Câu 2
    expect(submissionsState).toHaveLength(1);
    expect(submissionsState[0].questionId).toBe('q2-palindrome');
    expect(submissionsState[0].questionTitle).toBe('Longest Palindromic Substring');

    // Verify UI reflects Q2 submission status
    await expect(page.locator('text=Đã chấm: 2.5/2.5 đ').first()).toBeVisible();
    await expect(page.locator('text=Nộp lại Câu 2').first()).toBeVisible();

    // Verify Question 1 was NOT submitted (still has solution_q1.zip ready and not submitted)
    await expect(q1Card.locator('text=Đã chọn ZIP')).toBeVisible();
    await expect(q1SubmitBtn).toContainText('Nộp Câu 1');

    // 8. Reload trang và kiểm tra trạng thái từng câu không bị trộn lẫn
    await page.reload();

    // After reload: Q2 still shows submitted/graded state
    const reloadedQ2Card = page.locator('[data-testid="question-card-q2-palindrome"]');
    await expect(reloadedQ2Card).toBeVisible();
    await expect(reloadedQ2Card.locator('text=Đã chấm: 2.5/2.5 đ')).toBeVisible();
    await expect(reloadedQ2Card.locator('button:has-text("Nộp lại Câu 2")')).toBeVisible();

    // After reload: Q1 and Q3 remain NOT_SUBMITTED (never mixed with Q2)
    const reloadedQ1Card = page.locator('[data-testid="question-card-q1-spiral"]');
    await expect(reloadedQ1Card.locator('text=Chưa nộp')).toBeVisible();
    await expect(reloadedQ1Card.locator('button:has-text("Nộp Câu 1")')).toBeDisabled();

    const reloadedQ3Card = page.locator('[data-testid="question-card-q3-prefix-tree"]');
    await expect(reloadedQ3Card.locator('text=Chưa nộp')).toBeVisible();
    await expect(reloadedQ3Card.locator('button:has-text("Nộp Câu 3")')).toBeDisabled();

    // Overall counter reflects exactly 1 / 4 questions submitted
    await expect(page.locator('[data-testid="submitted-questions-counter"]')).toContainText('1 / 4 câu');
  });
});
