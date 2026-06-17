/**
 * Debug home page — try multiple connect + read state
 */
const automator = require('miniprogram-automator');

async function tryConnect(WS) {
  for (let i = 0; i < 3; i++) {
    try {
      return await automator.connect({ wsEndpoint: WS });
    } catch (e) {
      console.log(`[retry ${i + 1}]`, e.message.slice(0, 80));
      await new Promise(r => setTimeout(r, 1500));
    }
  }
  throw new Error('connect failed');
}

(async () => {
  let mp;
  try {
    mp = await tryConnect('ws://127.0.0.1:9420');
    console.log('[ok] connected');

    const consoleErrs = [];
    const exceptions = [];
    mp.on('console', m => {
      if (m.type === 'error' || m.type === 'warn') {
        consoleErrs.push('[' + m.type + '] ' + (m.message || '').slice(0, 200));
      }
    });
    mp.on('exception', e => exceptions.push((e.message || String(e)).slice(0, 200)));

    // launch a project first if needed
    try {
      await mp.switchTab('/pages/index/index');
    } catch (e) {
      console.log('[switchTab err]', e.message.slice(0, 100));
      // try reLaunch
      await mp.reLaunch('/pages/index/index').catch(e2 => console.log('[reLaunch err]', e2.message.slice(0, 100)));
    }
    await new Promise(r => setTimeout(r, 5000));
    const page = await mp.currentPage();
    const data = await page.data();
    console.log('--- console errors ---');
    consoleErrs.slice(0, 20).forEach(c => console.log(c));
    console.log('--- exceptions ---');
    exceptions.slice(0, 10).forEach(e => console.log(e));
    console.log('--- page.data keys ---');
    console.log(Object.keys(data).join(','));
    console.log('--- products.length ---', (data.products || []).length);
    console.log('--- isLoading / isError / isEmpty ---', data.isLoading, data.isError, data.isEmpty);
    console.log('--- banners field ---', data.banners ? 'present' : 'MISSING');
    console.log('--- errorMessage ---', data.errorMessage);
  } catch (e) {
    console.error('ERR', e.message);
  } finally {
    if (mp) { try { await mp.close(); } catch {} }
  }
})();
