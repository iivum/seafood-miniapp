/**
 * mp-blocker-fix 真机 e2e(PLACEHOLDER,等 MP MCP 稳定后启用)
 *
 * 背景:本环境 weapp-dev MCP(`mcp__weapp-dev__mp_*` / `mcp__weapp-dev__page_*`)
 *      在 Task 1-6 多次 disconnect,Task 7 之前刚重连成功。**本测试所有 it.skip
 *      占位**,不调任何 MP 工具。后续 MCP 稳定后:
 *
 *   1. 取消每个 `it.skip(...)` → `it(...)`(或保留 skip 但加 `runIf(canConnect)`)
 *   2. 接入 `mp-harness.cjs` 的 `launchAuto / devLogin / clearStorage / navigate / getLogs`
 *   3. 跑法:`cd frontend && TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 \
 *           ./node_modules/.bin/jest e2e/mp-e2e-live.test.ts --runInBand`
 *
 * 7 项真机验收(对应 MP-ACCEPTANCE-RUNBOOK.md 7 项 + mp-blocker-fix.test.ts 7 it):
 *
 *   §1 P0-1 启动崩 — 首页 reLaunch 无 TypeError: request is not a function
 *   §2 P0-2 Skyline 降级 — 首页无 Skyline CSS 警告
 *   §3 P1 裂图 — login 页 logo 渲染正常
 *   §4 P1 鉴权 — 未登录点 addToCart 跳 login
 *   §5 P1 鉴权 — 未登录进 order-list 跳 login
 *   §6 P2 登录 — dev-login 成功 + storage 有 accessToken + 跳回原页
 *   §7 完整 flow — 登录 → 加购 → 购物车有数据
 *
 * 静态 + API 自动验收(mp-blocker-fix.test.ts)目前 14/14 PASS;真机层用本文件
 * 兜底,MCP 修好 unskip 即可。
 */

// @ts-nocheck — CommonJS only,jest globals 由 jest runtime 注入

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';

/**
 * MP MCP 可用性快速探测。等真机 e2e 启用时,改用真正的 mp_ensureConnection。
 * 当前的占位实现永远返 false → 所有 it.skip 生效,测试不被计入 PASS/FAIL。
 */
async function canConnect(): Promise<boolean> {
  // TODO(mp-mcp-ok):改为调 mp_ensureConnection 或 automator.connect({ wsEndpoint: WS })
  // return (await connectWithRetry()) != null;
  return false;
}

describe('mp-blocker-fix 真机 e2e(PLACEHOLDER — 等 MP MCP 稳定后 unskip)', () => {
  // 占位用 — 不接 harness,纯骨架。所有 it 跳过,不影响 CI 跑测试。
  // 真接 MP 后,把 describe 顶层加 beforeAll/afterAll + 调 launchAuto/connect。
  void WS; // eslint-disable-line @typescript-eslint/no-unused-vars

  it.skip('§1 P0-1 启动崩 — 首页 reLaunch 无 TypeError: request is not a function', async () => {
    // placeholder — 等 MP MCP 稳定后 unskip
    // 实现思路:
    //   await clearStorage(['accessToken', 'token']);
    //   await navigate('pages/index/index', 'reLaunch');
    //   const errs = await getLogs('error');
    //   expect(errs).not.toEqual(expect.arrayContaining([
    //     expect.stringMatching(/TypeError.*request is not a function/),
    //   ]));
    expect(await canConnect()).toBe(false); // 永远不命中,仅占位
  });

  it.skip('§2 P0-2 Skyline 降级 — 首页无 Skyline CSS 警告', async () => {
    // placeholder
    // 实现思路:
    //   await navigate('pages/index/index', 'reLaunch');
    //   const warns = await getLogs('warning');
    //   const skylineWarns = warns.filter(w =>
    //     /Skyline|object-fit|-webkit-line-clamp|@font-face.*unicode-range/.test(w));
    //   expect(skylineWarns).toEqual([]);
    expect(await canConnect()).toBe(false);
  });

  it.skip('§3 P1 裂图 — login 页 logo 渲染正常', async () => {
    // placeholder
    // 实现思路:
    //   await navigate('pages-sub/user/login/login', 'reLaunch');
    //   const wxml = await page.outerWxml();
    //   expect(wxml).toMatch(/logo\.png|images\/logo/);  // image src 引用了 logo
    //   const imgRect = await page.$('image');
    //   expect(imgRect).not.toBeNull();                  // image 节点存在
    expect(await canConnect()).toBe(false);
  });

  it.skip('§4 P1 鉴权 — 未登录点 addToCart 跳 login', async () => {
    // placeholder
    // 实现思路:
    //   await clearStorage(['accessToken', 'token']);
    //   await navigate('pages/index/index', 'reLaunch');
    //   await page.callMethod('addToCart', [{ currentTarget: { dataset: { product: { id: 'test' } } } }]);
    //   await new Promise(r => setTimeout(r, 1000));
    //   const page_ = await currentPage();
    //   expect(page_.path).toMatch(/pages-sub\/user\/login\/login/);
    expect(await canConnect()).toBe(false);
  });

  it.skip('§5 P1 鉴权 — 未登录进 order-list 跳 login', async () => {
    // placeholder
    // 实现思路:
    //   await clearStorage(['accessToken', 'token']);
    //   await navigate('pages-sub/order/order-list/order-list', 'reLaunch');
    //   await new Promise(r => setTimeout(r, 1000));
    //   const page_ = await currentPage();
    //   expect(page_.path).toMatch(/pages-sub\/user\/login\/login/);
    expect(await canConnect()).toBe(false);
  });

  it.skip('§6 P2 登录 — dev-login 成功 + storage 有 accessToken + 跳回原页', async () => {
    // placeholder
    // 实现思路:
    //   await navigate('pages-sub/user/login/login', 'reLaunch');
    //   await page.callMethod('onDevLogin', []);
    //   await new Promise(r => setTimeout(r, 2000));
    //   const token = await page.evaluate(`wx.getStorageSync('accessToken')`);
    //   expect(token).toBeTruthy();
    //   const page_ = await currentPage();
    //   expect(page_).not.toMatch(/pages-sub\/user\/login\/login/);
    expect(await canConnect()).toBe(false);
  });

  it.skip('§7 完整 flow — 登录 → 加购 → 购物车有数据', async () => {
    // placeholder
    // 实现思路:
    //   await devLogin('e2e-flow-test');
    //   await navigate('pages/index/index', 'reLaunch');
    //   const firstProduct = await page.data('products[0].id');
    //   await page.callMethod('addToCart', [{ currentTarget: { dataset: { product: { id: firstProduct } } } }]);
    //   await new Promise(r => setTimeout(r, 1000));
    //   await navigate('pages/cart/cart', 'switchTab');
    //   await new Promise(r => setTimeout(r, 1500));
    //   const cartItems = await page.data('items');
    //   expect(Array.isArray(cartItems)).toBe(true);
    //   expect(cartItems.length).toBeGreaterThanOrEqual(1);
    expect(await canConnect()).toBe(false);
  });
});