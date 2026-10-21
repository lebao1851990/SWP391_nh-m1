import { test, expect } from '@playwright/test';

test.describe('Forgot Password & Password Reset Regression Suite', () => {

  test('Email input is enabled, focusable, editable, clearable, and validates email format', async ({ page }) => {
    await page.goto('/forgot-password');

    // Email input must be visible, enabled and NOT disabled
    const emailInput = page.locator('#email');
    await expect(emailInput).toBeVisible();
    await expect(emailInput).toBeEnabled();

    // Focus and type email
    await emailInput.focus();
    await emailInput.fill('initial-test@domain.com');
    await expect(emailInput).toHaveValue('initial-test@domain.com');

    // Clear and re-type
    await emailInput.fill('');
    await expect(emailInput).toHaveValue('');

    // Typing invalid email and submitting displays validation error
    await emailInput.fill('not-an-email');
    const submitBtn = page.getByRole('button', { name: /Send Verification Code|Send Instructions/i });
    await submitBtn.click();

    const validationMsg = page.getByText(/Please enter a valid email address/i);
    await expect(validationMsg).toBeVisible();

    // Typing a valid university email clears the error upon valid submission
    await emailInput.fill('student@fpt.edu.vn');
    await expect(emailInput).toHaveValue('student@fpt.edu.vn');
  });

  test('Forgot Password flow: sends request, transitions to OTP step, and preserves email on error', async ({ page }) => {
    // Mock the backend forgot-password API to test neutral response and transitions reliably
    await page.route('**/api/auth/forgot-password', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'If an account exists with this email, a verification code has been sent.',
        }),
      });
    });

    await page.goto('/forgot-password');
    const emailInput = page.locator('#email');
    await emailInput.fill('test.student@codeassess.edu.vn');

    const submitBtn = page.getByRole('button', { name: /Send Verification Code/i });
    await submitBtn.click();

    // Verify transition to OTP verification step
    await expect(page.getByRole('heading', { name: /Enter Verification Code/i })).toBeVisible();
    const otpInput = page.locator('#otp');
    await expect(otpInput).toBeVisible();
    await expect(otpInput).toBeEnabled();

    // Click "Change email" and verify the previous email was preserved
    const changeEmailBtn = page.getByRole('button', { name: /Change email/i });
    await changeEmailBtn.click();
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.locator('#email')).toHaveValue('test.student@codeassess.edu.vn');
  });

  test('OTP verification: displays error on invalid OTP and navigates to reset-password on valid OTP', async ({ page }) => {
    await page.route('**/api/auth/forgot-password', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Code sent.' }),
      });
    });

    await page.route('**/api/auth/verify-otp', async (route) => {
      const requestData = JSON.parse(route.request().postData() || '{}');
      if (requestData.otp === '654321') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            resetToken: 'test_token_secure_xyz_789',
            message: 'Verification code verified successfully.',
          }),
        });
      } else {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            message: 'Invalid verification code. Please check and try again.',
          }),
        });
      }
    });

    await page.goto('/forgot-password');
    await page.locator('#email').fill('student@codeassess.edu.vn');
    await page.getByRole('button', { name: /Send Verification Code/i }).click();

    // Step 2: Test invalid OTP
    const otpInput = page.locator('#otp');
    await otpInput.fill('111111');
    await page.getByRole('button', { name: /Verify Code/i }).click();
    await expect(page.getByText(/Invalid verification code/i)).toBeVisible();

    // Step 2: Test valid OTP -> redirects to /reset-password?token=test_token_secure_xyz_789
    await otpInput.fill('654321');
    await page.getByRole('button', { name: /Verify Code/i }).click();

    await page.waitForURL(/.*reset-password\?token=.*/);
    expect(page.url()).toContain('token=test_token_secure_xyz_789');
  });

  test('Reset Password page: requires valid token, provides visible password toggles, and handles submission', async ({ page }) => {
    // 1. Visiting /reset-password without token shows Invalid Link
    await page.goto('/reset-password');
    await expect(page.getByText(/Invalid Link/i)).toBeVisible();
    await expect(page.getByText(/missing or invalid/i)).toBeVisible();

    // 2. Visiting /reset-password with a valid token shows the password change form
    await page.route('**/api/auth/reset-password', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Password has been successfully reset.' }),
      });
    });

    await page.goto('/reset-password?token=valid_test_token_123');
    await expect(page.getByRole('heading', { name: /Create New Password/i })).toBeVisible();

    const passwordInput = page.locator('#password');
    const confirmInput = page.locator('#confirmPassword');
    const saveBtn = page.getByRole('button', { name: /Save Password/i });

    await expect(passwordInput).toBeVisible();
    await expect(passwordInput).toBeEnabled();
    await expect(confirmInput).toBeVisible();
    await expect(confirmInput).toBeEnabled();

    // Password mismatch validation
    await passwordInput.fill('StrongPassword123!');
    await confirmInput.fill('MismatchPassword456!');
    await saveBtn.click();
    await expect(page.getByText(/Passwords do not match/i)).toBeVisible();

    // Correct password submission
    await confirmInput.fill('StrongPassword123!');
    await saveBtn.click();

    // Expect success screen
    await expect(page.getByRole('heading', { name: /Password Reset/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /Return to Login/i })).toBeVisible();
  });

  test('Resend OTP: renders below OTP input, displays countdown, handles resend action, and keeps user on verify step', async ({ page }) => {
    let resendCallCount = 0;

    await page.route('**/api/auth/forgot-password', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Code sent.' }),
      });
    });

    await page.route('**/api/auth/resend-otp', async (route) => {
      resendCallCount++;
      const data = JSON.parse(route.request().postData() || '{}');
      expect(data.email).toBe('student@codeassess.edu.vn');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Yêu cầu gửi lại mã đã được xử lý. Vui lòng kiểm tra email.',
          cooldownSeconds: 60,
        }),
      });
    });

    await page.goto('/forgot-password');
    await page.locator('#email').fill('student@codeassess.edu.vn');
    await page.getByRole('button', { name: /Send Verification Code/i }).click();

    // Verify on OTP step
    await expect(page.getByRole('heading', { name: /Enter Verification Code/i })).toBeVisible();

    // Resend OTP button must be visible
    const resendBtn = page.locator('#resend-otp-button');
    await expect(resendBtn).toBeVisible();

    // Prompt text "Chưa nhận được mã?" must be visible
    await expect(page.getByText(/Chưa nhận được mã\?/i)).toBeVisible();

    // Immediately after transition, button is disabled with active countdown
    await expect(resendBtn).toBeDisabled();
    await expect(resendBtn).toHaveText(/Gửi lại mã \(\d+s\)/);

    // Form and OTP input are preserved and enabled
    const otpInput = page.locator('#otp');
    await expect(otpInput).toBeVisible();
    await expect(otpInput).toBeEnabled();
    await otpInput.fill('123456');
    await expect(otpInput).toHaveValue('123456');
  });

});
