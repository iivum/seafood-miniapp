#!/usr/bin/env bash
# seed.sh — 启动后注入种子数据(50 商品 / 5 分类 / 1 admin / 1 customer)
# 用法: ./seed.sh [mongo-uri]
# 默认: mongodb://localhost:27017/seafood

set -euo pipefail

MONGO_URI="${1:-${MONGODB_URI:-mongodb://localhost:27017/seafood}}"
DB="${MONGO_URI##*/}"
SEED_DIR="$(dirname "$0")/fixtures"

echo "[seed] target=$MONGO_URI db=$DB"

# 1. 清空(数据兼容:否,proposal §"后端:从 7 模块收敛到 1 模块")
mongosh "$MONGO_URI" --quiet --eval '
  db.products.deleteMany({});
  db.users.deleteMany({});
  db.banners.deleteMany({});
' >/dev/null

# 2. 导入分类
for f in "$SEED_DIR"/categories.json; do
  [ -f "$f" ] || continue
  echo "[seed] import $(basename "$f")"
  jq -c '.[]' "$f" | while read -r doc; do
    echo "$doc" | mongosh "$MONGO_URI" --quiet --eval "
      db.products.updateOne(
        { _id: 'category-${RANDOM}' },
        { \$set: $doc },
        { upsert: true }
      );
    " >/dev/null
  done
done

# 3. 导入商品
# fix-category-bad-status-500:非法 status 值（历史上出现过手写 INACTIVE）会让
# 该分类整个查询在 document→entity 转换阶段抛异常，此处 fail-fast 挡在导入前，
# 而不是留到运行时靠用户点开某个分类才发现。
echo "[seed] validate products.json status values"
bad_status=$(jq -r '[.[] | select(.status != "ACTIVE" and .status != "OUT_OF_STOCK" and .status != "DISCONTINUED") | .name] | length' "$SEED_DIR/products.json")
if [ "$bad_status" -gt 0 ]; then
  echo "[seed] ERROR: products.json 中有 $bad_status 条商品的 status 不在 {ACTIVE,OUT_OF_STOCK,DISCONTINUED} 合法枚举内" >&2
  exit 1
fi

echo "[seed] import products.json"
jq -c '.[]' "$SEED_DIR/products.json" | while read -r doc; do
  echo "$doc" | mongosh "$MONGO_URI" --quiet --eval "
    db.products.insertOne($doc);
  " >/dev/null
done

# 4. 导入用户(admin + customer)
echo "[seed] import users.json"
jq -c '.[]' "$SEED_DIR/users.json" | while read -r doc; do
  echo "$doc" | mongosh "$MONGO_URI" --quiet --eval "
    db.users.insertOne($doc);
  " >/dev/null
done

# cleanup-mp-e2e-minor-findings:查询回 seed customer 用户的真实 _id(MongoDB
# 插入时自动生成,fixture 里不写死)。下面导订单前用它 patch userId —— 不能反过来
# 在 users.json 里手写固定 _id:openId 无唯一索引,_id 在反复 reseed/relogin 间
# 不保证稳定(memory c5-visual-test-runbook 已记录过这条教训),动态查询回真实值
# 才是不依赖"这是不是第一次插入"这个前提的做法(design.md 决策 2)。
CUSTOMER_ID=$(mongosh "$MONGO_URI" --quiet --eval "print(db.users.findOne({role:'CUSTOMER'})._id.toString())" | tr -d '[:space:]')

# 5. 导入 banner(home hero 轮播,后端驱动)
echo "[seed] import banners.json"
jq -c '.[]' "$SEED_DIR/banners.json" | while read -r doc; do
  echo "$doc" | mongosh "$MONGO_URI" --quiet --eval "
    db.banners.insertOne($doc);
  " >/dev/null
done
# banner 时间字段字符串 → ISODate(BannerDocument.createdAt 是 Instant,避免反序列化失败)
mongosh "$MONGO_URI" --quiet --eval '
  db.banners.updateMany({ title: { $exists: true } },
    [{ $set: { createdAt: { $toDate: "$createdAt" }, updatedAt: { $toDate: "$updatedAt" } } }]);
' >/dev/null

# 6. 导入订单(mp-09 e2e 验收用 fixture)
if [ -f "$SEED_DIR/orders.json" ]; then
  echo "[seed] import orders.json"
  mongosh "$MONGO_URI" --quiet --eval 'db.orders.deleteMany({});' >/dev/null
  # cleanup-mp-e2e-minor-findings:订单 fixture 的 userId 不再是写死的占位符,
  # 动态 patch 成上面查回的真实 customer _id —— 否则订单对任何真实登录用户都
  # 不可见(mp 按 userId 隔离,fixture 原样导入会永久孤儿化)。
  jq -c --arg uid "$CUSTOMER_ID" 'map(.userId = $uid) | .[]' "$SEED_DIR/orders.json" | while read -r doc; do
    echo "$doc" | mongosh "$MONGO_URI" --quiet --eval "
      db.orders.insertOne($doc);
    " >/dev/null
  done
  # 时间字段字符串 → ISODate
  mongosh "$MONGO_URI" --quiet --eval '
    db.orders.updateMany({},
      [{ $set: {
        createdAt: { $toDate: "$createdAt" },
        updatedAt: { $toDate: "$updatedAt" },
        estimatedDelivery: { $toDate: "$estimatedDelivery" }
      } }]);
  ' >/dev/null
fi

echo "[seed] done. counts:"
mongosh "$MONGO_URI" --quiet --eval '
  print("  products: " + db.products.countDocuments());
  print("  users:    " + db.users.countDocuments());
  print("  banners:  " + db.banners.countDocuments());
  print("  orders:   " + db.orders.countDocuments());
'
