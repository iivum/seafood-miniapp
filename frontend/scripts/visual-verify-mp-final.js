// 最后冲刺:用 currentPage() 监控 + 短 wait 跑 mp-04 cart / mp-08 order-list
const automator = require('miniprogram-automator');
const fs = require('node:fs');
const path = require('node:path');

const OUT_DIR = path.join(__dirname, '..', 'e2e', 'screenshots');

async function shot(miniProgram, name) {
  const out = path.join(OUT_DIR, `${name}-actual.png`);
  await miniProgram.screenshot({ path: out, type: 'png' });
  return { out, size: fs.statSync(out).size };
}

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
  console.log('[final] connected, currentPage:', (await miniProgram.currentPage())?.path);

  await miniProgram.evaluate(() => {
    wx.setStorageSync('userInfo', { openId: 'dev-mock', nickName: '视觉验收' });
    wx.setStorageSync('token', 'dev-mock-jwt');
    wx.setStorageSync('cart', []);
  });

  // mp-04 cart:tabBar page,switchTab
  try {
    await miniProgram.switchTab('/pages/cart/cart');
    await new Promise((r) => setTimeout(r, 2500));
    const cp = await miniProgram.currentPage();
    console.log('[final] cart currentPage:', cp?.path);
    const { out, size } = await shot(miniProgram, 'mp-04-cart');
    console.log(`[final] cart → ${out} (${size} bytes)`);
  } catch (e) {
    console.log('[final] cart failed:', e.message);
  }

  // mp-08 order-list: 非 tabBar
  try {
    await miniProgram.navigateTo('/pages-sub/order/order-list/order-list');
    await new Promise((r) => setTimeout(r, 3500));
    const cp = await miniProgram.currentPage();
    console.log('[final] order-list currentPage:', cp?.path);
    const { out, size } = await shot(miniProgram, 'mp-08-order-list');
    console.log(`[final] order-list → ${out} (${size} bytes)`);
  } catch (e) {
    console.log('[final] order-list failed:', e.message);
  }

  miniProgram.disconnect();
  console.log('[final] done');
}

main().catch((e) => { console.error(e); process.exit(1); });
