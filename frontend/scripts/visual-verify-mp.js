/**
 * mp 端视觉验收脚本(简化版):launch DevTools,逐个页面跳转+截图。
 * 关键改动:
 *  - 不用 mockWxMethod(此前 mock getStorage 返 null 触发页面逻辑死循环)
 *  - tabBar 页用 switchTab;非 tabBar 用 reLaunch 后 catch 改 navigateTo
 *  - 每页超时保护 8s 防止单点卡死
 */
const automator = require('miniprogram-automator');
const fs = require('node:fs');
const path = require('node:path');

const OUT_DIR = path.join(__dirname, '..', 'e2e', 'screenshots');
fs.mkdirSync(OUT_DIR, { recursive: true });

const PRODUCT_ID = '6a2f097fcb28035db83d88b3';

const PAGES = [
  { name: 'mp-01-home', nav: 'switchTab', target: '/pages/index/index' },
  { name: 'mp-02-category', nav: 'switchTab', target: '/pages/category/category' },
  { name: 'mp-04-cart', nav: 'switchTab', target: '/pages/cart/cart' },
  { name: 'mp-03-product-detail', nav: 'navigateTo', target: `/pages-sub/product/product-detail/product-detail?id=${PRODUCT_ID}` },
  { name: 'mp-08-order-list', nav: 'navigateTo', target: '/pages-sub/order/order-list/order-list' },
  { name: 'mp-06-order-confirm', nav: 'navigateTo', target: '/pages-sub/order/order-confirm/order-confirm' },
];

async function withTimeout(promise, ms, label) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error(`${label} timeout ${ms}ms`)), ms)),
  ]);
}

async function main() {
  console.log('[visual] connecting to DevTools ws://127.0.0.1:9420 ...');
  const miniProgram = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
  console.log('[visual] connected');

  for (const p of PAGES) {
    console.log(`[visual] → ${p.target} (${p.nav})`);
    try {
      const navPromise = p.nav === 'switchTab'
        ? miniProgram.switchTab(p.target)
        : miniProgram.navigateTo(p.target);
      await withTimeout(navPromise, 8000, `nav ${p.name}`);
    } catch (e) {
      console.log(`[visual] nav error: ${e.message} — try reLaunch fallback`);
      try {
        await withTimeout(miniProgram.reLaunch(p.target), 8000, `reLaunch ${p.name}`);
      } catch (e2) {
        console.log(`[visual] reLaunch also failed: ${e2.message} — skip`);
        continue;
      }
    }
    await withTimeout(new Promise((r) => setTimeout(r, 4000)), 5000, 'wait');
    const outFile = path.join(OUT_DIR, `${p.name}-actual.png`);
    try {
      await withTimeout(miniProgram.screenshot({ path: outFile, type: 'png' }), 5000, 'screenshot');
      const stat = fs.existsSync(outFile) ? fs.statSync(outFile).size : 0;
      console.log(`[visual]   ${p.name} → ${outFile} (${stat} bytes)`);
    } catch (e) {
      console.log(`[visual]   screenshot failed: ${e.message}`);
    }
  }

  miniProgram.disconnect();
  console.log('[visual] disconnected from DevTools');
}

main().catch((e) => {
  console.error('[visual] FAILED:', e);
  process.exit(1);
});
