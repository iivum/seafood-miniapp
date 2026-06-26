/**
 * P1 addToCart 鉴权拦截守卫测试。
 *
 * 根因:pages/index/index.js addToCart 无 isAuthenticated 检查 + 走本地
 * cartUtil.addToCart(非后端 needAuth API),导致:
 *   1. 未登录可加购物车(违反 needAuth 原则)
 *   2. 本地购物车数据永远到不了后端,登录后看不到
 *
 * 修法:addToCart 函数体必须:
 *   - 调 wx.getStorageSync('accessToken') 或 authStore.isAuthenticated() 守卫
 *   - 未登录分支 wx.navigateTo 跳 login
 *   - 已登录分支调 cartApi.addItem(后端 needAuth API)而非 cartUtil
 *
 * 此测试锁住三个不变量防漏。现状:函数体无 token 检查、无 navigateTo、
 * 仍调 cartUtil.addToCart → 3/3 FAIL。
 */
import * as fs from 'fs';
import * as path from 'path';

function readIndex(): string {
  return fs.readFileSync(
    path.join(__dirname, '../../pages/index/index.js'),
    'utf-8'
  );
}

describe('首页 addToCart 鉴权拦截(P1 守卫)', () => {
  it('addToCart 函数体内调 wx.getStorageSync("accessToken") 或 isAuthenticated()', () => {
    const src = readIndex();
    // 找到 addToCart 函数体
    const match = src.match(/addToCart\s*\([^)]*\)\s*\{([\s\S]*?)\n\s*\}/);
    expect(match).not.toBeNull();
    const body = match![1];
    // 必须检查 token
    expect(
      /wx\.getStorageSync\(['"]accessToken['"]\)|isAuthenticated\(/.test(body)
    ).toBe(true);
  });

  it('未登录分支应 wx.navigateTo 跳 login', () => {
    const src = readIndex();
    const match = src.match(/addToCart\s*\([^)]*\)\s*\{([\s\S]*?)\n\s*\}/);
    expect(match).not.toBeNull();
    const body = match![1];
    // 未登录时必须跳 login 页
    expect(
      /wx\.navigateTo\(\{[^}]*url:\s*['"][^'"]*login[^'"]*['"]/.test(body)
    ).toBe(true);
  });

  it('已登录分支应调 cartApi.addItem 而非本地 cartUtil', () => {
    const src = readIndex();
    const match = src.match(/addToCart\s*\([^)]*\)\s*\{([\s\S]*?)\n\s*\}/);
    expect(match).not.toBeNull();
    const body = match![1];
    // 必须 require cartApi
    expect(/require\([^)]*features\/cart\/api/.test(body) || /require\([^)]*cart\/api/.test(src)).toBe(true);
    // 不能直接调 cartUtil.addToCart(无 token 检查的本地实现)
    expect(!/cartUtil\.addToCart\(/.test(body)).toBe(true);
  });
});
