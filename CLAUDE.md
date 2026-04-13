# CLAUDE.md - AI 开发指南

本文件为 Claude Code 等 AI 编程工具提供项目开发指导。

---

## 🎯 项目概述

| 项目 | 说明 |
|------|------|
| **名称** | 海鲜商城小程序 (Seafood E-commerce Mini Program) |
| **类型** | 微信小程序 + Spring Cloud 微服务 |
| **状态** | 开发中 |
| **目标** | 功能完整、性能优良、安全可靠的海鲜电商平台 |

---

## 🏗️ 技术架构

### 前端（微信小程序）
```
seafood-miniapp/frontend/
├── app.js                 # 小程序入口
├── app.json               # 全局配置
├── pages/                 # 页面目录
│   ├── index/             # 首页/商品列表
│   ├── product-detail/    # 商品详情
│   ├── cart/              # 购物车
│   ├── order-list/        # 订单列表
│   ├── address/           # 地址管理
│   ├── login/             # 登录
│   └── profile/           # 个人中心
├── src/
│   ├── api/               # API 调用层
│   ├── modules/           # 业务模块
│   ├── types/             # TypeScript 类型
│   └── utils/             # 工具函数
└── utils/
    └── request.ts         # HTTP 请求封装
```

### 后端（Spring Cloud 微服务）
```
seafood-miniapp/backend/
├── discovery-service/     # 服务注册发现 (Eureka) - 端口 8761
├── gateway/               # API 网关 - 端口 8080
├── product-service/       # 商品服务 - 端口 8081
├── order-service/         # 订单服务 - 端口 8082
├── user-service/          # 用户服务 - 端口 8083
├── admin-ui/              # 管理后台 (Vaadin) - 端口 8084
└── common/               # 公共模块
```

### 服务依赖关系
```
                        ┌─────────────────┐
                        │  Discovery Svc  │
                        │    (8761)       │
                        └────────┬────────┘
                                 │
                        ┌────────▼────────┐
                        │    Gateway      │
                        │   (8080)       │
                        └────────┬────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        │                        │                        │
┌───────▼───────┐        ┌───────▼───────┐        ┌───────▼───────┐
│   Product     │        │    Order      │        │     User      │
│   (8081)      │        │   (8082)      │        │   (8083)      │
└───────┬───────┘        └───────┬───────┘        └───────────────┘
        │                        │
        └───────────┬─────────────┘
                    ▼
              ┌──────────┐
              │ MongoDB  │
              │ (27017)  │
              └──────────┘
```

---

## 📦 技术栈

### 前端
| 技术 | 版本 | 说明 |
|------|------|------|
| TypeScript | 5.x | 类型安全 |
| Jest | 29.x | 单元测试 |
| ESLint | 8.x | 代码规范 |

### 后端
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.x | 应用框架 |
| Spring Cloud | 2023.x | 微服务 |
| Gradle | 9.x | 构建工具 |
| MongoDB | 6.x | 数据库 |
| Eureka | - | 服务发现 |

---

## 🔌 API 设计

### 前端 → 后端通信

**Base URL**: `http://localhost:8080` (开发环境)

#### 商品 API (`ProductAPI`)
```typescript
// 获取商品列表
GET /products?page=0&pageSize=10&category=鱼类&keyword=三文鱼

// 响应结构
interface PaginatedProducts {
  products: Product[];
  page: number;
  totalPages: number;
  totalProducts: number;
  hasNext: boolean;
  hasPrev: boolean;
}
```

#### 购物车 API (`CartAPI`)
```typescript
// 获取购物车
GET /cart

// 添加商品
POST /cart
Body: { productId: string, quantity: number }

// 更新数量
PUT /cart/items/:itemId
Body: { quantity: number }

// 删除商品
DELETE /cart/items/:itemId

// 清空购物车
DELETE /cart
```

### 数据模型

**Product (商品)**
```typescript
interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  stock: number;
  category: string;
  imageUrl: string;
  onSale: boolean;
}
```

**CartItem (购物车项)**
```typescript
interface CartItem {
  id: string;
  productId: string;
  name: string;
  price: number;
  quantity: number;
  imageUrl: string;
}
```

**Cart (购物车)**
```typescript
interface Cart {
  id: string;
  items: CartItem[];
  totalPrice: number;
  totalItems: number;
  selectedItems: string[];
}
```

---

## 🧪 开发规范

### TDD 开发流程
```
1. Red    → 先写测试用例（必须失败）
2. Green  → 实现最小代码使测试通过
3. Refactor → 重构优化，保持测试通过
```

### 测试覆盖率要求
| 模块 | 覆盖率目标 |
|------|-----------|
| 全局 | ≥ 80% |
| 核心模块 | ≥ 90% |
| 关键功能 | 100% |

### 代码风格
- **前端**: TypeScript strict mode，无 `any` 类型
- **后端**: Google Java Format，120 字符行宽
- **注释**: 中文

---

## 🚀 常用命令

### 前端
```bash
cd seafood-miniapp/frontend

# 安装依赖
npm install

# 运行测试
npm test

# 单文件测试
npm test -- src/api/product.test.ts

# 带覆盖率
npm test -- --coverage

# ESLint 检查
npm run lint

# 类型检查
npm run type-check
```

### 后端
```bash
cd seafood-miniapp/backend

# 构建所有服务
./gradlew build

# 运行单个服务
./gradlew :product-service:bootRun

# 运行测试
./gradlew test

# 代码检查
./gradlew checkstyleMain
```

### Docker
```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### GraalVM Native 部署 (可选)
```bash
cd seafood-miniapp/backend

# 方式一: 本地构建原生可执行文件 (需要本地安装 GraalVM)
./gradlew :product-service:nativeRun

# 方式二: 构建 Docker 镜像
./gradlew :product-service:bootBuildImage

# 方式三: 使用 native profile 构建
./gradlew build -Pnative

# Docker Compose 启动 Native 模式
docker-compose -f docker-compose.yml -f docker-compose.native.yml up -d
```

---

## 📦 技术栈 (更新)

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.2.4 | 应用框架 |
| Spring Cloud | 2023.0.0 | 微服务 |
| Spring Native | 0.12.2 | GraalVM 原生镜像支持 |
| Gradle | 9.x | 构建工具 |
| MongoDB | 6.x | 数据库 |
| Eureka | - | 服务发现 |
| GraalVM | 23.x | 原生镜像编译 |

---

## 📋 模块开发顺序

| 状态 | 模块 | 说明 |
|------|------|------|
| ✅ 完成 | 购物车模块 | CartAPI, 状态管理 |
| 🚀 开发中 | 商品列表模块 | ProductAPI, 列表/搜索/分页 |
| 📋 待开发 | 订单处理模块 | 订单创建/支付/状态管理 |
| 👤 待开发 | 用户中心模块 | 登录/个人信息/地址管理 |

---

## 🔒 安全要求

- 所有用户输入必须验证和过滤
- 防止 XSS：对 keyword 等参数做 HTML 转义
- 防止 SQL 注入：使用参数化查询（MongoDB 自动处理）
- JWT Token 认证（待实现）
- 敏感数据加密存储

---

## 📁 关键文件

| 文件 | 说明 |
|------|------|
| `frontend/src/types/index.ts` | TypeScript 类型定义 |
| `frontend/src/api/product.ts` | 商品 API 实现 |
| `frontend/src/api/cart.ts` | 购物车 API 实现 |
| `frontend/src/utils/request.ts` | HTTP 请求封装 |
| `frontend/src/modules/*` | 业务模块（TDD 测试优先）|
| `backend/*/src/main/java/com/seafood/*` | Java 微服务代码 |
| `docker-compose.yml` | Docker 编排配置（默认 JVM 模式）|
| `docker-compose.native.yml` | Docker Compose Native 配置 |
| `backend/*/Dockerfile.native` | GraalVM Native Image Dockerfile |

---

## ⚠️ 重要提示

1. **TDD 优先**: 所有新功能必须先写测试
2. **类型安全**: 严禁 `any` 类型（测试文件除外）
3. **安全审查**: 所有代码需通过安全检查
4. **性能要求**: 首屏 < 2s，页面切换 < 300ms

---

*本文件为 AI 开发辅助文档，具体实现请参考代码注释和测试用例。*
