#!/usr/bin/env bash
# seed-order-visibility-smoke.sh
# ============================================================
# 用途:OpenSpec cleanup-mp-e2e-minor-findings task 2.2 验收 ——
# seed.sh 跑完后,种子 customer 用户必须能真实登录并看到种子订单。
#
# 不是"MongoDB 里 orders.userId 字段和 users._id 字段对不对得上"这种
# 静态断言 —— 而是走真实 HTTP 登录 + 真实鉴权查询,零 mock 端到端验证
# design.md 决策 2(动态 _id patch)+ 决策 3(customer openId 改 dev- 前缀)
# 合起来真的闭环了,不是"看起来对了"。
#
# 用法:
#   1. docker compose up -d(backend + mongodb 已在跑)
#   2. bash backend/seed/seed.sh   (或让本脚本调用时机自行 seed,见下)
#   3. bash backend/scripts/seed-order-visibility-smoke.sh
#
# 环境变量:
#   BACKEND_CONTAINER  默认 seafood-backend
#   BUSINESS_PORT      默认 8080
#   DEV_LOGIN_CODE     默认 dev-customer-seed-001(必须与 fixtures/users.json
#                      里 customer 条目的 openId 完全一致 —— 开发模式登录直接把
#                      整个 code 字符串当 openId 用,见 design.md 决策 3)
#
# 退出码:0 通过 / 1 验收失败 / 2 工具缺失 / 3 backend 不可达。
# ============================================================

set -euo pipefail

BUSINESS_PORT="${BUSINESS_PORT:-8080}"
BASE_URL="http://localhost:${BUSINESS_PORT}"
DEV_LOGIN_CODE="${DEV_LOGIN_CODE:-dev-customer-seed-001}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; exit 1; }
warn() { echo -e "${YELLOW}⚠${NC} $1"; }
step() { echo -e "\n${YELLOW}=== $1 ===${NC}"; }

for cmd in curl jq; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "需要 $cmd 但未安装"; exit 2; }
done

step "1. Backend 业务端口 ${BUSINESS_PORT} 可达性"
curl -sf "${BASE_URL}/api/products?page=0&size=1" -o /dev/null --max-time 5 \
  || { echo "Backend ${BASE_URL} 不可达,确认 docker compose up -d 已完成"; exit 3; }
pass "Backend ${BASE_URL} 响应正常"

step "2. 用种子 customer 身份走真实开发模式登录"
echo "  → POST /api/auth/wechat-login (code=${DEV_LOGIN_CODE})"
LOGIN_RESP=$(curl -sf -X POST "${BASE_URL}/api/auth/wechat-login" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"${DEV_LOGIN_CODE}\"}") || fail "登录请求失败(HTTP 错误),检查 WECHAT_ENABLED 是否为 false"

ACCESS_TOKEN=$(echo "$LOGIN_RESP" | jq -r '.accessToken // empty')
[ -n "$ACCESS_TOKEN" ] || fail "登录响应里没有 accessToken:$LOGIN_RESP"
pass "登录成功,拿到 accessToken"

step "3. 用该 token 查询 GET /api/orders,断言能看到种子订单"
ORDERS_RESP=$(curl -sf "${BASE_URL}/api/orders?page=0&size=20" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}") || fail "GET /api/orders 请求失败"

ORDER_COUNT=$(echo "$ORDERS_RESP" | jq -r '.orders // .content // [] | length' 2>/dev/null || echo 0)

if [ "$ORDER_COUNT" -lt 1 ]; then
  fail "种子 customer(openId=${DEV_LOGIN_CODE})登录后 GET /api/orders 看不到任何订单
  —— seed 订单 fixture 仍处于孤儿状态(design.md 决策 2/3 未生效或未对齐)。
  响应:$ORDERS_RESP"
fi
pass "GET /api/orders 返回 ${ORDER_COUNT} 条订单,种子订单可见"

echo
echo "================================================"
echo -e "${GREEN}seed-order-visibility-smoke.sh 通过${NC}"
echo "================================================"
