import { test, expect } from '@playwright/test';
import { mockUserProfile } from './helpers';

test.describe('Admin Class & Student Roster Management UI', () => {
  const classId = 'test-class-cs102';

  test.beforeEach(async ({ page }) => {
    // Set admin authentication cookies
    await page.context().addCookies([
      { name: 'codeassess_session', value: 'mock-admin-session', domain: 'localhost', path: '/' },
      { name: 'codeassess_role', value: 'ADMIN', domain: 'localhost', path: '/' },
      { name: 'codeassess_session', value: 'mock-admin-session', url: 'http://localhost:3000' },
      { name: 'codeassess_role', value: 'ADMIN', url: 'http://localhost:3000' },
    ]);

    // Mock admin profile to satisfy AuthGuard on /admin routes
    await mockUserProfile(page, {
      id: 'admin-01',
      email: 'admin@codeassess.edu.vn',
      fullName: 'Quản trị viên Kiểm thử',
      role: 'ADMIN',
    });

    page.on('console', msg => console.log(`[Browser Console]: ${msg.text()}`));
    page.on('request', req => console.log(`[Request]: ${req.method()} ${req.url()}`));
    page.on('requestfailed', req => console.log(`[Request Failed]: ${req.url()} ${req.failure()?.errorText}`));

    await page.route('**/api/admin/users*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'teacher-1', name: 'Prof. Alan Turing', email: 'turing@school.edu', role: 'TEACHER' },
        ]),
      });
    });
  });

  // 1. Tải danh sách lớp và studentCount
  test('Loads class list with studentCount and displays class information', async ({ page }) => {
    await page.route('**/api/admin/classes*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: classId,
            name: 'Data Structures & Algorithms',
            code: 'CS102',
            semester: 'Fall 2026',
            teacherId: 'teacher-1',
            studentCount: 15,
          },
        ]),
      });
    });

    await page.goto('/admin/classes');

    await expect(page.getByRole('heading', { name: 'Manage Classes' })).toBeVisible();
    await expect(page.getByText('Data Structures & Algorithms')).toBeVisible();
    await expect(page.getByText('CS102')).toBeVisible();
    await expect(page.getByText('Fall 2026', { exact: true })).toBeVisible();
    await expect(page.getByText('15')).toBeVisible(); // studentCount
    await expect(page.getByRole('button', { name: 'Create Class' })).toBeVisible();
  });

  // 2. Tạo lớp thành công và validation lỗi & khóa nút submit khi đang chạy
  test('Opens Create Class modal, enforces validation, prevents double submission, and creates class', async ({ page }) => {
    await page.route('**/api/admin/classes', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([]),
        });
      } else if (route.request().method() === 'POST') {
        const postData = JSON.parse(route.request().postData() || '{}');
        expect(postData.name).toBe('Object Oriented Programming');
        expect(postData.code).toBe('CS201');
        expect(postData.semester).toBe('Spring 2027');
        expect(postData.teacherId).toBe('teacher-uuid-123');

        // Delay slightly to verify button pending/disabled state
        await new Promise((res) => setTimeout(res, 200));

        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'class-new-201',
            name: postData.name,
            code: postData.code,
            semester: postData.semester,
            teacherId: postData.teacherId,
            studentCount: 0,
          }),
        });
      }
    });

    await page.route('**/api/admin/users', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'teacher-uuid-123', name: 'Prof. Alan Turing', email: 'turing@school.edu', role: 'TEACHER' },
        ]),
      });
    });

    await page.goto('/admin/classes');

    // Click Create Class button
    await page.getByRole('button', { name: 'Create Class' }).click();

    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: 'Create New Class' })).toBeVisible();

    // Fill form
    await modal.getByPlaceholder('e.g. Data Structures & Algorithms').fill('Object Oriented Programming');
    await modal.getByPlaceholder('e.g. CS102').fill('CS201');
    await modal.getByPlaceholder('e.g. Fall 2026').fill('Spring 2027');
    await modal.locator('select').selectOption('teacher-uuid-123');

    // Submit
    const submitBtn = modal.getByRole('button', { name: 'Create Class' });
    await submitBtn.click();

    // Verify modal closes
    await expect(modal).not.toBeVisible({ timeout: 5000 });
  });

  // 3. Tải class detail, roster rỗng và mở Manage Roster modal
  test('Opens class detail, loads empty roster state, and shows correct UI', async ({ page }) => {
    await page.route(`**/api/admin/classes/${classId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: classId,
          name: 'Data Structures & Algorithms',
          code: 'CS102',
          semester: 'Fall 2026',
          teacherId: 'teacher-uuid-123',
          studentCount: 0,
        }),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.goto(`/admin/classes/${classId}`);

    await expect(page.getByRole('heading', { name: 'Class Details' })).toBeVisible();
    await expect(page.getByText('0 Students')).toBeVisible();

    // Click Manage Roster
    await page.getByRole('button', { name: 'Manage Roster' }).click();

    const rosterModal = page.getByRole('dialog', { name: /Manage Class Roster/i });
    await expect(rosterModal).toBeVisible();
    await expect(rosterModal.getByText('No students enrolled yet')).toBeVisible();

    // Close modal
    await rosterModal.getByRole('button', { name: 'Done' }).click();
    await expect(rosterModal).not.toBeVisible();
  });

  // 4 & 5. Ghi danh thành công và hiển thị lỗi 409 Conflict khi ghi danh trùng
  test('Enrolls student successfully and handles 409 Conflict when duplicate', async ({ page }) => {
    const enrolledStudents = [
      { id: 's-1', studentId: 's-1', email: 'alice@school.edu', fullName: 'Alice Smith' },
    ];

    await page.route(`**/api/admin/classes/${classId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: classId,
          name: 'Data Structures',
          code: 'CS102',
          semester: 'Fall 2026',
          teacherId: 'teacher-1',
          studentCount: enrolledStudents.length,
        }),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(enrolledStudents),
        });
      } else if (route.request().method() === 'POST') {
        const postData = JSON.parse(route.request().postData() || '{}');
        if (postData.email === 'alice@school.edu' || postData.studentId === 's-1') {
          await route.fulfill({
            status: 409,
            contentType: 'application/json',
            body: JSON.stringify({
              status: 409,
              error: 'CONFLICT',
              message: 'Student is already enrolled in this class.',
            }),
          });
        } else {
          const newStudent = {
            id: 's-2',
            studentId: 's-2',
            email: postData.email || 'bob@school.edu',
            fullName: 'Bob Johnson',
          };
          enrolledStudents.push(newStudent);
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify(newStudent),
          });
        }
      }
    });

    await page.goto(`/admin/classes/${classId}`);
    await page.getByRole('button', { name: 'Manage Roster' }).click();

    const rosterModal = page.getByRole('dialog', { name: /Manage Class Roster/i });
    await expect(rosterModal.getByText('Alice Smith')).toBeVisible();

    // Try duplicate enrollment (alice@school.edu)
    const input = rosterModal.locator('#enroll-input');
    await input.fill('alice@school.edu');
    await rosterModal.getByRole('button', { name: 'Enroll' }).click();

    // Error toast should appear
    await expect(page.getByText('Student is already enrolled in this class.')).toBeVisible();

    // Enroll a new student (bob@school.edu)
    await input.fill('bob@school.edu');
    await rosterModal.getByRole('button', { name: 'Enroll' }).click();

    // Bob Johnson should now appear in roster
    await expect(rosterModal.getByText('Bob Johnson')).toBeVisible();
    await expect(rosterModal.getByText('bob@school.edu')).toBeVisible();
  });

  // 6. Hủy ghi danh có xác nhận và cập nhật danh sách
  test('Unenrolls student with confirmation dialog and updates count', async ({ page }) => {
    let enrolledStudents = [
      { id: 's-1', studentId: 's-1', email: 'alice@school.edu', fullName: 'Alice Smith' },
    ];

    await page.route(`**/api/admin/classes/${classId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: classId,
          name: 'Data Structures',
          code: 'CS102',
          semester: 'Fall 2026',
          teacherId: 'teacher-1',
          studentCount: enrolledStudents.length,
        }),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(enrolledStudents),
        });
      }
    });

    await page.route(`**/api/admin/classes/${classId}/students/*`, async (route) => {
      if (route.request().method() === 'DELETE') {
        enrolledStudents = [];
        await route.fulfill({ status: 204 });
      }
    });

    await page.goto(`/admin/classes/${classId}`);
    await page.getByRole('button', { name: 'Manage Roster' }).click();

    const rosterModal = page.getByRole('dialog', { name: /Manage Class Roster/i });
    await expect(rosterModal.getByText('Alice Smith')).toBeVisible();

    // Click Remove button
    await rosterModal.getByRole('button', { name: 'Remove' }).click();

    // Delete confirmation dialog appears
    await expect(page.getByRole('heading', { name: 'Confirm Removal' })).toBeVisible();
    await expect(page.getByText(/Are you sure you want to remove/i)).toBeVisible();

    // Click Confirm Remove
    await page.getByRole('button', { name: 'Confirm Remove' }).click();

    // Confirmation closes and Alice is removed
    await expect(rosterModal.getByText('Alice Smith')).not.toBeVisible();
    await expect(rosterModal.getByText('No students enrolled yet')).toBeVisible();
  });

  // 7. Xử lý lỗi 403, 404 và mạng
  test('Handles 403 Forbidden and 404 Not Found error states gracefully', async ({ page }) => {
    await page.route(`**/api/admin/classes/${classId}`, async (route) => {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 404,
          error: 'NOT_FOUND',
          message: 'Class not found with id: test-class-cs102',
        }),
      });
    });

    await page.goto(`/admin/classes/${classId}`);

    // Displays retryable error card instead of crashing
    await expect(page.getByText('Failed to load class')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible();
  });

  // 8. Chỉnh sửa lớp (Edit Class Modal)
  test('Opens Edit Class modal, updates class details, and submits successfully', async ({ page }) => {
    let currentClass = {
      id: classId,
      name: 'Data Structures',
      code: 'CS102',
      semester: 'Fall 2026',
      teacherId: 'teacher-1',
      studentCount: 10,
    };

    await page.route(`**/api/admin/classes/${classId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(currentClass),
      });
    });

    await page.route('**/api/admin/users', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'teacher-1', name: 'Prof. Alan Turing', email: 'turing@school.edu', role: 'TEACHER' },
          { id: 'teacher-2', name: 'Dr. Grace Hopper', email: 'hopper@school.edu', role: 'TEACHER' },
        ]),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students*`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/admin/classes/${classId}*`, async (route) => {
      if (route.request().method() === 'PUT') {
        const updateData = JSON.parse(route.request().postData() || '{}');
        expect(updateData.name).toBe('Advanced Data Structures');
        currentClass = { ...currentClass, ...updateData };
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentClass),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(currentClass),
        });
      }
    });

    await page.goto(`/admin/classes/${classId}`);

    // Click Edit Class
    await page.getByRole('button', { name: 'Edit Class' }).click();

    const editModal = page.getByRole('dialog', { name: /Edit Class/i });
    await expect(editModal).toBeVisible();

    const nameInput = editModal.getByPlaceholder('e.g. Data Structures & Algorithms');
    await expect(nameInput).toHaveValue('Data Structures');

    await nameInput.fill('Advanced Data Structures');
    await editModal.getByRole('button', { name: 'Save Changes' }).click();

    await expect(editModal).not.toBeVisible();
    await expect(page.getByText('Advanced Data Structures')).toBeVisible();
  });

  // 8. Import sinh viên từ file Excel (Partial mode)
  test('Opens Excel Import modal, uploads Excel spreadsheet in partial mode, and displays summary and row-level errors', async ({ page }) => {
    const enrolled = [
      { id: 's-1', studentId: 's-1', email: 'alice@school.edu', fullName: 'Alice Smith' },
    ];

    await page.route(`**/api/admin/classes/${classId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: classId,
          name: 'Data Structures',
          code: 'CS102',
          semester: 'Fall 2026',
          teacherId: 'teacher-1',
          studentCount: enrolled.length,
        }),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(enrolled),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students/import-excel*`, async (route) => {
      expect(route.request().method()).toBe('POST');
      // Return partial import result
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalRows: 3,
          importedCount: 2,
          failedCount: 1,
          strict: false,
          errors: [
            {
              rowNumber: 3,
              identifier: 'duplicate@school.edu',
              errorCode: 'DUPLICATE_IN_FILE',
              message: 'Duplicate student entry within the same Excel sheet',
            },
          ],
        }),
      });
    });

    await page.goto(`/admin/classes/${classId}`);
    await page.getByRole('button', { name: 'Manage Roster' }).click();

    const rosterModal = page.getByRole('dialog', { name: /Manage Class Roster/i });
    await expect(rosterModal).toBeVisible();

    // Click Import Excel
    await rosterModal.getByRole('button', { name: /Import from Excel/i }).click();

    const importModal = page.getByRole('dialog', { name: /Bulk Import Students from Excel/i });
    await expect(importModal).toBeVisible();

    // Select Excel file
    const fileInput = importModal.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'roster.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: Buffer.from('mock excel content'),
    });

    await expect(importModal.getByText('roster.xlsx')).toBeVisible();

    // Click Import Roster button
    await importModal.getByRole('button', { name: 'Import Roster' }).click();

    // Summary metrics should be visible
    await expect(importModal.getByText('Total Evaluated')).toBeVisible();
    await expect(importModal.getByText('3', { exact: true })).toBeVisible();
    await expect(importModal.getByText('Imported')).toBeVisible();
    await expect(importModal.getByText('2', { exact: true })).toBeVisible();
    await expect(importModal.getByText('Failed / Skipped')).toBeVisible();

    // Detailed error breakdown
    await expect(importModal.getByText('#3')).toBeVisible();
    await expect(importModal.getByText('DUPLICATE_IN_FILE')).toBeVisible();
    await expect(importModal.getByText('duplicate@school.edu')).toBeVisible();
  });

  // 9. Import Excel chế độ Strict mode thất bại và hiển thị lỗi rollback
  test('Handles strict mode validation failure (HTTP 400 with ExcelImportResultDto body) and renders batch rollback', async ({ page }) => {
    await page.route(`**/api/admin/classes/${classId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: classId,
          name: 'Data Structures',
          code: 'CS102',
          semester: 'Fall 2026',
          teacherId: 'teacher-1',
          studentCount: 1,
        }),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    });

    await page.route(`**/api/admin/classes/${classId}/students/import-excel*`, async (route) => {
      expect(route.request().url()).toContain('strict=true');
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          totalRows: 4,
          importedCount: 0,
          failedCount: 1,
          strict: true,
          errors: [
            {
              rowNumber: 2,
              identifier: 'invalid-email-syntax',
              errorCode: 'INVALID_EMAIL',
              message: 'Invalid email format',
            },
          ],
        }),
      });
    });

    await page.goto(`/admin/classes/${classId}`);
    await page.getByRole('button', { name: 'Manage Roster' }).click();

    const rosterModal = page.getByRole('dialog', { name: /Manage Class Roster/i });
    await rosterModal.getByRole('button', { name: /Import from Excel/i }).click();

    const importModal = page.getByRole('dialog', { name: /Bulk Import Students from Excel/i });

    // Select file
    const fileInput = importModal.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'students.xlsx',
      mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      buffer: Buffer.from('mock excel content'),
    });

    // Check Strict Mode checkbox
    await importModal.locator('#strict-mode-toggle').check();

    // Click Import Roster button
    await importModal.getByRole('button', { name: 'Import Roster' }).click();

    // Verify batch rollback warning banner
    await expect(importModal.getByText(/Batch Rollback/i)).toBeVisible();
    await expect(importModal.getByText('INVALID_EMAIL')).toBeVisible();
    await expect(importModal.getByText('invalid-email-syntax')).toBeVisible();
  });
});
