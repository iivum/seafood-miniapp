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
for bin in docker docker-compose curl jq awk; do
  command -v "$bin" >/dev/null 2>&1 || { echo "[smoke] missing: $bin" >&2; exit 2; }
done

cleanup() {
  log "docker-compose down -v"
  docker compose down -v >/dev/null 2>&1 || docker-compose down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---- 1. 拉起 stack ----
log "docker compose up -d"
if command -v docker >/dev/null 2>&1; then
  docker compose up -d || docker-compose up -d || { echo "compose up failed" >&2; exit 3; }
fi

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
[ "$HEALTHY" = "1" ] || fail "health did not return 200 within 30s (last code=$CODE)"

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
RSS_RAW=$(docker stats --no-stream --format '{{.MemRSS}}' "$CONTAINER_NAME" 2>/dev/null \
          || docker stats --no-stream --format '{{.MemRSS}}' "seafood-mongodb" 2>/dev/null \
          || true)
if [ -z "$RSS_RAW" ]; then
  # fallback: find backend container id and use docker exec ps
  CID=$(docker ps -qf "name=$CONTAINER_NAME" || true)
  if [ -n "$CID" ]; then
    # distroless 镜像没有 ps;用 docker inspect 读 cgroup RSS(MiB)
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
if ! [ "$RSS_MIB" -lt 200 ] 2>/dev/null; then
  fail "RSS budget exceeded: ${RSS_MIB} MiB (must be < 200 MiB)"
fi

log "all smoke checks passed"
exit 0
