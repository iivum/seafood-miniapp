// C5 — 捕获 mp 某屏实截图(感知层用)。需 DevTools 自动化端口已起(cli auto --auto-port 9420)。
//   cd frontend && node e2e/tools/capture-mp.cjs <screen> <tabPath>
// 例:node e2e/tools/capture-mp.cjs mp-01-home /pages/index/index
const automator = require('miniprogram-automator');
const path = require('node:path');
const fs = require('node:fs');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const screen = process.argv[2] || 'mp-01-home';
const tab = process.argv[3] || '/pages/index/index';
const OUT = path.resolve(__dirname, `../screenshots/${screen}-actual.png`);
const race = (p, ms, l) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('TIMEOUT@' + l)), ms))]);

(async () => {
  const mp = await race(automator.connect({ wsEndpoint: WS }), 20000, 'connect');
  await race(mp.switchTab(tab), 15000, 'switchTab').catch(() => race(mp.reLaunch(tab), 15000, 'reLaunch'));
  await new Promise((r) => setTimeout(r, 2500)); // 等渲染/数据
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  await race(mp.screenshot({ path: OUT }), 15000, 'screenshot');
  await mp.disconnect();
  process.stdout.write('captured ' + OUT + '\n');
})().catch((e) => { process.stderr.write('FAIL ' + (e && e.message) + '\n'); process.exit(1); });
