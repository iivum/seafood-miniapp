// 补 cart + order-list(visual-verify-mp-extra 漏掉的 2 张)
// 关键:cart 是 tabBar → 必须 switchTab;order-list 重启后 navigateTo
const automator = require('miniprogram-automator');
const fs = require('node:fs');
const path = require('node:path');

const OUT_DIR = path.join(__dirname, '..', 'e2e', 'screenshots');

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
  console.log('[extra2] connected');

  await miniProgram.evaluate(() => {
    wx.setStorageSync('userInfo', { openId: 'dev-mock', nickName: '视觉验收' });
    wx.setStorageSync('token', 'dev-mock-jwt');
    // 预填空 cart,避免 onShow 拉真实 cart 时阻塞
    wx.setStorageSync('cart', []);
  });

  // 1. cart (tabBar)
  try {
    await miniProgram.switchTab('/pages/cart/cart');
    await new Promise((r) => setTimeout(r, 4000));
    const out = path.join(OUT_DIR, 'mp-04-cart-actual.png');
    await miniProgram.screenshot({ path: out, type: 'png' });
    const stat = fs.statSync(out).size;
    console.log(`[extra2] mp-04-cart → ${out} (${stat} bytes)`);
  } catch (e) {
    console.log('[extra2] cart failed:', e.message);
  }

  // 2. order-list (非 tabBar,需 navigateTo)
  try {
    await miniProgram.navigateTo('/pages-sub/order/order-list/order-list');
    await new Promise((r) => setTimeout(r, 4000));
    const out = path.join(OUT_DIR, 'mp-08-order-list-actual.png');
    await miniProgram.screenshot({ path: out, type: 'png' });
    const stat = fs.statSync(out).size;
    console.log(`[extra2] mp-08-order-list → ${out} (${stat} bytes)`);
  } catch (e) {
    console.log('[extra2] order-list failed:', e.message);
  }

  miniProgram.disconnect();
  console.log('[extra2] done');
}

main().catch((e) => { console.error(e); process.exit(1); });
