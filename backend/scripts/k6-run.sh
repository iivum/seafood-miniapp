#!/usr/bin/env bash
# k6 baseline runner — starts backend, runs k6, captures per-endpoint stats
# Usage: ./backend/scripts/k6-run.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 1. Start backend (assume docker-compose up -d, or native binary)
if ! curl -sf -m 2 "${BASE:-http://localhost:8080}/actuator/health" > /dev/null 2>&1; then
    echo "ERROR: backend not reachable at ${BASE:-http://localhost:8080}"
    echo "Start backend first: cd $PROJECT_ROOT && docker-compose up -d backend"
    exit 1
fi

# 2. Run k6, capture stdout
k6_LOG_FORMAT=json \
k6 run --summary-export="$SCRIPT_DIR/k6-stats.json" \
    "$SCRIPT_DIR/k6-baseline.js" 2>&1 | tee "$SCRIPT_DIR/k6-stdout.log"

# 3. Parse k6-stats.json → k6-results.json (per-endpoint P50/P95/P99)
node -e "
const fs = require('fs');
const stats = JSON.parse(fs.readFileSync('$SCRIPT_DIR/k6-stats.json', 'utf8'));
const endpoints = {};
for (const [name, m] of Object.entries(stats.metrics.http_req_duration?.values || {})) {
  if (m?.tags?.name) {
    endpoints[m.tags.name] = {
      p50_ms: Math.round(m.values?.['p(50)'] ?? 0),
      p95_ms: Math.round(m.values?.['p(95)'] ?? 0),
      p99_ms: Math.round(m.values?.['p(99)'] ?? 0),
    };
  }
}
const result = {
  timestamp: new Date().toISOString(),
  endpoints,
  total_requests: stats.metrics.http_reqs?.values?.count ?? 0,
  error_rate: (stats.metrics.http_req_failed?.values?.rate ?? 0).toFixed(4),
};
fs.writeFileSync('$SCRIPT_DIR/k6-results.json', JSON.stringify(result, null, 2));
console.log('Wrote $SCRIPT_DIR/k6-results.json');
console.log(JSON.stringify(result, null, 2));
" || echo "node parse step failed (node not installed?) — k6-stats.json retained"
