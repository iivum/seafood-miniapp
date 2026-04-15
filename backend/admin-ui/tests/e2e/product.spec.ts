import { test, expect } from '@playwright/test';

test.describe('商品管理功能', () => {
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

  test('导航到商品列表页面', async ({ page }) => {
    // 点击商品菜单
    const productMenu = page.locator('vaadin-side-nav').locator('text=商品');
    await productMenu.click();
    await page.waitForURL('**/products**', { timeout: 10000 });
    await expect(page.locator('h1, h2, vaadin-grid')).toBeVisible();
  });

  test('商品列表页面显示商品数据', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle');
    
    // 等待商品表格加载
    const grid = page.locator('vaadin-grid');
    await expect(grid).toBeVisible({ timeout: 10000 });
    
    // 验证至少有一行数据或空状态提示
    const rows = page.locator('vaadin-grid tr');
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
  });

  test('打开商品表单进行编辑', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('networkidle');
    
    // 点击新建或编辑按钮
    const addButton = page.locator('button').filter({ hasText: /新建|Add|New/ }).first();
    if (await addButton.isVisible()) {
      await addButton.click();
      
      // 验证表单对话框出现
      const dialog = page.locator('vaadin-dialog');
      await expect(dialog).toBeVisible({ timeout: 5000 });
    }
  });
});
