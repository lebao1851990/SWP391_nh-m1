import { test, expect } from '@playwright/test';
import { login } from './helpers';

// Fallback to standard test seed accounts if environment variables are not configured
const TEACHER_EMAIL = process.env.E2E_TEACHER_EMAIL || 'teachera@fpt.edu.vn';
const STUDENT_EMAIL = process.env.E2E_STUDENT_EMAIL || 'studenta@fpt.edu.vn';

test.describe('AuthGuard & Multi-Tab Session Synchronization', () => {

  test.beforeEach(async ({ context }) => {
    // Clear cookies between tests to ensure complete test isolation
    await context.clearCookies();
  });

  // ---------------------------------------------------------------------------
  // 1. Unauthenticated redirect
  // ---------------------------------------------------------------------------
  test('1. Unauthenticated access to protected route redirects to login with redirect param', async ({ page }) => {
    // Navigate directly to protected teacher route without session
    await page.goto('/teacher/classes');

    // Verify redirection to /login preserving destination path
    await expect(page).toHaveURL(/\/login\?redirect=%2Fteacher%2Fclasses/);

    // Verify login form is displayed
    await expect(page.locator('input[name="identity"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();

    // Verify protected layout (Sidebar / main content) is completely unmounted
    await expect(page.locator('aside')).toHaveCount(0);
    await expect(page.locator('aside a[href="/teacher/classes"]')).toHaveCount(0);
  });

  // ---------------------------------------------------------------------------
  // 2. Role mismatch
  // ---------------------------------------------------------------------------
  test('2. Authenticated Student accessing Teacher route triggers session conflict and locks layout', async ({ page }) => {
    // Login as Student and wait for student dashboard
    await login(page, STUDENT_EMAIL);
    await expect(page).toHaveURL(/\/student\/dashboard/);

    // Register profile response listener before navigation to verify RBAC reconciliation
    const profileResponsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/user/profile') && res.request().method() === 'GET'
    );

    // Attempt to navigate to Teacher-only route
    await page.goto('/teacher/classes');
    const profileResp = await profileResponsePromise;
    expect(profileResp.status()).toBe(200);

    // AuthGuard detects role STUDENT does not match allowedRoles ['TEACHER']
    // Conflict modal is rendered immediately
    const conflictHeading = page.locator('h2:has-text("Phát hiện tài khoản khác trên trình duyệt")');
    await expect(conflictHeading).toBeVisible({ timeout: 8000 });
    await expect(page.locator('span:has-text("Phiên đăng nhập đã thay đổi")')).toBeVisible();

    // Verify protected teacher layout is completely unmounted (frozen view)
    await expect(page.locator('aside')).toHaveCount(0);
    await expect(page.locator('aside a[href="/teacher/classes"]')).toHaveCount(0);

    // Verify actionable buttons with correct localized role labels
    await expect(page.locator('button:has-text("Chuyển sang Sinh viên")')).toBeVisible();
    await expect(page.locator('button:has-text("Đăng nhập lại Giảng viên")')).toBeVisible();
  });

  // ---------------------------------------------------------------------------
  // 3. Multi-tab session conflict
  // ---------------------------------------------------------------------------
  test('3. Teacher in Tab 1, Student logs in Tab 2 -> Tab 1 detects conflict on focus and switches role', async ({ context }) => {
    // Tab 1: Teacher logs in and accesses class management
    const tab1 = await context.newPage();
    await login(tab1, TEACHER_EMAIL);
    await tab1.goto('/teacher/classes');
    await expect(tab1).toHaveURL(/\/teacher\/classes/);

    // Verify Tab 1 currently renders teacher layout
    await expect(tab1.locator('aside')).toBeVisible();
    await expect(tab1.locator('aside a[href="/teacher/classes"]')).toBeVisible();

    // Tab 2: Same BrowserContext (shared cookie jar), Student logs in
    const tab2 = await context.newPage();
    await login(tab2, STUDENT_EMAIL);
    await expect(tab2).toHaveURL(/\/student\/dashboard/);

    // Tab 1: Switch back to Tab 1
    // Register listener BEFORE triggering focus to avoid missing async response
    const profileResponsePromise = tab1.waitForResponse(
      (res) => res.url().includes('/api/user/profile') && res.request().method() === 'GET'
    );

    await tab1.bringToFront();
    // Dispatch controlled focus event to ensure AuthGuard multi-tab listener fires reliably in headless mode
    await tab1.evaluate(() => window.dispatchEvent(new Event('focus')));

    const profileResp = await profileResponsePromise;
    expect(profileResp.status()).toBe(200);

    // Verify conflict modal appeared on Tab 1 and protected teacher layout is unmounted
    const conflictHeading = tab1.locator('h2:has-text("Phát hiện tài khoản khác trên trình duyệt")');
    await expect(conflictHeading).toBeVisible({ timeout: 8000 });
    await expect(tab1.locator('aside')).toHaveCount(0);

    // User chooses to switch to the active session ("Chuyển sang Sinh viên")
    const switchButton = tab1.locator('button:has-text("Chuyển sang Sinh viên")');
    await expect(switchButton).toBeVisible();
    await switchButton.click();

    // Verify Tab 1 navigated to Student dashboard and Student layout is mounted
    await expect(tab1).toHaveURL(/\/student\/dashboard/);
    await expect(tab1.locator('h2:has-text("Phát hiện tài khoản khác trên trình duyệt")')).toHaveCount(0);
    await expect(tab1.locator('aside')).toBeVisible();

    await tab1.close();
    await tab2.close();
  });

  // ---------------------------------------------------------------------------
  // 4. Re-login
  // ---------------------------------------------------------------------------
  test('4. Re-login from conflict modal logs out conflicting session and redirects to /login', async ({ page }) => {
    // Trigger conflict modal via role mismatch
    await login(page, STUDENT_EMAIL);
    await page.goto('/teacher/classes');
    await expect(page.locator('h2:has-text("Phát hiện tài khoản khác trên trình duyệt")')).toBeVisible({ timeout: 8000 });

    // Register logout response listener BEFORE clicking action button
    const logoutResponsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/logout') && res.request().method() === 'POST'
    );

    // Click "Đăng nhập lại Giảng viên"
    await page.click('button:has-text("Đăng nhập lại Giảng viên")');
    const logoutResp = await logoutResponsePromise;
    expect(logoutResp.status()).toBe(200);

    // Verify redirection to /login with original destination path preserved
    await expect(page).toHaveURL(/\/login\?redirect=%2Fteacher%2Fclasses/);
    await expect(page.locator('input[name="identity"]')).toBeVisible();
    await expect(page.locator('h2:has-text("Phát hiện tài khoản khác trên trình duyệt")')).toHaveCount(0);
    await expect(page.locator('aside')).toHaveCount(0);
  });

  // ---------------------------------------------------------------------------
  // 5. Session expiry / HTTP 401
  // ---------------------------------------------------------------------------
  test('5. Session expiry (HTTP 401 from profile API) redirects to login and clears layout', async ({ page }) => {
    let profileRequestIntercepted = false;

    // Intercept profile verification to simulate session expiry
    await page.route('**/api/user/profile', (route) => {
      profileRequestIntercepted = true;
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Session expired / Unauthorized' }),
      });
    });

    // Access protected route
    await page.goto('/teacher/classes');

    // Verify 401 intercept was triggered
    expect(profileRequestIntercepted).toBe(true);

    // Verify redirection to /login with original destination
    await expect(page).toHaveURL(/\/login\?redirect=%2Fteacher%2Fclasses/);

    // Verify login form is visible and protected layout is completely unmounted
    await expect(page.locator('input[name="identity"]')).toBeVisible();
    await expect(page.locator('aside')).toHaveCount(0);
  });

});
