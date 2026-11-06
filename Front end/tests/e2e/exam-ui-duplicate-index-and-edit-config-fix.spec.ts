import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('TASK-EXAM-UI-DUPLICATE-INDEX-AND-EDIT-CONFIG-FIX-01: E2E Verification', () => {
  const targetExamId = '38ecada1-d0b9-49ba-a79d-91f466a902cb';

  const mockExamData = {
    id: targetExamId,
    title: 'Đề thi thực hành tổng hợp Lập trình',
    examDate: (() => {
      const now = new Date(Date.now() - 15 * 60 * 1000);
      const pad = (n) => n.toString().padStart(2, '0');
      return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
    })(), // Non-ISO format with space to verify date parser
    startTime: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
    endTime: new Date(Date.now() + 75 * 60 * 1000).toISOString(),
    duration: 90,
    durationMinutes: 90,
    status: 'active',
    classId: '8e84478e-bd09-4fe4-a39d-40f16f30179c',
    classIds: ['8e84478e-bd09-4fe4-a39d-40f16f30179c'],
    courseName: 'Lập trình nâng cao',
    courseCode: 'CS202',
    questions: [
      {
        id: 'q1-spiral',
        examId: targetExamId,
        title: 'Câu 1: Multidimensional Array Spiral Traversal and Transformation', // Raw title with redundant prefix
        description: 'Cho mảng 2 chiều kích thước N x M, in ra thứ tự duyệt ma trận theo hình xoắn ốc.',
        maxScore: 2.5,
        displayOrder: 0,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc1',
            name: 'Sample 3x3',
            points: 1.0,
            isHidden: false,
            displayOrder: 1,
            inputData: '3 3\n1 2 3\n4 5 6\n7 8 9',
            expectedOutput: '1 2 3 6 9 8 7 4 5',
          },
        ],
      },
      {
        id: 'q2-palindrome',
        examId: targetExamId,
        title: 'Optimized Longest Palindromic Substring with Constraints', // Clean title
        description: 'Tìm chuỗi con đối xứng dài nhất trong chuỗi ký tự độ dài tối đa 10^5.',
        maxScore: 2.5,
        displayOrder: 1,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc2',
            name: 'Sample Palindrome',
            points: 1.0,
            isHidden: false,
            displayOrder: 1,
            inputData: 'babad',
            expectedOutput: 'bab',
          },
        ],
      },
    ],
  };

  const mockClasses = [
    {
      id: '8e84478e-bd09-4fe4-a39d-40f16f30179c',
      code: 'CS202-01',
      name: 'Lớp Lập trình nâng cao K18',
      studentCount: 35,
    },
  ];

  test.beforeEach(async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, { role: 'TEACHER' });
  });

  test('Issue A: Question numbering rendered as [1] Title without duplicate "Câu 1: Câu 1:" prefix', async ({ page }) => {
    await page.route(`**/api/teacher/exams/${targetExamId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockExamData),
      });
    });

    await page.route(`**/api/teacher/exams/${targetExamId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/teacher/exams/${targetExamId}/test-cases`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/teacher/exams/${targetExamId}/test-cases`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/teacher/classes`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockClasses),
      });
    });

    await page.goto(`/teacher/exams/${targetExamId}`);

    // Switch to Problem tab
    await page.click('button:has-text("Đề bài & Rubric")');

    // Verify Question 1 has badge "1" and title WITHOUT duplicate "Câu 1:"
    const q1Card = page.locator('div:has-text("Multidimensional Array Spiral Traversal and Transformation")').first();
    await expect(q1Card).toBeVisible();

    // The h3 title must NOT contain duplicate "Câu 1:" prefix
    const q1Title = page.locator('h3:has-text("Multidimensional Array Spiral Traversal and Transformation")');
    await expect(q1Title).toBeVisible();
    const titleText = await q1Title.textContent();
    expect(titleText?.trim()).toBe('Multidimensional Array Spiral Traversal and Transformation');
    expect(titleText).not.toContain('Câu 1:');

    // Verify Question 2 title is clean as well
    const q2Title = page.locator('h3:has-text("Optimized Longest Palindromic Substring with Constraints")');
    await expect(q2Title).toBeVisible();
    const q2TitleText = await q2Title.textContent();
    expect(q2TitleText?.trim()).toBe('Optimized Longest Palindromic Substring with Constraints');
  });

  test('Issue B: Student view has exactly ONE submission action per question card and NO bottom submit button', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'STUDENT' });
    await mockUserProfile(page, { role: 'STUDENT' });

    await page.route(`**/api/student/exams/${targetExamId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockExamData),
      });
    });

    await page.route(`**/api/student/exams/${targetExamId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/student/exams/${targetExamId}`);

    // Verify exactly 1 submit button for Question 1 inside its card
    const q1Card = page.locator('#question-card-q1-spiral');
    await expect(q1Card).toBeVisible();
    const q1SubmitBtn = q1Card.locator('#submit-question-q1-spiral');
    await expect(q1SubmitBtn).toBeVisible();
    await expect(q1SubmitBtn).toBeDisabled();

    // Verify exactly 1 submit button for Question 2 inside its card
    const q2Card = page.locator('#question-card-q2-palindrome');
    await expect(q2Card).toBeVisible();
    const q2SubmitBtn = q2Card.locator('#submit-question-q2-palindrome');
    await expect(q2SubmitBtn).toBeVisible();
    await expect(q2SubmitBtn).toBeDisabled();

    // Verify NO duplicate bottom submit button in the multi-question workspace
    const duplicateBottomBtn = page.locator('#submit-student-exam-btn');
    await expect(duplicateBottomBtn).toHaveCount(0);

    // Verify selecting file for Question 1 enables ONLY Question 1 submit button
    const validZipBuffer = Buffer.from('PK\x05\x06' + '\x00'.repeat(18));
    await page.setInputFiles('#file-input-q1-spiral', {
      name: 'q1_spiral.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    await expect(q1SubmitBtn).toBeEnabled();
    await expect(q2SubmitBtn).toBeDisabled();
  });

  test('Issue C: Edit exam configuration modal opens on target exam and allows editing without modifying questions', async ({ page }) => {
    let capturedUpdatePayload: any = null;

    const draftExamData = {
      ...mockExamData,
      status: 'draft',
    };

    await page.route(`**/api/teacher/exams/${targetExamId}`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(draftExamData),
        });
      } else if (route.request().method() === 'PUT') {
        capturedUpdatePayload = route.request().postDataJSON();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            ...draftExamData,
            title: capturedUpdatePayload?.title || draftExamData.title,
            duration: capturedUpdatePayload?.durationMinutes || draftExamData.duration,
          }),
        });
      } else {
        await route.continue();
      }
    });

    await page.route(`**/api/teacher/exams/${targetExamId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/teacher/exams/${targetExamId}/test-cases`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/teacher/classes`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockClasses),
      });
    });

    await page.goto(`/teacher/exams/${targetExamId}`);

    // Verify Edit Exam Button is visible
    const editBtn = page.locator('#btn-edit-exam');
    await expect(editBtn).toBeVisible();

    // Click "Cấu hình bài thi" -> Modal MUST open (previously threw RangeError: Invalid time value on non-ISO date)
    await editBtn.click();

    const editModal = page.locator('#edit-exam-modal');
    await expect(editModal).toBeVisible();

    // Verify status dropdown <select> is REMOVED from configuration modal
    await expect(editModal.locator('select')).toHaveCount(0);

    // Verify modal inputs are populated correctly
    const titleInput = editModal.locator('input[type="text"]').first();
    await expect(titleInput).toHaveValue('Đề thi thực hành tổng hợp Lập trình');

    const durationInput = editModal.locator('input[type="number"]');
    await expect(durationInput).toHaveValue('90');

    // Change title and duration
    await titleInput.fill('Đề thi thực hành tổng hợp Lập trình (Đã cập nhật)');
    await durationInput.fill('120');

    // Submit the edit form
    const submitEditBtn = editModal.locator('button[type="submit"]');
    await submitEditBtn.click();

    // Verify modal closes
    await expect(editModal).not.toBeVisible();

    // Verify payload sent to backend
    expect(capturedUpdatePayload).not.toBeNull();
    expect(capturedUpdatePayload?.title).toBe('Đề thi thực hành tổng hợp Lập trình (Đã cập nhật)');
    expect(capturedUpdatePayload?.durationMinutes).toBe(120);
    // Status is not overridden from modal
    expect(capturedUpdatePayload?.status).toBeUndefined();
    // Crucial check: questions array MUST NOT be sent in edit config payload, preserving all existing questions in DB!
    expect(capturedUpdatePayload?.questions).toBeUndefined();
  });

  test('TASK-EXAM-CONFIGURATION-AND-STATUS-LIFECYCLE-REFACTOR-01: Read-only Runtime banner, auto-calculated end time, and schedule locking for ACTIVE exam', async ({ page }) => {
    const activeExamData = {
      ...mockExamData,
      status: 'active',
      examDate: '2026-11-05T10:00:00.000Z',
      duration: 90,
    };

    await page.route(`**/api/teacher/exams/${targetExamId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(activeExamData),
      });
    });

    await page.route(`**/api/teacher/exams/${targetExamId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/teacher/exams/${targetExamId}/test-cases`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/teacher/classes`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockClasses),
      });
    });

    await page.goto(`/teacher/exams/${targetExamId}`);

    const editBtn = page.locator('#btn-edit-exam');
    await expect(editBtn).toBeVisible();
    await editBtn.click();

    const editModal = page.locator('#edit-exam-modal');
    await expect(editModal).toBeVisible();

    // 1. Verify Publication & Runtime status banner
    await expect(editModal).toContainText('Đã phát hành (Published)');
    await expect(editModal).toContainText('Đang diễn ra (Active)');

    // 2. Verify status dropdown is NOT present
    await expect(editModal.locator('select')).toHaveCount(0);

    // 3. Verify schedule inputs are disabled when ACTIVE
    const dateInput = editModal.locator('input[type="datetime-local"]');
    await expect(dateInput).toBeDisabled();

    const durationInput = editModal.locator('input[type="number"]');
    await expect(durationInput).toBeDisabled();

    // 4. Verify auto-calculated expected end time is visible
    await expect(editModal).toContainText('Thời gian kết thúc dự kiến:');
  });
});
