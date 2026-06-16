/**
 * v2.1 visual-verify — 逐屏截图,每屏独立 connect,避开 describe.each stall
 * 用法:TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 node /tmp/visual-verify-v2.1.mjs
 */
const automator = require('miniprogram-automator');
const path = require('node:path');
const fs = require('node:fs');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const OUT = '/Users/linbinghui/agent-work/seafood-miniapp/frontend/e2e/screenshots';
fs.mkdirSync(OUT, { recursive: true });

const PAGES = [
  { name: 'mp-01-home', tab: '/pages/index/index', storage: undefined },
  { name: 'mp-07-address-list', url: '/pages-sub/user/address/address-list',
    storage: { userInfo: { id: 'dev-user-001', openId: 'dev-mock', nickName: '视觉验收' }, token: 'dev-mock-jwt' } },
  { name: 'mp-08-order-list', url: '/pages-sub/order/order-list/order-list',
    storage: { userInfo: { openId: 'dev-mock', nickName: '视觉验收' }, token: 'dev-mock-jwt' } },
];

(async () => {
  let miniProgram;
  const log = [];
  for (const p of PAGES) {
    try {
      miniProgram = await automator.connect({ wsEndpoint: WS });
      const page = await miniProgram.currentPage();
      if (p.storage) {
        await miniProgram.evaluate((s) => {
          for (const [k, v] of Object.entries(s)) wx.setStorageSync(k, v);
        }, p.storage);
      }
      if (p.tab) await miniProgram.switchTab(p.tab);
      else await miniProgram.navigateTo(p.url);
      await new Promise(r => setTimeout(r, 3500));
      const cur = await miniProgram.currentPage();
      const file = path.join(OUT, `${p.name}-v2.1.png`);
      // MiniProgram.screenshot() 截整屏(不是 page 级 API)
      await miniProgram.screenshot({ path: file });
      const data = await cur.data();
      log.push({ name: p.name, file, dataKeys: Object.keys(data).slice(0, 8) });
      console.log(`✓ ${p.name} → ${file}`);
    } catch (e) {
      log.push({ name: p.name, error: e.message.slice(0, 120) });
      console.log(`✗ ${p.name}: ${e.message.slice(0, 120)}`);
    } finally {
      if (miniProgram) { try { await miniProgram.disconnect(); } catch {} }
    }
  }
  console.log('\n--- summary ---');
  console.log(JSON.stringify(log, null, 2));
})();
