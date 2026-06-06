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

# ---- 2. 等待 /actuator/health 200 within 30 s ----
HEALTH_URL="http://localhost:8080/actuator/health"
log "waiting up to 30s for $HEALTH_URL → 200"
DEADLINE=$((SECONDS + 30))
HEALTHY=0
while [ $SECONDS -lt $DEADLINE ]; do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)
  if [ "$CODE" = "200" ]; then
    HEALTHY=1
    ELAPSED=$((SECONDS - (DEADLINE - 30)))
    log "health 200 after ${ELAPSED}s"
    break
  fi
  sleep 1
done
[ "$HEALTHY" = "1" ] || {
  # CI 修复:health 超时 → dump 容器日志,便于排查 native binary 启动失败原因。
  log "health timeout — dumping container logs:"
  docker logs seafood-backend 2>&1 | head -30 || true
  fail "health did not return 200 within 30s (last code=$CODE)"
}

# ---- 3. GET /api/products?page=0&size=10,assert totalElements > 0 ----
PRODUCTS_URL="http://localhost:8080/api/products?page=0&size=10"
log "GET $PRODUCTS_URL"
PRODUCTS_BODY=$(curl -fsS "$PRODUCTS_URL") || fail "products endpoint failed"
TOTAL=$(printf '%s' "$PRODUCTS_BODY" | jq -r '.totalElements // 0' 2>/dev/null || echo 0)
log "totalElements=$TOTAL"
if ! [ "$TOTAL" -gt 0 ] 2>/dev/null; then
  fail "expected totalElements > 0, got '$TOTAL' (body: $PRODUCTS_BODY)"
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
# 解析 "150MiB" / "1.5GiB" / 数字 bytes → 统一为 MiB
RSS_MIB=$(printf '%s' "$RSS_RAW" | awk '
  /[0-9]+MiB$/ { gsub("MiB",""); printf "%d", $0; next }
  /[0-9]+GiB$/ { gsub("GiB",""); printf "%d", $0*1024; next }
  /[0-9]+KiB$/ { gsub("KiB",""); printf "%d", $0/1024; next }
  /^[0-9]+$/    { printf "%d", $0/1024/1024; next }
  { print 0 }
')
log "RSS ≈ ${RSS_MIB} MiB"
# PR review #23 关键修复:原版 `! [ "$RSS_MIB" -lt 200 ]` 在 RSS_MIB 为空/0 时
# ([ 表达式 ] 解析失败 → 退出 1 → !1 = 0 → 条件不成立) 会静默通过 ——
# 容器起不来、stats 不可读、inspect 拿不到数字时,验收完全失去意义。
# 显式三态判定:空 / 0 / 非数字 → 失败;否则才比较 < 200。
if [ -z "$RSS_MIB" ] || ! printf '%s' "$RSS_MIB" | grep -qE '^[0-9]+$'; then
  fail "could not measure backend RSS (raw='$RSS_RAW', parsed='$RSS_MIB')"
fi
if [ "$RSS_MIB" -le 0 ]; then
  fail "backend RSS is 0/empty — measurement broken or container not running"
fi
if [ "$RSS_MIB" -ge 200 ]; then
  fail "RSS budget exceeded: ${RSS_MIB} MiB (must be < 200 MiB)"
fi

log "all smoke checks passed"
exit 0
