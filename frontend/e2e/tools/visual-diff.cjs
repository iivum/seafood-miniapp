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
const http = require('node:http');
const path = require('node:path');
const fs = require('node:fs');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const THRESHOLD_PCT = Number(process.env.VISUAL_THRESHOLD || 5);
const SHOTS = path.resolve(__dirname, '../screenshots');
const GOLD = path.resolve(__dirname, '../od-golden');

// 鉴权页用:运行时 dev wechat-login 拿一枚新 accessToken(JWT exp ~15min,必须现取)。
// 同时把 sub(userId)解出来 —— mp-08/09 走 /orders 由 JWT principal 强制 own,
// mp-07 address-list url 含 userInfo.id,二者都要这个 id 与 seed 的订单/地址对齐。
const API_HOST = process.env.API_HOST || '127.0.0.1';
const API_PORT = Number(process.env.API_PORT || 8080);
const DEV_CODE = process.env.DEV_LOGIN_CODE || 'dev-visual-001';
// detail 页商品 id:products fixture 无固定 _id,reseed 即变 → 默认运行时取(见 fetchFirstProductId);
// 仅当 PRODUCT_ID 环境变量显式给定时才用它。
const PRODUCT_ID = process.env.PRODUCT_ID || null;
// order-detail 已知订单 id(见 run-visual.sh seed 步,归属 DEV_CODE 对应用户)。
const ORDER_ID = process.env.ORDER_ID || 'v2.1-closure-order-001';

// dev wechat-login 每次返回的 userId(JWT sub)会漂移 —— 后端按 openId 重建 user、_id 变,
// 且 GET /api/orders/{id} 强制 own。故静态 seed 的订单对不上当前 login → mp-08/09 空/loading。
// 唯一可复现做法:登录后**为当前 userId 运行时 seed 订单**。best-effort(docker/mongo 不在则跳过)。
const SEED_ORDER_IDS = ['v2.1-closure-order-001', 'v2.1-closure-order-002', 'v2.1-closure-order-003'];
function seedOrdersFor(userId) {
  const js = `const uid=${JSON.stringify(userId)};
db.orders.deleteMany({$or:[{userId:uid},{_id:{$in:${JSON.stringify(SEED_ORDER_IDS)}}}]});
const now=new Date("2026-06-18T08:00:00Z"),eta=new Date("2026-06-21T08:00:00Z");
db.orders.insertMany([
 {_id:"v2.1-closure-order-001",userId:uid,status:"PENDING",items:[{productId:"p1",productName:"挪威三文鱼刺身",unitPrice:NumberDecimal("128.00"),quantity:2},{productId:"p2",productName:"波士顿龙虾",unitPrice:NumberDecimal("288.00"),quantity:1}],totalAmount:NumberDecimal("544.00"),estimatedDelivery:eta,createdAt:now,updatedAt:now},
 {_id:"v2.1-closure-order-002",userId:uid,status:"PAID",items:[{productId:"p3",productName:"大闸蟹 4 两公",unitPrice:NumberDecimal("88.00"),quantity:4}],totalAmount:NumberDecimal("352.00"),estimatedDelivery:eta,createdAt:now,updatedAt:now},
 {_id:"v2.1-closure-order-003",userId:uid,status:"COMPLETED",items:[{productId:"p4",productName:"冰鲜大黄鱼",unitPrice:NumberDecimal("59.90"),quantity:3}],totalAmount:NumberDecimal("179.70"),estimatedDelivery:eta,createdAt:now,updatedAt:now}
]);`;
  try {
    execFileSync('docker', ['exec', '-i', 'seafood-mongodb', 'mongosh', 'seafood', '--quiet', '--eval', js], { stdio: 'ignore' });
    console.log(`  [seed] 已为当前 userId=${userId} seed ${SEED_ORDER_IDS.length} 单`);
  } catch (e) {
    console.warn(`  [seed] order seed 跳过(docker/mongo 不可达,mp-08/09 将空态):${String(e.message).slice(0, 80)}`);
  }
}

/** GET /api/products 取第一个商品 id。products fixture 无固定 _id,每次 reseed 都变 →
 *  detail 页商品 id 必须运行时取,不能硬编码(否则 reseed 后 404 "商品不存在")。 */
function fetchFirstProductId() {
  return new Promise((resolve) => {
    http.get({ host: API_HOST, port: API_PORT, path: '/api/products?page=0&size=1' }, (res) => {
      let s = '';
      res.on('data', (d) => (s += d));
      res.on('end', () => {
        try {
          const o = JSON.parse(s);
          const list = Array.isArray(o) ? o : o.content;
          resolve(list && list[0] ? list[0].id : null);
        } catch (e) { resolve(null); }
      });
    }).on('error', () => resolve(null));
  });
}

/** POST /api/auth/wechat-login,返回 { token, userId }(失败抛错,鉴权屏据此标 err)。 */
function devLogin() {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ code: DEV_CODE });
    const req = http.request(
      { host: API_HOST, port: API_PORT, path: '/api/auth/wechat-login', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) } },
      (res) => {
        let s = '';
        res.on('data', (d) => (s += d));
        res.on('end', () => {
          try {
            const o = JSON.parse(s);
            const token = o.accessToken || o.token;
            if (!token) return reject(new Error('login 无 accessToken: ' + s.slice(0, 120)));
            const userId = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString()).sub;
            resolve({ token, userId });
          } catch (e) { reject(new Error('login 解析失败: ' + e.message)); }
        });
      },
    );
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

// 屏清单(参数化)。一律用 reLaunch(path):关闭页栈重开目标页 → onLoad 必触发 → 重新拉后端数据。
// switchTab 到「已激活的 tabBar 页」不会重跑 onLoad,会截到上次的陈旧渲染(空态)。
// auth:true → 截图前注入 dev 登录态(token 写 storage + app.globalData,见 captureActual)。
const SCREENS = [
  { name: 'mp-01-home', path: '/pages/index/index' },
  { name: 'mp-02-category', path: '/pages/category/category' },
  { name: 'mp-03-product-detail', path: '/pages-sub/product/product-detail/product-detail', productDetail: true },
  { name: 'mp-04-cart', path: '/pages/cart/cart' },
  { name: 'mp-05-profile', path: '/pages/profile/profile' },
  { name: 'mp-06-order-confirm', path: '/pages-sub/order/order-confirm/order-confirm', auth: true },
  { name: 'mp-07-address', path: '/pages-sub/user/address/address-list', auth: true },
  { name: 'mp-08-order-list', path: '/pages-sub/order/order-list/order-list', auth: true },
  { name: 'mp-09-order-detail', path: `/pages-sub/order/order-detail/order-detail?id=${ORDER_ID}`, auth: true },
];

const race = (p, ms, l) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('TIMEOUT@' + l)), ms))]);

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

// 注入 dev 登录态。token 写 storage 不够 —— mp 有两套 request 层,取处不同,都喂:
//  · utils/request.js(order-detail/address-list)读 app.globalData.token + storage 'token'
//  · src/shared/api/request.js(OrderAPI→order-list)读 storage 'accessToken'/'refreshToken'
// app onLaunch 只跑一次、reLaunch 不重跑,故同时写 storage 和 globalData。storage 全局
// 持久、跨 navigateTo 不丢 → 在 home 上注入一次,后续 navigateTo 子页仍带 Authorization。
async function injectAuth(mp, auth) {
  await mp.evaluate(
    (token, userInfo) => {
      wx.setStorageSync('token', token);
      wx.setStorageSync('accessToken', token);
      wx.setStorageSync('refreshToken', token);
      wx.setStorageSync('userInfo', userInfo);
      try {
        const app = getApp();
        if (app && app.globalData) { app.globalData.token = token; app.globalData.userInfo = userInfo; }
      } catch (e) { /* getApp 可能未就绪,storage 兜底 */ }
    },
    auth.token,
    { id: auth.userId, openId: 'dev-visual', nickName: '视觉验收' },
  );
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
      seedOrdersFor(auth.userId); // 为当前 login 用户 seed 订单(否则 _id 漂移导致空态)
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
