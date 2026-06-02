#!/usr/bin/env bash
# Seed MongoDB with admin user + sample products
# Usage: ./seed.sh (requires mongosh + a running MongoDB)
set -euo pipefail

MONGO_URI="${MONGODB_URI:-mongodb://localhost:27017/seafood}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"

echo "Seeding into: $MONGO_URI"
mongosh "$MONGO_URI" --quiet --eval "
db.users.deleteMany({username: '$ADMIN_USERNAME'});
db.users.deleteMany({username: 'demo'});
db.products.deleteMany({});

const now = new Date();
db.users.insertOne({
  username: '$ADMIN_USERNAME',
  passwordHash: '$(htpasswd -nbBC 10 "$ADMIN_USERNAME" "$ADMIN_PASSWORD" 2>/dev/null | cut -d: -f2)',
  role: 'ADMIN',
  displayName: 'Administrator',
  addresses: [],
  createdAt: now,
  updatedAt: now
});

db.products.insertMany($(cat "$(dirname "$0")/products.json" | sed 's/ObjectId/ObjectId/g'));
" || {
  echo "mongosh failed; falling back to direct insert (no admin seed)";
  mongosh "$MONGO_URI" --quiet --eval "
  db.products.deleteMany({});
  db.products.insertMany($(cat "$(dirname "$0")/products.json"));
  "
}

echo "Seed complete."
echo "  admin: $ADMIN_USERNAME / $ADMIN_PASSWORD"
echo "  products: $(mongosh "$MONGO_URI" --quiet --eval 'print(db.products.countDocuments())')"
