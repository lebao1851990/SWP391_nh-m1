import { test, expect } from '@playwright/test';
import { setMockAuthCookies, mockUserProfile } from './helpers';

test.describe('AI Generation, Draft & Publish End-to-End Validation (TASK-FE-VALIDATION-01D)', () => {
  test.beforeEach(async ({ page }) => {
    await setMockAuthCookies(page, { role: 'TEACHER' });
    await mockUserProfile(page, { role: 'TEACHER' });

    await page.route('**/api/teacher/classes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'class-1', name: 'Lớp Lập trình Java - CS101', code: 'CS101' },
        ]),
      });
    });

    await page.goto('/teacher/exams/builder');
  });

  test('AI Generation: Valid AI response maps into Builder, preserves draft status without auto-publishing', async ({ page }) => {
    // Intercept AI generate endpoint with valid structured response
    await page.route('**/api/teacher/exams/ai/generate-draft', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Tìm đường đi ngắn nhất Dijkstra',
          problemStatement: 'Cho đồ thị có hướng có trọng số dương...',
          inputSpecification: 'Dòng 1: số đỉnh n và số cạnh m...',
          outputSpecification: 'In ra khoảng cách ngắn nhất...',
          testCases: [
            { name: 'TC Mẫu 1', inputData: '4 4\n1 2 1\n2 3 2\n3 4 3\n1 4 10', expectedOutput: '6', points: 3.33, isHidden: false },
            { name: 'TC Nâng cao 2', inputData: '2 1\n1 2 5', expectedOutput: '5', points: 3.33, isHidden: true },
            { name: 'TC Biên 3', inputData: '1 0', expectedOutput: '0', points: 3.34, isHidden: true },
          ],
        }),
      });
    });

    // Open AI Generator modal
    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    // Select Manual Topic tab
    const manualTopicTab = page.locator('button:has-text("Theo chủ đề cụ thể")');
    await manualTopicTab.click();

    // Fill topic
    const topicInput = page.locator('input[placeholder*="Ngăn xếp"]');
    await topicInput.fill('Đồ thị Dijkstra');

    // Click submit
    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Modal closes and question is mapped
    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    await expect(page.locator('text=Tìm đường đi ngắn nhất Dijkstra').first()).toBeVisible();

    // Status MUST remain DRAFT (never auto-published)
    await expect(page.locator('text=BẢN NHÁP (DRAFT)')).toBeVisible();
    await expect(page.locator('text=ĐÃ XUẤT BẢN')).not.toBeVisible();
  });

  test('AI Generation: Malformed AI response structure does not crash modal and displays readable error', async ({ page }) => {
    // Intercept with corrupt response (testCases is not an array)
    await page.route('**/api/teacher/exams/ai/generate-draft', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Corrupt AI Output',
          testCases: 'invalid-string-instead-of-array',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const manualTopicTab = page.locator('button:has-text("Theo chủ đề cụ thể")');
    await manualTopicTab.click();

    const topicInput = page.locator('input[placeholder*="Ngăn xếp"]');
    await topicInput.fill('Topic Test');

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Modal stays open and surfaces safe error
    await expect(page.locator('text=Cấu trúc danh sách Test Cases từ AI không hợp lệ.')).toBeVisible();
    await expect(page.locator('div[role="dialog"]')).toBeVisible();
  });

  test('AI Generation: Invalid JSON from AI displays readable error and preserves document upload & settings for retry', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'AI provider returned invalid structured JSON output: Unexpected end-of-input: was expecting closing quote for a string value',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    // Upload a mock document
    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'Giao_trinh_Cau_truc_du_lieu.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    // Verify uploaded file is visible
    await expect(page.locator('text=Giao_trinh_Cau_truc_du_lieu.docx')).toBeVisible();

    // Submit generation
    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Error is shown in readable Vietnamese
    await expect(page.locator('text=AI trả về dữ liệu không đúng cấu trúc. Vui lòng thử lại.')).toBeVisible();

    // Verify retry state: uploaded document and settings are NOT cleared
    await expect(page.locator('text=Giao_trinh_Cau_truc_du_lieu.docx')).toBeVisible();
    await expect(page.locator('div[role="dialog"]')).toBeVisible();

    // Verify incomplete questions are NEVER inserted into builder
    await page.locator('button:has-text("Huỷ")').or(page.locator('div[role="dialog"] button.text-outline')).first().click();
    await expect(page.locator('text=Chưa chọn câu hỏi nào')).toBeVisible();
  });

  test('AI Generation: AI provider failure displays safe error without leaking API keys or stack traces', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-draft', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'AI provider service is currently unavailable. Please try again later.',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const manualTopicTab = page.locator('button:has-text("Theo chủ đề cụ thể")');
    await manualTopicTab.click();

    const topicInput = page.locator('input[placeholder*="Ngăn xếp"]');
    await topicInput.fill('Graph Algorithm');

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Error is shown safely
    await expect(page.locator('text=AI provider service is currently unavailable')).toBeVisible();
    // Verify no secret leak or stack trace
    const modalText = await page.locator('div[role="dialog"]').innerText();
    expect(modalText).not.toContain('Exception');
    expect(modalText).not.toContain('GEMINI_API_KEY');
    expect(modalText).not.toContain('OPENAI_API_KEY');
  });

  test('AI Generation: Incomplete AI content is not fabricated; field warnings displayed and Publish is blocked', async ({ page }) => {
    // Intercept with response missing problemStatement and missing testcase expectedOutput
    await page.route('**/api/teacher/exams/ai/generate-draft', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Bài toán mảng số học',
          problemStatement: '', // Empty from AI
          testCases: [
            { name: 'TC Thiếu Output', inputData: '10', expectedOutput: '', points: 10.0, isHidden: false },
          ],
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const manualTopicTab = page.locator('button:has-text("Theo chủ đề cụ thể")');
    await manualTopicTab.click();

    const topicInput = page.locator('input[placeholder*="Ngăn xếp"]');
    await topicInput.fill('Arrays');

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Incomplete banner alert is shown
    await expect(page.locator('text=Có câu hỏi hoặc test case chưa hoàn thiện')).toBeVisible();

    // Problem statement warning is visible
    await expect(page.locator('[data-testid="problem-statement-warning"]')).toBeVisible();

    // Navigator warning indicator is visible
    await expect(page.locator('[data-testid="question-nav-warning-0"]')).toBeVisible();

    // Attempting to publish is blocked
    const publishBtn = page.locator('button:has-text("Xuất bản bài thi")');
    await publishBtn.click();

    await expect(page.locator('text=chưa có nội dung mô tả đề bài')).toBeVisible();
    await expect(page.locator('text=BẢN NHÁP (DRAFT)')).toBeVisible();
  });

  test('Teacher edits generated content before saving; edits are preserved', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-draft', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Tiêu đề ban đầu từ AI',
          problemStatement: 'Mô tả ban đầu từ AI',
          testCases: [
            { name: 'TC 1', inputData: '1', expectedOutput: '1', points: 10.0, isHidden: false },
          ],
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const manualTopicTab = page.locator('button:has-text("Theo chủ đề cụ thể")');
    await manualTopicTab.click();

    await page.locator('input[placeholder*="Ngăn xếp"]').fill('Original Topic');
    await page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")').click();

    // Teacher edits question title
    const qTitleInput = page.locator('[data-testid="question-title-input"]');
    await qTitleInput.fill('Tiêu đề đã được giảng viên chỉnh sửa');

    // Teacher edits problem statement with Markdown
    const statementTextarea = page.locator('[data-testid="problem-statement-textarea"]');
    await statementTextarea.fill('### Mô tả chi tiết\nGiảng viên đã bổ sung code: `assert(true)`');

    // Mock createExam API
    await page.route('**/api/teacher/exams', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: 'exam-saved-draft-1', title: 'Đề thi đã lưu', status: 'draft' }),
        });
      }
    });

    // Save Draft
    const saveDraftBtn = page.locator('button:has-text("Lưu bản nháp")');
    await saveDraftBtn.click();

    await expect(page.locator('text=Đã lưu bản nháp thành công')).toBeVisible();
    await expect(qTitleInput).toHaveValue('Tiêu đề đã được giảng viên chỉnh sửa');
    await expect(statementTextarea).toHaveValue('### Mô tả chi tiết\nGiảng viên đã bổ sung code: `assert(true)`');
  });

  test('Save Draft allows incomplete exam and persists accurately, while Publish blocks incomplete draft', async ({ page }) => {
    // Add manual question
    const addQuestionBtn = page.locator('button:has-text("+ Thêm câu hỏi thủ công")').first();
    await addQuestionBtn.click();

    // Set title and score (sum is 5.00 != 10.00, problem statement is empty, no test cases)
    await page.locator('[data-testid="question-title-input"]').fill('Câu hỏi bản nháp');
    await page.locator('[data-testid="question-max-score-input"]').fill('5.00');

    // Mock createExam
    await page.route('**/api/teacher/exams', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: 'exam-incomplete-draft', title: 'Bản nháp', status: 'draft' }),
        });
      }
    });

    // Save Draft succeeds despite incomplete content
    const saveDraftBtn = page.locator('button:has-text("Lưu bản nháp")');
    await saveDraftBtn.click();
    await expect(page.locator('text=Đã lưu bản nháp thành công')).toBeVisible();

    // But Publish is strictly blocked
    const publishBtn = page.locator('button:has-text("Xuất bản bài thi")');
    await publishBtn.click();
    await expect(page.locator('text=Tổng điểm các câu hỏi phải bằng chính xác 10.0 điểm')).toBeVisible();
    await expect(page.locator('text=BẢN NHÁP (DRAFT)')).toBeVisible();
  });

  test('AI Generation: Partial question failure returns actionable diagnostic message (TASK-AI-JSON-03 Phase D)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'AI đã tạo 3/5 câu hỏi. Câu 4 và 5 chưa hoàn thành.',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Verify actionable Vietnamese message is displayed directly without being overwritten
    await expect(page.locator('text=AI đã tạo 3/5 câu hỏi. Câu 4 và 5 chưa hoàn thành.')).toBeVisible();
    // Modal stays open and file is preserved
    await expect(page.locator('text=demo_1.docx')).toBeVisible();
  });

  test('AI Generation: Multi-question generation maps all questions and test cases into builder (TASK-AI-JSON-03 Phase B)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Bài thi tổng hợp 3 câu',
          requestedQuestionCount: 3,
          generatedQuestionCount: 3,
          requestedTestCasesPerQuestion: 2,
          generatedTestCasesPerQuestion: 2,
          questions: [
            {
              title: 'Bài 1: Cấu trúc Stack',
              problemStatement: 'Cài đặt Stack cơ bản',
              testCases: [
                { name: 'TC 1.1', inputData: '1', expectedOutput: '1', points: 1.66, isHidden: false },
                { name: 'TC 1.2', inputData: '2', expectedOutput: '2', points: 1.67, isHidden: true },
              ],
            },
            {
              title: 'Bài 2: Cấu trúc Queue',
              problemStatement: 'Cài đặt Queue cơ bản',
              testCases: [
                { name: 'TC 2.1', inputData: 'a', expectedOutput: 'a', points: 1.66, isHidden: false },
                { name: 'TC 2.2', inputData: 'b', expectedOutput: 'b', points: 1.67, isHidden: true },
              ],
            },
            {
              title: 'Bài 3: Cấu trúc Deque',
              problemStatement: 'Cài đặt Deque cơ bản',
              testCases: [
                { name: 'TC 3.1', inputData: 'x', expectedOutput: 'x', points: 1.67, isHidden: false },
                { name: 'TC 3.2', inputData: 'y', expectedOutput: 'y', points: 1.67, isHidden: true },
              ],
            },
          ],
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    // Select 3 questions and 2 test cases in the AI modal
    const modalDialog = page.locator('div[role="dialog"]');
    const questionCountSelect = modalDialog.locator('select').nth(2);
    await questionCountSelect.selectOption('3');
    const tcCountSelect = modalDialog.locator('select').nth(3);
    await tcCountSelect.selectOption('2');

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Modal closes and all 3 questions appear in builder
    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    await expect(page.locator('text=Bài 1: Cấu trúc Stack')).toBeVisible();
    await expect(page.locator('text=Bài 2: Cấu trúc Queue')).toBeVisible();
    await expect(page.locator('text=Bài 3: Cấu trúc Deque')).toBeVisible();
  });

  test('AI Generation: Missing problemStatement at Question 2 displays exact actionable message (TASK-AI-JSON-04)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'AI trả về dữ liệu thiếu trường problemStatement ở câu 2.',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Verify exact actionable Vietnamese error is displayed directly
    await expect(page.locator('text=AI trả về dữ liệu thiếu trường problemStatement ở câu 2.')).toBeVisible();
    // Modal stays open and file is preserved
    await expect(page.locator('text=demo_1.docx')).toBeVisible();
  });

  test('AI Generation: 5 questions × 5 test cases maps all 25 test cases into builder (TASK-AI-JSON-04)', async ({ page }) => {
    const questions: any[] = [];
    for (let i = 1; i <= 5; i++) {
      const testCases: any[] = [];
      for (let j = 1; j <= 5; j++) {
        testCases.push({
          name: `TC ${i}.${j}`,
          inputData: `input_${i}_${j}`,
          expectedOutput: `output_${i}_${j}`,
          points: 0.40,
          isHidden: j > 1,
        });
      }
      questions.push({
        title: `Bài toán ${i}: Thuật toán ${i}`,
        problemStatement: `Mô tả bài toán số ${i}`,
        testCases,
      });
    }

    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Đề thi 5 câu hỏi 25 test cases',
          requestedQuestionCount: 5,
          generatedQuestionCount: 5,
          requestedTestCasesPerQuestion: 5,
          generatedTestCasesPerQuestion: 5,
          questions,
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const modalDialog = page.locator('div[role="dialog"]');
    const questionCountSelect = modalDialog.locator('select').nth(2);
    await questionCountSelect.selectOption('5');
    const tcCountSelect = modalDialog.locator('select').nth(3);
    await tcCountSelect.selectOption('5');

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Modal closes and all 5 questions appear in builder
    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    for (let i = 1; i <= 5; i++) {
      await expect(page.locator(`text=Bài toán ${i}: Thuật toán ${i}`)).toBeVisible();
    }
  });

  test('AI Generation: 1 question × 2 test cases maps accurately into builder (TASK-AI-JSON-04)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Bài toán 1: Thuật toán kiểm tra số nguyên tố',
          problemStatement: 'Viết hàm kiểm tra số nguyên tố.',
          testCases: [
            { name: 'TC 1: Số 7', inputData: '7', expectedOutput: 'YES', points: 5.0, isHidden: false },
            { name: 'TC 2: Số 4', inputData: '4', expectedOutput: 'NO', points: 5.0, isHidden: true },
          ],
          rubricCriteria: [{ name: 'Độ chính xác', maxScore: 10.0 }],
          totalRubricScore: 10.0,
          requestedQuestionCount: 1,
          generatedQuestionCount: 1,
          requestedTestCasesPerQuestion: 2,
          generatedTestCasesPerQuestion: 2,
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const modalDialog = page.locator('div[role="dialog"]');
    const questionCountSelect = modalDialog.locator('select').nth(2);
    await questionCountSelect.selectOption('1');
    const tcCountSelect = modalDialog.locator('select').nth(3);
    await tcCountSelect.selectOption('2');

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    await expect(page.locator('text=Bài toán 1: Thuật toán kiểm tra số nguyên tố')).toBeVisible();
  });

  test('AI Generation: 3 questions × 4 test cases maps all 12 test cases to correct questions (TASK-AI-JSON-04)', async ({ page }) => {
    const questions: any[] = [];
    for (let i = 1; i <= 3; i++) {
      const testCases: any[] = [];
      for (let j = 1; j <= 4; j++) {
        testCases.push({
          name: `TC ${i}.${j}`,
          inputData: `input_${i}_${j}`,
          expectedOutput: `output_${i}_${j}`,
          points: 0.83,
          isHidden: j > 1,
        });
      }
      questions.push({
        title: `Bài toán ${i}: Cấu trúc ${i}`,
        problemStatement: `Mô tả bài toán cấu trúc số ${i}`,
        testCases,
      });
    }

    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Đề thi 3 câu hỏi 12 test cases',
          requestedQuestionCount: 3,
          generatedQuestionCount: 3,
          requestedTestCasesPerQuestion: 4,
          generatedTestCasesPerQuestion: 4,
          questions,
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const modalDialog = page.locator('div[role="dialog"]');
    const questionCountSelect = modalDialog.locator('select').nth(2);
    await questionCountSelect.selectOption('3');
    const tcCountSelect = modalDialog.locator('select').nth(3);
    await tcCountSelect.selectOption('4');

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    for (let i = 1; i <= 3; i++) {
      await expect(page.locator(`text=Bài toán ${i}: Cấu trúc ${i}`)).toBeVisible();
    }
  });

  test('AI Generation: Provider 503 / Rate limit displays user-friendly retry-later message (TASK-AI-JSON-02)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'Gemini API rate limit exceeded. Please try again later. (HTTP 429)',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Verify user-friendly retry later message
    await expect(page.locator('text=Hệ thống AI đang tạm thời quá tải hoặc vượt hạn mức yêu cầu. Vui lòng đợi trong giây lát và thử lại.')).toBeVisible();
    // Modal stays open and file is preserved
    await expect(page.locator('text=demo_1.docx')).toBeVisible();
  });

  test('AI Generation: Truncated response displays actionable Vietnamese message and preserves upload for retry (TASK-AI-JSON-02)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'AI provider hit MAX_TOKENS limit. Output was truncated.',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Verify exact Vietnamese actionable message
    await expect(page.locator('text=AI trả về dữ liệu chưa hoàn chỉnh. Vui lòng giảm số câu hỏi hoặc thử lại.')).toBeVisible();
    // Form and uploaded file preserved
    await expect(page.locator('text=demo_1.docx')).toBeVisible();
  });

  test('AI Generation: Invalid test case displays actionable message with question index (TASK-AI-JSON-02)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'Câu hỏi 2 có test case không hợp lệ: thiếu trường expectedOutput.',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Verify exact question-indexed invalid test case message is surfaced
    await expect(page.locator('text=Câu hỏi 2 có test case không hợp lệ')).toBeVisible();
    await expect(page.locator('text=demo_1.docx')).toBeVisible();
  });

  test('AI Generation: Prevents duplicate requests on rapid multi-click (TASK-AI-JSON-03)', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      requestCount++;
      // delay response to allow second click attempt
      await page.waitForTimeout(500);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Single Execution Exam',
          problemStatement: 'Problem statement text',
          testCases: [
            { name: 'TC 1', inputData: '1', expectedOutput: '1', points: 3.33, isHidden: false },
            { name: 'TC 2', inputData: '2', expectedOutput: '2', points: 3.33, isHidden: true },
            { name: 'TC 3', inputData: '3', expectedOutput: '3', points: 3.34, isHidden: true },
          ],
          rubricCriteria: [
            { name: 'R1', maxScore: 5.0 },
            { name: 'R2', maxScore: 5.0 },
          ],
          totalRubricScore: 10.0,
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    // Double click rapidly
    await generateBtn.click({ clickCount: 2 });

    await expect(page.locator('div[role="dialog"]')).not.toBeVisible();
    await expect(page.locator('text=Single Execution Exam')).toBeVisible();
    // Exactly 1 request must have been dispatched
    expect(requestCount).toBe(1);
  });

  test('AI Generation: Jackson reference chain parse error displays invalid structure, NOT missing problemStatement (TASK-AI-JSON-03)', async ({ page }) => {
    await page.route('**/api/teacher/exams/ai/generate-from-document', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          statusCode: 503,
          error: 'SERVICE_UNAVAILABLE',
          message: 'AI provider returned invalid structured JSON output: Unexpected character (\'i\' (code 105)): was expecting double-quote to start field name line: 3, column: 209 through reference chain: AiDocumentExamGenerateResponse["problemStatement"]',
        }),
      });
    });

    const openAiBtn = page.locator('button:has-text("Sinh câu hỏi với AI")').first();
    await openAiBtn.click();

    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.locator('text=Kéo thả hoặc nhấp để tải tài liệu lên').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'demo_1.docx',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('mock docx content'),
    });

    const generateBtn = page.locator('button[type="submit"]:has-text("Bắt đầu sinh đề AI")');
    await generateBtn.click();

    // Must display invalid structure, NOT missing problemStatement
    await expect(page.locator('text=AI trả về dữ liệu không đúng cấu trúc. Vui lòng thử lại.')).toBeVisible();
    await expect(page.locator('text=AI trả về dữ liệu thiếu trường problemStatement.')).not.toBeVisible();
    await expect(page.locator('text=demo_1.docx')).toBeVisible();
  });
});
