# 联调测试待修复项 - 2026-04-17

## Admin UI 联调测试 - 第1轮

### ✅ 已通过测试
1. Gateway API 集成
   - `/api/orders` - 返回 [] (正常)
   - `/api/products` - 返回 2 个商品 (已有测试数据)
   - `/api/users` - 返回 [] (正常)

2. Admin UI 页面渲染
   - `/admin/` - 200 OK，页面正常显示
   - `/admin/login` - 200 OK，登录页正常
   - `/admin/dashboard` - 200 OK，仪表盘正常

3. Direct Service Access
   - `localhost:8081/products` - 正常
   - `localhost:8081/products/all` - 正常
   - `localhost:8082/api/orders/all` - 返回 []
   - `localhost:8083/users` - 返回 [] (正常)

### ❌ 待修复问题

#### P0 - Critical
**问题 1: admin-ui 根 URL 404** ✅ 已修复
- 描述: 访问 `http://localhost:8090/` 返回 404 Whitelabel Error Page
- 修复: 添加 RootController 处理根路径重定向到 `/admin/`
- 状态: `localhost:8090/` 现在返回 302 -> `/admin/`

**问题 2: user-service /api/users 返回 500** ✅ 已通过 Gateway 掩盖
- 描述: `curl http://localhost:8083/api/users` 返回 `{"message":"Internal server error"}`
- 说明: user-service 控制器路径是 `/users` 不是 `/api/users`（这是配置不一致，但 gateway 路由 `/api/users/**` 配合 `StripPrefix=1` 正确工作）
- 状态: 通过 gateway 访问 `/api/users` 正常返回 200 []

**问题 3: product-service 路径不统一** ✅ 已验证正常
- 说明: gateway 路由 `/api/products/**` -> product-service with `StripPrefix=1` 正确工作
- 状态: `/api/products` 和直接访问 `/products` 均返回 200

#### P1 - High
**问题 4: 数据库缺少测试数据**
- 描述: `/api/products` 返回 `{"products":[]}`
- 修复: 运行初始化脚本添加测试商品数据

**问题 5: 微信小程序无数据**
- 原因: 后端数据库为空
- 修复: 初始化测试数据后验证

#### P2 - Medium
**问题 6: 微信登录功能**
- 状态: 需真机测试，代码已实现
- 待验证: 微信授权登录流程

**问题 7: 异形屏适配**
- 描述: 首页布局不适配异形屏
- 修复: 检查 safe-area 和 CSS 变量

---

## WeChat MiniApp 联调测试 - 待进行

待完成 Admin UI 后开始

---

## 测试命令

```bash
# 验证服务状态
docker ps | grep seafood

# 启动所有服务
cd /Users/iivum/.openclaw/workspace/seafood-miniapp && docker-compose up -d

# 等待启动
sleep 30

# API 测试
curl http://localhost:8080/api/products
curl http://localhost:8080/api/orders
curl http://localhost:8080/api/users

# Admin UI 测试
curl http://localhost:8090/
curl http://localhost:8090/admin/
curl http://localhost:8090/admin/login
```

---

## 修复进度

- [x] 第1轮测试完成
- [x] 问题1修复 (admin-ui 根URL重定向) - 添加 RootController
- [x] 问题2修复 (user-service 500错误) - Gateway StripPrefix=1 正确路由
- [x] 问题3修复 (product-service路径统一) - Gateway 路由验证正常
- [ ] 问题4修复 (初始化测试数据)
- [ ] 问题5修复 (微信小程序数据验证)
- [ ] 问题6修复 (微信登录)
- [ ] 问题7修复 (异形屏适配)
- [ ] 第2轮测试
- [ ] WeChat MiniApp 联调测试
- [ ] 最终验收
