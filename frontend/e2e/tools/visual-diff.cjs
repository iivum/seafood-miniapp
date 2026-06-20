/*
 * C5 — mp 视觉验证(感知层):mp 实截图 vs OD golden 的 odiff 比对 + 阈值 gate。
 *
 * 前置:
 *   1. 微信 DevTools 自动化端口已起:
 *      /Applications/wechatwebdevtools.app/Contents/MacOS/cli auto --project frontend --auto-port 9420
 *   2. OD golden 已生成并提交(frontend/e2e/od-golden/<screen>.png,见 gen-od-golden 说明)
 *   3. **有意义的逐屏信号需后端起着 + seed**(否则 mp 渲染 loading/空态,diff 必然很大 —— 这本身
 *      也是一种对 OD 的偏离信号,但非"像素级对齐"判断)
 *
 * 跑法:cd frontend && node e2e/tools/visual-diff.cjs            # 全部屏
 *      VISUAL_THRESHOLD=5 node e2e/tools/visual-diff.cjs mp-01-home  # 单屏 + 自定阈值
 *
 * 退出码:任一屏 diff% > 阈值 → 非零(RED,驱动修复);全部 ≤ 阈值 → 0(GREEN,防偏)。
 */
const automator = require('miniprogram-automator');
const { compare } = require('odiff-bin');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const THRESHOLD_PCT = Number(process.env.VISUAL_THRESHOLD || 5);
const SHOTS = path.resolve(__dirname, '../screenshots');
const GOLD = path.resolve(__dirname, '../od-golden');

// 屏清单(参数化,后续扩到 9 屏只加条目)。
// 一律用 reLaunch(path):关闭页栈重开目标页 → onLoad 必触发 → 重新拉后端数据。
// switchTab 到「已激活的 tabBar 页」不会重跑 onLoad,会截到上次的陈旧渲染(空态)。
const SCREENS = [
  { name: 'mp-01-home', path: '/pages/index/index' },
];

const race = (p, ms, l) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('TIMEOUT@' + l)), ms))]);

/** 用 macOS 内置 sips 把 src 缩放到 w×h(归一化到 golden 尺寸,消除 DPR 差)。 */
function normalize(src, w, h, out) {
  execFileSync('sips', ['-z', String(h), String(w), src, '--out', out], { stdio: 'ignore' });
}

function dims(png) {
  const o = execFileSync('sips', ['-g', 'pixelWidth', '-g', 'pixelHeight', png]).toString();
  return {
    w: Number((o.match(/pixelWidth:\s*(\d+)/) || [])[1]),
    h: Number((o.match(/pixelHeight:\s*(\d+)/) || [])[1]),
  };
}

async function captureActual(mp, screen) {
  const out = path.join(SHOTS, `${screen.name}-actual.png`);
  // reLaunch 保证 onLoad 重跑 → 反映当前后端状态(非陈旧空态)。tab/普通页通吃。
  await race(mp.reLaunch(screen.path), 15000, 'reLaunch');
  await new Promise((r) => setTimeout(r, 4000)); // 留足后端往返 + 列表渲染
  await race(mp.screenshot({ path: out }), 15000, 'screenshot');
  return out;
}

(async () => {
  const only = process.argv[2];
  const screens = only ? SCREENS.filter((s) => s.name === only) : SCREENS;
  if (!screens.length) { console.error('no such screen:', only); process.exit(2); }

  const mp = await race(automator.connect({ wsEndpoint: WS }), 20000, 'connect');
  const results = [];
  for (const s of screens) {
    const golden = path.join(GOLD, `${s.name}.png`);
    if (!fs.existsSync(golden)) { results.push({ name: s.name, err: 'golden 缺失:' + golden }); continue; }
    const actual = await captureActual(mp, s);
    const g = dims(golden);
    const norm = path.join(SHOTS, `${s.name}-actual-norm.png`);
    normalize(actual, g.w, g.h, norm); // 归一化到 golden 尺寸
    const diff = path.join(SHOTS, `${s.name}-diff.png`);
    const res = await compare(golden, norm, diff, { antialiasing: true, outputDiffMask: false });
    const pct = res.match ? 0 : Number((res.diffPercentage || 0).toFixed(2));
    results.push({ name: s.name, pct, diff: res.match ? null : diff });
  }
  await mp.disconnect();

  let failed = false;
  console.log(`\n=== C5 视觉验证(感知层,阈值 ${THRESHOLD_PCT}%)===`);
  for (const r of results) {
    if (r.err) { console.log(`  ✖ ${r.name}: ${r.err}`); failed = true; continue; }
    const over = r.pct > THRESHOLD_PCT;
    failed = failed || over;
    console.log(`  ${over ? '✖ RED ' : '✓ GREEN'} ${r.name}: diff ${r.pct}%${over ? `  → ${r.diff}` : ''}`);
  }
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error('FAIL', e && e.message ? e.message : e); process.exit(1); });
