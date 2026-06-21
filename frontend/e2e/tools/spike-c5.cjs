// C5 spike — 几何 rect + 截图链路,每步超时定位(tasks §1.2)
const automator = require('miniprogram-automator');
const path = require('node:path');
const fs = require('node:fs');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const OUT = path.resolve(__dirname, '../screenshots/mp-01-home-spike.png');
const LOG = path.resolve(__dirname, '../screenshots/spike-c5.log');
function log(m) { fs.appendFileSync(LOG, `${Date.now()} ${m}\n`); }
const race = (p, ms, label) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('TIMEOUT@' + label + ' ' + ms + 'ms')), ms))]);

(async () => {
  fs.writeFileSync(LOG, '');
  log('connecting ' + WS);
  const mp = await race(automator.connect({ wsEndpoint: WS }), 20000, 'connect');
  log('connected');
  const page = await race(mp.switchTab('/pages/index/index'), 15000, 'switchTab');
  log('switchTab ok');
  await new Promise((r) => setTimeout(r, 1500));
  for (const sel of ['.home-banner', '.home-chips']) {
    try {
      const el = await race(page.$(sel), 8000, 'query ' + sel);
      if (!el) { log(sel + ' NOT FOUND'); continue; }
      const size = await race(el.size(), 8000, 'size ' + sel);
      const offset = await race(el.offset(), 8000, 'offset ' + sel);
      log(`${sel} offset=${JSON.stringify(offset)} size=${JSON.stringify(size)}`);
    } catch (e) { log(sel + ' ERR ' + e.message); }
  }
  await race(mp.screenshot({ path: OUT }), 15000, 'screenshot');
  log('screenshot -> ' + OUT);
  await mp.disconnect();
  log('done');
})().catch((e) => { log('FAILED ' + (e && e.message)); process.exit(1); });
