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
- [x] 订单支付集成（微信支付） - PaymentModule实现，包含wx.requestPayment集成和模拟支付环境
- [x] 订单列表页面 `pages/order-list/` - 已修复API调用
- [x] 订单确认页面 `pages/order-confirm/`

### 微信开发者工具测试调试 (P0)
- [ ] 启动微信开发者工具，加载前端项目
- [ ] 验证所有 TabBar 页面正常切换（首页、分类、购物车、个人中心）
- [ ] 测试商品列表下拉刷新、上拉加载更多
- [ ] 测试购物车添加、删除、数量修改
- [ ] 测试下单流程（订单确认 → 支付）
- [ ] 测试登录流程（微信授权登录）
- [ ] 测试地址管理（增删改查）
- [ ] 使用 `weixin-devtools-mcp` 自动化测试核心路径
- [ ] 修复开发者工具中发现的 JS Error 和渲染问题

### 微信小程序前端 — 遗留问题修复
- [x] 实现 `pages/category/category` 分类页面（app.json tabBar 引用但缺失）
- [ ] 添加 TabBar 图标资源 `images/tabbar/`（home.png, category.png, cart.png, profile.png 等）⚠️ 目录已创建，需补充实际图标文件
- [x] 修复 `src/utils/request.ts` TypeScript 错误（WeChat 全局类型 `wx`, `getApp` 未定义）
- [x] 安装 `miniprogram-api-typings` 并配置 tsconfig.json types



### Skyline 渲染引擎迁移
- [x] 阅读 [Skyline 迁移文档](https://developers.weixin.qq.com/miniprogram/dev/framework/runtime/skyline/migration/)
- [ ] 升级基础库依赖（要求 ≥ 3.0.2，安卓/iOS 客户端 ≥ 8.0.40）⚠️ 需手动升级微信开发者工具
- [ ] 安装 [skylint](https://github.com/wechat-miniprogram/skylint) 迁移工具，运行代码扫描
- [x] `app.json` 添加 Skyline 配置：
  ```json
  "lazyCodeLoading": "requiredComponents",
  "componentFramework": "glass-easel",
  "rendererOptions": {
    "skyline": {
      "defaultDisplayBlock": true,
      "defaultContentBox": true
    }
  }
  ```
- [x] 逐页面迁移（渐进式），关键路径页面优先（商品列表、购物车、订单）
- [x] 页面配置 `page.json` 添加 `"renderer": "skyline"`（index/cart/category）
- [ ] 开发者工具勾选「开启 Skyline 渲染调试」和「编译 worklet 代码」
- [ ] 真机测试验证，切换路径：开发版菜单 > 开发调试 > Switch Render
- [ ] 验证热重载兼容性（Skyline 暂不支持热重载）

### PC 小程序适配
- [x] 阅读 [PC 小程序接入指南](https://developers.weixin.qq.com/miniprogram/dev/framework/pc/)
- [x] `app.json` 配置 `"resizable": true` 开启大屏适配
- [x] 使用 `wx.getSystemInfoSync()` 判断 PC 平台，统一处理 Windows/Mac
- [x] 使用 `wx.onWindowResize` 监听窗口变化，动态调整布局
- [x] 大屏模式下自定义导航栏不支持，需适配系统导航栏（PC模式自动检测）
- [x] 双栏模式自动启用（窗口拉伸 > 768px），无需额外适配
- [x] 添加 PC 端键盘事件支持（`wx.onKeyUp`/`wx.onKeyDown`）
- [ ] PC 端二维码支付测试（有效期 5 分钟）
- [ ] 开发者工具开启自动预览测试 PC 效果

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
- [x] 商品推荐算法 - RecommendationModule实现，基于规则的同类/常购/热门推荐

### 性能优化
- [x] 图片懒加载 - 商品列表、购物车、订单确认页图片启用lazy-load
- [x] 接口缓存策略 - ProductAPI添加2分钟缓存减少重复请求
- [x] 小程序分包加载 - 实现分包结构，product/order/user/merchant 四个分包

### Admin 管理后台
- [x] 商品管理（增删改查） - product-manage 页面实现
- [x] 订单管理 - order-manage 页面实现，支持状态筛选和操作
- [x] 用户管理 - user-manage 页面实现，支持角色切换和禁用
- [x] 数据统计面板 - dashboard 页面实现，支持收入/订单/商品/用户统计和快捷操作

---

## 📱 微信小程序前端开发进度

### 主包页面（TabBar）
| 页面 | 路径 | 状态 |
|------|------|------|
| 首页/商品列表 | `pages/index/` | ✅ 完成 |
| 分类 | `pages/category/` | ❌ 缺失 |
| 购物车 | `pages/cart/` | ✅ 完成 |
| 个人中心 | `pages/profile/` | ✅ 完成 |

### 分包页面（Subpackages）

#### 商品分包 `pages-sub/product/`
| 页面 | 状态 |
|------|------|
| 商品详情 `product-detail/` | ✅ 完成 |

#### 订单分包 `pages-sub/order/`
| 页面 | 状态 |
|------|------|
| 订单列表 `order-list/` | ✅ 完成 |
| 订单确认 `order-confirm/` | ✅ 完成 |

#### 用户分包 `pages-sub/user/`
| 页面 | 状态 |
|------|------|
| 登录 `login/` | ✅ 完成 |
| 地址列表 `address/address-list/` | ✅ 完成 |
| 地址编辑 `address/address-edit/` | ✅ 完成 |

#### 商户分包 `pages-sub/merchant/`
| 页面 | 状态 |
|------|------|
| 商户中心 `merchant/` | ✅ 完成 |
| 商品管理 `product-manage/` | ✅ 完成 |
| 订单管理 `order-manage/` | ✅ 完成 |
| 用户管理 `user-manage/` | ✅ 完成 |
| 数据看板 `dashboard/` | ✅ 完成 |

### 前端待修复（P0）
- [x] `pages/category/category` 页面实现 ✅
- [ ] TabBar 图标 `images/tabbar/` 资源文件 ⚠️ 目录已创建，需补充实际图标文件
- [x] TypeScript 类型：`src/utils/request.ts` 中 `wx` / `getApp` 全局类型定义 ✅
- [x] 安装 `miniprogram-api-typings` 类型包 ✅

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

### 阶段 1: 核心功能补全 ✅
**目标**: 完成商品浏览和下单流程

### 阶段 2: 用户体系 ✅
**目标**: 完整的用户登录和订单管理

### 阶段 3: 管理功能 ✅
**目标**: Admin 后台和数据分析

### 阶段 4: 优化上线 (当前)
**目标**: 修复前端遗留问题，迁移 Skyline，适配 PC 端

#### 前端收尾任务
1. ✅ 实现分类页面 `pages/category/`
2. ⚠️ 补充 TabBar 图标资源（目录已创建，需图标文件）
3. ✅ 修复 TypeScript 类型错误
4. 完成 WeChat开发者工具 真机测试

#### Skyline 迁移 & PC 适配
5. ⚠️ 运行 skylint 工具扫描现有代码，修复迁移问题（待手动执行）
6. ✅ 渐进式迁移到 Skyline 渲染引擎（已完成 index/cart/category）
7. ✅ 开启大屏适配并测试 PC 端效果
8. 提交审核并发布

---

*任务状态随开发进度更新。*
