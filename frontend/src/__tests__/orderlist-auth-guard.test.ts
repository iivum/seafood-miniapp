/**
 * P1 order-list onShow 鉴权拦截守卫测试。
 *
 * 根因:pages-sub/order/order-list/order-list.js onShow 无 token 检查,直接
 * this.fetchOrders() → orderStore.refresh() → GET /api/orders。未登录拿到 403。
 * 修法:onShow 函数体必须:
 *   - 顶部调 wx.getStorageSync('accessToken') 或 authStore.isAuthenticated() 守卫
 *   - 未登录分支 wx.navigateTo 跳 login 带 redirect
 *
 * 此测试锁住两个不变量防漏。现状:函数体无 token 检查、无 navigateTo → 2/2 FAIL。
 *
 * 关键正则陷阱(T4 教训):redirect query 字符串必须保留,test regex 用
 * `login[^'"]*` 模式(允许后跟 query);不删 redirect。
 */
import * as fs from 'fs';
import * as path from 'path';

function readOrderList(): string {
  return fs.readFileSync(
    path.join(__dirname, '../../pages-sub/order/order-list/order-list.js'),
    'utf-8'
  );
}

describe('order-list onShow 鉴权拦截(P1 守卫)', () => {
  it('onShow 函数体顶部有 token 检查', () => {
    const src = readOrderList();
    // 找到 onShow 函数体 — 注意:onShow 后跟 `:` (对象方法) 或 `(`
    const match = src.match(/onShow\s*\(\s*\)\s*\{([\s\S]*?)\n\s{4}\}/);
    expect(match).not.toBeNull();
    const body = match![1];
    // 顶部前 8 行内必须有 token 检查(放最顶部 + 立刻 return)
    const head = body.split('\n').slice(0, 8).join('\n');
    expect(
      /wx\.getStorageSync\(['"]accessToken['"]\)|isAuthenticated\(/.test(head)
    ).toBe(true);
  });

  it('未登录分支应 wx.navigateTo 跳 login 带 redirect', () => {
    const src = readOrderList();
    const match = src.match(/onShow\s*\(\s*\)\s*\{([\s\S]*?)\n\s{4}\}/);
    expect(match).not.toBeNull();
    const body = match![1];
    // 未登录时必须跳 login 页,允许后跟 ?redirect= query
    expect(
      /wx\.navigateTo\(\{[^}]*url:\s*['"][^'"]*login[^'"]*redirect/.test(body)
    ).toBe(true);
  });
});
