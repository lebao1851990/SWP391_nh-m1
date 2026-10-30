import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 60 * 1000,
  expect: {
    timeout: 10 * 1000,
  },
  fullyParallel: false, // Ensure tests run sequentially so database state changes don't conflict, although they are designed to be mostly isolated.
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1, // Run serially to avoid race conditions with shared test accounts
  reporter: 'list',
  use: {
    actionTimeout: 0,
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    video: 'off',
  },
  projects: [
    {
      name: 'mocked',
      testMatch: [
        '**/admin-class-roster.spec.ts',
        '**/student-ai-assistant.spec.ts',
        '**/teacher-plagiarism-detail-visual.spec.ts',
        '**/plagiarism-normalizer.spec.ts',
      ],
      grepInvert: /UI Consumer/,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'live',
      testMatch: [
        '**/auth-guard-session.spec.ts',
        '**/class-aware-exams.spec.ts',
        '**/core-flows.spec.ts',
        '**/grade-audit-history.spec.ts',
        '**/scoring-override.spec.ts',
        '**/student-plagiarism-appeal-link.spec.ts',
        '**/responsive.spec.ts',
        '**/ast-plagiarism-dashboard-matrix.spec.ts',
        '**/live-ai-task17.spec.ts',
      ],
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'hybrid',
      testMatch: ['**/plagiarism-normalizer.spec.ts'],
      grep: /UI Consumer/,
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
