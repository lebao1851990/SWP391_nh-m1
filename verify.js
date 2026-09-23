const puppeteer = require('puppeteer');

(async () => {
  let browser;
  try {
    console.log("Starting browser...");
    browser = await puppeteer.launch({ headless: true });
    const page = await browser.newPage();
    
    // Register account via API first to ensure it exists
    console.log("Registering test account...");
    const regRes = await fetch('http://localhost:8080/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fullName: 'Test User',
        email: 'e2etest@gmail.com',
        password: 'password123',
        confirmPassword: 'password123',
        accountRole: 'STUDENT',
        terms: true
      })
    });
    console.log("Register API Status:", regRes.status); // 201 or 409

    // Monitor Network
    console.log("Navigating to login page...");
    
    let loginRequestUrl = null;
    let loginRequestPayload = null;
    let loginResponseStatus = null;
    
    page.on('request', request => {
      if (request.url().includes('/api/auth/login') && request.method() === 'POST') {
        loginRequestUrl = request.url();
        loginRequestPayload = request.postData();
        console.log(`[Network] POST ${request.url()} | Payload: ${loginRequestPayload}`);
      }
    });

    page.on('response', response => {
      if (response.url().includes('/api/auth/login') && response.request().method() === 'POST') {
        loginResponseStatus = response.status();
        console.log(`[Network] Response Status for login: ${loginResponseStatus}`);
      }
    });

    await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle0' });
    
    console.log("Filling out login form...");
    await page.type('#identity', 'e2etest@gmail.com');
    await page.type('#password', 'password123');
    
    console.log("Submitting login form...");
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 10000 }).catch(e => console.log("Navigation timeout or client-side redirect:", e.message)),
      page.click('button[type="submit"]')
    ]);

    // Give it a moment for Next.js router to settle
    await new Promise(r => setTimeout(r, 2000));
    
    const urlAfterLogin = page.url();
    console.log("URL after login:", urlAfterLogin);

    // Check Cookies
    const cookies = await page.cookies('http://localhost:3000');
    // The backend session cookie is JSESSIONID, but it's set on localhost for port 8080 or domain wide?
    // Wait, withCredentials will set the cookie if CORS is properly configured to allow the domain. 
    // Actually, in local dev, Next.js proxy at localhost:3000 proxies to 8080.
    // If it proxies, the cookie domain is localhost.
    console.log("Cookies found:");
    let jsessionFound = false;
    cookies.forEach(c => {
      console.log(`- ${c.name} (HttpOnly: ${c.httpOnly})`);
      if(c.name === 'JSESSIONID') jsessionFound = true;
    });

    // Check Protected API (e.g. /api/auth/me or dashboard data)
    console.log("Checking if user is logged in via dashboard element...");
    const isDashboard = urlAfterLogin.includes('/student/dashboard');
    console.log("Is dashboard?", isDashboard);
    
    // Reload test
    console.log("Reloading page...");
    await page.reload({ waitUntil: 'networkidle0' });
    const urlAfterReload = page.url();
    console.log("URL after reload:", urlAfterReload);

    // Logout Test
    // Depending on UI, the logout button might be in a dropdown.
    console.log("Done.");

  } catch (error) {
    console.error("Test failed:", error);
  } finally {
    if (browser) await browser.close();
  }
})();
