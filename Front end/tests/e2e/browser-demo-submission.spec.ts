import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';
import path from 'path';

const ARTIFACT_DIR = 'C:/Users/Admin/.gemini/antigravity/brain/5c15bb48-bec2-42a5-9dd7-eb73c43b75ac';
const TARGET_EXAM_ID = '99088390-ef53-4587-99e3-5b336e2f383e';

const mockFourQuestionExam = {
  id: TARGET_EXAM_ID,
  title: 'Kỳ thi Thực hành Lập trình Java Cơ bản — CS101',
  description: 'Đề thi gồm 4 câu hỏi lập trình Java với các test case đánh giá tự động.',
  duration: 90,
  durationMinutes: 90,
  startTime: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
  endTime: new Date(Date.now() + 75 * 60 * 1000).toISOString(),
  serverTime: new Date().toISOString(),
  status: 'active',
  courseClassCode: 'PRJ301-JAVA',
  courseCode: 'PRJ301',
  courseName: 'Lập trình ứng dụng Java Web',
  totalAllocatedPoints: 10,
  totalQuestions: 4,
  enrolledCount: 35,
  submittedCount: 12,
  questions: [
    {
      id: 'q1-student-class',
      examId: TARGET_EXAM_ID,
      title: 'Câu 1: Xây dựng lớp Student và Đóng gói (Encapsulation)',
      description: 'Hiện thực lớp Student với các thuộc tính private (id, name, gpa), constructor chuẩn, getter/setter kiểm tra ràng buộc dữ liệu hợp lệ và phương thức displayInfo().',
      maxScore: 2.5,
      displayOrder: 1,
      status: 'PUBLISHED',
      testCases: [
        {
          id: 'tc-q1-01',
          name: 'Kiểm tra Constructor và Getter/Setter',
          points: 1.5,
          isHidden: false,
          displayOrder: 1,
          inputData: 'SE12345\nNguyen Van An\n3.85',
          expectedOutput: 'Student[id=SE12345, name=Nguyen Van An, gpa=3.85]',
        },
        {
          id: 'tc-q1-02',
          name: 'Kiểm tra Ràng buộc điểm GPA âm (Test ẩn)',
          points: 1.0,
          isHidden: true,
          displayOrder: 2,
        },
      ],
    },
    {
      id: 'q2-inheritance-grad',
      examId: TARGET_EXAM_ID,
      title: 'Câu 2: Kế thừa và Đa hình với GraduateStudent',
      description: 'Tạo lớp GraduateStudent kế thừa từ Student, bổ sung trường researchTopic và ghi đè (override) phương thức calculateTuition() theo chính sách miễn giảm học phí.',
      maxScore: 2.5,
      displayOrder: 2,
      status: 'PUBLISHED',
      testCases: [
        {
          id: 'tc-q2-01',
          name: 'Kiểm tra Đa hình calculateTuition()',
          points: 2.5,
          isHidden: false,
          displayOrder: 1,
          inputData: 'AI Research\n5 credits\n12000000',
          expectedOutput: 'Tuition Fee: 9600000 VND (Discount 20%)',
        },
      ],
    },
    {
      id: 'q3-collection-manager',
      examId: TARGET_EXAM_ID,
      title: 'Câu 3: Quản lý danh sách sinh viên với HashMap & ArrayList',
      description: 'Xây dựng lớp StudentManager cung cấp các thao tác thêm mới sinh viên, tìm kiếm O(1) theo mã số sinh viên (id) và xuất danh sách sắp xếp theo GPA giảm dần.',
      maxScore: 2.5,
      displayOrder: 3,
      status: 'PUBLISHED',
      testCases: [
        {
          id: 'tc-q3-01',
          name: 'Kiểm tra Thêm và Tìm kiếm theo ID',
          points: 2.5,
          isHidden: false,
          displayOrder: 1,
          inputData: 'ADD SE101\nFIND SE101',
          expectedOutput: 'SUCCESS: Found student SE101',
        },
      ],
    },
    {
      id: 'q4-exception-handler',
      examId: TARGET_EXAM_ID,
      title: 'Câu 4: Xử lý ngoại lệ Custom Exception',
      description: 'Định nghĩa ngoại lệ InvalidStudentDataException kế thừa Exception và ném ngoại lệ khi nhập GPA ngoài khoảng [0.0, 4.0] hoặc tên rỗng.',
      maxScore: 2.5,
      displayOrder: 4,
      status: 'PUBLISHED',
      testCases: [
        {
          id: 'tc-q4-01',
          name: 'Kiểm tra Bắt ngoại lệ GPA không hợp lệ',
          points: 2.5,
          isHidden: false,
          displayOrder: 1,
          inputData: 'GPA 4.5',
          expectedOutput: 'CAUGHT: InvalidStudentDataException - GPA must be between 0.0 and 4.0',
        },
      ],
    },
  ],
};

const mockSubmissionsList = [
  {
    id: 'sub-01',
    studentName: 'Nguyen Van An',
    studentEmail: 'annv.student@fpt.edu.vn',
    examTitle: 'Kỳ thi Thực hành Lập trình Java Cơ bản — CS101',
    courseCode: 'PRJ301',
    status: 'GRADED',
    totalScore: 2.5,
    submittedAt: new Date(Date.now() - 25 * 60 * 1000).toISOString(),
    questionId: 'q1-student-class',
    questionTitle: 'Câu 1: Xây dựng lớp Student và Đóng gói (Encapsulation)',
    originalFilename: 'annv_q1_solution.zip',
    executionStatus: 'COMPLETED',
    autoScore: 2.5,
    testCasesPassed: 2,
    testCasesTotal: 2,
    plagiarismStatus: 'SAFE',
    maxSimilarityRate: 11.2,
  },
  {
    id: 'sub-02',
    studentName: 'Tran Thi Binh',
    studentEmail: 'binhtt.student@fpt.edu.vn',
    examTitle: 'Kỳ thi Thực hành Lập trình Java Cơ bản — CS101',
    courseCode: 'PRJ301',
    status: 'GRADED',
    totalScore: 2.5,
    submittedAt: new Date(Date.now() - 20 * 60 * 1000).toISOString(),
    questionId: 'q2-inheritance-grad',
    questionTitle: 'Câu 2: Kế thừa và Đa hình với GraduateStudent',
    originalFilename: 'binhtt_q2_solution.zip',
    executionStatus: 'COMPLETED',
    autoScore: 2.5,
    testCasesPassed: 1,
    testCasesTotal: 1,
    plagiarismStatus: 'SAFE',
    maxSimilarityRate: 8.5,
  },
  {
    id: 'sub-03',
    studentName: 'Le Quoc Cuong',
    studentEmail: 'cuonglq.student@fpt.edu.vn',
    examTitle: 'Kỳ thi Thực hành Lập trình Java Cơ bản — CS101',
    courseCode: 'PRJ301',
    status: 'GRADED',
    totalScore: 2.5,
    submittedAt: new Date(Date.now() - 18 * 60 * 1000).toISOString(),
    questionId: 'q3-collection-manager',
    questionTitle: 'Câu 3: Quản lý danh sách sinh viên với HashMap & ArrayList',
    originalFilename: 'cuonglq_q3_solution.zip',
    executionStatus: 'COMPLETED',
    autoScore: 2.5,
    testCasesPassed: 1,
    testCasesTotal: 1,
    plagiarismStatus: 'SAFE',
    maxSimilarityRate: 14.0,
  },
  {
    id: 'sub-04',
    studentName: 'Pham Minh Duc',
    studentEmail: 'ducpm.student@fpt.edu.vn',
    examTitle: 'Kỳ thi Thực hành Lập trình Java Cơ bản — CS101',
    courseCode: 'PRJ301',
    status: 'GRADED',
    totalScore: 2.5,
    submittedAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
    questionId: 'q4-exception-handler',
    questionTitle: 'Câu 4: Xử lý ngoại lệ Custom Exception',
    originalFilename: 'ducpm_q4_solution.zip',
    executionStatus: 'COMPLETED',
    autoScore: 2.5,
    testCasesPassed: 1,
    testCasesTotal: 1,
    plagiarismStatus: 'SAFE',
    maxSimilarityRate: 6.8,
  },
];

test.describe('TASK-FE-SUBMISSION-01C — Autonomous Browser Demonstration & Visual Verification', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test('Demo Part 1: Teacher Exam Detail with 4 Questions and Point Distributions', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, { role: 'TEACHER' });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockFourQuestionExam),
      });
    });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSubmissionsList),
      });
    });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}/test-cases`, async (route) => {
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
        body: JSON.stringify([{ id: 'class-prj301', name: 'Lập trình ứng dụng Java Web', code: 'PRJ301' }]),
      });
    });

    await page.route('**/api/teacher/classes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 'class-prj301', name: 'Lập trình ứng dụng Java Web', code: 'PRJ301' }]),
      });
    });

    await page.goto(`/teacher/exams/${TARGET_EXAM_ID}`);
    await page.waitForLoadState('networkidle');

    // Switch to Problem & Rubric tab
    const problemTab = page.locator('button:has-text("Đề bài & Rubric")');
    await expect(problemTab).toBeVisible({ timeout: 10000 });
    await problemTab.click();

    // Verify all 4 questions are displayed (formatQuestionTitle strips duplicate prefix)
    for (const q of mockFourQuestionExam.questions) {
      const cleanTitle = q.title.replace(/^Câu \d+:\s*/, '');
      await expect(page.locator(`text=${cleanTitle}`).first()).toBeVisible();
      await expect(page.locator(`text=${q.maxScore} điểm`).first()).toBeVisible();
    }

    // Capture Screenshot 1: Teacher Exam Detail
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, 'fe_submission_01_teacher_detail.png'),
      fullPage: true,
    });
  });

  test('Demo Part 2: Teacher Submissions List with Question Column and Filter', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, { role: 'TEACHER' });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockFourQuestionExam),
      });
    });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockSubmissionsList),
      });
    });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}/test-cases`, async (route) => {
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
        body: JSON.stringify([{ id: 'class-prj301', name: 'Lập trình ứng dụng Java Web', code: 'PRJ301' }]),
      });
    });

    await page.goto(`/teacher/exams/${TARGET_EXAM_ID}`);
    await page.waitForLoadState('networkidle');

    // Verify Submissions table is active
    const table = page.locator('#submissions-table');
    await expect(table).toBeVisible({ timeout: 10000 });
    await expect(table.locator('thead')).toContainText('Câu hỏi');
    await expect(table.locator('tbody')).toContainText('annv_q1_solution.zip');
    await expect(table.locator('tbody')).toContainText('binhtt_q2_solution.zip');

    // Capture Screenshot 2: Teacher Submissions with Question Column & Filter
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, 'fe_submission_02_teacher_submissions.png'),
      fullPage: false,
    });
  });

  test('Demo Part 3, 4, 5, 6, 7: Student Multi-Question ZIP Submission Full Lifecycle', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'STUDENT' });
    await mockUserProfile(page, { role: 'STUDENT' });

    await page.route(`**/api/student/exams/${TARGET_EXAM_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockFourQuestionExam),
      });
    });

    let demoSubmissions: any[] = [];
    await page.route(`**/api/student/exams/${TARGET_EXAM_ID}/submissions`, async (route) => {
      if (route.request().method() === 'POST') {
        const newSub = {
          id: 'sub-res-q1',
          examId: TARGET_EXAM_ID,
          questionId: 'q1-student-class',
          questionTitle: 'Xây dựng lớp Student và Đóng gói (Encapsulation)',
          questionMaxScore: 2.5,
          originalFilename: 'solution_q1_student.zip',
          executionStatus: 'COMPLETED',
          testCasesPassed: 2,
          testCasesTotal: 2,
          autoScore: 2.5,
          totalScore: 2.5,
          createdAt: new Date().toISOString(),
        };
        demoSubmissions = [newSub];
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(newSub),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(demoSubmissions),
        });
      }
    });

    // Mock batch submission response
    await page.route(`**/api/student/exams/${TARGET_EXAM_ID}/submissions/batch`, async (route) => {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'sub-res-q1',
            examId: TARGET_EXAM_ID,
            questionId: 'q1-student-class',
            questionTitle: 'Câu 1: Xây dựng lớp Student và Đóng gói (Encapsulation)',
            questionMaxScore: 2.5,
            originalFilename: 'solution_q1_student.zip',
            executionStatus: 'COMPLETED',
            testCasesPassed: 2,
            testCasesTotal: 2,
            autoScore: 2.5,
            totalScore: 2.5,
          },
          {
            id: 'sub-res-q2',
            examId: TARGET_EXAM_ID,
            questionId: 'q2-inheritance-grad',
            questionTitle: 'Câu 2: Kế thừa và Đa hình với GraduateStudent',
            questionMaxScore: 2.5,
            originalFilename: 'solution_q2_graduate.zip',
            executionStatus: 'COMPLETED',
            testCasesPassed: 1,
            testCasesTotal: 1,
            autoScore: 2.5,
            totalScore: 2.5,
          },
          {
            id: 'sub-res-q3',
            examId: TARGET_EXAM_ID,
            questionId: 'q3-collection-manager',
            questionTitle: 'Câu 3: Quản lý danh sách sinh viên với HashMap & ArrayList',
            questionMaxScore: 2.5,
            originalFilename: 'solution_q3_manager.zip',
            executionStatus: 'COMPLETED',
            testCasesPassed: 1,
            testCasesTotal: 1,
            autoScore: 2.5,
            totalScore: 2.5,
          },
          {
            id: 'sub-res-q4',
            examId: TARGET_EXAM_ID,
            questionId: 'q4-exception-handler',
            questionTitle: 'Câu 4: Xử lý ngoại lệ Custom Exception',
            questionMaxScore: 2.5,
            originalFilename: 'solution_q4_exception.zip',
            executionStatus: 'COMPLETED',
            testCasesPassed: 1,
            testCasesTotal: 1,
            autoScore: 2.5,
            totalScore: 2.5,
          },
        ]),
      });
    });

    await page.goto(`/student/exams/${TARGET_EXAM_ID}`);
    await page.waitForLoadState('networkidle');

    // Screenshot 3: Student Exam Detail with 4 Question ZIP Upload Areas (Empty state, counter 0/4)
    await expect(page.locator('[data-testid="ready-questions-counter"]')).toContainText('0 / 4 câu hỏi đã sẵn sàng nộp');
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, 'fe_submission_03_student_detail.png'),
      fullPage: true,
    });

    // Attach ZIP files for all 4 questions
    const validZipBuffer = Buffer.from('PK\x05\x06' + '\x00'.repeat(18));

    await page.setInputFiles('#file-input-q1-student-class', {
      name: 'solution_q1_student.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    await page.setInputFiles('#file-input-q2-inheritance-grad', {
      name: 'solution_q2_graduate.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    await page.setInputFiles('#file-input-q3-collection-manager', {
      name: 'solution_q3_manager.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    await page.setInputFiles('#file-input-q4-exception-handler', {
      name: 'solution_q4_exception.zip',
      mimeType: 'application/zip',
      buffer: validZipBuffer,
    });

    // Wait for counter to show 4 / 4
    const counter = page.locator('[data-testid="ready-questions-counter"]');
    await expect(counter).toContainText('4 / 4 câu hỏi đã sẵn sàng nộp');

    // Verify no duplicate bottom submit button (eliminated per TASK-EXAM-UI-DUPLICATE-INDEX-AND-EDIT-CONFIG-FIX-01 Issue B)
    await expect(page.locator('#submit-student-exam-btn')).toHaveCount(0);

    // Verify per-question submit button is enabled
    const q1SubmitBtn = page.locator('#submit-question-q1-student-class');
    await expect(q1SubmitBtn).toBeEnabled();

    // Screenshot 4: Student ZIPs Selected & Counter 4/4 Ready
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, 'fe_submission_04_student_zips_ready.png'),
      fullPage: true,
    });

    // Submit Question 1 independently
    await q1SubmitBtn.click();

    // Verify independent submission progress updates to 1 / 4
    await expect(page.locator('text=1 / 4 câu hỏi đã nộp')).toBeVisible({ timeout: 10000 });

    // Screenshot 5: Independent Question Submission Success
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, 'fe_submission_05_confirmation_modal.png'),
      fullPage: true,
    });
  });

  test('Demo Part 8: Target Exam Real Database State Inspection (0 Questions in MySQL)', async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, { role: 'TEACHER' });

    // Mock teacher classes and test-cases
    await page.route('**/api/teacher/classes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: '8e84478e-bd09-4fe4-a39d-40f16f30179c', name: 'Lập trình web', code: 'PRJ301' }]),
      });
    });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}/test-cases`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    // This test reflects the exact MySQL persisted state of target exam 99088390-ef53-4587-99e3-5b336e2f383e
    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: TARGET_EXAM_ID,
          title: 'Đề thi lập trình mới',
          description: null,
          duration: 90,
          durationMinutes: 90,
          examDate: '2026-11-04T15:00:00.000Z',
          status: 'upcoming',
          courseCode: 'PRJ301',
          courseName: 'Lập trình web',
          enrolledCount: 1,
          submittedCount: 0,
          totalQuestions: 0,
          totalAllocatedPoints: 0,
          questions: [],
          assignedClasses: [
            {
              id: '8e84478e-bd09-4fe4-a39d-40f16f30179c',
              code: 'PRJ301',
              name: 'Lập trình web',
            },
          ],
        }),
      });
    });

    await page.route(`**/api/teacher/exams/${TARGET_EXAM_ID}/submissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/teacher/exams/${TARGET_EXAM_ID}`);
    await page.waitForLoadState('networkidle');

    // Switch to Problem tab to document 0 questions in real DB
    const problemTab = page.locator('button:has-text("Đề bài & Rubric")');
    await expect(problemTab).toBeVisible({ timeout: 10000 });
    await problemTab.click();
    await page.waitForTimeout(300);

    // Screenshot 8: Target Exam Real DB State
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, 'fe_submission_08_target_exam_real_db.png'),
      fullPage: true,
    });
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, 'fe_submission_08_target_exam_real_db.png'),
      fullPage: true,
    });
  });
});
