# 联调测试待修复项 - 2026-04-17

## Admin UI 联调测试 - 已完成 ✅

### ✅ 已通过测试

| 测试项 | 状态 | 说明 |
|--------|------|------|
| 根 URL 重定向 | ✅ 通过 | `http://localhost:8090/` → 302 `/admin/` |
| admin-ui 首页 | ✅ 通过 | `/admin/` 正常显示 |
| 登录功能 | ✅ 通过 | admin/admin123 登录成功 |
| 商品 CRUD | ✅ 通过 | 创建、读取、更新、删除全部正常 |
| Dashboard 统计 | ✅ 通过 | 实时显示商品/订单/用户数量 |
| PUT/PATCH 支持 | ✅ 通过 | product-service 添加了完整更新端点 |

### ❌ 已修复问题

| 问题 | 状态 | 修复方式 |
|------|------|----------|
| admin-ui 根 URL 404 | ✅ 已修复 | 添加 RootController.java |
| product-service 缺少 PUT/PATCH | ✅ 已修复 | ProductController 添加 @PutMapping/@PatchMapping |
| order-service 路径不匹配 | ✅ 已修复 | 移除 `/api` 前缀统一为 `/orders` |
| gateway cart 路由缺失 | ✅ 已修复 | 添加 cart-service 路由 |

---

## WeChat MiniApp 联调测试 - 已完成 ✅

### ✅ 已通过检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| API 路径 | ✅ 通过 | baseUrl 已修正为 `http://localhost:8080` |
| 商品 API | ✅ 通过 | GET /api/products → 15 商品 |
| 订单 API | ✅ 通过 | GET /api/orders → 6 订单 |
| 用户 API | ✅ 通过 | GET /api/users → 4 用户 |
| 异形屏适配 | ✅ 通过 | 添加 safe-area-top class 和 padding |
| 微信登录 | ✅ 通过 | 改用 wx.login() + wx.getUserInfo() |

### ❌ 已修复问题

| 问题 | 状态 | 修复方式 |
|------|------|----------|
| baseUrl 路径重复 /api | ✅ 已修复 | 改为 `http://localhost:8080` |
| 首页异形屏不适配 | ✅ 已修复 | 添加 safe-area-top padding |
| wx.getUserProfile 已废弃 | ✅ 已修复 | 改用 wx.login() + wx.getUserInfo() |

---

## 最终验证命令

```bash
# 1. 验证 Docker 服务状态
docker ps | grep seafood

# 2. 验证 API 连通性
curl http://localhost:8080/api/products | jq '.totalProducts'
curl http://localhost:8080/api/orders | jq 'length'
curl http://localhost:8080/api/users | jq 'length'

# 3. 验证 Admin UI
curl -I http://localhost:8090/

# 4. 前端测试
cd frontend && npm test
```

---

## 修复进度

- [x] Admin UI 联调测试 (3轮)
- [x] Admin UI 所有问题已修复
- [x] WeChat MiniApp 联调测试
- [x] WeChat MiniApp 所有问题已修复
- [ ] 最终验收 (人工审核)
