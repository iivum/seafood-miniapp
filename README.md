# 🦐 海鲜商城小程序

微信小程序 + Spring Cloud 微服务架构的海鲜电商平台。

## 📦 项目结构

```
seafood-miniapp/
├── frontend/              # 微信小程序前端
│   ├── pages/            # 页面文件
│   ├── src/              # 源代码
│   │   ├── api/          # API 调用
│   │   ├── modules/      # 业务模块
│   │   ├── types/        # 类型定义
│   │   └── utils/        # 工具函数
│   └── package.json
│
├── backend/              # Spring Cloud 后端
│   ├── gateway/          # API 网关 (8080)
│   ├── product-service/  # 商品服务 (8081)
│   ├── order-service/    # 订单服务 (8082)
│   ├── user-service/    # 用户服务 (8083)
│   ├── discovery-service/ # 服务发现 (8761)
│   ├── admin-ui/         # 管理后台 (8084)
│   └── common/           # 公共模块
│
├── docker-compose.yml    # Docker 编排
├── CLAUDE.md            # AI 开发指南
├── SPEC.md              # 功能规格
├── TODO.md              # 开发任务
└── ARCHITECTURE.md      # 架构文档
```

## 🚀 快速开始

### 前端

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm test -- --watch

# 构建
npm run build
```

### 后端

```bash
cd backend

# 构建所有服务
./gradlew build

# 启动所有服务 (Docker)
docker-compose up -d

# 查看日志
docker-compose logs -f
```

### 服务端口

| 服务 | 端口 |
|------|------|
| Gateway | 8080 |
| Product Service | 8081 |
| Order Service | 8082 |
| User Service | 8083 |
| Discovery | 8761 |
| Admin UI | 8084 |
| MongoDB | 27017 |

## 🛠️ 技术栈

### 前端
- TypeScript
- 微信小程序
- Jest (测试)

### 后端
- Java 17+
- Spring Boot 3.x
- Spring Cloud
- MongoDB
- Eureka
- Gradle

## 📋 开发状态

| 模块 | 状态 |
|------|------|
| 购物车 | ✅ 完成 |
| 商品列表 | 🚧 开发中 |
| 订单处理 | 📋 待开发 |
| 用户中心 | 📋 待开发 |

## 📖 文档

- [CLAUDE.md](CLAUDE.md) - AI 开发指南
- [SPEC.md](SPEC.md) - 功能规格
- [TODO.md](TODO.md) - 开发任务
- [ARCHITECTURE.md](ARCHITECTURE.md) - 架构文档

## 📝 License

MIT
