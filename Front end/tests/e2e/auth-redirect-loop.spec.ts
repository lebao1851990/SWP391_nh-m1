import { test, expect } from '@playwright/test';

test.describe('Auth Redirect Loop & Safe Routing Regression Suite', () => {

  test.beforeEach(async ({ page }) => {
    // Clear all cookies before each test to establish baseline
    await page.context().clearCookies();
  });

  test('1. Unauthenticated user visits /login: renders without infinite redirects', async ({ page }) => {
    const response = await page.goto('/login');
    expect(response?.status()).toBe(200);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: /Sign in to CodeAssess/i })).toBeVisible();
    await expect(page.locator('#identity')).toBeVisible();
  });

  test('2. Unauthenticated user visits /forgot-password: accessible directly without redirect', async ({ page }) => {
    const response = await page.goto('/forgot-password');
    expect(response?.status()).toBe(200);
    await expect(page).toHaveURL(/\/forgot-password$/);
    await expect(page.getByRole('heading', { name: /Reset Password/i })).toBeVisible();
    await expect(page.locator('#email')).toBeVisible();
  });

  test('3. Unauthenticated user visits /reset-password: accessible directly without redirect', async ({ page }) => {
    const response = await page.goto('/reset-password');
    expect(response?.status()).toBe(200);
    await expect(page).toHaveURL(/\/reset-password$/);
    await expect(page.getByText(/Invalid Link/i)).toBeVisible();
  });

  test('4. Unauthenticated user visits protected route: redirected to /login once with valid return URL', async ({ page }) => {
    await page.goto('/teacher/exams');
    await expect(page).toHaveURL(/\/login\?redirect=%2Fteacher%2Fexams/);
    await expect(page.getByRole('heading', { name: /Sign in to CodeAssess/i })).toBeVisible();
  });

  test('5. Self-redirect loop: /login?redirect=%2Flogin is stripped and broken immediately', async ({ page }) => {
    // This is the exact URL reported in the bug: /login?redirect=%2Flogin
    await page.goto('/login?redirect=%2Flogin');
    // Must normalize cleanly to /login with zero infinite redirect loops
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: /Sign in to CodeAssess/i })).toBeVisible();
  });

  test('6. Unsafe open redirect: external domain in redirect parameter is neutralized', async ({ page }) => {
    await page.goto('/login?redirect=https%3A%2F%2Fmalicious-site.com');
    // Must normalize to clean /login
    await expect(page).toHaveURL(/\/login$/);

    await page.goto('/login?redirect=%2F%2Fmalicious-site.com');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('7. Stale/Dangling session cookie without valid role on /login does not cause loop', async ({ page }) => {
    // Simulate expired/dangling session where HttpOnly session cookie exists but role cookie is gone
    await page.context().addCookies([
      {
        name: 'codeassess_session',
        value: 'stale_dangling_session_token_123',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
      },
    ]);

    // Navigating to /login must NOT trigger ERR_TOO_MANY_REDIRECTS
    const response = await page.goto('/login');
    expect(response?.status()).toBe(200);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: /Sign in to CodeAssess/i })).toBeVisible();

    // Verify stale cookies were cleaned up on response
    const cookies = await page.context().cookies();
    const sessionCookie = cookies.find((c) => c.name === 'codeassess_session');
    expect(sessionCookie).toBeUndefined();
  });

  test('8. Stale/Dangling session cookie on protected route redirects to /login and clears cookies', async ({ page }) => {
    await page.context().addCookies([
      {
        name: 'codeassess_session',
        value: 'stale_session_without_role',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
      },
    ]);

    await page.goto('/teacher/dashboard');
    // Redirects to /login and purges stale cookie
    await expect(page).toHaveURL(/\/login\?redirect=%2Fteacher%2Fdashboard/);

    const cookies = await page.context().cookies();
    const sessionCookie = cookies.find((c) => c.name === 'codeassess_session');
    expect(sessionCookie).toBeUndefined();
  });

  test('9a. Student trying to access teacher route redirects to student dashboard', async ({ page }) => {
    await page.context().addCookies([
      {
        name: 'codeassess_session',
        value: 'valid_student_session',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
      },
      {
        name: 'codeassess_role',
        value: 'STUDENT',
        domain: 'localhost',
        path: '/',
      },
    ]);

    await page.goto('/teacher/dashboard');
    await expect(page).toHaveURL(/\/student\/dashboard/);
  });

  test('9b. Teacher trying to access student route redirects to teacher dashboard', async ({ page }) => {
    await page.context().addCookies([
      {
        name: 'codeassess_session',
        value: 'valid_teacher_session',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
      },
      {
        name: 'codeassess_role',
        value: 'TEACHER',
        domain: 'localhost',
        path: '/',
      },
    ]);

    await page.goto('/student/dashboard');
    await expect(page).toHaveURL(/\/teacher\/dashboard/);
  });

  test('10. Stale session on /forgot-password cleans up cookies without redirecting to /login', async ({ page }) => {
    await page.context().addCookies([
      {
        name: 'codeassess_session',
        value: 'stale_session_on_forgot_password',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
      },
    ]);

    const response = await page.goto('/forgot-password');
    expect(response?.status()).toBe(200);
    await expect(page).toHaveURL(/\/forgot-password$/);
    await expect(page.getByRole('heading', { name: /Reset Password/i })).toBeVisible();
    await expect(page.locator('#email')).toBeVisible();

    const cookies = await page.context().cookies();
    const sessionCookie = cookies.find((c) => c.name === 'codeassess_session');
    expect(sessionCookie).toBeUndefined();
  });

});
