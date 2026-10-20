import { Page, expect } from '@playwright/test';

export async function login(page: Page, email: string, password: string = 'testpass123') {
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
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  
  try {
    await page.waitForURL(/\/.*\/dashboard/);
  } catch (e) {
    await page.screenshot({ path: 'login-failure.png' });
    throw e;
  }
}
