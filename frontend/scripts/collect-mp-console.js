// 收集 mp 页面 console 错误和异常 — 用 MiniProgram.on('console'/'exception') 事件
const automator = require('miniprogram-automator');
const path = require('node:path');

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
  console.log('[console-collect] connected');

  const events = [];
  miniProgram.on('console', (msg) => {
    events.push({ type: 'console', level: msg.type, message: msg.message, time: msg.time });
    console.log(`[console.${msg.type}] ${msg.message}`);
  });
  miniProgram.on('exception', (err) => {
    events.push({ type: 'exception', message: err.message || String(err), stack: err.stack, time: Date.now() });
    console.log(`[exception] ${err.message || err}`);
  });

  // 预填 userInfo
  await miniProgram.evaluate(() => {
    wx.setStorageSync('userInfo', { openId: 'dev-mock', nickName: '视觉验收' });
    wx.setStorageSync('token', 'dev-mock-jwt');
    wx.setStorageSync('cart', []);
  });

  const PAGES = [
    { name: 'mp-01-home', target: '/pages/index/index', nav: 'switchTab' },
    { name: 'mp-02-category', target: '/pages/category/category', nav: 'switchTab' },
    { name: 'mp-04-cart', target: '/pages/cart/cart', nav: 'switchTab' },
    { name: 'mp-03-product-detail', target: '/pages-sub/product/product-detail/product-detail?id=6a2f097fcb28035db83d88b3', nav: 'navigateTo' },
    { name: 'mp-08-order-list', target: '/pages-sub/order/order-list/order-list', nav: 'navigateTo' },
    { name: 'mp-06-order-confirm', target: '/pages-sub/order/order-confirm/order-confirm', nav: 'navigateTo' },
  ];

  for (const p of PAGES) {
    console.log(`\n=== ${p.name} (${p.target}) ===`);
    events.length = 0;
    try {
      const fn = p.nav === 'switchTab' ? miniProgram.switchTab : miniProgram.navigateTo;
      await Promise.race([fn.call(miniProgram, p.target), new Promise((_, r) => setTimeout(() => r(new Error('nav timeout')), 5000))]);
    } catch (e) {
      console.log(`  [nav] ${e.message}`);
      try {
        await Promise.race([miniProgram.reLaunch(p.target), new Promise((_, r) => setTimeout(() => r(new Error('reLaunch timeout')), 5000))]);
      } catch (e2) {
        console.log(`  [reLaunch] ${e2.message}`);
        continue;
      }
    }
    await new Promise((r) => setTimeout(r, 4000));
    console.log(`  → events: ${events.length}`);
    for (const e of events) {
      console.log(`    [${e.type}/${e.level || ''}] ${e.message}`);
    }
  }

  miniProgram.disconnect();
  console.log('\n[console-collect] done');
}

main().catch((e) => { console.error(e); process.exit(1); });
