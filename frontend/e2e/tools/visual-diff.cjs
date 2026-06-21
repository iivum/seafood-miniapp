/*
 * C5 — mp 视觉验证(感知层):mp 实截图 vs OD golden 的 odiff 比对 + 阈值 gate。
 *
 * 前置:
 *   1. 微信 DevTools 自动化端口已起:
 *      /Applications/wechatwebdevtools.app/Contents/MacOS/cli auto --project frontend --auto-port 9420
 *   2. OD golden 已生成并提交(frontend/e2e/od-golden/<screen>.png,见 gen-od-golden 说明)
 *   3. **有意义的逐屏信号需后端起着 + seed**(否则 mp 渲染 loading/空态,diff 必然很大 —— 这本身
 *      也是一种对 OD 的偏离信号,但非"像素级对齐"判断)
 *
 * 跑法:cd frontend && node e2e/tools/visual-diff.cjs            # 全部屏
 *      VISUAL_THRESHOLD=5 node e2e/tools/visual-diff.cjs mp-01-home  # 单屏 + 自定阈值
 *
 * 退出码:任一屏 diff% > 阈值 → 非零(RED,驱动修复);全部 ≤ 阈值 → 0(GREEN,防偏)。
 */
const automator = require('miniprogram-automator');
const { compare } = require('odiff-bin');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');
// 鉴权/seed/导航/轮询共享 harness —— 与 geometry-diff.cjs 同源(单一事实源,见 mp-harness.cjs)。
const {
  race, devLogin, fetchFirstProductId, injectAuth, waitForData,
  seedOrdersFor, seedAddressesFor,
} = require('./mp-harness.cjs');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const THRESHOLD_PCT = Number(process.env.VISUAL_THRESHOLD || 5);
const SHOTS = path.resolve(__dirname, '../screenshots');
const GOLD = path.resolve(__dirname, '../od-golden');

// detail 页商品 id 与 order-detail 订单 id 仍可经环境变量覆盖(默认走 harness 运行时取 / 已知 seed id)。
const PRODUCT_ID = process.env.PRODUCT_ID || null;
const ORDER_ID = process.env.ORDER_ID || 'v2.1-closure-order-001';

// 屏清单(参数化)。一律用 reLaunch(path):关闭页栈重开目标页 → onLoad 必触发 → 重新拉后端数据。
// switchTab 到「已激活的 tabBar 页」不会重跑 onLoad,会截到上次的陈旧渲染(空态)。
// auth:true → 截图前注入 dev 登录态(token 写 storage + app.globalData,见 captureActual)。
const SCREENS = [
  { name: 'mp-01-home', path: '/pages/index/index' },
  { name: 'mp-02-category', path: '/pages/category/category' },
  // waitFor:截图前轮询 page.data() 到内容就绪 —— 固定 4s sleep 在多屏顺序跑里偶尔抢不过
  // 异步取数(order-list 实证),截到空态假信号。数据屏给谓词,非数据屏走固定 sleep。
  { name: 'mp-03-product-detail', path: '/pages-sub/product/product-detail/product-detail', productDetail: true,
    waitFor: (d) => !d.isLoading && !!d.product },
  { name: 'mp-04-cart', path: '/pages/cart/cart' },
  { name: 'mp-05-profile', path: '/pages/profile/profile' },
  { name: 'mp-06-order-confirm', path: '/pages-sub/order/order-confirm/order-confirm', auth: true },
  { name: 'mp-07-address', path: '/pages-sub/user/address/address-list', auth: true,
    waitFor: (d) => Array.isArray(d.addresses) && d.addresses.length > 0 },
  { name: 'mp-08-order-list', path: '/pages-sub/order/order-list/order-list', auth: true,
    waitFor: (d) => Array.isArray(d.orders) && d.orders.length > 0 },
  { name: 'mp-09-order-detail', path: `/pages-sub/order/order-detail/order-detail?id=${ORDER_ID}`, auth: true,
    waitFor: (d) => !d.isLoading && !!d.order },
];

/** 用 macOS 内置 sips 把 src 缩放到 w×h(归一化到 golden 尺寸,消除 DPR 差)。 */
function normalize(src, w, h, out) {
  execFileSync('sips', ['-z', String(h), String(w), src, '--out', out], { stdio: 'ignore' });
}

function dims(png) {
  const o = execFileSync('sips', ['-g', 'pixelWidth', '-g', 'pixelHeight', png]).toString();
  return {
    w: Number((o.match(/pixelWidth:\s*(\d+)/) || [])[1]),
    h: Number((o.match(/pixelHeight:\s*(\d+)/) || [])[1]),
  };
}

async function captureActual(mp, screen, auth) {
  const out = path.join(SHOTS, `${screen.name}-actual.png`);
  const isSub = screen.path.startsWith('/pages-sub/');

  if (isSub) {
    // 分包带参页:reLaunch 直达深层页 flaky(automator 偶落回 home)。改走 app 真实流转:
    // reLaunch(home) → 注入登录态 → navigateTo(子页) → 校验 currentPage 落对 → 不对重试。
    // navigateTo 是到达分包页的规范方式(push 栈),比 reLaunch 清栈重开稳。
    const base = screen.path.replace(/^\//, '').split('?')[0];
    let landed = false;
    for (let attempt = 0; attempt < 2 && !landed; attempt++) {
      await race(mp.reLaunch('/pages/index/index'), 6000, 'reLaunch-home').catch(() => {});
      await new Promise((r) => setTimeout(r, 1500));
      if (screen.auth && auth) await injectAuth(mp, auth);
      await race(mp.navigateTo(screen.path), 6000, 'navigateTo').catch(() => {});
      await new Promise((r) => setTimeout(r, 4000)); // 后端往返 + 渲染
      try {
        const pg = await mp.currentPage();
        landed = !!(pg && pg.path && pg.path.indexOf(base) !== -1);
      } catch (e) { /* currentPage 偶抛,landed 保持 false 触发重试 */ }
      if (!landed) console.warn(`    [nav] ${screen.name} 落点非 ${base}(attempt ${attempt + 1}/2),重试…`);
    }
    if (!landed) throw new Error(`navigateTo 未落到 ${base}(分包页 flaky,2 次重试均失败)`);
  } else {
    // tab/普通页:reLaunch 保证 onLoad 重跑 → 反映当前后端状态(非陈旧空态)。
    // best-effort:部分 tabBar 页 reLaunch promise 不 resolve(automator 怪癖)但页面实已加载。
    if (screen.auth && auth) await injectAuth(mp, auth);
    await race(mp.reLaunch(screen.path), 6000, 'reLaunch').catch(() => {});
    await new Promise((r) => setTimeout(r, 4000));
  }
  // 数据屏:固定 sleep 后再轮询内容就绪,抢不过异步取数时不截空态(最多再等 9s)。
  if (screen.waitFor) {
    const ready = await waitForData(mp, screen.waitFor, 9000);
    if (!ready) console.warn(`    [wait] ${screen.name} 内容未就绪(9s 超时)→ 可能截到空态/loading`);
  }
  await race(mp.screenshot({ path: out }), 15000, 'screenshot');
  return out;
}

(async () => {
  const only = process.argv[2];
  const screens = only ? SCREENS.filter((s) => s.name === only) : SCREENS;
  if (!screens.length) { console.error('no such screen:', only); process.exit(2); }

  // 任一选中屏需鉴权 → 先 dev 登录拿一枚新 token(失败不致命:鉴权屏各自标 err)。
  let auth = null;
  if (screens.some((s) => s.auth)) {
    try {
      auth = await devLogin();
      console.log(`  [auth] dev 登录 ok,userId=${auth.userId}`);
      seedOrdersFor(auth.userId);    // mp-08/09:为当前 login 用户 seed 订单(_id 漂移否则空态)
      seedAddressesFor(auth.userId); // mp-07:为当前 login 用户 seed 地址(经 /api/addresses 门面渲染)
    } catch (e) { console.warn(`  [auth] dev 登录失败(鉴权屏将渲染未登录态):${e.message}`); }
  }

  // detail 屏:运行时取真实 product id 拼 url(硬编码会因 reseed 失效 → 404 "商品不存在")。
  const pd = screens.find((s) => s.productDetail);
  if (pd) {
    const pid = PRODUCT_ID || (await fetchFirstProductId());
    if (pid) { pd.path = `${pd.path}?id=${pid}`; console.log(`  [product] detail 用 product id=${pid}`); }
    else { console.warn('  [product] 取 product id 失败(后端无商品?)→ mp-03 将渲空'); }
  }

  const mp = await race(automator.connect({ wsEndpoint: WS }), 20000, 'connect');
  const results = [];
  for (const s of screens) {
    const golden = path.join(GOLD, `${s.name}.png`);
    if (!fs.existsSync(golden)) { results.push({ name: s.name, err: 'golden 缺失:' + golden }); continue; }
    try {
      const actual = await captureActual(mp, s, auth);
      const g = dims(golden);
      const norm = path.join(SHOTS, `${s.name}-actual-norm.png`);
      normalize(actual, g.w, g.h, norm); // 归一化到 golden 尺寸
      const diff = path.join(SHOTS, `${s.name}-diff.png`);
      const res = await compare(golden, norm, diff, { antialiasing: true, outputDiffMask: false });
      const pct = res.match ? 0 : Number((res.diffPercentage || 0).toFixed(2));
      results.push({ name: s.name, pct, diff: res.match ? null : diff });
    } catch (e) {
      results.push({ name: s.name, err: (e && e.message) || String(e) }); // 单屏失败不致命,继续下一屏
    }
  }
  await mp.disconnect();

  let failed = false;
  console.log(`\n=== C5 视觉验证(感知层,阈值 ${THRESHOLD_PCT}%)===`);
  for (const r of results) {
    if (r.err) { console.log(`  ✖ ${r.name}: ${r.err}`); failed = true; continue; }
    const over = r.pct > THRESHOLD_PCT;
    failed = failed || over;
    console.log(`  ${over ? '✖ RED ' : '✓ GREEN'} ${r.name}: diff ${r.pct}%${over ? `  → ${r.diff}` : ''}`);
  }
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error('FAIL', e && e.message ? e.message : e); process.exit(1); });
