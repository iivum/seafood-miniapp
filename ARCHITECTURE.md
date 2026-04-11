# ARCHITECTURE.md - 架构文档

本文档描述海鲜商城小程序的系统架构、技术选型和数据流。

---

## 🏗️ 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        微信小程序                                │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│  │  首页   │ │ 商品详情 │ │ 购物车  │ │ 订单页  │ │  个人   │  │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘  │
│       └──────────┴───────────┴───────────┴───────────┘        │
│                            │                                    │
│                     ┌──────▼──────┐                            │
│                     │  API 请求层  │                            │
│                     │  (request.ts)│                           │
│                     └──────┬──────┘                            │
└────────────────────────────┼───────────────────────────────────┘
                             │
                             ▼ HTTP/REST
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Cloud Gateway                        │
│                         (端口: 8080)                            │
│                    路由转发 / 认证 / 限流                         │
└────────────────────────────┬───────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ Product Svc   │   │  Order Svc   │   │   User Svc    │
│  (8081)       │   │   (8082)     │   │   (8083)      │
│               │   │              │   │               │
│ • 商品管理    │   │ • 订单处理   │   │ • 用户认证    │
│ • 分类管理    │   │ • 支付集成   │   │ • 收货地址    │
│ • 库存管理    │   │ • 物流跟踪   │   │ • 会员权益    │
└───────┬───────┘   └───────┬───────┘   └───────┬───────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │    MongoDB      │
                   │   (27017)      │
                   │                │
                   │ • seafood_product │
                   │ • seafood_order  │
                   │ • seafood_user  │
                   └─────────────────┘
                            
┌─────────────────────────────────────────────────────────────────┐
│                   Service Infrastructure                         │
│  ┌─────────────────┐              ┌─────────────────┐           │
│  │ Discovery Svc   │              │  Admin UI       │           │
│  │   (8761)        │              │   (8084)        │           │
│  │   Eureka        │              │   Vaadin        │           │
│  └─────────────────┘              └─────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 技术选型

### 前端技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| TypeScript | 5.x | 类型安全开发 |
| 原生小程序 | - | 跨平台框架 |
| Jest | 29.x | 单元测试 |
| ESLint | 8.x | 代码质量 |

### 后端技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.2.x | 应用框架 |
| Spring Cloud | 2023.x | 微服务生态 |
| Spring Data MongoDB | 4.x | MongoDB 集成 |
| Eureka | - | 服务注册发现 |
| Spring Cloud Gateway | - | API 网关 |
| Gradle | 9.x | 构建工具 |

### 基础设施
| 技术 | 用途 |
|------|------|
| Docker | 容器化部署 |
| MongoDB | 文档数据库 |
| Nginx | HTTP 服务器（生产环境）|

---

## 🔄 数据流

### 1. 商品浏览流程
```
用户 → 小程序首页
      → 发起 GET /products 请求
      → Gateway 路由到 Product Service
      → 查询 MongoDB
      → 返回 JSON 响应
      → 小程序渲染列表
```

### 2. 加入购物车流程
```
用户 → 商品详情页 → 点击"加入购物车"
      → 发起 POST /cart 请求
      → Gateway 路由到 User Service (获取用户) → Order Service (购物车)
      → 购物车数据存储到 MongoDB
      → 返回更新后的购物车
      → 小程序更新购物车状态
```

### 3. 订单创建流程
```
用户 → 购物车 → 选择商品 → 点击结算
      → 发起 POST /orders 请求
      → Gateway 路由到 Order Service
      → 验证库存
      → 创建订单记录
      → 调用微信支付 API
      → 支付回调处理
      → 更新订单状态
      → 返回订单结果
```

---

## 📁 目录结构

### 前端结构
```
frontend/
├── app.js                    # 应用入口
├── app.json                  # 全局配置
├── project.config.json       # 项目配置
├── pages/                    # 页面
│   ├── index/                # 首页/商品列表
│   ├── product-detail/       # 商品详情
│   ├── cart/                 # 购物车
│   ├── order-list/           # 订单列表
│   ├── order-confirm/        # 订单确认
│   ├── address/              # 地址管理
│   ├── login/                # 登录
│   └── profile/              # 个人中心
├── src/
│   ├── api/                  # API 调用
│   │   ├── product.ts        # 商品 API
│   │   ├── cart.ts           # 购物车 API
│   │   └── order.ts          # 订单 API
│   ├── modules/              # 业务模块（TDD）
│   │   ├── productList/      # 商品列表模块
│   │   └── shoppingCart/     # 购物车模块
│   ├── types/                # 类型定义
│   │   └── index.ts
│   └── utils/                # 工具函数
│       └── request.ts        # HTTP 请求封装
└── utils/                    # 原生工具
```

### 后端结构
```
backend/
├── build.gradle              # 根构建配置
├── settings.gradle           # 项目包含的模块
├── docker-compose.yml        # Docker 编排
│
├── common/                   # 公共模块
│   └── src/main/java/com/seafood/common/
│       ├── model/            # 公共模型
│       ├── config/           # 公共配置
│       └── util/             # 公共工具
│
├── discovery-service/        # 服务注册发现 (Eureka)
│   └── src/main/java/com/seafood/
│       └── DiscoveryApplication.java
│
├── gateway/                  # API 网关
│   └── src/main/java/com/seafood/
│       └── GatewayApplication.java
│
├── product-service/          # 商品服务
│   └── src/main/java/com/seafood/
│       ├── ProductApplication.java
│       ├── controller/       # REST 控制器
│       ├── service/         # 业务逻辑
│       ├── repository/      # 数据访问
│       └── model/           # 领域模型
│
├── order-service/            # 订单服务
│   └── src/main/java/com/seafood/
│       ├── OrderApplication.java
│       ├── controller/
│       ├── service/
│       ├── repository/
│       └── model/
│
├── user-service/             # 用户服务
│   └── src/main/java/com/seafood/
│       ├── UserApplication.java
│       ├── controller/
│       ├── service/
│       ├── repository/
│       └── model/
│
└── admin-ui/                  # 管理后台 (Vaadin)
    └── src/main/java/com/seafood/
        └── AdminUIApplication.java
```

---

## 🔌 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Discovery | 8761 | Eureka 服务注册 |
| Gateway | 8080 | API 网关入口 |
| Product | 8081 | 商品服务 |
| Order | 8082 | 订单服务 |
| User | 8083 | 用户服务 |
| Admin UI | 8084 | 管理后台 |

---

## 🔐 安全机制

### 前端安全
1. **XSS 防护**: 用户输入做 HTML 转义
2. **请求验证**: 参数类型和范围检查
3. **敏感数据**: 不在本地存储明文

### 后端安全
1. **输入验证**: Spring Validation
2. **SQL 注入**: MongoDB 自动防护（无 SQL）
3. **认证授权**: JWT Token (待实现)
4. **限流熔断**: Gateway 限流

---

## 📊 监控与日志

### 日志
- 结构化日志格式 (JSON)
- 请求追踪 ID
- 分布式日志收集 (待实现)

### 监控指标
- API 响应时间
- 服务健康状态
- 数据库连接池
- 错误率统计

---

## 🚀 部署架构 (生产环境)

```
                    ┌─────────────────┐
                    │   Nginx         │
                    │  (HTTPS, SSL)  │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  Docker Swarm   │
                    │   or K8s        │
                    └────────┬────────┘
                             │
    ┌────────────────────────┼────────────────────────┐
    │                        │                        │
┌───▼───┐              ┌─────▼─────┐             ┌─────▼─────┐
│Front  │              │  Gateway  │             │ Admin UI  │
│ (CDN) │              │ Cluster   │             │ Cluster   │
└───────┘              └─────┬─────┘             └───────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
   ┌────▼────┐        ┌──────▼──────┐       ┌──────▼──────┐
   │Product  │        │   Order    │       │    User     │
   │Cluster  │        │  Cluster   │       │   Cluster   │
   └────┬────┘        └──────┬──────┘       └──────┬──────┘
        │                   │                    │
        └───────────────────┼────────────────────┘
                            │
                     ┌──────▼──────┐
                     │  MongoDB    │
                     │  Replica    │
                     │    Set      │
                     └─────────────┘
```

---

*架构文档随系统演进持续更新。*
