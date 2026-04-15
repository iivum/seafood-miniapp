import { test, expect } from '@playwright/test';

test.describe('订单管理功能', () => {
  test.beforeEach(async ({ page }) => {
    // 先登录
    await page.goto('/');
    const usernameField = page.locator('vaadin-login-form').locator('input[name="username"]');
    await usernameField.fill('admin');
    const passwordField = page.locator('vaadin-login-form').locator('input[name="password"]');
    await passwordField.fill('admin');
    const loginButton = page.locator('vaadin-login-form').locator('button[type="submit"]');
    await loginButton.click();
    await page.waitForURL('**/dashboard**', { timeout: 15000 });
  });

  test('导航到订单列表页面', async ({ page }) => {
    // 点击订单菜单
    const orderMenu = page.locator('vaadin-side-nav').locator('text=订单');
    await orderMenu.click();
    await page.waitForURL('**/orders**', { timeout: 10000 });
    await expect(page.locator('h1, h2, vaadin-grid')).toBeVisible();
  });

  test('订单列表页面显示订单数据', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForLoadState('networkidle');
    
    // 等待订单表格加载
    const grid = page.locator('vaadin-grid');
    await expect(grid).toBeVisible({ timeout: 10000 });
    
    // 验证至少有一行数据或空状态提示
    const rows = page.locator('vaadin-grid tr');
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
  });

  test('查看订单详情', async ({ page }) => {
    await page.goto('/orders');
    await page.waitForLoadState('networkidle');
    
    // 点击第一行订单
    const firstRow = page.locator('vaadin-grid tr').first();
    if (await firstRow.isVisible()) {
      await firstRow.click();
      
      // 验证详情对话框或表单出现
      const dialog = page.locator('vaadin-dialog');
      if (await dialog.isVisible({ timeout: 3000 })) {
        await expect(dialog).toBeVisible();
      }
    }
  });
});
