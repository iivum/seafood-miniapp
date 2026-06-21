/*
 * C5 — mp 视觉验证共享 harness。
 *
 * 感知层(visual-diff.cjs)与几何层(geometry-diff.cjs)都要:dev 登录拿 token、
 * 把登录态注入 mp 运行时、为「当前漂移 userId」运行时 seed 订单/地址/购物车、
 * 轮询 page.data() 到内容就绪。这些逻辑此前只在 visual-diff.cjs 里 inline,
 * 几何层要复用 → 抽到本模块,单一源,两边 require。
 *
 * 设计约束(沿用 visual-diff.cjs 既有注释,行为字节级一致):
 *   · dev wechat-login 每次返回的 userId(JWT sub)会漂移(后端按 openId 重建 user、_id 变),
 *     且 GET /api/orders/{id} 强制 own、/api/addresses 经 self-scoped 门面读该 userId 内嵌地址、
 *     carts 集合 _id = userId → 所有 seed 必须挂到「当前 login 的 userId」,不能静态。best-effort。
 *   · 用户/购物车文档 _id:Spring Data 把 24-hex String @Id 持久化为 ObjectId → 字符串匹配 0 条,
 *     按 hex 形态选 ObjectId / 原值匹配。
 *   · 两套 mp request 层取 token 处不同(utils/request.js 读 globalData.token + storage 'token';
 *     src/shared/api/request.js 读 storage 'accessToken'/'refreshToken')→ 注入时全喂。
 */
const { execFileSync } = require('node:child_process');
const http = require('node:http');

const API_HOST = process.env.API_HOST || '127.0.0.1';
const API_PORT = Number(process.env.API_PORT || 8080);
const DEV_CODE = process.env.DEV_LOGIN_CODE || 'dev-visual-001';
const SEED_ORDER_IDS = ['v2.1-closure-order-001', 'v2.1-closure-order-002', 'v2.1-closure-order-003'];

const race = (p, ms, l) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('TIMEOUT@' + l)), ms))]);

/** POST /api/auth/wechat-login,返回 { token, userId }(失败抛错,调用方据此标 err)。 */
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

/** GET /api/products 取前 n 个商品 id(fixture 无固定 _id,reseed 即变 → 运行时取)。 */
function fetchProductIds(n) {
  return new Promise((resolve) => {
    http.get({ host: API_HOST, port: API_PORT, path: `/api/products?page=0&size=${n}` }, (res) => {
      let s = '';
      res.on('data', (d) => (s += d));
      res.on('end', () => {
        try {
          const o = JSON.parse(s);
          const list = Array.isArray(o) ? o : o.content;
          resolve((list || []).map((p) => p.id).filter(Boolean));
        } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });
}

/** 取第一个商品 id(detail 页拼 url 用)。 */
async function fetchFirstProductId() {
  const ids = await fetchProductIds(1);
  return ids[0] || null;
}

/** mongosh 形态选择:24-hex String @Id 在 Mongo 里是 ObjectId,字符串匹配 0 条。 */
function idExpr(varName) {
  return `(/^[0-9a-fA-F]{24}$/.test(${varName})?ObjectId(${varName}):${varName})`;
}

function runMongo(js, label) {
  try {
    execFileSync('docker', ['exec', '-i', 'seafood-mongodb', 'mongosh', 'seafood', '--quiet', '--eval', js], { stdio: 'ignore' });
    return true;
  } catch (e) {
    console.warn(`  [seed] ${label} 跳过(docker/mongo 不可达):${String(e.message).slice(0, 80)}`);
    return false;
  }
}

/** mp-08/09:为当前 userId seed 订单(_id 漂移否则空态)。 */
function seedOrdersFor(userId) {
  const js = `const uid=${JSON.stringify(userId)};
db.orders.deleteMany({$or:[{userId:uid},{_id:{$in:${JSON.stringify(SEED_ORDER_IDS)}}}]});
const now=new Date("2026-06-18T08:00:00Z"),eta=new Date("2026-06-21T08:00:00Z");
db.orders.insertMany([
 {_id:"v2.1-closure-order-001",userId:uid,status:"PENDING",items:[{productId:"p1",productName:"挪威三文鱼刺身",unitPrice:NumberDecimal("128.00"),quantity:2},{productId:"p2",productName:"波士顿龙虾",unitPrice:NumberDecimal("288.00"),quantity:1}],totalAmount:NumberDecimal("544.00"),estimatedDelivery:eta,createdAt:now,updatedAt:now},
 {_id:"v2.1-closure-order-002",userId:uid,status:"PAID",items:[{productId:"p3",productName:"大闸蟹 4 两公",unitPrice:NumberDecimal("88.00"),quantity:4}],totalAmount:NumberDecimal("352.00"),estimatedDelivery:eta,createdAt:now,updatedAt:now},
 {_id:"v2.1-closure-order-003",userId:uid,status:"COMPLETED",items:[{productId:"p4",productName:"冰鲜大黄鱼",unitPrice:NumberDecimal("59.90"),quantity:3}],totalAmount:NumberDecimal("179.70"),estimatedDelivery:eta,createdAt:now,updatedAt:now}
]);`;
  if (runMongo(js, 'order seed')) console.log(`  [seed] 已为当前 userId=${userId} seed ${SEED_ORDER_IDS.length} 单`);
}

/** mp-07:为当前 userId seed 内嵌地址(经 /api/addresses 门面渲染,_id 漂移否则空态)。 */
function seedAddressesFor(userId) {
  const js = `const uid=${JSON.stringify(userId)};
const q={_id:${idExpr('uid')}};
db.users.updateOne(q,{$set:{addresses:[
 {_id:"addr-001",name:"张伟",phone:"13800138001",province:"广东省",city:"深圳市",detail:"南山区科技园路 1 号海王大厦 12 楼",isDefault:true},
 {_id:"addr-002",name:"李娜",phone:"13900139002",province:"上海市",city:"上海市",detail:"浦东新区世纪大道 100 号环球金融中心 30 层",isDefault:false},
 {_id:"addr-003",name:"王芳",phone:"13700137003",province:"北京市",city:"北京市",detail:"朝阳区建国路 88 号 SOHO 现代城 B 座 1801",isDefault:false}
]}});`;
  if (runMongo(js, 'address seed')) console.log(`  [seed] 已为当前 userId=${userId} seed 3 条地址`);
}

/** mp-04:为当前 userId seed 购物车(carts 集合 _id=userId)。CartService.get 不富集 →
 *  只要 items 非空,cart-item 行就渲染;productId 用真实 id(就近真实,避免脏数据)。 */
function seedCartFor(userId, productIds) {
  const items = productIds.map((pid, i) =>
    `{productId:${JSON.stringify(pid)},quantity:NumberInt(${i === 0 ? 2 : 1}),selected:true,addedAt:now}`);
  const js = `const uid=${JSON.stringify(userId)};
const id=${idExpr('uid')};
const now=new Date();
db.carts.deleteOne({_id:id});
db.carts.insertOne({_id:id,items:[${items.join(',')}],updatedAt:now});`;
  if (runMongo(js, 'cart seed')) console.log(`  [seed] 已为当前 userId=${userId} seed 购物车 ${productIds.length} 行`);
}

/** 注入 dev 登录态。token 写 storage(两套 request 层)+ app.globalData(onLaunch 不重跑)。 */
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

/** 轮询 page.data() 直到 predicate 为真或超时 → 防"异步取数未完成就采样"的空态假信号。 */
async function waitForData(mp, predicate, maxMs) {
  const deadline = Date.now() + maxMs;
  while (Date.now() < deadline) {
    try {
      const pg = await mp.currentPage();
      const data = pg && (await pg.data());
      if (data && predicate(data)) return true;
    } catch (e) { /* currentPage/data 偶抛,继续轮询 */ }
    await new Promise((r) => setTimeout(r, 400));
  }
  return false;
}

module.exports = {
  API_HOST, API_PORT, DEV_CODE, SEED_ORDER_IDS,
  race, devLogin, fetchProductIds, fetchFirstProductId,
  seedOrdersFor, seedAddressesFor, seedCartFor, injectAuth, waitForData,
};
