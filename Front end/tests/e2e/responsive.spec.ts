import { test, expect } from '@playwright/test';
import { login } from './helpers';
import { useUiStore } from '../../src/stores/ui.store';

test.describe.serial('Responsive Layout & Sidebar Regression Verification', () => {

  test('1. Zustand UI Store Initialization & Data Isolation', async () => {
    // Check initial state
    expect(useUiStore.getState().isMobileNavOpen).toBe(false);

    // Test toggle
    useUiStore.getState().toggleMobileNav();
    expect(useUiStore.getState().isMobileNavOpen).toBe(true);

    useUiStore.getState().setMobileNavOpen(false);
    expect(useUiStore.getState().isMobileNavOpen).toBe(false);

    // Verify UI store contains NO tokens, credentials, or business data
    const storeKeys = Object.keys(useUiStore.getState());
    const forbiddenKeys = ['token', 'accessToken', 'refreshToken', 'password', 'user', 'session', 'role', 'email'];
    for (const key of forbiddenKeys) {
      expect(storeKeys).not.toContain(key);
    }
  });

  const viewports = [
    { name: 'Desktop (1440x900)', width: 1440, height: 900, isMobile: false },
    { name: 'Tablet (768x1024)', width: 768, height: 1024, isMobile: true },
    { name: 'Mobile (390x844)', width: 390, height: 844, isMobile: true },
  ];

  const studentRoutes = [
    '/student/dashboard',
    '/student/exams',
    '/student/submissions',
    '/student/appeals',
  ];

  const teacherRoutes = [
    '/teacher/dashboard',
    '/teacher/exams',
    '/teacher/submissions',
    '/teacher/plagiarism',
  ];

  for (const vp of viewports) {
    test.describe(`Viewport: ${vp.name}`, () => {

      test.use({ viewport: { width: vp.width, height: vp.height } });

      test(`Student Portal Routes & Overflow Check (${vp.name})`, async ({ page }) => {
        await login(page, 'studenta@fpt.edu.vn', 'testpass123');
        await expect(page).toHaveURL(/\/student\/dashboard/);

        for (const route of studentRoutes) {
          await page.goto(`http://localhost:3000${route}`);
          await page.waitForLoadState('networkidle');

          // Check no horizontal scrollbar / overflow
          const overflowDetails = await page.evaluate(() => {
            const docWidth = document.documentElement.scrollWidth;
            const winWidth = window.innerWidth;
            const overflowingElements: Array<{ tag: string; id: string; className: string; scrollWidth: number; clientWidth: number; offsetWidth: number }> = [];
            if (docWidth > winWidth + 1) {
              const all = document.querySelectorAll('*');
              all.forEach(el => {
                const rect = el.getBoundingClientRect();
                if (rect.right > winWidth + 1) {
                  overflowingElements.push({
                    tag: el.tagName,
                    id: el.id,
                    className: el.className ? String(el.className).substring(0, 80) : '',
                    scrollWidth: (el as HTMLElement).scrollWidth,
                    clientWidth: (el as HTMLElement).clientWidth,
                    offsetWidth: (el as HTMLElement).offsetWidth,
                  });
                }
              });
            }
            return {
              docWidth,
              winWidth,
              route: window.location.pathname,
              overflowingCount: overflowingElements.length,
              elements: overflowingElements.slice(0, 5),
            };
          });

          if (overflowDetails.docWidth > overflowDetails.winWidth + 1) {
            console.error(`OVERFLOW DETECTED on ${overflowDetails.route}: docWidth=${overflowDetails.docWidth}, winWidth=${overflowDetails.winWidth}`, overflowDetails.elements);
          }
          expect(overflowDetails.docWidth <= overflowDetails.winWidth + 1).toBe(true);

          const hamburger = page.locator('button[aria-label="Toggle navigation menu"]');
          if (vp.isMobile) {
            await expect(hamburger).toBeVisible();
          } else {
            await expect(hamburger).toBeHidden();
          }
        }
      });

      test(`Teacher Portal Routes & Overflow Check (${vp.name})`, async ({ page }) => {
        await login(page, 'teachera@fpt.edu.vn', 'testpass123');
        await expect(page).toHaveURL(/\/teacher\/dashboard/);

        for (const route of teacherRoutes) {
          await page.goto(`http://localhost:3000${route}`);
          await page.waitForLoadState('networkidle');

          // Check no horizontal scrollbar / overflow
          const overflowDetails = await page.evaluate(() => {
            const docWidth = document.documentElement.scrollWidth;
            const winWidth = window.innerWidth;
            const overflowingElements: Array<{ tag: string; id: string; className: string; scrollWidth: number; clientWidth: number; offsetWidth: number }> = [];
            if (docWidth > winWidth + 1) {
              const all = document.querySelectorAll('*');
              all.forEach(el => {
                const rect = el.getBoundingClientRect();
                if (rect.right > winWidth + 1) {
                  overflowingElements.push({
                    tag: el.tagName,
                    id: el.id,
                    className: el.className ? String(el.className).substring(0, 80) : '',
                    scrollWidth: (el as HTMLElement).scrollWidth,
                    clientWidth: (el as HTMLElement).clientWidth,
                    offsetWidth: (el as HTMLElement).offsetWidth,
                  });
                }
              });
            }
            return {
              docWidth,
              winWidth,
              route: window.location.pathname,
              overflowingCount: overflowingElements.length,
              elements: overflowingElements.slice(0, 5),
            };
          });

          if (overflowDetails.docWidth > overflowDetails.winWidth + 1) {
            console.error(`OVERFLOW DETECTED on ${overflowDetails.route}: docWidth=${overflowDetails.docWidth}, winWidth=${overflowDetails.winWidth}`, overflowDetails.elements);
          }
          expect(overflowDetails.docWidth <= overflowDetails.winWidth + 1).toBe(true);

          const hamburger = page.locator('button[aria-label="Toggle navigation menu"]');
          if (vp.isMobile) {
            await expect(hamburger).toBeVisible();
          } else {
            await expect(hamburger).toBeHidden();
          }
        }
      });

      if (vp.isMobile) {
        test(`Mobile Drawer Interactions (${vp.name})`, async ({ page }) => {
          await login(page, 'teachera@fpt.edu.vn', 'testpass123');
          await page.goto('http://localhost:3000/teacher/dashboard');
          await page.waitForLoadState('networkidle');

          const aside = page.locator('aside');
          const hamburger = page.locator('button[aria-label="Toggle navigation menu"]');
          const closeBtn = page.locator('button[aria-label="Close navigation"]');

          // 1. Sidebar is closed by default on small viewports
          await expect(aside).toHaveClass(/-(translate-x-full)/);

          // 2. Click hamburger opens sidebar
          await hamburger.click();
          await expect(aside).toHaveClass(/translate-x-0/);
          await expect(closeBtn).toBeVisible();

          // 3. Click close button 'X' closes sidebar
          await closeBtn.click();
          await expect(aside).toHaveClass(/-(translate-x-full)/);

          // 4. Reopen and click backdrop closes sidebar
          await hamburger.click();
          await expect(aside).toHaveClass(/translate-x-0/);
          const backdrop = page.locator('[data-testid="sidebar-backdrop"]');
          await expect(backdrop).toBeVisible();
          await backdrop.click();
          await expect(aside).toHaveClass(/-(translate-x-full)/);

          // 5. Reopen and click a nav link closes sidebar and navigates
          await hamburger.click();
          await expect(aside).toHaveClass(/translate-x-0/);
          const examsLink = aside.locator('a[href="/teacher/exams"]');
          await examsLink.click();
          await expect(page).toHaveURL(/\/teacher\/exams/);
          await expect(aside).toHaveClass(/-(translate-x-full)/);
        });
      } else {
        test(`Desktop Sidebar Persistence (${vp.name})`, async ({ page }) => {
          await login(page, 'teachera@fpt.edu.vn', 'testpass123');
          await page.goto('http://localhost:3000/teacher/dashboard');
          await page.waitForLoadState('networkidle');

          const aside = page.locator('aside');
          const hamburger = page.locator('button[aria-label="Toggle navigation menu"]');
          const closeBtn = page.locator('button[aria-label="Close navigation"]');

          // Desktop: Sidebar is visible and fixed, no hamburger or close button visible
          await expect(aside).toBeVisible();
          await expect(hamburger).toBeHidden();
          await expect(closeBtn).toBeHidden();

          // Backdrop is never rendered
          const backdrop = page.locator('[data-testid="sidebar-backdrop"]');
          await expect(backdrop).toHaveCount(0);
        });
      }
    });
  }
});
