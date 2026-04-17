# 微信小程序前后端联调测试 - 问题记录

**测试时间**: 2026-04-17  
**测试环境**: localhost (Docker Services)  
**Gateway**: http://localhost:8080  
**状态**: 首次测试 - 仅记录问题，不做修复

---

## 1. 服务健康状态

| 服务 | 端口 | 状态 | 备注 |
|------|------|------|------|
| gateway | 8080 | ✅ healthy | /actuator/health 返回 UP |
| product-service | 8081 | ✅ healthy | |
| order-service | 8082 | ❌ 500 | /health 返回 Internal server error |
| user-service | 8083 | ❌ 500 | /health 返回 Internal server error |

---

## 2. API 路由问题

### 2.1 Gateway 路由配置不完整

**问题描述**: gateway/application.yml 中只配置了部分服务的路由

**当前配置**:
```yaml
routes:
  - id: product-service
    uri: lb://product-service
    predicates:
      - Path=/api/products/**
    filters:
      - StripPrefix=1
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/api/orders/**
  - id: user-service
    uri: lb://user-service
    predicates:
      - Path=/api/users/**
  - id: admin-ui
    uri: lb://admin-ui
    predicates:
      - Path=/admin/**
```

**缺失路由**:
- ❌ `/api/cart/**` - 购物车服务无路由配置 (CartAPI.BASE_ENDPOINT = '/cart')
- ❌ `/api/user/**` - 用户相关接口路径不匹配

### 2.2 前端 API 端点路径不匹配

| 前端 API | 端点路径 | Gateway 路由 | 问题 |
|----------|----------|--------------|------|
| ProductAPI | `/products` | `/api/products/**` | ✅ 正常 |
| OrderAPI | `/api/orders` | `/api/orders/**` | ✅ 正常 |
| CartAPI | `/cart` | 无路由 | ❌ 404 |
| app.js validateToken | `/auth/me` | 无路由 | ❌ 404 |
| app.js fetchWeChatToken | `/auth/wx-login` | 无路由 | ❌ 404 |
| app.js logout | `/auth/logout` | 无路由 | ❌ 404 |

---

## 3. 前后端 API 连通性测试

### 3.1 Products API
```bash
curl http://localhost:8080/api/products?page=0&pageSize=20
```
**结果**: ✅ 200 OK
**响应**: `{"hasPrev":false,"totalProducts":0,"totalPages":0,"hasNext":false,"page":0,"products":[]}`
**备注**: 正常返回空数组 (数据库无数据)

### 3.2 User/Profile API
```bash
curl http://localhost:8080/api/users/profile
curl http://localhost:8080/api/user/profile
```
**结果**: ✅ 200 OK (空响应)
**备注**: gateway 路由到 user-service，但返回空

### 3.3 User/Login API
```bash
curl -X POST http://localhost:8080/api/users/login -d '{"username":"test","password":"test"}'
```
**结果**: ❌ 500 Internal Server Error
**问题**: user-service 健康检查失败

### 3.4 Orders API
```bash
curl http://localhost:8080/api/orders?page=0&pageSize=10
```
**结果**: ❌ 404 Not Found
**原因**: gateway 配置 `Path=/api/orders/**`，但 StripPrefix=1 会导致 `/api/orders/list` -> `/orders/list`，后端可能没有此端点

### 3.5 Cart API
```bash
curl -X POST http://localhost:8080/cart -d '{"productId":"test","quantity":1}'
```
**结果**: ❌ 404 Not Found
**原因**: gateway 没有配置 cart-service 路由

---

## 4. 前端代码问题

### 4.1 app.js 中 hardcoded 的 baseUrl
```javascript
// app.js line 231
baseUrl: 'http://localhost:8080/api'
```
**问题**: 生产环境需要修改为实际服务器地址

### 4.2 登录页面路径不存在
```javascript
// pages/index/index.js line 197
url: '/pages-sub/user/login/login'
```
**问题**: 检查 pages-sub 目录下是否存在此路径

### 4.3 Product Detail 页面路径
```javascript
// pages/index/index.js line 204
url: `/pages-sub/product/product-detail/product-detail?id=${id}`
```
**问题**: 需要确认此路径存在

---

## 5. 数据库/数据问题

| 服务 | MongoDB Database | 问题 |
|------|------------------|------|
| product-service | seafood_product | ✅ 正常 |
| order-service | seafood_order | ❌ 连接问题 |
| user-service | seafood_user | ❌ 连接问题 |

---

## 6. 页面列表 (微信小程序)

需要测试的页面:
- [ ] pages/index/index - 首页/商品列表
- [ ] pages/cart/cart - 购物车
- [ ] pages/category/category - 分类
- [ ] pages/profile/profile - 个人中心
- [ ] pages-sub/user/login/login - 登录页 (需确认存在)
- [ ] pages-sub/product/product-detail/product-detail - 商品详情 (需确认存在)

---

## 7. 待修复问题汇总

### P0 - 阻塞性问题
1. **order-service 和 user-service 健康检查失败** - 需要检查 MongoDB 连接和 Eureka 注册
2. **购物车路由缺失** - gateway 需要添加 cart-service 路由
3. **Auth 路由缺失** - gateway 需要添加 auth 相关路由

### P1 - 功能性问题
4. **登录接口 500 错误** - user-service 问题
5. **Orders API 404** - 可能是路径问题或后端实现问题
6. **数据库连接问题** - order/user services 的 MongoDB 连接

### P2 - 体验问题
7. **前端 baseUrl hardcoded** - 需要支持多环境配置
8. **商品列表为空** - 需要初始化测试数据

---

### P2 - 体验问题
7. **前端 baseUrl hardcoded** - 需要支持多环境配置
8. **商品列表为空** - 需要初始化测试数据

---

## 9. 页面路径验证

| 页面路径 | 状态 | 备注 |
|----------|------|------|
| pages/index/index | ✅ 存在 | |
| pages/cart/cart | ✅ 存在 | |
| pages/category/category | ✅ 存在 | |
| pages/profile/profile | ✅ 存在 | |
| pages-sub/user/login/login | ✅ 存在 | |
| pages-sub/product/product-detail | ✅ 存在 | |

---

## 10. 测试建议

1. 修复 order-service 和 user-service 的启动问题 (500错误)
2. 在 gateway 添加缺失的路由配置:
   - cart-service: `/api/cart/**`
   - auth 相关路由
3. 确认所有前端页面路径存在 ✅ 已验证
4. 初始化测试商品数据到 MongoDB
5. 重新测试所有 API 连通性

---

*文档创建时间: 2026-04-17*
*最后更新: 2026-04-17*
*测试方式: curl + 代码审查*
