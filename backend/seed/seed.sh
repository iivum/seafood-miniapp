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

echo "[seed] done. counts:"
mongosh "$MONGO_URI" --quiet --eval '
  print("  products: " + db.products.countDocuments());
  print("  users:    " + db.users.countDocuments());
'
