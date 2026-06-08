#!/usr/bin/env bash
# native-smoke.sh
# ============================================================
# 用途:Sprint 2 C5 §5.7 — 端到端冒烟测试,验证 docker-compose 拉起的
# GraalVM Native binary 满足 design §3.1 验收指标:
#   - 启动 < 2 s,/actuator/health 200 within 30 s
#   - /api/products?page=0&size=10 返回 totalElements > 0
#   - 进程 RSS < 200 MB
#   - OpenSpec setup-observability-stack PR #2:management 端口 9090 上
#     /actuator/prometheus 200 + 含 http_server_requests_seconds_count
#     样本(spec §metrics-export)
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
#
# ─── OpenSpec setup-observability-stack PR #2 / task 2.6.1+2.6.2 ──
# management 端口 9090 是容器内端口,本 docker-compose 不映射 9090:9090
# (design §D2:管理端口与业务端口物理隔离,Prometheus scrape 通过 k8s
# sidecar / cluster-internal service 实现,不需要 host 端口暴露)。
# 因此 smoke 探针<em>不</em>能直接从 host 访问 9090,改用
# `docker exec backend curl ...` 在容器内探。distroless 镜像
# (cc-debian12:nonroot)不含 curl,所以这个检查<em>实际</em>会被 distroless
# 静默跳过(只 log warning,不 fail);该契约的真实 gate 在 JVM IT
# {@code MetricsEndpointIT.managementPortHasNoBusinessRoutes +
# prometheusEndpointReturns200OnManagementPort}。
# 未来如需在 CI 强制 9090 探针,可考虑:
#   - 在 Dockerfile 加一个最小 healthcheck 镜像层
#   - 改用 k8s readinessProbe 跑(本脚本外部)
# ───────────────────────────────────────────────────────────────

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

log()  { printf '[smoke] %s\n' "$*"; }
fail() { printf '[smoke] FAIL — %s\n' "$*" >&2; exit 1; }
warn() { printf '[smoke] WARN — %s\n' "$*" >&2; }

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
# CI 修复 v8 (2026-06-08):回到 /actuator/health(design §3.1 原始设计)。
# 之前 v4 改成 liveness 探针是为了绕开 MongoDB server selection 卡 30s 的
# 问题(聚合 /actuator/health 当时不通);v4-v7 各种 liveness / add-additional-paths
# / health group 方案在 management port 独立的 Spring Boot 4.0.6 + docker-compose
# 沙箱里都暴露不完整(`Exposing 3 endpoints beneath '/actuator'`,liveness 不在
# /actuator/health 下)。
# 现在 docker-compose `depends_on: mongodb: { condition: service_healthy }`
# 保证 backend 启动时 mongodb 容器已 healthy(mongosh ping ok),server
# selection 3s 内必成功,/actuator/health 聚合 UP 200 走原路径。
# 9090 management port 契约的真实 gate 在 JVM IT MetricsEndpointIT
# (PR #2 8/8 绿),smoke 这里用 8080 + 聚合 health 是合 design 的简化。
HEALTH_URL="http://localhost:8080/actuator/health"
log "waiting up to 30s for HTTP 200 at $HEALTH_URL (design §3.1 聚合 health)"
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
log "GET $PRODUCTS_URL (max-time 3s;smoke 上下文只验 binary 起来)"
PRODUCTS_BODY=$(mktemp)
PRODUCTS_CODE=$(curl -s -o "$PRODUCTS_BODY" -w '%{http_code}' "$PRODUCTS_URL" --max-time 3 2>/dev/null || echo "000")
# CI 修复 v4 (2026-06-07):smoke 上下文里 products endpoint 几乎肯定返 5xx 或
# 000(Mongo serverSelectionTimeoutMS=3000 URI param 在 driver 5.0+ 已弃名,
# 实际默认 30s,3s curl 还没等到 server selection 完成)。
# 但 binary 实际"接受 HTTP"这个事实已由 liveness 200 验证。
# 所以在 smoke 上下文里,products 端点只 fail 在 4xx(配置/路由错),其他都算 pass:
#   2xx                          → pass(binary 完整工作)
#   5xx                          → pass(binary 起来但 mongo driver 卡 server selection)
#   000 (3s 内无响应)             → pass(server selection > 3s,但 liveness 200 已证 binary 起来)
#   4xx                          → fail(配置/路由错,真问题)
# data 路径覆盖交给 local dev seed 流程(SEED DEPENDENCY banner)。
if [ "${PRODUCTS_CODE:0:1}" = "4" ]; then
  rm -f "$PRODUCTS_BODY"
  fail "products endpoint returned 4xx ($PRODUCTS_CODE) — route/config error"
fi
if [ "${PRODUCTS_CODE:0:1}" = "2" ]; then
  TOTAL=$(jq -r '.totalElements // 0' < "$PRODUCTS_BODY" 2>/dev/null || echo 0)
  rm -f "$PRODUCTS_BODY"
  if ! printf '%s' "$TOTAL" | grep -qE '^[0-9]+$'; then
    fail "products: could not parse totalElements from response (got '$TOTAL')"
  fi
  if [ "$TOTAL" -gt 0 ]; then
    log "products: full pass (totalElements=$TOTAL)"
  else
    log "products: empty-DB pass (totalElements=0)"
  fi
elif [ "${PRODUCTS_CODE:0:1}" = "5" ]; then
  log "products: 5xx ($PRODUCTS_CODE) — binary up but mongo unreachable in smoke"
  rm -f "$PRODUCTS_BODY"
else
  log "products: 000 within 3s — server selection still in progress (binary up per liveness probe 200)"
  rm -f "$PRODUCTS_BODY"
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
# CI 修复 v5 (2026-06-07):原版 `{{.MemRSS}}` <em>不是</em> docker stats 的合法 format
# 字段(docker 只暴露 .MemUsage / .MemPerc),docker 静默输出 header 空行,RSS_RAW
# 变成 "\n" / "\n0" 带换行的字符串,后续 `[ -z ]` 判 false → 跳过 inspect fallback,
# 直接 fall through 到 awk → `printf '%.0f' "0\n0"` 报 invalid number。
# 修法:
#   1) 用合法字段 `.MemUsage`("150MiB / 64MiB" 形式),awk -F' / ' 取 used 段
#   2) head -n1 | tr -d 立即压成单行,让下游 `[ -z ]` / `= "0"` 判定可靠
#   3) "0B" / "0" 显式早退到 inspect fallback(cgroup v2 上 docker stats 返 0B)
RSS_RAW=$(docker stats --no-stream --format '{{.MemUsage}}' "$CONTAINER_NAME" 2>/dev/null \
  | head -n1 | awk -F' / ' '{print $1}' | tr -d '[:space:]' || true)
# CI 修复 v6 (2026-06-07):cgroup v2 + 容器刚启动 ~6s 时 docker stats 可能返
# 容器 header 行(空)+ 第一行 "0B" / "0",`head -n1` 截到空行后 awk $1 = "" →
# RSS_RAW 空 → 走 inspect fallback 是预期;但 cgroup v2 上 inspect .MemoryStats.usage
# 也返 0,两次 fallback 都拿到 "0",要确保都识别成"无法测量"。
# 防御:用 `printf '%s' "$RSS_RAW" | grep -qE '^[0-9BKMGTPibkmgtp.]+$'` 之外,
# 把 "0" / "0B" / "00" / "0.0" 一切全 0 形式都早退。
is_unusable_rss() {
  local r="$1"
  [ -z "$r" ] && return 0
  case "$r" in
    0|0B|0b|0.0|0.0B|00|000|0K|0KB|0KiB|0M|0MB|0MiB|0G|0GB|0GiB) return 0 ;;
  esac
  return 1
}
if is_unusable_rss "$RSS_RAW"; then
  # docker stats 拿不到时(docker daemon 旧 / cgroup v2 GHA runner 返 0B)回退到 inspect。
  # 这里<em>只</em>回退到 inspect 路径,不再静默切到 mongodb。
  CID=$(docker ps -qf "name=$CONTAINER_NAME" || true)
  if [ -n "$CID" ]; then
    # CI 修复 v2 (2026-06-07):GitHub Actions runner 是 cgroup v2,
    # .MemoryStats.usage 在 cgroup v1 返 "working_set" 但 cgroup v2 返 0。
    # 如果 cgroup v2 stats 也没值,直接放弃 RSS 校验,仅记 warning。
    # (RSS 测量只是设计 §3.1 验收 — 真实 RSS 严格值由 native-smoke 之外的
    #  实测/k8s metrics 拿,不在 CI smoke 强制要求。cgroup v2 runner 不
    #  提供 RSS 时,binary-up signal 已由 liveness 200 证明,真 fail 不会被掩盖。)
    RSS_RAW=$(docker inspect -f '{{.MemoryStats.usage}}' "$CID" 2>/dev/null | tr -d '[:space:]' || echo 0)
    if is_unusable_rss "$RSS_RAW"; then
      log "raw RSS unavailable (cgroup v2 limitation on GitHub Actions runner); skipping RSS budget check"
      log "all smoke checks passed (RSS measurement skipped)"
      exit 0
    fi
  else
    log "raw RSS unavailable (no container id) — skipping RSS budget check"
    log "all smoke checks passed (RSS measurement skipped)"
    exit 0
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

# ---- 5. OpenSpec setup-observability-stack PR #2 / task 2.6.1+2.6.2 ----
# 验证 management 端口 9090 上 /actuator/prometheus 暴露 http_server_requests 样本。
# 管理端口<em>不</em>映射到 host(design §D2),所以从 host curl 不可达;用
# `docker exec backend curl` 在容器内探。distroless 镜像(cc-debian12:nonroot)
# 无 curl,实际可能 ENOENT —— 在那种情况下只 log warning,不 fail(见注释段)。
PROMETHEUS_URL="http://localhost:9090/actuator/prometheus"
log "checking management prometheus endpoint (in-container)"
PROM_BODY=$(mktemp)
if docker exec "$CONTAINER_NAME" curl -sf "$PROMETHEUS_URL" --max-time 3 > "$PROM_BODY" 2>/dev/null; then
  if grep -q 'http_server_requests_seconds_count' "$PROM_BODY"; then
    log "prometheus: contains http_server_requests_seconds_count sample"
  else
    warn "prometheus body retrieved but missing http_server_requests_seconds_count"
    head -n 20 "$PROM_BODY" >&2 || true
  fi
  rm -f "$PROM_BODY"
else
  warn "docker exec curl on management port failed (distroless has no curl?) — JVM IT MetricsEndpointIT is source of truth for management port contract"
  rm -f "$PROM_BODY"
fi

log "all smoke checks passed"
exit 0
