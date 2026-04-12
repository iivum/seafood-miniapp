# TODO.md - 开发任务清单

本文件列出项目的待办任务，按优先级排序。

---

## 🔴 高优先级 (P0)

### 商品列表模块收尾
- [x] 完成首页商品列表展示
- [x] 实现商品详情页 `pages/product-detail/`
- [x] 实现下拉刷新功能
- [x] 实现上拉加载更多
- [x] 补充 ProductAPI 单元测试（覆盖率 ≥ 80%）

### 订单处理模块
- [x] 订单创建 API `POST /orders` - OrderAPI.createOrder()
- [x] 订单查询 API `GET /orders` - OrderAPI.getOrdersByUser()
- [x] 订单状态更新 `PUT /orders/:id/status` - OrderAPI.cancelOrder()
- [ ] 订单支付集成（微信支付）
- [x] 订单列表页面 `pages/order-list/` - 已修复API调用
- [x] 订单确认页面 `pages/order-confirm/`

---

## 🟡 中优先级 (P1)

### 用户认证
- [x] 微信登录集成 - app.js wxLogin()
- [x] JWT Token 管理 - app.js validateToken()
- [x] 登录页面 `pages/login/` - 完整实现
- [x] Token 刷新机制 - request.js 处理

### 用户中心
- [x] 个人中心页面 `pages/profile/` - 功能完整
- [x] 收货地址管理 `pages/address/` - address-list/address-edit
- [x] 地址编辑功能 - address-edit 完整实现

### 后端微服务
- [x] 完善 product-service CRUD
- [x] 实现 order-service 业务逻辑
- [x] 实现 user-service 用户管理
- [x] 服务间通信优化

---

## 🟢 低优先级 (P2)

### 购物车增强
- [x] 选中商品总价计算 - cart 页面支持选择商品、全选、显示选中总价
- [x] 购物车数据持久化 - 使用 wx.setStorageSync
- [x] 优惠券/折扣功能 - 支持优惠码输入和折扣计算

### 搜索与推荐
- [x] 商品搜索高亮 - index页面实现搜索和关键词高亮显示
- [x] 热门搜索词 - 显示热门海鲜关键词，点击可搜索
- [ ] 商品推荐算法

### 性能优化
- [x] 图片懒加载 - 商品列表、购物车、订单确认页图片启用lazy-load
- [x] 接口缓存策略 - ProductAPI添加2分钟缓存减少重复请求
- [x] 小程序分包加载 - 已规划分包结构，待文件重组后生效

### Admin 管理后台
- [ ] 商品管理（增删改查）
- [ ] 订单管理
- [ ] 用户管理
- [ ] 数据统计面板

---

## ✅ 已完成

### 基础设施
- [x] 项目结构搭建
- [x] TypeScript 配置
- [x] Jest 测试配置
- [x] ESLint 代码规范
- [x] Spring Cloud 微服务框架
- [x] MongoDB 数据库集成
- [x] Docker 容器化
- [x] API 网关配置

### 核心模块
- [x] 购物车 API 实现 (`CartAPI`)
- [x] 购物车状态管理
- [x] 商品列表 API 实现 (`ProductAPI`)
- [x] TypeScript 类型定义
- [x] HTTP 请求封装 (`request.ts`)
- [x] XSS 防护机制
- [x] 错误处理机制

### 文档
- [x] CLAUDE.md 创建
- [x] SPEC.md 创建
- [x] PROJECT_MEMORY.md 更新
- [x] 本 TODO.md 创建

---

## 📅 开发计划

### 阶段 1: 核心功能补全 (当前)
**目标**: 完成商品浏览和下单流程

### 阶段 2: 用户体系
**目标**: 完整的用户登录和订单管理

### 阶段 3: 管理功能
**目标**: Admin 后台和数据分析

### 阶段 4: 优化上线
**目标**: 性能优化和正式发布

---

*任务状态随开发进度更新。*
