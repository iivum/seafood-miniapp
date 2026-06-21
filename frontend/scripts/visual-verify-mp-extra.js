/**
 * 单独跑需登录的 3 个页面:cart / order-list / order-confirm。
 * 用 evaluate() 在 AppService 注入 userInfo / token,绕过跳 login。
 */
const automator = require('miniprogram-automator');
const fs = require('node:fs');
const path = require('node:path');

const OUT_DIR = path.join(__dirname, '..', 'e2e', 'screenshots');
fs.mkdirSync(OUT_DIR, { recursive: true });

const PAGES = [
  { name: 'mp-04-cart', url: '/pages/cart/cart' },
  { name: 'mp-08-order-list', url: '/pages-sub/order/order-list/order-list' },
  { name: 'mp-06-order-confirm', url: '/pages-sub/order/order-confirm/order-confirm' },
];

async function withTimeout(p, ms, label) {
  return Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error(`${label} timeout`)), ms))]);
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
  console.log('[visual-extra] connected');

  // 注入 userInfo/token 绕过 auth 守卫
  await miniProgram.evaluate(() => {
    // 模拟已登录用户(伪数据 — 主要让页面不跳 login)
    wx.setStorageSync('userInfo', { openId: 'dev-mock-openid', nickName: '视觉验收' });
    wx.setStorageSync('token', 'dev-mock-jwt-token');
  });

  for (const p of PAGES) {
    console.log(`[visual-extra] → ${p.url}`);
    try {
      await withTimeout(miniProgram.navigateTo(p.url), 6000, `nav ${p.name}`);
    } catch (e) {
      console.log(`[visual-extra] nav error: ${e.message} — try reLaunch`);
      try {
        await withTimeout(miniProgram.reLaunch(p.url), 6000, `reLaunch ${p.name}`);
      } catch (e2) {
        console.log(`[visual-extra] reLaunch also failed: ${e2.message} — skip`);
        continue;
      }
    }
    await new Promise((r) => setTimeout(r, 3500));
    const outFile = path.join(OUT_DIR, `${p.name}-actual.png`);
    try {
      await miniProgram.screenshot({ path: outFile, type: 'png' });
      const stat = fs.existsSync(outFile) ? fs.statSync(outFile).size : 0;
      console.log(`[visual-extra]   ${p.name} → ${outFile} (${stat} bytes)`);
    } catch (e) {
      console.log(`[visual-extra]   screenshot failed: ${e.message}`);
    }
    // 返回到 home 防止下一轮状态污染
    try { await miniProgram.switchTab('/pages/index/index'); } catch {}
    await new Promise((r) => setTimeout(r, 1000));
  }

  miniProgram.disconnect();
  console.log('[visual-extra] done');
}

main().catch((e) => { console.error('[visual-extra] FAILED:', e); process.exit(1); });
