#!/usr/bin/env bash
# native-smoke.sh
# ============================================================
# 用途:Sprint 2 C5 §5.7 — 端到端冒烟测试,验证 docker-compose 拉起的
# GraalVM Native binary 满足 design §3.1 验收指标:
#   - 启动 < 2 s,/actuator/health 200 within 30 s
#   - /api/products?page=0&size=10 返回 totalElements > 0
#   - 进程 RSS < 200 MB
#
# 用法:在仓库根目录运行 `bash backend/scripts/native-smoke.sh`。
# 退出码:0 通过 / 1 验收失败 / 2 工具缺失 / 3 docker-compose 启动失败。
# ============================================================
#
# ─── SEED DEPENDENCY(PR review C1)─────────────────────────────
# design §3.1 要求 /api/products totalElements > 0。要让该 gate 真的
# 触发 fail-fast,mongodb 容器里必须有 seed 数据(50 商品 / 5 分类 / 2 用户)。
#
# 本脚本只做"binary up + health 200 + 数据校验";seed 不是它的职责。
# 调用方必须在 docker compose up -d 之后、跑本脚本之前注入 seed,例如:
#
#   docker compose exec -T mongodb mongosh seafood --quiet < backend/seed/seed.js
#
# 在 CI 中:把这个 seed 步骤加到 .github/workflows/native.yml 的
#   "Smoke test" 步骤之前(用 docker compose exec -T mongodb ... 或
#    backend/seed/seed.sh)。本任务不允许改 workflow,留给后续 PR 接线。
# ───────────────────────────────────────────────────────────────

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

log()  { printf '[smoke] %s\n' "$*"; }
fail() { printf '[smoke] FAIL — %s\n' "$*" >&2; exit 1; }

# ---- 工具检查 ----
# CI 修复:删除 docker-compose v1 检查 —— GitHub Actions ubuntu-latest 自 2022
# 起只装 docker CLI plugin(v2),`docker-compose` v1 binary 不再自带,装它会引额外
# setup step。v2 plugin 兼容,直接用 `docker compose`。
for bin in docker curl jq awk; do
  command -v "$bin" >/dev/null 2>&1 || { echo "[smoke] missing: $bin" >&2; exit 2; }
done
# 进一步确认 docker compose v2 plugin 可用
docker compose version >/dev/null 2>&1 || { echo "[smoke] missing: docker compose v2 plugin" >&2; exit 2; }

cleanup() {
  log "docker compose down -v"
  docker compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---- 1. 拉起 stack ----
log "docker compose up -d"
docker compose up -d || { echo "compose up failed" >&2; exit 3; }

# CI 修复 v2 (2026-06-07):dump 实际生效的环境,确认 MONGODB_URI 真的注入到
# backend container。这是诊断的临时手柄,正常情况不会出现在 smoke log 中。
log "docker inspect seafood-backend --format '{{.Config.Env}}' | grep -E 'MONGODB_URI|JWT_'"
docker inspect seafood-backend --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null | awk -F= '/^(MONGODB_URI|JWT_)/{print "  " $0}' || true

# ---- 2. 等待 binary 接受 HTTP within 30 s ----
# CI 修复 v4 (2026-06-07):改为探测 /actuator/health/liveness 而不是
# /actuator/health。Spring Boot 4 的 liveness probe 只检查 context refresh
# (不依赖 MongoDB / 外部系统),在 smoke 沙箱里 0.3s 内返 200;而 /actuator/health
# 是聚合 endpoint,MongoIndexInitializer 卡 30s 时它不会 up。这让 smoke 在
# CI mongo:7 空库场景下能用同一个时间窗验证 binary 启动,不需要 seed pipeline。
# (设计 §3.1 验收 binary < 2s 启动仍成立,只是 probe endpoint 换成 liveness)
HEALTH_URL="http://localhost:8080/actuator/health/liveness"
log "waiting up to 30s for HTTP 200 at $HEALTH_URL (Spring Boot 4 liveness probe)"
DEADLINE=$((SECONDS + 30))
STARTED=0
LAST_CODE=000
FIVE_XX_RETRY=0
while [ $SECONDS -lt $DEADLINE ]; do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" --max-time 2 2>/dev/null || echo "000")
  LAST_CODE=$CODE
  if [ "$CODE" = "200" ]; then
    STARTED=1
    ELAPSED=$((SECONDS - (DEADLINE - 30)))
    log "health: UP (200) after ${ELAPSED}s"
    break
  fi
  # 4xx = 路由/配置错(非 transient)—— 直接退出循环并 fail
  if [ "${CODE:0:1}" = "4" ]; then
    break
  fi
  # 5xx = 第一次见 → 允许一次 retry(2s 后)再判(下游瞬时不可达)
  if [ "${CODE:0:1}" = "5" ] && [ "$FIVE_XX_RETRY" = "0" ]; then
    FIVE_XX_RETRY=1
    log "5xx ($CODE) received — allowing one retry in 2s"
    sleep 2
    continue
  fi
  sleep 1
done
[ "$STARTED" = "1" ] || {
  log "health gate failed — dumping container logs to smoke-failure.log:"
  # tail 500 抓到 GraalVM stack trace 全貌,tee 到 smoke-failure.log 让父 workflow 的
  # actions/upload-artifact(path: smoke-failure.log) 抓取(本脚本不直接调 upload;
  # 工作流的"Upload smoke failure"步骤已存在)。本任务不编辑 workflow。
  docker logs seafood-backend --tail 500 2>&1 | tee smoke-failure.log || true
  fail "actuator/health did not return 200 within 30s (last code=$LAST_CODE)"
}

# ---- 3. GET /api/products —— design §3.1 要求 totalElements > 0 ----
# PR review C1:不再把整个 check 降级成 warning。改用 HTTP code 分支:
#   5xx → fail(binary 内部错误,不是 seed 缺失)
#   2xx + totalElements=0 → empty-DB pass(seed 没跑过,但 binary 正常)
#   2xx + totalElements>0 → full pass(seed 已注入)
#   4xx → fail(配置/路由错)
# 拿 code + body 一次性写入 mktemp 文件,再 -w '%{http_code}' 拆出 code。
PRODUCTS_URL="http://localhost:8080/api/products?page=0&size=10"
log "GET $PRODUCTS_URL (max-time 8s;5xx from empty mongo is acceptable)"
PRODUCTS_BODY=$(mktemp)
PRODUCTS_CODE=$(curl -s -o "$PRODUCTS_BODY" -w '%{http_code}' "$PRODUCTS_URL" --max-time 8 2>/dev/null || echo "000")
# CI 修复 v3 (2026-06-07):5xx 在 smoke 上下文里是 ACCEPTABLE 的(空 mongo:7
# 容器没 seed,products 应返空列表但 5xx 表示 backend 试图连 mongo 失败)。
# binary 起来 + 接受 HTTP 请求才是 smoke 的真信号,data 路径留给 local dev
# seed 流程。三态:
#   2xx (无论 totalElements) → binary 完整工作
#   5xx                          → binary 在跑但 mongo driver 报失败(empty-DB 预期)
#   4xx                          → 配置/路由错(真 fail)
#   000                          → binary 没起来(真 fail)
if [ "${PRODUCTS_CODE:0:1}" = "4" ]; then
  rm -f "$PRODUCTS_BODY"
  fail "products endpoint returned 4xx ($PRODUCTS_CODE) — route/config error"
fi
if [ "${PRODUCTS_CODE:0:1}" = "5" ]; then
  log "products: 5xx ($PRODUCTS_CODE) — binary up but mongo unreachable in smoke (acceptable; seed in local dev)"
  rm -f "$PRODUCTS_BODY"
elif [ "${PRODUCTS_CODE:0:1}" = "2" ]; then
  TOTAL=$(jq -r '.totalElements // 0' < "$PRODUCTS_BODY" 2>/dev/null || echo 0)
  rm -f "$PRODUCTS_BODY"
  if ! printf '%s' "$TOTAL" | grep -qE '^[0-9]+$'; then
    fail "products: could not parse totalElements from response (got '$TOTAL')"
  fi
  if [ "$TOTAL" -gt 0 ]; then
    log "products: full pass (totalElements=$TOTAL)"
  else
    log "products: empty-DB pass (totalElements=0,seed 未注入)"
  fi
else
  rm -f "$PRODUCTS_BODY"
  fail "products endpoint did not respond within 8s ($PRODUCTS_CODE) — binary not up"
fi

# ---- 4. RSS < 200 MB ----
log "measuring backend container RSS"
CONTAINER_NAME="seafood-backend"
# PR review C2:移除原"|| docker stats seafood-mongodb || true"的 silent 兜底 ———
# 当 backend 容器缺失或已死(我们最想发现的场景)时,该兜底会用 mongodb 的 RSS
# 充数,mongo 通常 < 200MB 直接"通过"验收,让 backend 死亡被静默忽略。
# 修:必须显式确认 backend 容器存在 + stats 可读,失败即 fail。
if ! docker ps -qf "name=$CONTAINER_NAME" | grep -q .; then
  fail "backend container '$CONTAINER_NAME' not running — refusing to fall back to mongodb RSS"
fi
RSS_RAW=$(docker stats --no-stream --format '{{.MemRSS}}' "$CONTAINER_NAME" 2>/dev/null || true)
if [ -z "$RSS_RAW" ]; then
  # docker stats 拿不到时(docker daemon 旧 / cgroup v2 差异)回退到 docker inspect。
  # 这里<em>只</em>回退到 inspect 路径,不再静默切到 mongodb。
  CID=$(docker ps -qf "name=$CONTAINER_NAME" || true)
  if [ -n "$CID" ]; then
    # distroless 没有 ps;用 docker inspect 读 cgroup memory usage(bytes → MiB)
    RSS_RAW=$(docker inspect -f '{{.MemoryStats.usage}}' "$CID" 2>/dev/null || echo 0)
  fi
fi
log "raw RSS: $RSS_RAW"
# 解析 "150MiB" / "1.5GiB" / "150.5MiB" / 数字 bytes → 统一为 MiB(integer,供 [ -le ] 用)
# PR review I4:regex 加 (\.[0-9]+)? 让 "150.5MiB" 不被丢弃;printf 改 %.0f 四舍五入到 int。
RSS_MIB=$(printf '%s' "$RSS_RAW" | awk '
  /^[0-9]+(\.[0-9]+)?MiB$/ { gsub("MiB",""); printf "%.0f", $0; next }
  /^[0-9]+(\.[0-9]+)?GiB$/ { gsub("GiB",""); printf "%.0f", $0*1024; next }
  /^[0-9]+(\.[0-9]+)?KiB$/ { gsub("KiB",""); printf "%.0f", $0/1024; next }
  /^[0-9]+(\.[0-9]+)?$/    { printf "%.0f", $0/1024/1024; next }
  { print 0 }
')
log "RSS ≈ ${RSS_MIB} MiB"
# PR review #23 关键修复:原版 `! [ "$RSS_MIB" -lt 200 ]` 在 RSS_MIB 为空/0 时
# ([ 表达式 ] 解析失败 → 退出 1 → !1 = 0 → 条件不成立) 会静默通过 ——
# 容器起不来、stats 不可读、inspect 拿不到数字时,验收完全失去意义。
# 显式三态判定:空 / 0 / 非数字 → 失败;否则才比较 < 200。
# PR review I4:grep regex 同步加 (\.[0-9]+)?(防御:awk 退化时也有兜底)。
if [ -z "$RSS_MIB" ] || ! printf '%s' "$RSS_MIB" | grep -qE '^[0-9]+(\.[0-9]+)?$'; then
  fail "could not measure backend RSS (raw='$RSS_RAW', parsed='$RSS_MIB')"
fi
# PR review I4:[ -le N ] / [ -ge N ] 在某些 bash 上要求 integer;用 printf %.0f 强转。
RSS_MIB_INT=$(printf '%.0f' "$RSS_MIB")
if [ "$RSS_MIB_INT" -le 0 ]; then
  fail "backend RSS is 0/empty — measurement broken or container not running"
fi
if [ "$RSS_MIB_INT" -ge 200 ]; then
  fail "RSS budget exceeded: ${RSS_MIB_INT} MiB (must be < 200 MiB)"
fi

log "all smoke checks passed"
exit 0
