/*
 * mp DevTools 进程日志（编译期错误）实时监控 —— tail 微信开发者工具自身进程的
 * stderr.log（可选 stdout.log），逐行输出到 stdout，前缀 [weapp-stderr]/[weapp-stdout]。
 *
 * 背景:mp_getLogs / console-watch.cjs 桥接的是 miniprogram-automator SDK 的运行时事件——
 * DevTools 协议的 App.logAdded / App.exceptionThrown，对应的是小程序 JS 运行时里的
 * console.* 调用和未捕获异常。但**编译期 / 组件解析错误**(典型如 miniprogram_npm 缺失
 * 导致「路径下未找到组件」)根本不经过小程序运行时，只会写进 DevTools 自身进程
 * (不是被调试的小程序进程)的日志文件:
 *   ~/Library/Application Support/微信开发者工具/<hash>/WeappLog/stderr.log
 * 这是 console-watch.cjs 完全够不到的盲区。本工具直接 tail 这个文件，与
 * console-watch.cjs 并列武装，配 Claude Code 的 Monitor 工具做到"编译期错误一出现
 * 立刻可见"，不用再手动去 Finder 里翻日志文件。
 *
 * 用法:
 *   node e2e/tools/devtools-log-watch.cjs
 *   WEAPP_WATCH_STDOUT=1 node e2e/tools/devtools-log-watch.cjs   # 连 stdout.log 也 tail
 *   WEAPP_LOG_DIR=/path/to/WeappLog node e2e/tools/devtools-log-watch.cjs  # 显式指定目录
 *
 * 输出格式(每行一个事件，方便 grep / Monitor 逐行消费):
 *   [weapp-stderr] <日志原文一行>
 *   [weapp-stdout] <日志原文一行>
 *
 * 环境变量:
 *   WEAPP_LOG_DIR      显式指定 WeappLog 目录(跳过自动扫描)，多项目/CI 场景用
 *   WEAPP_WATCH_STDOUT 设为 1 时额外并行 tail 同目录 stdout.log(默认只 tail stderr.log)
 *
 * 实现取舍:
 *   - 只用 Node 标准库(fs/path/os)，不引入 glob 之类第三方依赖——这是一次性诊断脚本，
 *     没必要为扫一个目录加依赖。
 *   - stderr.log 实测可达 2.8MB，严禁 fs.readFileSync 全量读取再 diff——用 fs.stat 轮询
 *     文件大小 + fs.read 指定 position 只读增量字节，常驻监控不会随时间累积开销。
 *   - 文件大小变小(DevTools 重启/日志轮转/被清空)时把 offset 归零重新从头 tail，
 *     否则后续按旧 offset 读会一直越界读到空内容。
 *   - 每个 64KB 读块必须经同一个 StringDecoder('utf8') 实例解码，不能各读块独立
 *     buf.toString('utf8')——多字节字符(如中文日志行)若恰好落在块边界上会被切成
 *     两半，独立解码每一半都会产生 U+FFFD 乱码(已实测复现)。StringDecoder 内部
 *     缓冲跨块的半个字符，等下一块补齐字节后再吐出完整字符，因此该实例必须
 *     跨读取、跨轮询存活；文件截断/轮转导致 offset 归零时要同步换新实例，
 *     丢弃其内部缓冲的半字符残留，否则会把跨轮转的残留字节拼接到新内容前面。
 */
const fs = require('fs');
const path = require('path');
const os = require('os');
const { StringDecoder } = require('string_decoder');

const POLL_INTERVAL_MS = 500;
const READ_CHUNK_SIZE = 64 * 1024;

function fail(message) {
  console.error(`[watch-fatal] ${message}`);
  process.exit(1);
}

// 扫描 os.homedir()/Library/Application Support/微信开发者工具/*/WeappLog，
// 取其中 stderr.log mtime 最新的目录(不用 glob 库，纯 fs.readdirSync 遍历)。
function findLatestWeappLogDir() {
  const explicit = process.env.WEAPP_LOG_DIR;
  if (explicit) {
    return path.resolve(explicit);
  }

  const base = path.join(os.homedir(), 'Library', 'Application Support', '微信开发者工具');
  let entries;
  try {
    entries = fs.readdirSync(base, { withFileTypes: true });
  } catch (e) {
    fail(
      `找不到微信开发者工具数据目录:${base}\n` +
        `(${e.message})\n` +
        '请确认微信开发者工具已安装且至少启动过一次;也可用 WEAPP_LOG_DIR 显式指定目录。'
    );
  }

  let best = null; // { dir, mtimeMs }
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const weappLogDir = path.join(base, entry.name, 'WeappLog');
    const stderrPath = path.join(weappLogDir, 'stderr.log');
    let stat;
    try {
      stat = fs.statSync(stderrPath);
    } catch (e) {
      continue; // 这个 hash 目录下没有 WeappLog/stderr.log，跳过
    }
    if (!best || stat.mtimeMs > best.mtimeMs) {
      best = { dir: weappLogDir, mtimeMs: stat.mtimeMs };
    }
  }

  if (!best) {
    fail(
      `在 ${base} 下没找到任何 <hash>/WeappLog/stderr.log。\n` +
        '请确认微信开发者工具已安装且至少打开/运行过一次项目(该日志由 DevTools 进程\n' +
        '自身写入，不是被调试的小程序产生的)。也可用 WEAPP_LOG_DIR 显式指定目录跳过自动扫描。'
    );
  }
  return best.dir;
}

// 常驻 tail 一个文件:从当前 EOF 开始，每 POLL_INTERVAL_MS 用 fs.stat 探测大小变化，
// 只读增量字节并按行输出；文件变小(轮转/截断)时 offset 归零重新从头 tail。
function tailFile(filePath, prefix) {
  let offset;
  try {
    offset = fs.statSync(filePath).size;
  } catch (e) {
    fail(`日志文件不存在或不可读:${filePath}\n(${e.message})`);
  }

  console.log(`[watch] tailing ${filePath} from offset ${offset}`);

  const buf = Buffer.alloc(READ_CHUNK_SIZE);
  let pending = ''; // 跨轮询的不完整行(还没遇到 \n)
  // 持久 decoder:跨读取、跨轮询存活，才能接住恰好落在 64KB 块边界上的半个
  // 多字节字符(独立 buf.toString('utf8') 会把每一半都解码出 U+FFFD)。
  let decoder = new StringDecoder('utf8');

  function poll() {
    let stat;
    try {
      stat = fs.statSync(filePath);
    } catch (e) {
      // 文件暂时不可读(例如 DevTools 正在重启),下一轮再试,不终止常驻进程
      return;
    }

    if (stat.size < offset) {
      offset = 0;
      pending = '';
      decoder = new StringDecoder('utf8'); // 换新 decoder，丢弃跨轮转的半字符残留
      console.log(`[watch] ${filePath} 变小(轮转/清空),offset 归零重新 tail`);
    }

    if (stat.size === offset) return; // 没有新内容

    let fd;
    try {
      fd = fs.openSync(filePath, 'r');
    } catch (e) {
      return;
    }
    try {
      let remaining = stat.size - offset;
      let readPos = offset;
      while (remaining > 0) {
        const chunkSize = Math.min(buf.length, remaining);
        const bytesRead = fs.readSync(fd, buf, 0, chunkSize, readPos);
        if (bytesRead <= 0) break;
        pending += decoder.write(buf.subarray(0, bytesRead));
        readPos += bytesRead;
        remaining -= bytesRead;
      }
      offset = readPos;
    } finally {
      fs.closeSync(fd);
    }

    const lines = pending.split('\n');
    pending = lines.pop(); // 最后一段可能是不完整行,留到下一轮拼接
    for (const line of lines) {
      if (line.length === 0) continue;
      console.log(`[${prefix}]`, line);
    }
  }

  setInterval(poll, POLL_INTERVAL_MS);
}

function main() {
  const logDir = findLatestWeappLogDir();
  tailFile(path.join(logDir, 'stderr.log'), 'weapp-stderr');

  if (process.env.WEAPP_WATCH_STDOUT === '1') {
    tailFile(path.join(logDir, 'stdout.log'), 'weapp-stdout');
  }
}

main();
