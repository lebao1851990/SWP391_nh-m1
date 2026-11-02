import { Page, expect } from '@playwright/test';

export interface LoginOptions {
  role?: 'TEACHER' | 'STUDENT' | 'ADMIN';
  password?: string;
}

export async function login(
  page: Page,
  email: string,
  passwordOrOptions?: string | LoginOptions
) {
  let explicitPassword: string | undefined;
  let explicitRole: 'TEACHER' | 'STUDENT' | 'ADMIN' | undefined;

  if (typeof passwordOrOptions === 'string') {
    explicitPassword = passwordOrOptions;
  } else if (passwordOrOptions) {
    explicitPassword = passwordOrOptions.password;
    explicitRole = passwordOrOptions.role;
  }

  // Resolve role: prefer explicit role, fallback to email prefix/substring heuristic
  const resolvedRole =
    explicitRole ||
    (email.toLowerCase().includes('admin') ? 'ADMIN' :
     email.toLowerCase().includes('teacher') ? 'TEACHER' :
     email.toLowerCase().includes('student') ? 'STUDENT' :
     undefined);

  const rolePassword =
    resolvedRole === 'ADMIN' ? process.env.E2E_ADMIN_PASSWORD :
    resolvedRole === 'TEACHER' ? process.env.E2E_TEACHER_PASSWORD :
    resolvedRole === 'STUDENT' ? process.env.E2E_STUDENT_PASSWORD :
    undefined;

  const resolvedPassword =
    explicitPassword ||
    rolePassword ||
    process.env.E2E_TEST_PASSWORD;

  if (!resolvedPassword) {
    throw new Error(
      `[Playwright Test Auth Error] No test password provided for user '${email}' (role: ${resolvedRole || 'UNSPECIFIED'}). ` +
      `Hardcoded default credentials have been eliminated from test fixtures. ` +
      `Please provide the password parameter to login() or configure environment variables: ` +
      `'E2E_TEST_PASSWORD' (or role-specific 'E2E_TEACHER_PASSWORD' / 'E2E_STUDENT_PASSWORD' / 'E2E_ADMIN_PASSWORD').`
    );
  }

  await page.goto('/login');
  
  // Setup request listener
  page.on('response', async response => {
    if (response.url().includes('/api/')) {
      console.log(`API Response ${response.request().method()} ${response.url()} -> ${response.status()}`);
    }
  });

  page.on('console', msg => {
    console.log(`Browser Console [${msg.type()}]: ${msg.text()}`);
  });
  page.on('pageerror', err => {
    console.log(`Page Exception: ${err.message}`);
  });

  await page.fill('input[name="identity"]', email);
  await page.fill('input[name="password"]', resolvedPassword);
  await page.click('button[type="submit"]');
  
  try {
    await page.waitForURL(/\/.*\/dashboard/);
  } catch (e) {
    await page.screenshot({ path: 'test-results/login-failure.png' });
    throw e;
  }
}

export interface MockAuthCookieOptions {
  role?: 'TEACHER' | 'STUDENT' | 'ADMIN';
  sessionToken?: string;
  baseUrl?: string;
}

/**
 * Standardized fixture to inject session and role cookies for mocked/isolated UI test suites.
 * Eliminates duplication across admin, teacher, and student mocked E2E specifications.
 */
export async function setMockAuthCookies(
  page: Page,
  options: MockAuthCookieOptions = {}
) {
  const role = options.role || 'TEACHER';
  const sessionToken = options.sessionToken || `mock-${role.toLowerCase()}-session-token`;
  const url = options.baseUrl || process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000';

  await page.context().addCookies([
    { name: 'codeassess_session', value: sessionToken, url },
    { name: 'codeassess_role', value: role, url },
    { name: 'codeassess_session', value: sessionToken, domain: 'localhost', path: '/' },
    { name: 'codeassess_role', value: role, domain: 'localhost', path: '/' },
  ]);
}

export interface MockUserProfileOptions {
  id?: string;
  email?: string;
  fullName?: string;
  role?: 'TEACHER' | 'STUDENT' | 'ADMIN';
}

/**
 * Standardized fixture to mock the /api/user/profile endpoint to satisfy AuthGuard.
 */
export async function mockUserProfile(
  page: Page,
  user: MockUserProfileOptions = {}
) {
  const role = user.role || 'TEACHER';
  const profile = {
    id: user.id || `mock-${role.toLowerCase()}-01`,
    email: user.email || `${role.toLowerCase()}@codeassess.edu.vn`,
    fullName: user.fullName || `Giảng viên/Người dùng Kiểm thử (${role})`,
    role,
  };

  await page.route('**/api/user/profile', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(profile),
    });
  });
}
