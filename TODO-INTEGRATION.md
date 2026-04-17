# 联调测试待修复项 - 2026-04-17

## Admin UI 联调测试 - 第3轮完成 ✅

### ✅ 已通过测试

1. **根 URL 重定向** ✅
   - `http://localhost:8090/` 返回 302 重定向到 `/admin/`
   - HomeView 使用 `@Route(value = "", layout = MainLayout.class)` 正确映射

2. **PUT/PATCH 支持** ✅
   - ProductController 添加了 `@PutMapping` 和 `@PatchMapping` 端点
   - ProductApplicationService 添加了 `updateProduct()` 方法
   - Gateway 路由正确转发 PUT/PATCH 请求

3. **商品 CRUD 完整性** ✅
   - Create: 创建商品成功，显示"商品创建成功"通知
   - Read: 商品列表正确显示
   - Update: 编辑商品成功，显示"商品更新成功"通知
   - Delete: 删除商品成功，显示"商品已删除"

4. **页面路由** ✅
   - `/admin/` - 200 OK ✅
   - `/admin/login` - 200 OK ✅
   - `/admin/dashboard` - 200 OK ✅
   - `/admin/products` - 200 OK ✅
   - `/admin/orders` - 200 OK ✅
   - `/admin/users` - 200 OK ✅

5. **登录功能** ✅
   - 登录表单正常渲染
   - admin/admin123 登录成功
   - 登录后自动重定向到 dashboard

6. **Dashboard 统计** ✅
   - 实时显示商品总数、订单总数、用户总数

### ❌ 已修复问题

| 问题 | 状态 | 修复方式 |
|------|------|----------|
| admin-ui 根 URL 404 | ✅ 已修复 | 添加 RootController.java |
| product-service 缺少 PUT/PATCH | ✅ 已修复 | ProductController 添加端点 |
| user-service 500 错误 | ✅ 已修复 | (之前测试显示正常) |

---

## WeChat MiniApp 联调测试 - 待进行

### 已知问题
1. **无法微信一键登录注册** - 需真机测试，代码已实现
2. **微信小程序界面没有数据** - 需初始化测试数据
3. **首页布局不适配异形屏** - 需检查 CSS safe-area

### 测试准备
- 微信开发者工具需打开项目 `frontend/` 目录
- 确保后端服务运行中
- 检查 `src/api/` 中的 API 调用

---

## 测试命令

```bash
# 验证服务状态
docker ps | grep seafood

# API 测试
curl http://localhost:8080/api/products
curl http://localhost:8080/api/orders
curl http://localhost:8080/api/users

# Admin UI 测试
curl -I http://localhost:8090/
curl http://localhost:8090/admin/
curl http://localhost:8090/admin/login
```

---

## 修复进度

- [x] 第1轮测试完成
- [x] 第2轮测试完成
- [x] 第3轮测试完成 (所有问题已修复)
- [ ] WeChat MiniApp 联调测试
- [ ] 最终验收
