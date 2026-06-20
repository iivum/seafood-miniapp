#!/usr/bin/env bash
# run-visual.sh — C5 mp 视觉验证一键触发(感知层 + 几何层)。
# 幂等:已就绪的依赖自动跳过;缺的才起。
#
# 用法:
#   ./run-visual.sh                 # 起齐依赖 + 跑感知 + 几何(全屏)
#   ./run-visual.sh visual          # 只跑感知层
#   ./run-visual.sh geometry        # 只跑几何层
#   ./run-visual.sh visual mp-01-home   # 单屏
#   RESEED=1 ./run-visual.sh        # 强制重灌 seed
#
# 前置(脚本无法代办):微信 DevTools 已装 + 已登录 + 项目已导入(GUI 应用)。
set -uo pipefail

# 绕开系统代理(clash 等)对 localhost 的拦截
export NO_PROXY="localhost,127.0.0.1,*"
export no_proxy="localhost,127.0.0.1,*"

# 路径锚定(脚本在 frontend/e2e/tools/)
TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$(cd "$TOOLS_DIR/../.." && pwd)"
REPO_DIR="$(cd "$FRONTEND_DIR/.." && pwd)"

DEVTOOLS_CLI="/Applications/wechatwebdevtools.app/Contents/MacOS/cli"
AUTO_PORT=9420
MONGO_CT="seafood-mongodb"
BACKEND_CT="seafood-backend"
NET="seafood-miniapp_seafood-network"
API="http://127.0.0.1:8080/api/products?page=0&size=1"

say()  { printf '\033[1;36m[run-visual]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[run-visual]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[run-visual] %s\033[0m\n' "$*" >&2; exit 1; }

api_code() { curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$API" 2>/dev/null; }

# ---------- ① DevTools 自动化端口 ----------
if lsof -nP -iTCP:$AUTO_PORT -sTCP:LISTEN >/dev/null 2>&1; then
  say "DevTools 自动化端口 $AUTO_PORT 已在监听 ✓"
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
fi

# ---------- ② MongoDB + seed ----------
if ! docker ps --format '{{.Names}}' | grep -qx "$MONGO_CT"; then
  warn "$MONGO_CT 容器未运行,尝试 docker compose 起 mongodb …"
  ( cd "$REPO_DIR" && docker compose up -d mongodb >/dev/null 2>&1 ) || die "起 mongodb 失败"
  sleep 3
fi
PRODUCTS=$(docker exec "$MONGO_CT" mongosh seafood --quiet --eval 'db.products.countDocuments()' 2>/dev/null | tr -dc '0-9')
PRODUCTS=${PRODUCTS:-0}
if [ "${RESEED:-0}" = "1" ] || [ "$PRODUCTS" -eq 0 ]; then
  say "灌 seed 数据(products=$PRODUCTS,RESEED=${RESEED:-0})…"
  FX="$REPO_DIR/backend/seed/fixtures"
  cat "$FX/products.json"   | docker exec -i "$MONGO_CT" mongoimport --db seafood --collection products --jsonArray --drop --quiet
  cat "$FX/categories.json" | docker exec -i "$MONGO_CT" mongoimport --db seafood --collection products --jsonArray --quiet
  cat "$FX/users.json"      | docker exec -i "$MONGO_CT" mongoimport --db seafood --collection users --jsonArray --drop --quiet
  cat "$FX/banners.json"    | docker exec -i "$MONGO_CT" mongoimport --db seafood --collection banners --jsonArray --drop --quiet
  # fixtures stale:缺 status 字段 + 时间是字符串 → 必须修,否则 listPublic 返 0 条
  # banner 时间同样字符串 → ISODate(BannerDocument.createdAt 是 Instant)
  docker exec "$MONGO_CT" mongosh seafood --quiet --eval \
    'db.products.updateMany({name:{$exists:true}},[{$set:{status:"ACTIVE",createdAt:{$toDate:"$createdAt"},updatedAt:{$toDate:"$updatedAt"}}}]);
     db.banners.updateMany({title:{$exists:true}},[{$set:{createdAt:{$toDate:"$createdAt"},updatedAt:{$toDate:"$updatedAt"}}}]);' >/dev/null
  say "seed 完成 ✓"
else
  say "MongoDB 已 seed(products=$PRODUCTS)✓"
fi

# ---------- ③ 后端 ----------
if [ "$(api_code)" = "200" ]; then
  say "后端 /api/products 已 200 ✓"
else
  if docker ps -a --format '{{.Names}}' | grep -qx "$BACKEND_CT"; then
    warn "存在旧 $BACKEND_CT 容器,删后重起(避免 arm64 native crash-loop)"
    docker rm -f "$BACKEND_CT" >/dev/null 2>&1
  fi
  say "起后端(jvm 镜像;native 是 amd64 不匹配本机)…"
  docker run -d --name "$BACKEND_CT" --network "$NET" -p 8080:8080 \
    -e JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n' | head -c 44)" \
    -e JWT_ADMIN_SECRET="$(openssl rand -base64 48 | tr -d '\n' | head -c 44 | rev)" \
    -e ADMIN_BOOTSTRAP_PASSWORD='SeafoodAdmin#2026' \
    -e MONGODB_URI='mongodb://mongodb:27017/seafood' \
    -e SPRING_MONGODB_URI='mongodb://mongodb:27017/seafood' \
    -e SPRING_PROFILES_ACTIVE=docker \
    seafood-backend:jvm >/dev/null || die "起后端失败(seafood-backend:jvm 镜像在?网络 $NET 在?)"
  for i in $(seq 1 30); do [ "$(api_code)" = "200" ] && break; sleep 2; done
  [ "$(api_code)" = "200" ] || die "后端 60s 内未就绪(docker logs $BACKEND_CT 查)"
  say "后端就绪 ✓"
fi

# ---------- ④ 跑测 ----------
MODE="${1:-both}"
SCREEN="${2:-}"
cd "$FRONTEND_DIR"
rc=0
run_visual()   { say "感知层 …"; node e2e/tools/visual-diff.cjs   $SCREEN; }
run_geometry() { say "几何层 …"; node e2e/tools/geometry-diff.cjs $SCREEN; }
case "$MODE" in
  visual)   run_visual   || rc=$? ;;
  geometry) run_geometry || rc=$? ;;
  both|"")  run_visual || rc=$?; run_geometry || rc=$? ;;
  *) die "未知模式:$MODE(可选 visual|geometry|both)" ;;
esac

echo
[ $rc -eq 0 ] && say "全部 GREEN ✓" || warn "存在 RED(退出码 $rc);diff 图见 e2e/screenshots/<screen>-diff.png"
exit $rc
