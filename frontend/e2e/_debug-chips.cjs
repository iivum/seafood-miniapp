/**
 * Debug chips — find home-chip element via class
 */
const automator = require('miniprogram-automator');

(async () => {
  let mp;
  try {
    mp = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
    await mp.switchTab('/pages/index/index');
    await new Promise(r => setTimeout(r, 5000));
    const page = await mp.currentPage();

    // query home-chip elements
    const chips = await page.$$('.home-chip');
    console.log('--- home-chip count:', chips.length);
    if (chips.length > 0) {
      for (let i = 0; i < Math.min(chips.length, 5); i++) {
        const c = chips[i];
        const size = await c.size();
        const text = await c.text();
        const attr = await c.attribute('class');
        console.log(`  chip[${i}]: size=${JSON.stringify(size)} text=${JSON.stringify(text)} class=${attr}`);
      }
    }

    // query scroll-view
    const sv = await page.$('.home-chips');
    if (sv) {
      const svSize = await sv.size();
      console.log('--- home-chips size:', JSON.stringify(svSize));
    } else {
      console.log('--- home-chips NOT FOUND');
    }

    // check swiper
    const sw = await page.$('.home-banner');
    console.log('--- home-banner:', sw ? 'present' : 'MISSING');
  } catch (e) {
    console.error('ERR', e.message);
  } finally {
    if (mp) { try { await mp.close(); } catch {} }
  }
})();
