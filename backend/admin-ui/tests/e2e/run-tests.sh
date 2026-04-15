#!/bin/bash
# ===========================================
# E2E测试执行脚本
# ===========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
ADMIN_UI_DIR="$PROJECT_ROOT/backend/admin-ui"

cd "$ADMIN_UI_DIR"

echo "=========================================="
echo "   Playwright E2E测试"
echo "=========================================="
echo ""

# 检查Playwright是否安装
if ! npx playwright --version > /dev/null 2>&1; then
    echo "安装Playwright..."
    npm install
    npx playwright install --with-deps chromium
fi

# 运行QA健康检查
echo "--- 运行QA健康检查 ---"
bash "$SCRIPT_DIR/qa-health-check.sh"

echo ""
echo "--- 运行Playwright测试 ---"
npx playwright test --reporter=list

echo ""
echo "=========================================="
echo "   测试完成"
echo "=========================================="
