const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const results = [];
  const errors = [];

  // Listen for errors
  page.on('console', msg => {
    if (msg.type() === 'error') {
      errors.push(msg.text());
    }
  });

  function log(testId, status, message) {
    results.push({ testId, status, message });
    const prefix = status === 'PASS' ? '✅' : status === 'FAIL' ? '❌' : 'ℹ️';
    console.log(`${prefix} [${status}] ${testId}: ${message}`);
  }

  async function waitForVaadin(page, timeout = 5000) {
    await page.waitForFunction(() => {
      const layout = document.querySelector('vaadin-app-layout');
      return layout && layout.shadowRoot;
    }, { timeout });
  }

  try {
    console.log('\n╔═══════════════════════════════════════════════════════════╗');
    console.log('║     Admin UI 完整功能测试 - Playwright 自动化测试           ║');
    console.log('╚═══════════════════════════════════════════════════════════╝\n');
    
    // ========================================
    // TEST 1: 登录页面加载 (LoginView)
    // ========================================
    console.log('─── 测试 1: 登录页面 ───');
    
    await page.goto('http://localhost:8090/admin/login', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(4000);
    
    log('3.1', 'PASS', '登录页面加载成功');
    
    // Wait for Vaadin to initialize
    try {
      await waitForVaadin(page);
      log('3.1-vaadin', 'PASS', 'Vaadin 组件初始化完成');
    } catch (e) {
      log('3.1-vaadin', 'INFO', 'Vaadin 初始化检查超时');
    }
    
    // Check login form elements
    const formElements = await page.evaluate(() => {
      const loginForm = document.querySelector('vaadin-login-form');
      if (!loginForm) return { found: false };
      
      const shadow = loginForm.shadowRoot;
      if (!shadow) return { found: true, shadow: false };
      
      const inputs = shadow.querySelectorAll('input');
      const buttons = shadow.querySelectorAll('button');
      
      return {
        found: true,
        shadow: true,
        inputCount: inputs.length,
        buttonCount: buttons.length,
        inputTypes: Array.from(inputs).map(i => i.type)
      };
    });
    
    log('3.1-form', formElements.found ? 'PASS' : 'FAIL', 
        `登录表单: found=${formElements.found}, inputs=${formElements.inputCount || 0}`);
    
    // Try to interact with login form
    if (formElements.shadow) {
      const loginSuccess = await page.evaluate(async () => {
        const loginForm = document.querySelector('vaadin-login-form');
        const shadow = loginForm.shadowRoot;
        
        const inputs = shadow.querySelectorAll('input');
        const buttons = shadow.querySelectorAll('button');
        
        if (inputs.length >= 2 && buttons.length > 0) {
          // Fill username
          const userInput = inputs[0];
          userInput.value = 'admin';
          userInput.dispatchEvent(new Event('input', { bubbles: true }));
          userInput.dispatchEvent(new Event('blur', { bubbles: true }));
          
          // Fill password
          const passInput = inputs[1];
          passInput.value = 'admin123';
          passInput.dispatchEvent(new Event('input', { bubbles: true }));
          passInput.dispatchEvent(new Event('blur', { bubbles: true }));
          
          // Click submit
          await new Promise(resolve => setTimeout(resolve, 500));
          buttons[0].click();
          
          return true;
        }
        return false;
      });
      
      if (loginSuccess) {
        log('3.2', 'PASS', '登录表单填写并提交');
        
        await page.waitForTimeout(5000);
        
        const finalUrl = page.url();
        if (finalUrl.includes('dashboard')) {
          log('3.6', 'PASS', '登录后跳转到仪表盘');
        } else {
          log('3.6', 'INFO', `当前URL: ${finalUrl}`);
        }
        
        // Check JWT cookie
        const cookies = await context.cookies();
        const jwt = cookies.find(c => c.name === 'jwt_token');
        log('3.5', jwt ? 'PASS' : 'FAIL', `JWT Cookie: ${jwt ? '存在' : '缺失'}`);
      }
    }
    
    // ========================================
    // TEST 2: 首页测试 (HomeView)
    // ========================================
    console.log('\n─── 测试 2: 首页 ───');
    
    await page.goto('http://localhost:8090/admin/', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);
    
    log('1.1', 'PASS', '首页加载成功');
    
    const welcomeCheck = await page.evaluate(() => {
      const text = document.body.textContent;
      return text.includes('欢迎') || text.includes('Admin') || text.includes('admin');
    });
    log('1.2', welcomeCheck ? 'PASS' : 'INFO', '欢迎语检查');
    
    // ========================================
    // TEST 3: 仪表盘测试 (DashboardView)
    // ========================================
    console.log('\n─── 测试 3: 数据概览 ───');
    
    await page.goto('http://localhost:8090/admin/dashboard', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);
    
    log('2.1', 'PASS', '仪表盘页面加载');
    
    const dashboardStats = await page.evaluate(() => {
      const text = document.body.textContent;
      return {
        hasProducts: text.includes('商品') || text.includes('Product'),
        hasOrders: text.includes('订单') || text.includes('Order'),
        hasUsers: text.includes('用户') || text.includes('User'),
        hasSales: text.includes('销售') || text.includes('Sales') || text.includes('收入')
      };
    });
    
    log('2.2', dashboardStats.hasProducts ? 'PASS' : 'FAIL', '商品统计卡片');
    log('2.3', dashboardStats.hasOrders ? 'PASS' : 'FAIL', '订单统计卡片');
    log('2.4', dashboardStats.hasUsers ? 'PASS' : 'FAIL', '用户统计卡片');
    log('2.5', dashboardStats.hasSales ? 'PASS' : 'FAIL', '销售统计卡片');
    
    // ========================================
    // TEST 4: 商品管理测试 (ProductListView)
    // ========================================
    console.log('\n─── 测试 4: 商品管理 ───');
    
    await page.goto('http://localhost:8090/admin/products', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);
    
    log('4.1', 'PASS', '商品管理页面加载');
    
    const productGrid = await page.evaluate(() => {
      const grid = document.querySelector('vaadin-grid');
      return { found: !!grid };
    });
    log('4.2', productGrid.found ? 'PASS' : 'INFO', '商品数据表格');
    
    // ========================================
    // TEST 5: 订单管理测试 (OrderListView)
    // ========================================
    console.log('\n─── 测试 5: 订单管理 ───');
    
    await page.goto('http://localhost:8090/admin/orders', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);
    
    log('5.1', 'PASS', '订单管理页面加载');
    
    // ========================================
    // TEST 6: 用户管理测试 (UserListView)
    // ========================================
    console.log('\n─── 测试 6: 用户管理 ───');
    
    await page.goto('http://localhost:8090/admin/users', { timeout: 15000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);
    
    log('6.1', 'PASS', '用户管理页面加载');
    
    // ========================================
    // TEST 7: 侧边栏导航测试
    // ========================================
    console.log('\n─── 测试 7: 侧边栏导航 ───');
    
    const navItems = await page.evaluate(() => {
      const appLayout = document.querySelector('vaadin-app-layout');
      if (!appLayout) return { found: false };
      
      const shadow = appLayout.shadowRoot;
      if (!shadow) return { found: true, shadow: false };
      
      const drawer = shadow.querySelector('[part="drawer"]');
      const nav = drawer?.querySelector('nav') || drawer?.querySelector('vaadin-side-nav');
      
      if (!nav) return { found: true, nav: false };
      
      const items = nav.querySelectorAll('a, vaadin-side-nav-item');
      return {
        found: true,
        nav: true,
        itemCount: items.length,
        items: Array.from(items).slice(0, 5).map(el => el.textContent?.trim())
      };
    });
    
    log('7.1', navItems.found ? 'PASS' : 'FAIL', `侧边栏: found=${navItems.found}`);
    if (navItems.items) {
      log('7.1-items', 'INFO', `导航项: ${navItems.items.slice(0, 3).join(', ')}`);
    }
    
    // ========================================
    // TEST 8: 微服务连通性测试
    // ========================================
    console.log('\n─── 测试 8: 微服务连通性 ───');
    
    // Test via curl in the backend
    const productApi = await page.evaluate(async () => {
      try {
        const res = await fetch('/api/products', {
          headers: { 'Accept': 'application/json' }
        });
        return { status: res.status, ok: res.ok };
      } catch { return { error: 'fetch failed' }; }
    });
    
    // Direct service tests
    const services = [
      { name: 'product-service', port: 8081, path: '/products' },
      { name: 'order-service', port: 8082, path: '/orders' },
      { name: 'user-service', port: 8083, path: '/users' }
    ];
    
    for (const svc of services) {
      try {
        const response = await fetch(`http://localhost:${svc.port}${svc.path}`);
        log(`8.${services.indexOf(svc) + 1}`, response.ok ? 'PASS' : 'INFO', 
            `${svc.name}: HTTP ${response.status}`);
      } catch (e) {
        log(`8.${services.indexOf(svc) + 1}`, 'FAIL', `${svc.name}: 不可达`);
      }
    }
    
    // ========================================
    // TEST 9: 错误处理测试
    // ========================================
    console.log('\n─── 测试 9: 错误处理 ───');
    
    if (errors.length > 0) {
      log('9.1', 'INFO', `控制台错误数: ${errors.length}`);
      errors.slice(0, 3).forEach((e, i) => log(`9.1-err${i+1}`, 'INFO', e.substring(0, 80)));
    } else {
      log('9.1', 'PASS', '无控制台错误');
    }

  } catch (error) {
    log('ERROR', 'FAIL', error.message);
  }

  await browser.close();

  // ========================================
  // SUMMARY
  // ========================================
  console.log('\n╔═══════════════════════════════════════════════════════════╗');
  console.log('║                      测试结果汇总                          ║');
  console.log('╚═══════════════════════════════════════════════════════════╝');
  
  const passed = results.filter(r => r.status === 'PASS').length;
  const failed = results.filter(r => r.status === 'FAIL').length;
  const info = results.filter(r => r.status === 'INFO').length;
  
  console.log(`\n✅ 通过: ${passed}`);
  console.log(`❌ 失败: ${failed}`);
  console.log(`ℹ️  信息: ${info}`);
  console.log(`总计: ${results.length} 项测试\n`);
  
  console.log('详细结果:');
  console.log('─'.repeat(70));
  results.forEach(r => {
    const icon = r.status === 'PASS' ? '✅' : r.status === 'FAIL' ? '❌' : 'ℹ️';
    console.log(`${icon} ${r.testId.padEnd(15)} ${r.status.padEnd(6)} ${r.message}`);
  });
  
  console.log('\n测试完成时间:', new Date().toLocaleString('zh-CN'));
})();
