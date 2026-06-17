/**
 * 3 层 mp 视觉验证 — outerWxml + page.data + console。
 *
 * 取代"截图对图"方案:
 *   - 像素对字体抗锯齿 / DPR 极度敏感
 *   - 同一份代码每次跑 outerWxml 100% 一致,数据断言直击"前端是否真接了后端"
 *   - console 错误作 fail 条件(回应"console 错误需认真排查")
 *
 * ## 4 层断言(对齐 visual-verification-patterns.md)
 *  1. 结构 — outerWxml 节点 / class / 文案
 *  2. 数据 — page.data 字段 + 来自后端的字段
 *  3. 行为 — exception + console error/warning = 0
 *  4. 颜色 — token-parity.test.ts 单独跑
 *
 * ## 稳定性(Sprint 1 closure)
 *  按 `~/.claude/skills/wechat-miniprogram-e2e/references/stability.md` 模板 A+B:
 *  - 每个 it 独立 connect(connectWithRetry),不共享 beforeAll
 *  - listener 在 beforeEach 内 attach(不累积)
 *  - 失败隔离:单个 it 失败不影响其他 it
 *
 * 前置:
 *   1. DevTools 在跑(wechatwebdevtools),CLI 启动:`cli auto --auto-port 9420`
 *   2. 后端在 8080 跑(本仓库单进程 + GraalVM Native binary / JVM)
 *   3. 跑命令:TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 npx jest e2e/mp-3layer.test.ts --runInBand
 */
// @ts-nocheck — CommonJS only,jest globals 由 jest runtime 注入
const automator = require('miniprogram-automator');

// 抑制 EventEmitter MaxListenersExceededWarning(治标,见 troubleshooting §10.1 根因 2)
require('node:events').defaultMaxListeners = 50;

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const RETRY = 3;
const RETRY_DELAY_MS = 2000;

interface PageSpec {
  name: string;
  /** tabBar 页用 switchTab,非 tabBar 用 navigateTo */
  tab?: string;
  url?: string;
  /** 启动前需 setStorage 的 key→value(seed dev 用户态) */
  storage?: Record<string, unknown>;
  /** 第 1 层:WXML 应含的节点 / class / 文案(部分,够抓住布局回归) */
  wxmlMust: RegExp[];
  /** 第 2 层:page.data 中应有非空值的字段路径,用 . 分割;每条断言 existence (path 存在) */
  dataMust?: string[];
  /** 第 2 层:page.data 应匹配的 value(精确值) */
  dataExact?: Array<{ path: string; equals: unknown }>;
  /** 第 2 层:必须从后端拿到的数据形状(用 dataPath 取数组第一个元素,断言含字段) */
  fromBackend?: { path: string; fields: string[] };
}

const PAGES: PageSpec[] = [
  {
    name: 'mp-01-home',
    tab: '/pages/index/index',
    wxmlMust: [/home-banner/, /home-chips/, /今日推荐/],
    dataMust: ['products', 'categories'],
    fromBackend: { path: 'products.0', fields: ['name', 'price', 'stock', 'category', 'imageUrl'] },
  },
  {
    name: 'mp-02-category',
    tab: '/pages/category/category',
    wxmlMust: [/cat-sidebar/, /cat-grid/],
    dataMust: ['categories', 'products'],
  },
  {
    name: 'mp-03-product-detail',
    url: '/pages-sub/product/product-detail/product-detail?id=6a2f097fcb28035db83d88b3',
    wxmlMust: [/detail-info/, /detail-stepper/, /detail-footer__btn--buy/],
    dataMust: ['product'],
    dataExact: [{ path: 'product.name', equals: '三文鱼' }],
    fromBackend: { path: 'product', fields: ['name', 'price', 'stock'] },
  },
  {
    name: 'mp-04-cart',
    tab: '/pages/cart/cart',
    storage: { userInfo: { openId: 'dev-mock', nickName: '视觉验收' }, token: 'dev-mock-jwt' },
    wxmlMust: [/cart-container|cart-empty|cart-content/],
    dataMust: ['cartItems'],
  },
  {
    name: 'mp-08-order-list',
    url: '/pages-sub/order/order-list/order-list',
    storage: { userInfo: { openId: 'dev-mock', nickName: '视觉验收' }, token: 'dev-mock-jwt' },
    wxmlMust: [/order-list/],
    dataMust: ['orders', 'tabs'],
  },
  {
    name: 'mp-06-order-confirm',
    url: '/pages-sub/order/order-confirm/order-confirm',
    storage: { userInfo: { openId: 'dev-mock', nickName: '视觉验收' }, token: 'dev-mock-jwt' },
    wxmlMust: [/order-confirm-container/],
  },
  {
    name: 'mp-07-address-list',
    url: '/pages-sub/user/address/address-list',
    storage: {
      userInfo: { id: 'dev-user-001', openId: 'dev-mock', nickName: '视觉验收' },
      token: 'dev-mock-jwt',
    },
    wxmlMust: [/address-list-container/, /address-list/],
    dataMust: ['addresses', 'selectMode'],
    fromBackend: { path: 'addresses.0', fields: ['name', 'phone', 'detail', 'isDefault'] },
  },
  // ⚠️ mp-05 / mp-09 订单详情 — v2.1 后由独立 TDD test 覆盖
  //   (mp-od-design.test.ts 含 mp-09 spec;mp-05 spec 待 OD 设计稿)
];

/** connect + 重试 3 次 — 见 stability.md 模板 A */
async function connectWithRetry() {
  let lastErr;
  for (let i = 0; i < RETRY; i++) {
    try {
      return await automator.connect({ wsEndpoint: WS });
    } catch (e: any) {
      lastErr = e;
      console.warn(`[connect] retry ${i + 1}/${RETRY} failed: ${e.message?.slice(0, 100)}`);
      await new Promise((r) => setTimeout(r, RETRY_DELAY_MS));
    }
  }
  throw lastErr;
}

function getByPath(obj: any, p: string): any {
  return p.split('.').reduce((acc, k) => (acc == null ? acc : acc[k]), obj);
}

/** 失败隔离:某个 spec 抛错(WS stall / timeout / Mock 失败)只让单 spec fail,不影响其他 */
function describeOrSkip(name: string, fn: () => void) {
  describe(name, fn);
}

describeOrSkip('mp 3-layer 视觉验证 (每 spec 独立连接 + 失败隔离)', () => {
  describe.each(PAGES)('$name', (spec: PageSpec) => {
    let mp: any;
    let page: any;
    let consoleErrs: string[] = [];
    let exceptions: string[] = [];

    beforeEach(async () => {
      mp = await connectWithRetry();
      // listeners 在 beforeEach 内 attach,旧的随旧连接被 close
      consoleErrs = [];
      exceptions = [];
      mp.on('console', (m: any) => {
        if (m.type === 'error' || m.type === 'warn') {
          consoleErrs.push(`[${m.type}] ${m.message}`);
        }
      });
      mp.on('exception', (e: any) => exceptions.push(e.message || String(e)));
      if (spec.storage) {
        await mp.evaluate((s: any) => {
          for (const [k, v] of Object.entries(s)) wx.setStorageSync(k, v);
        }, spec.storage);
      }
      if (spec.tab) {
        await mp.switchTab(spec.tab);
      } else {
        await mp.navigateTo(spec.url!);
      }
      // 4s 等首屏 + 数据加载
      await new Promise((r) => setTimeout(r, 4000));
      page = await mp.currentPage();
    }, 60000);

    afterEach(async () => {
      if (mp) {
        try { await mp.close(); } catch { /* ignore */ }
        mp = null;
      }
    });

    it('L1 结构:outerWxml 包含期望节点 / 文案', async () => {
      const wxml = await page.outerWxml();
      for (const pat of spec.wxmlMust) {
        expect(wxml).toMatch(pat);
      }
    }, 30000);

    it('L2 数据:page.data 包含期望字段 + 来自后端', async () => {
      const data = await page.data();
      for (const p of spec.dataMust || []) {
        expect(getByPath(data, p)).toBeDefined();
      }
      for (const { path, equals } of spec.dataExact || []) {
        expect(getByPath(data, path)).toEqual(equals);
      }
      if (spec.fromBackend) {
        const v = getByPath(data, spec.fromBackend.path);
        expect(v).toBeTruthy();
        for (const f of spec.fromBackend.fields) {
          expect(v[f]).toBeDefined();
        }
      }
    }, 30000);

    it('L3 行为:无 exception + 无 console error/warning', async () => {
      if (consoleErrs.length) {
        console.log(`[${spec.name}] console issues:\n  ${consoleErrs.join('\n  ')}`);
      }
      // 用户显式要求:console 不应抛任何 error / warning
      expect(exceptions).toEqual([]);
      expect(consoleErrs).toEqual([]);
    }, 30000);
  });
});
