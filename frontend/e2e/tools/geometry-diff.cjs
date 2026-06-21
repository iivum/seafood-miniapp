/*
 * C5 — mp 视觉验证(几何层):结构不变量比对,AA/DPR/设备框「完全免疫」。
 *
 * 与感知层(visual-diff.cjs)互补:
 *   - 感知层(odiff 像素)抓「外观偏离」,但掺设备框/状态栏/图片噪声;
 *   - 几何层量 mp 实际渲染的结构不变量(区域存在性、栅格列数),剥离噪声,
 *     精确回答「布局崩没崩」——非 flaky,直接驱动 TDD 修复。
 *
 * 关键实现:automator 的元素句柄 API(page.$ / page.$$ / element.size)在本
 * 环境直接超时挂死,page.outerWxml 在 automator 0.12.1 不存在。改用 mp 原生
 * 布局查询 `wx.createSelectorQuery().boundingClientRect()`,经 mp.evaluate 在
 * mp 运行时内执行 —— 稳定返回真实 rect(left/top/width/height),绕开句柄路线。
 *
 * SoT = 提交的 `od-geometry/<screen>.json`(从 OD mockup 量出的期望不变量)。
 *
 * metric:
 *   - present  : 选择器匹配 ≥1 且首元素 height>0(区域真渲染,非空 wx:for 折叠)
 *   - count    : 匹配元素数(可带 tol)
 *   - columns  : 把所有匹配元素的 left 按 ~12px 容差聚类,簇数 = 列数
 *
 * 跑法:cd frontend && node e2e/tools/geometry-diff.cjs            # 全部屏
 *      node e2e/tools/geometry-diff.cjs mp-01-home               # 单屏
 * 退出码:任一 check 不符 → 非零(RED);全符 → 0(GREEN)。
 */
const automator = require('miniprogram-automator');
const path = require('node:path');
const fs = require('node:fs');
const {
  devLogin, injectAuth, seedCartFor, fetchProductIds, fetchFirstProductId,
  seedOrdersFor, seedAddressesFor, seedBanners,
} = require('./mp-harness.cjs');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const GEOM = path.resolve(__dirname, '../od-geometry');
const COL_TOL_PX = 12; // 列聚类容差(同列元素 left 抖动)

// 屏 → mp 路由(与 visual-diff.cjs 对齐)。
const ROUTES = {
  'mp-01-home': '/pages/index/index',
  'mp-02-category': '/pages/category/category',
  'mp-04-cart': '/pages/cart/cart',
  'mp-05-profile': '/pages/profile/profile',
};

const race = (p, ms, l) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('TIMEOUT@' + l)), ms))]);

/** left 值按容差聚类,返回簇数(= 列数)。 */
function countColumns(lefts) {
  const sorted = [...lefts].sort((a, b) => a - b);
  let cols = 0;
  let anchor = -Infinity;
  for (const x of sorted) {
    if (x - anchor > COL_TOL_PX) { cols++; anchor = x; }
  }
  return cols;
}

/** 在 mp 运行时内一次性批量量出所有 check 选择器的 boundingClientRect。 */
async function measureAll(mp, checks) {
  const selectors = checks.map((c) => c.selector);
  const raw = await race(mp.evaluate((sels) => new Promise((resolve) => {
    const q = wx.createSelectorQuery();
    sels.forEach((s) => q.selectAll(s).boundingClientRect());
    q.exec((res) => resolve(
      res.map((arr) => (arr || []).map((r) => ({
        left: Math.round(r.left), top: Math.round(r.top),
        w: Math.round(r.width), h: Math.round(r.height),
      })))
    ));
  }), selectors), 12000, 'evaluate');
  return raw; // raw[i] = check[i] 的 rect 数组
}

function metricOf(check, rects) {
  if (check.metric === 'present') {
    // carousel-aware:swiper 非激活 slide 的 boundingClientRect 高度为 0,
    // 故判「任一匹配元素有高度」而非只看首个(否则轮播首图离屏会误报缺失)。
    const actual = rects.length > 0 && rects.some((r) => (r.h || 0) > 0);
    return { actual, extra: `n=${rects.length}` };
  }
  if (check.metric === 'count') {
    return { actual: rects.length, extra: '' };
  }
  if (check.metric === 'columns') {
    const cols = rects.length ? countColumns(rects.map((r) => r.left)) : 0;
    return { actual: cols, extra: `cells=${rects.length} w=${rects[0] ? rects[0].w : '-'}` };
  }
  return { actual: null, extra: '' };
}

function ok(check, actual) {
  if (check.metric === 'present') return actual === check.expected;
  if (check.metric === 'count') return Math.abs(actual - check.expected) <= (check.tol || 0);
  if (check.metric === 'columns') return actual === check.expected;
  return false;
}

/** 轮询单 selector 直到 ≥1 匹配且首高>0,或超时 → 防 auth 屏异步取数未完成就量(空态假信号)。 */
async function waitForSelector(mp, selector, maxMs) {
  const deadline = Date.now() + maxMs;
  while (Date.now() < deadline) {
    try {
      const raw = await measureAll(mp, [{ selector }]);
      if (raw[0] && raw[0].some((r) => (r.h || 0) > 0)) return true;
    } catch (e) { /* 偶抛,继续轮询 */ }
    await new Promise((r) => setTimeout(r, 500));
  }
  return false;
}

(async () => {
  const only = process.argv[2];
  const files = fs.readdirSync(GEOM).filter((f) => f.endsWith('.json') && (!only || f === only + '.json'));
  if (!files.length) { console.error('no geometry spec', only || ''); process.exit(2); }

  const specs = files.map((f) => JSON.parse(fs.readFileSync(path.join(GEOM, f), 'utf8')));

  // banner seed(全局公共读,无 userId/auth 依赖)→ 任一屏标 seed:["banners"] 即 seed。
  if (specs.some((s) => (s.seed || []).includes('banners'))) seedBanners();

  // productParam 屏(detail,公共页):运行时取真实 product id(reseed 后硬编码会 404)。
  let productId = null;
  if (specs.some((s) => s.productParam)) {
    productId = await fetchFirstProductId();
    if (productId) console.log(`  [product] detail 用 product id=${productId}`);
    else console.warn('  [product] 取 product id 失败 → mp-03 将空');
  }

  // 任一 auth 屏 → dev 登录 + 按需 seed(cart/orders/addresses)。失败不致命:该屏量空态、check 红。
  let auth = null;
  if (specs.some((s) => s.auth)) {
    try {
      auth = await devLogin();
      console.log(`  [auth] dev 登录 ok,userId=${auth.userId}`);
      const needSeed = (kind) => specs.some((s) =>
        (s.seed || []).includes(kind) || (kind === 'cart' && s.screen === 'mp-04-cart'));
      if (needSeed('cart')) {
        const pids = await fetchProductIds(2);
        if (pids.length) seedCartFor(auth.userId, pids);
        else console.warn('  [seed] 取 product id 失败 → cart 将空态');
      }
      if (needSeed('orders')) seedOrdersFor(auth.userId);
      if (needSeed('addresses')) seedAddressesFor(auth.userId);
    } catch (e) { console.warn(`  [auth] dev 登录失败(鉴权屏将渲染未登录态):${e.message}`); }
  }

  const mp = await race(automator.connect({ wsEndpoint: WS }), 20000, 'connect');
  const out = [];
  for (const spec of specs) {
    const baseRoute = spec.route || ROUTES[spec.screen];
    const rec = { screen: spec.screen, checks: [] };
    if (!baseRoute) { rec.err = 'no route for ' + spec.screen; out.push(rec); continue; }
    // 拼带参 route:detail 用运行时 product id;order-detail 用已知 order id。
    let route = baseRoute;
    if (spec.productParam) {
      if (!productId) { rec.err = 'productParam 屏但无 product id'; out.push(rec); continue; }
      route = `${baseRoute}?id=${productId}`;
    } else if (spec.orderId) {
      route = `${baseRoute}?id=${spec.orderId}`;
    }
    const isSub = baseRoute.startsWith('/pages-sub/');
    try {
      if (isSub) {
        // 分包带参页:reLaunch 直达深层页 flaky(automator 偶落回 home)→ 走 app 真实流转:
        // reLaunch(home) → 注入登录态 → navigateTo(子页) → 校验 currentPage 落对 → 不对重试。
        const base = baseRoute.replace(/^\//, '');
        let landed = false;
        for (let attempt = 0; attempt < 2 && !landed; attempt++) {
          await race(mp.reLaunch('/pages/index/index'), 6000, 'reLaunch-home').catch(() => {});
          await new Promise((r) => setTimeout(r, 1500));
          if (spec.auth && auth) await injectAuth(mp, auth);
          await race(mp.navigateTo(route), 6000, 'navigateTo').catch(() => {});
          await new Promise((r) => setTimeout(r, 4000));
          try {
            const pg = await mp.currentPage();
            landed = !!(pg && pg.path && pg.path.indexOf(base) !== -1);
          } catch (e) { /* currentPage 偶抛,landed 保持 false 触发重试 */ }
          if (!landed) console.warn(`    [nav] ${spec.screen} 落点非 ${base}(attempt ${attempt + 1}/2),重试…`);
        }
        if (!landed) { rec.err = `navigateTo 未落到 ${base}(分包页 flaky,2 次重试均失败)`; out.push(rec); continue; }
      } else {
        // tab/普通页:reLaunch 保证 onLoad 重跑(反映当前后端状态)。auth 屏先注入登录态。
        if (spec.auth && auth) await injectAuth(mp, auth);
        await race(mp.reLaunch(route), 6000, 'reLaunch').catch(() => {});
        await new Promise((r) => setTimeout(r, 4000));
      }
      // 数据屏:固定 sleep 后再轮询关键 selector 就绪,抢不过异步取数时不量空态(最多再等 9s)。
      if (spec.waitForSelector) {
        const ready = await waitForSelector(mp, spec.waitForSelector, 9000);
        if (!ready) console.warn(`    [wait] ${spec.screen} ${spec.waitForSelector} 未就绪(9s)→ 可能量到空态`);
      }
      const raw = await measureAll(mp, spec.checks);
      spec.checks.forEach((c, i) => {
        const m = metricOf(c, raw[i] || []);
        rec.checks.push({ key: c.key, metric: c.metric, expected: c.expected, actual: m.actual, pass: ok(c, m.actual), extra: m.extra });
      });
    } catch (e) { rec.err = (e && e.message) || String(e); }
    out.push(rec);
  }
  await mp.disconnect();

  let failed = false;
  console.log('\n=== C5 视觉验证(几何层:结构不变量,框/AA/DPR 免疫)===');
  for (const r of out) {
    if (r.err) { console.log(`  ✖ ${r.screen}: ${r.err}`); failed = true; continue; }
    console.log(`  ${r.screen}:`);
    for (const c of r.checks) {
      if (!c.pass) failed = true;
      console.log(`    ${c.pass ? '✓ GREEN' : '✖ RED  '} ${c.key} [${c.metric}] 期望=${c.expected} 实际=${c.actual}  ${c.extra}`);
    }
  }
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error('FAIL', e && e.message ? e.message : e); process.exit(1); });
