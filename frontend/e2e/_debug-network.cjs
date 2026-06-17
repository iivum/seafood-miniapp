/**
 * Debug network — capture wx.request + check products load
 */
const automator = require('miniprogram-automator');

(async () => {
  let mp;
  try {
    mp = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
    const consoleLogs = [];
    const consoleErrs = [];
    const exceptions = [];
    mp.on('console', m => {
      const t = m.type;
      const msg = (m.message || '').slice(0, 200);
      if (t === 'log') consoleLogs.push(msg);
      else if (t === 'error' || t === 'warn') consoleErrs.push('[' + t + '] ' + msg);
    });
    mp.on('exception', e => exceptions.push((e.message || String(e)).slice(0, 200)));

    await mp.switchTab('/pages/index/index');
    await new Promise(r => setTimeout(r, 6000));
    const page = await mp.currentPage();
    const data = await page.data();
    console.log('--- consoleLogs (last 15) ---');
    consoleLogs.slice(-15).forEach(l => console.log('LOG:', l));
    console.log('--- consoleErrs ---');
    consoleErrs.slice(0, 10).forEach(l => console.log(l));
    console.log('--- exceptions ---');
    exceptions.slice(0, 10).forEach(l => console.log(l));
    console.log('--- data dump ---');
    console.log('products.length:', (data.products || []).length);
    console.log('isLoading:', data.isLoading, 'isError:', data.isError, 'isEmpty:', data.isEmpty);
    console.log('errorMessage:', JSON.stringify(data.errorMessage));
  } catch (e) {
    console.error('ERR', e.message);
  } finally {
    if (mp) { try { await mp.close(); } catch {} }
  }
})();
