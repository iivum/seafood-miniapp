/*
 * mp DevTools console 实时监控 —— 常驻订阅 warn/error/exception,逐行输出到 stdout。
 *
 * 背景(mp-od-prototype-alignment,2026-07):此前排查 mp 端 bug 靠手动调 weapp-dev
 * MCP 的 mp_getLogs 轮询,容易漏掉检查点之间发生的异常。miniprogram-automator 内部
 * 其实已经把 DevTools 协议的 App.logAdded / App.exceptionThrown 桥接成了
 * mp.on('console', ...) / mp.on('exception', ...) 两个 EventEmitter 事件(见
 * node_modules/miniprogram-automator/out/MiniProgram.js 的 onLogAdded/onExceptionThrown),
 * 本脚本直接订阅这两个事件,配合 Claude Code 的 Monitor 工具(或任意"逐行 tail"式监控)
 * 做到"小程序端一报错就立刻知道",不用再手动查。
 *
 * 用法:
 *   1) 先起 DevTools 自动化端口(同 test:visual/test:geometry 前置):
 *      /Applications/wechatwebdevtools.app/Contents/MacOS/cli auto --project frontend --auto-port 9420
 *   2) cd frontend && npm run watch:console
 *      (或直接 node e2e/tools/console-watch.cjs)
 *   3) 常驻运行,warn/error/exception 各占一行输出到 stdout,Ctrl-C 退出。
 *      默认过滤掉 console.log(噪音太大);要看全部级别用 CONSOLE_WATCH_ALL=1。
 *
 * 输出格式(每行一个事件,方便 grep / Monitor 逐行消费):
 *   [mp-warn] <args...>
 *   [mp-error] <args...>
 *   [mp-exception] <JSON>
 *
 * 环境变量:
 *   WS_ENDPOINT       DevTools 自动化 WebSocket 端点,默认 ws://127.0.0.1:9420(同其余 e2e 脚本)
 *   CONSOLE_WATCH_ALL 设为 1 时连 console.log 也输出(默认只输出 warn/error + exception)
 */
const automator = require('miniprogram-automator');

const WS = process.env.WS_ENDPOINT || 'ws://127.0.0.1:9420';
const WATCH_ALL = process.env.CONSOLE_WATCH_ALL === '1';

function fmtArgs(args) {
  return (args || []).map((a) => (typeof a === 'string' ? a : JSON.stringify(a))).join(' ');
}

async function connectAndWatch() {
  const mp = await automator.connect({ wsEndpoint: WS });
  console.log(`[watch] connected to ${WS},watching ${WATCH_ALL ? 'all console levels' : 'warn/error'} + exceptions`);

  mp.on('console', (msg) => {
    if (!msg) return;
    if (!WATCH_ALL && msg.type !== 'warn' && msg.type !== 'error') return;
    console.log(`[mp-${msg.type}]`, fmtArgs(msg.args));
  });

  mp.on('exception', (err) => {
    console.log('[mp-exception]', JSON.stringify(err));
  });

  // 已知限制:miniprogram-automator 的 MiniProgram 类只 emit 'console'/'exception'
  // 两个事件(已读源码确认,不存在 'close'/'disconnect' 事件可订阅)。DevTools 自动化
  // 端口长时间运行后偶尔会进异常状态(实测:跑几十次截图后 screenshot 请求开始报
  // "fail to capture screenshot",但底层连接本身不会主动通知关闭)。本脚本无法自动
  // 探测这种情况,如果长时间没有任何输出但明确知道 mp 端有操作发生,先确认自动化
  // 端口本身是否还健康(重启 cli auto 进程),再重启本脚本。
}

connectAndWatch().catch((e) => {
  console.log('[watch-fatal]', e.message);
  process.exit(1);
});
