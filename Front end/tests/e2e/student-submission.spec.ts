import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('Student Multi-Question ZIP Submission E2E (TASK-FE-SUBMISSION-01)', () => {
  const examId = '99088390-ef53-4587-99e3-5b336e2f383e';

  const mockFourQuestionExam = {
    id: examId,
    title: 'Kỳ thi Thực hành Lập trình Java Cơ bản',
    description: 'Đề thi gồm 4 câu hỏi lập trình Java.',
    duration: 90,
    durationMinutes: 90,
    startTime: new Date(Date.now() - 10 * 60 * 1000).toISOString(), // started 10 mins ago
    endTime: new Date(Date.now() + 80 * 60 * 1000).toISOString(), // ends in 80 mins
    serverTime: new Date().toISOString(),
    status: 'active',
    courseClassCode: 'CS101-JAVA',
    totalAllocatedPoints: 10,
    totalQuestions: 4,
    questions: [
      {
        id: 'q1-uuid',
        examId,
        title: 'Xây dựng lớp Student và Encapsulation',
        description: 'Tạo class Student với private fields, constructor, getters, setters và toString.',
        maxScore: 2.5,
        displayOrder: 1,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc1',
            name: 'Test Student Constructor',
            points: 1.5,
            isHidden: false,
            displayOrder: 1,
            inputData: 'Nguyen Van A\n20',
            expectedOutput: 'Student{name=Nguyen Van A, age=20}',
          },
          {
            id: 'tc2',
            name: 'Test Edge Cases (Ẩn)',
            points: 1.0,
            isHidden: true,
            displayOrder: 2,
          },
        ],
      },
      {
        id: 'q2-uuid',
        examId,
        title: 'Kế thừa và Đa hình với GraduateStudent',
        description: 'Tạo class GraduateStudent kế thừa từ Student và override phương thức calculateTuition().',
        maxScore: 2.5,
        displayOrder: 2,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc3',
            name: 'Test Polymorphism Call',
            points: 2.5,
            isHidden: false,
            displayOrder: 1,
            inputData: '5\n1000',
            expectedOutput: 'Tuition: 4500.0',
          },
        ],
      },
      {
        id: 'q3-uuid',
        examId,
        title: 'Quản lý danh sách với Generic List & Map',
        description: 'Cài đặt lớp StudentManager sử dụng HashMap và ArrayList để tìm kiếm theo mã SV.',
        maxScore: 2.5,
        displayOrder: 3,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc4',
            name: 'Test Add & Search Student',
            points: 2.5,
            isHidden: false,
            displayOrder: 1,
            inputData: 'ADD SV01\nSEARCH SV01',
            expectedOutput: 'FOUND SV01',
          },
        ],
      },
      {
        id: 'q4-uuid',
        examId,
        title: 'Xử lý ngoại lệ Custom Exception',
        description: 'Tạo InvalidStudentDataException và throw khi dữ liệu đầu vào không hợp lệ.',
        maxScore: 2.5,
        displayOrder: 4,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc5',
            name: 'Test Exception Handling',
            points: 2.5,
            isHidden: false,
            displayOrder: 1,
            inputData: 'AGE -1',
            expectedOutput: 'InvalidStudentDataException: Age cannot be negative',
          },
        ],
      },
    ],
  };

  test.beforeEach(async ({ page }) => {
    await setMockAuthCookies(page, { role: 'STUDENT' });
    await mockUserProfile(page, { role: 'STUDENT' });

    // Mock student exam detail endpoint
    await page.route(`**/api/student/exams/${examId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockFourQuestionExam),
      });
    });

    // Mock student exam submissions endpoint
    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });
  });

  test('Teacher opens exam -> student can submit during permitted window (status active)', async ({ page }) => {
    await page.goto(`/student/exams/${examId}`);

    // Verify exam title and active status
    await expect(page.locator('h1')).toContainText(mockFourQuestionExam.title);
    const statusBadge = page.locator('[data-testid="student-exam-status-badge"]');
    await expect(statusBadge).toBeVisible();
    await expect(statusBadge).toContainText(/ĐANG MỞ|Đang hoạt động/i);

    // Verify countdown timer is visible and counting down (not showing expired)
    await expect(page.locator('text=Thời gian còn lại')).toBeVisible();

    // Verify all 4 questions are displayed
    for (const q of mockFourQuestionExam.questions) {
      const qCard = page.locator(`[data-testid="question-card-${q.id}"]`);
      await expect(qCard).toBeVisible();
      await expect(qCard).toContainText(q.title);
      await expect(qCard).toContainText(`${q.maxScore} điểm`);
    }

    // Verify each question has exactly one submit button and it's disabled before file selection
    for (const q of mockFourQuestionExam.questions) {
      const qSubmitBtn = page.locator(`#submit-question-${q.id}`);
      await expect(qSubmitBtn).toBeVisible();
      await expect(qSubmitBtn).toBeDisabled();
    }

    // Verify there is NO duplicate bottom submit button in multi-question exam
    await expect(page.locator('#submit-student-exam-btn')).toHaveCount(0);
  });

  test('Missing 1 question ZIP blocks only that question from submitting', async ({ page }) => {
    await page.goto(`/student/exams/${examId}`);

    // Attach ZIP to question 1 only
    const validZipBuffer = Buffer.from('PK\x05\x06' + '\x00'.repeat(18)); // Valid empty zip header

    await page.setInputFiles('#file-input-q1-uuid', {
      name: 'solution_q1.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    // Question 1 submit button is enabled
    const q1SubmitBtn = page.locator('#submit-question-q1-uuid');
    await expect(q1SubmitBtn).toBeEnabled();
    await expect(q1SubmitBtn).toContainText('Nộp Câu 1');

    // Question 4 submit button must remain disabled
    const q4SubmitBtn = page.locator('#submit-question-q4-uuid');
    await expect(q4SubmitBtn).toBeDisabled();
  });

  test('Invalid file format rejected without partial submission', async ({ page }) => {
    await page.goto(`/student/exams/${examId}`);

    // Attempt to upload .txt instead of .zip
    await page.setInputFiles('#file-input-q1-uuid', {
      name: 'solution.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('public class Main {}'),
    });

    // Should show error message
    await expect(page.locator('text=Chỉ chấp nhận tệp nén có định dạng .zip').first()).toBeVisible();

    // Question 1 must not be marked ready
    const counter = page.locator('[data-testid="ready-questions-counter"]');
    await expect(counter).toContainText('0 / 4 câu hỏi đã sẵn sàng nộp');
  });

  test('Exam with multiple questions: submitting Question 1 succeeds independently', async ({ page }) => {
    let capturedSingleRequest: any = null;
    let submissionsDatabase: any[] = [];

    // Mock single submission endpoint
    await page.route(`**/api/student/exams/${examId}/submissions`, async (route) => {
      if (route.request().method() === 'POST') {
        capturedSingleRequest = {
          examId,
          questionId: 'q1-uuid',
        };

        const newSub = {
          id: 'sub-q1',
          examId,
          questionId: 'q1-uuid',
          questionTitle: 'Xây dựng lớp Student và Encapsulation',
          questionMaxScore: 2.5,
          originalFilename: 'q1_solution.zip',
          executionStatus: 'COMPLETED',
          testCasesPassed: 2,
          testCasesTotal: 2,
          autoScore: 2.5,
          totalScore: 2.5,
        };
        submissionsDatabase = [newSub];

        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newSub),
        });
      } else if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(submissionsDatabase),
        });
      } else {
        await route.continue();
      }
    });

    await page.goto(`/student/exams/${examId}`);

    const validZipBuffer = Buffer.from('PK\x05\x06' + '\x00'.repeat(18));

    // Upload ZIP for Question 1
    await page.setInputFiles('#file-input-q1-uuid', {
      name: 'q1_solution.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    const q1SubmitBtn = page.locator('#submit-question-q1-uuid');
    await expect(q1SubmitBtn).toBeEnabled();
    await expect(q1SubmitBtn).toContainText('Nộp Câu 1');

    await q1SubmitBtn.click();

    expect(capturedSingleRequest).not.toBeNull();
    expect(capturedSingleRequest?.questionId).toBe('q1-uuid');
  });
});

test.describe('Teacher Exam Detail Multi-Question & Submissions Display E2E', () => {
  const teacherExamId = '99088390-ef53-4587-99e3-5b336e2f383e';

  const mockTeacherExamData = {
    id: teacherExamId,
    title: 'Kỳ thi Thực hành Lập trình Java Cơ bản',
    classId: 'class-cs101',
    courseCode: 'CS101',
    courseName: 'Java Programming',
    status: 'active',
    duration: 90,
    examDate: new Date().toISOString(),
    enrolledCount: 30,
    submittedCount: 2,
    totalQuestions: 4,
    totalAllocatedPoints: 10,
    questions: [
      {
        id: 'q1-uuid',
        examId: teacherExamId,
        title: 'Xây dựng lớp Student và Encapsulation',
        description: 'Tạo class Student với private fields, constructor, getters, setters và toString.',
        maxScore: 2.5,
        displayOrder: 1,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc1',
            examId: teacherExamId,
            name: 'Test Student Constructor',
            points: 1.5,
            isHidden: false,
            displayOrder: 1,
            inputData: 'Nguyen Van A\n20',
            expectedOutput: 'Student{name=Nguyen Van A, age=20}',
          },
          {
            id: 'tc2',
            examId: teacherExamId,
            name: 'Test Edge Cases (Ẩn)',
            points: 1.0,
            isHidden: true,
            displayOrder: 2,
          },
        ],
      },
      {
        id: 'q2-uuid',
        examId: teacherExamId,
        title: 'Kế thừa và Đa hình với GraduateStudent',
        description: 'Tạo class GraduateStudent kế thừa từ Student và override phương thức calculateTuition().',
        maxScore: 2.5,
        displayOrder: 2,
        status: 'PUBLISHED',
        testCases: [
          {
            id: 'tc3',
            examId: teacherExamId,
            name: 'Test Polymorphism Call',
            points: 2.5,
            isHidden: false,
            displayOrder: 1,
            inputData: '5\n1000',
            expectedOutput: 'Tuition: 4500.0',
          },
        ],
      },
      {
        id: 'q3-uuid',
        examId: teacherExamId,
        title: 'Quản lý danh sách với Generic List & Map',
        description: 'Cài đặt lớp StudentManager sử dụng HashMap và ArrayList để tìm kiếm theo mã SV.',
        maxScore: 2.5,
        displayOrder: 3,
        status: 'PUBLISHED',
        testCases: [],
      },
      {
        id: 'q4-uuid',
        examId: teacherExamId,
        title: 'Xử lý ngoại lệ Custom Exception',
        description: 'Tạo InvalidStudentDataException và throw khi dữ liệu đầu vào không hợp lệ.',
        maxScore: 2.5,
        displayOrder: 4,
        status: 'PUBLISHED',
        testCases: [],
      },
    ],
  };

  const mockSubmissionsList = [
    {
      id: 'sub-1',
      studentName: 'Nguyen Van An',
      studentEmail: 'annv@fpt.edu.vn',
      examTitle: 'Kỳ thi Thực hành Lập trình Java Cơ bản',
      courseCode: 'CS101',
      status: 'GRADED',
      totalScore: 2.5,
      submittedAt: new Date().toISOString(),
      questionId: 'q1-uuid',
      questionTitle: 'Xây dựng lớp Student và Encapsulation',
      originalFilename: 'an_q1.zip',
      executionStatus: 'COMPLETED',
      autoScore: 2.5,
      testCasesPassed: 2,
      testCasesTotal: 2,
      plagiarismStatus: 'SAFE',
      maxSimilarityRate: 12,
    },
    {
      id: 'sub-2',
      studentName: 'Tran Thi Binh',
      studentEmail: 'binhtt@fpt.edu.vn',
      examTitle: 'Kỳ thi Thực hành Lập trình Java Cơ bản',
      courseCode: 'CS101',
      status: 'GRADED',
      totalScore: 2.5,
      submittedAt: new Date().toISOString(),
      questionId: 'q2-uuid',
      questionTitle: 'Kế thừa và Đa hình với GraduateStudent',
      originalFilename: 'binh_q2.zip',
      executionStatus: 'COMPLETED',
      autoScore: 2.5,
      testCasesPassed: 1,
      testCasesTotal: 1,
      plagiarismStatus: 'SAFE',
      maxSimilarityRate: 8,
    },
  ];

  test.beforeEach(async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, { role: 'TEACHER' });

    await page.route(`**/api/teacher/exams/${teacherExamId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockTeacherExamData),
      });
    });

    await page.route(`**/api/teacher/exams/${teacherExamId}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSubmissionsList),
      });
    });

    await page.route(`**/api/teacher/exams/${teacherExamId}/test-cases`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route('**/api/teacher/classes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 'class-cs101', name: 'Java Programming', code: 'CS101' }]),
      });
    });
  });

  test('Teacher Exam Detail: displays complete questions, descriptions, max scores, and test cases', async ({ page }) => {
    await page.goto(`/teacher/exams/${teacherExamId}`);

    // Click Problem & Rubric tab
    const problemTab = page.locator('button:has-text("Đề bài & Rubric")');
    await expect(problemTab).toBeVisible();
    await problemTab.click();

    // Verify all 4 questions are displayed
    for (const q of mockTeacherExamData.questions) {
      await expect(page.locator(`text=${q.title}`).first()).toBeVisible();
      await expect(page.locator(`text=${q.description}`).first()).toBeVisible();
      await expect(page.locator(`text=${q.maxScore} điểm`).first()).toBeVisible();
    }

    // Verify question 1 test cases are displayed
    await expect(page.locator('text=Test Student Constructor').first()).toBeVisible();
    await expect(page.locator('text=Test Edge Cases (Ẩn)').first()).toBeVisible();
  });

  test('Teacher Exam Detail: submissions list displays Question column and supports Question filter', async ({ page }) => {
    await page.goto(`/teacher/exams/${teacherExamId}`);

    // Verify Submissions table header has "Câu hỏi" column
    const tableHeader = page.locator('#submissions-table thead');
    await expect(tableHeader).toContainText('Câu hỏi');

    // Verify submissions rows show question titles
    const tableBody = page.locator('#submissions-table tbody');
    await expect(tableBody).toContainText('Xây dựng lớp Student và Encapsulation');
    await expect(tableBody).toContainText('Kế thừa và Đa hình với GraduateStudent');

    // Filter by Question 1
    const questionSelect = page.locator('select').first();
    await questionSelect.selectOption('q1-uuid');

    // Table should now show Nguyen Van An and not Tran Thi Binh
    await expect(tableBody).toContainText('Nguyen Van An');
    await expect(tableBody).not.toContainText('Tran Thi Binh');
  });
});

