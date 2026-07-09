#!/usr/bin/env bash
# preflight.sh — mp DevTools 自动化前置就绪检查(供 mp-e2e-expert agent 和人类手动使用)。
#
# 背景:run-visual.sh 面向"一键跑视觉测试全流程"(起 DevTools 端口 + Mongo + seed + 后端 + 跑测),
# 本脚本只做更轻量的一件事 —— 在任意 mp 自动化操作(page_*/element_*/mp_screenshot 等)前,
# 检查 + 自动修复 mp 侧依赖是否就绪。2026-07 实测新建 git worktree 时缺 miniprogram_npm 目录,
# 导致所有 @vant/weapp 组件解析失败,且报错只出现在微信 DevTools 自身进程日志里(mp_getLogs 拿不到),
# 表现为截图/自动化操作"看似成功但界面空白/组件不渲染" —— 这种坑必须在自动化前挡住,而不是事后debug。
#
# 用法:
#   ./preflight.sh                    # 检查 miniprogram_npm + DevTools 端口;后端未就绪只 warn
#   ./preflight.sh --require-backend  # 后端未就绪时也 die(退出非 0)
set -uo pipefail

# 绕开系统代理(clash 等)对 localhost 的拦截
export NO_PROXY="localhost,127.0.0.1,*"
export no_proxy="localhost,127.0.0.1,*"

# 路径锚定(脚本在 frontend/e2e/tools/,与 run-visual.sh 同款推导)
TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "$TOOLS_DIR/../.." && pwd)"

DEVTOOLS_CLI="/Applications/wechatwebdevtools.app/Contents/MacOS/cli"
AUTO_PORT=9420
API="http://127.0.0.1:8080/api/products?page=0&size=1"

say()  { printf '\033[1;36m[preflight]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[preflight]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[preflight] %s\033[0m\n' "$*" >&2; exit 1; }

REQUIRE_BACKEND=0
[ "${1:-}" = "--require-backend" ] && REQUIRE_BACKEND=1

# ---------- ① miniprogram_npm ----------
if [ -d "$FRONTEND_DIR/miniprogram_npm" ]; then
  say "miniprogram_npm 已存在 ✓"
  NPM_OK=1
else
  warn "miniprogram_npm 不存在(新 worktree 常见坑,@vant/weapp 会解析失败),尝试 build-npm …"
  [ -d "$FRONTEND_DIR/node_modules" ] || die "node_modules 缺失:先 cd frontend && npm install,再重跑"
  [ -x "$DEVTOOLS_CLI" ] || die "微信 DevTools cli 不存在:$DEVTOOLS_CLI(需先装 + 登录)"
  "$DEVTOOLS_CLI" build-npm --project "$FRONTEND_DIR" \
    || die "build-npm 失败(DevTools 未登录?项目未导入?)"
  [ -d "$FRONTEND_DIR/miniprogram_npm" ] || die "build-npm 执行后 miniprogram_npm 仍不存在,需人工排查"
  say "build-npm 完成,miniprogram_npm 已生成 ✓"
  NPM_OK=1
fi

# ---------- ② DevTools 自动化端口 ----------
if lsof -nP -iTCP:$AUTO_PORT -sTCP:LISTEN >/dev/null 2>&1; then
  say "DevTools 自动化端口 $AUTO_PORT 已在监听 ✓"
  PORT_OK=1

  # 项目归属校验:多 worktree 下,端口可能挂在别的代码树上(别的 worktree/项目先起了
  # DevTools 自动化端口),此时截图/evaluate 全部命中错误项目,产出假信号却不报错。
  PORT_PID="$(lsof -nP -iTCP:$AUTO_PORT -sTCP:LISTEN -t 2>/dev/null | head -n1)"
  if [ -z "$PORT_PID" ]; then
    warn "取不到监听 $AUTO_PORT 的进程 PID,跳过项目归属校验"
  else
    PORT_CMD="$(ps -p "$PORT_PID" -o command= 2>/dev/null)"
    case "$PORT_CMD" in
      *"$FRONTEND_DIR"*|*--project*) : ;; # 已有可用信号,不需要 pgrep 兜底
      *)
        # 监听端口的常是某个 renderer/helper 子进程,命令行里没有 --project;
        # 退而查同名相关进程(通常是携带 --project 的主进程)补充信号。
        PGREP_OUT="$(pgrep -fl wechatwebdevtools 2>/dev/null)"
        [ -n "$PGREP_OUT" ] && PORT_CMD="${PORT_CMD}"$'\n'"${PGREP_OUT}"
        ;;
    esac
    case "$PORT_CMD" in
      *"$FRONTEND_DIR"*)
        say "端口 $AUTO_PORT 归属校验:相关进程命令行含本 worktree 路径 ✓"
        ;;
      *--project*)
        die "端口 $AUTO_PORT 疑似被其他项目占用(相关进程命令行含 --project 但不含本 worktree 路径)。先 \"$DEVTOOLS_CLI\" quit 再重跑本脚本(会对本 worktree 重起端口)"
        ;;
      *)
        warn "无法确认 $AUTO_PORT 挂载项目是否为本 worktree。若后续截图内容与预期不符,先 \"$DEVTOOLS_CLI\" quit 重启自动化端口"
        ;;
    esac
  fi
else
  [ -x "$DEVTOOLS_CLI" ] || die "微信 DevTools cli 不存在:$DEVTOOLS_CLI(需先装 + 登录)"
  say "起 DevTools 自动化端口 $AUTO_PORT …"
  "$DEVTOOLS_CLI" auto --project "$FRONTEND_DIR" --auto-port $AUTO_PORT >/dev/null 2>&1 &
  for i in $(seq 1 30); do
    lsof -nP -iTCP:$AUTO_PORT -sTCP:LISTEN >/dev/null 2>&1 && break
    sleep 1
  done
  lsof -nP -iTCP:$AUTO_PORT -sTCP:LISTEN >/dev/null 2>&1 \
    || die "$AUTO_PORT 30s 内未监听(DevTools 未登录?项目未导入?)"
  say "DevTools 自动化端口就绪 ✓"
  PORT_OK=1
fi

# ---------- ③ 后端(默认仅 warn,--require-backend 时 die) ----------
if curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$API" 2>/dev/null | grep -qx 200; then
  say "后端 /api/products 已 200 ✓"
  BACKEND_OK=1
else
  BACKEND_OK=0
  if [ "$REQUIRE_BACKEND" = "1" ]; then
    die "后端未就绪:视觉验证需真数据,可执行 bash frontend/e2e/tools/run-visual.sh 自动起后端+seed"
  else
    warn "后端未就绪:视觉验证需真数据,可执行 bash frontend/e2e/tools/run-visual.sh 自动起后端+seed"
  fi
fi

# ---------- 汇总 ----------
echo
say "汇总:miniprogram_npm=${NPM_OK:-0} DevTools端口=${PORT_OK:-0} 后端=${BACKEND_OK:-0}"
if [ "${NPM_OK:-0}" = "1" ] && [ "${PORT_OK:-0}" = "1" ]; then
  say "mp 侧自动化前置就绪 ✓"
  exit 0
else
  die "mp 侧自动化前置未就绪"
fi
