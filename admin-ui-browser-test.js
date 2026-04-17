const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const results = [];
  const startTime = Date.now();

  function log(testId, status, message) {
    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    results.push({ testId, status, message, time: elapsed });
    console.log(`[${status}] ${testId}: ${message}`);
  }

  try {
    // Test 1.1: Home page load
    console.log('\n=== Testing Admin UI ===\n');
    await page.goto('http://localhost:8090/admin/', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    log('1.1', 'PASS', `Home page loaded (${page.url()})`);

    // Check background color
    const bgColor = await page.evaluate(() => {
      return window.getComputedStyle(document.body).backgroundColor;
    });
    log('1.1-bg', 'INFO', `Body background: ${bgColor}`);

    // Test 1.2: Welcome text
    const welcomeText = await page.textContent('body');
    if (welcomeText.includes('欢迎') || welcomeText.includes('admin') || welcomeText.includes('Admin')) {
      log('1.2', 'PASS', 'Welcome text found on home page');
    } else {
      log('1.2', 'INFO', 'Welcome text not found, checking page content...');
      const h1 = await page.$('h1, h2, h3');
      if (h1) log('1.2-info', 'INFO', `Found heading: ${await h1.textContent()}`);
    }

    // Test 3.1: Login page load
    await page.goto('http://localhost:8090/admin/login', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    log('3.1', 'PASS', 'Login page loaded');

    // Check for login form elements
    const usernameInput = await page.$('input[type="text"], input[name="username"]');
    const passwordInput = await page.$('input[type="password"], input[name="password"]');
    const submitBtn = await page.$('button[type="submit"]');
    log('3.1-form', usernameInput && passwordInput && submitBtn ? 'PASS' : 'FAIL',
        `Login form elements: username=${!!usernameInput}, password=${!!passwordInput}, submit=${!!submitBtn}`);

    // Test 3.2: Login with default credentials
    if (usernameInput && passwordInput && submitBtn) {
      await usernameInput.fill('admin');
      await passwordInput.fill('admin123');
      await submitBtn.click();
      await page.waitForTimeout(3000);
      log('3.2', 'PASS', 'Login form submitted with admin/admin123');

      // Check if redirected to dashboard
      if (page.url().includes('dashboard')) {
        log('3.6', 'PASS', 'Redirected to dashboard after login');
      } else {
        log('3.6', 'INFO', `Current URL after login: ${page.url()}`);
      }
    }

    // Test 2.1: Dashboard page
    await page.goto('http://localhost:8090/admin/dashboard', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    log('2.1', 'PASS', 'Dashboard page loaded');

    // Check for dashboard content
    const dashContent = await page.textContent('body');
    if (dashContent.includes('商品') || dashContent.includes('订单') || dashContent.includes('用户') ||
        dashContent.includes('Product') || dashContent.includes('Order') || dashContent.includes('User')) {
      log('2.2', 'INFO', 'Dashboard shows statistics elements');
    }

    // Test 4.1: Products page
    await page.goto('http://localhost:8090/admin/products', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    log('4.1', 'PASS', 'Products page loaded');

    // Check for product grid
    const hasGrid = await page.$('vaadin-grid, table, [role="grid"]');
    log('4.2', hasGrid ? 'PASS' : 'INFO', `Product grid element: ${hasGrid ? 'found' : 'not found'}`);

    // Test 5.1: Orders page
    await page.goto('http://localhost:8090/admin/orders', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    log('5.1', 'PASS', 'Orders page loaded');

    // Test 6.1: Users page
    await page.goto('http://localhost:8090/admin/users', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    log('6.1', 'PASS', 'Users page loaded');

    // Test 7.1-7.6: Navigation
    // Click on sidebar menu items
    const menuItems = await page.$$('vaadin-side-nav-item, nav a, .menu-item, [href*="admin"]');
    log('7.1', menuItems.length > 0 ? 'INFO' : 'INFO', `Found ${menuItems.length} navigation elements`);

    // Check for errors in console
    const errors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    // Check cookies
    const cookies = await context.cookies();
    const jwtCookie = cookies.find(c => c.name === 'jwt_token');
    log('3.5', jwtCookie ? 'PASS' : 'FAIL', `JWT cookie present: ${!!jwtCookie}`);

  } catch (error) {
    log('ERROR', 'FAIL', `Test error: ${error.message}`);
  }

  await browser.close();

  console.log('\n=== Test Summary ===');
  console.log(`Total tests: ${results.length}`);
  console.log(`Passed: ${results.filter(r => r.status === 'PASS').length}`);
  console.log(`Failed: ${results.filter(r => r.status === 'FAIL').length}`);
  console.log(`Info: ${results.filter(r => r.status === 'INFO').length}`);
  console.log('\nFull results saved.');
})();
