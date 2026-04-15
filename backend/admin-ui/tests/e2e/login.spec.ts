import { test, expect } from '@playwright/test';

test.describe('登录功能', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('登录页面正确加载', async ({ page }) => {
    await expect(page).toHaveTitle(/海鲜商城|Admin|Seafood/);
    // 检查登录表单存在
    const loginForm = page.locator('vaadin-login-form');
    await expect(loginForm).toBeVisible({ timeout: 10000 });
  });

  test('使用默认凭据登录成功', async ({ page }) => {
    // 输入用户名
    const usernameField = page.locator('vaadin-login-form').locator('input[name="username"]');
    await usernameField.fill('admin');
    
    // 输入密码
    const passwordField = page.locator('vaadin-login-form').locator('input[name="password"]');
    await passwordField.fill('admin');
    
    // 点击登录按钮
    const loginButton = page.locator('vaadin-login-form').locator('button[type="submit"]');
    await loginButton.click();
    
    // 等待跳转到仪表盘
    await page.waitForURL('**/dashboard**', { timeout: 15000 });
    await expect(page.locator('vaadin-app-layout')).toBeVisible();
  });

  test('错误凭据登录失败', async ({ page }) => {
    const usernameField = page.locator('vaadin-login-form').locator('input[name="username"]');
    await usernameField.fill('wronguser');
    
    const passwordField = page.locator('vaadin-login-form').locator('input[name="password"]');
    await passwordField.fill('wrongpassword');
    
    const loginButton = page.locator('vaadin-login-form').locator('button[type="submit"]');
    await loginButton.click();
    
    // 验证错误消息显示
    await expect(page.locator('vaadin-login-form')).toContainText(/错误|失败|invalid/i);
  });
});
