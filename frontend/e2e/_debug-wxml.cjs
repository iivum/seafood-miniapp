/**
 * Debug wxml — get home page full wxml + check chips/banner state
 */
const automator = require('miniprogram-automator');

(async () => {
  let mp;
  try {
    mp = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
    await mp.switchTab('/pages/index/index');
    await new Promise(r => setTimeout(r, 5000));
    const page = await mp.currentPage();
    const data = await page.data();
    console.log('--- categories ---', JSON.stringify(data.categories));
    console.log('--- banners field ---', data.banners);
    console.log('--- products.length ---', (data.products||[]).length);
    const wxml = await page.outerWxml();
    console.log('--- wxml first 3000 chars ---');
    console.log(wxml.slice(0, 3000));
  } catch (e) {
    console.error('ERR', e.message);
  } finally {
    if (mp) { try { await mp.close(); } catch {} }
  }
})();
