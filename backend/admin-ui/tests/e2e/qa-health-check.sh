#!/bin/bash
# ===========================================
# QA验证脚本 - Docker服务健康状态检查
# ===========================================

set -e

ADMIN_UI_URL="${ADMIN_UI_URL:-http://localhost:8090}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
PRODUCT_SERVICE_URL="${PRODUCT_SERVICE_URL:-http://localhost:8081}"
ORDER_SERVICE_URL="${ORDER_SERVICE_URL:-http://localhost:8082}"
USER_SERVICE_URL="${USER_SERVICE_URL:-http://localhost:8083}"
DISCOVERY_SERVICE_URL="${DISCOVERY_SERVICE_URL:-http://localhost:8761}"
MONGODB_URL="${MONGODB_URL:-localhost:27017}"
REDIS_URL="${REDIS_URL:-localhost:6379}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

passed=0
failed=0

check_service() {
    local name=$1
    local url=$2
    local timeout=${3:-5}
    
    echo -n "检查 $name... "
    if curl -sf --max-time "$timeout" "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        ((passed++))
        return 0
    else
        echo -e "${RED}✗ 失败${NC}"
        ((failed++))
        return 1
    fi
}

check_health_endpoint() {
    local name=$1
    local url=$2
    
    echo -n "检查 $name 健康端点... "
    if curl -sf --max-time 10 "$url/actuator/health" 2>/dev/null | grep -q '"status":"UP\|"status":"UP"'; then
        echo -e "${GREEN}✓ 健康${NC}"
        ((passed++))
        return 0
    else
        echo -e "${YELLOW}⚠ 未通过健康检查${NC}"
        ((failed++))
        return 1
    fi
}

echo "=========================================="
echo "   Docker服务健康状态检查"
echo "=========================================="
echo ""

echo "--- 基础设施服务 ---"
check_service "MongoDB" "mongodb://$MONGODB_URL" 5
check_service "Redis" "redis://$REDIS_URL" 5

echo ""
echo "--- 微服务 ---"
check_service "Discovery Service" "$DISCOVERY_SERVICE_URL/actuator/health" 10
check_service "Gateway" "$GATEWAY_URL/actuator/health" 10
check_service "Product Service" "$PRODUCT_SERVICE_URL/actuator/health" 10
check_service "Order Service" "$ORDER_SERVICE_URL/actuator/health" 10
check_service "User Service" "$USER_SERVICE_URL/actuator/health" 10

echo ""
echo "--- 管理后台 ---"
check_service "Admin UI" "$ADMIN_UI_URL" 10
check_service "Admin UI 健康端点" "$ADMIN_UI_URL/actuator/health" 10

echo ""
echo "--- API网关路由测试 ---"
echo -n "测试商品服务路由... "
if curl -sf --max-time 10 "$GATEWAY_URL/api/products/actuator/health" > /dev/null 2>&1 || \
   curl -sf --max-time 10 "$GATEWAY_URL/products/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 路由正常${NC}"
    ((passed++))
else
    echo -e "${YELLOW}⚠ 路由可能未配置${NC}"
fi

echo ""
echo "=========================================="
echo "   检查结果汇总"
echo "=========================================="
echo -e "通过: ${GREEN}$passed${NC}"
echo -e "失败: ${RED}$failed${NC}"
echo ""

if [ $failed -eq 0 ]; then
    echo -e "${GREEN}所有检查通过!${NC}"
    exit 0
else
    echo -e "${YELLOW}部分检查未通过,请检查相关服务${NC}"
    exit 1
fi
