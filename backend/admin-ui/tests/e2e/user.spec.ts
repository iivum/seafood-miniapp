import { test, expect } from '@playwright/test';

test.describe('用户管理功能', () => {
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

  test('导航到用户列表页面', async ({ page }) => {
    // 点击用户菜单
    const userMenu = page.locator('vaadin-side-nav').locator('text=用户');
    await userMenu.click();
    await page.waitForURL('**/users**', { timeout: 10000 });
    await expect(page.locator('h1, h2, vaadin-grid')).toBeVisible();
  });

  test('用户列表页面显示用户数据', async ({ page }) => {
    await page.goto('/users');
    await page.waitForLoadState('networkidle');
    
    // 等待用户表格加载
    const grid = page.locator('vaadin-grid');
    await expect(grid).toBeVisible({ timeout: 10000 });
    
    // 验证至少有一行数据或空状态提示
    const rows = page.locator('vaadin-grid tr');
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
  });
});
