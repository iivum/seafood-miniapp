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

# 单模块 Spring Boot 4.0.6 + Java 25 + GraalVM Native(单仓收敛,7 模块已归档)
./gradlew build                  # JVM 单元测试
./gradlew nativeTest             # @Tag("native") 切片,带 GraalVM agent 收集 metadata
./gradlew nativeCompile          # 编译 native binary(~80MB,启动 < 2s)
./gradlew test -PexcludeTags=docker   # 无 Docker 环境跳过 Testcontainers IT

# 启动 (Docker Compose:2 服务 — backend GraalVM native + mongodb)
docker-compose up -d
docker-compose logs -f
docker-compose down
```

### 服务端口(Sprint 2 单仓)

| 服务 | 端口 | 镜像 / 形态 |
|------|------|------------|
| Backend (单仓,API + admin-ui 静态资源) | 8080 | `seafood-backend:native`(GraalVM binary,distroless) |
| MongoDB | 27017 | `mongo:7` |

> **Sprint 2 C5**:多模块 Spring Cloud 架构已收敛为单 Spring Boot 模块;`docker-compose up -d`
> 启动后 backend RSS 验收 < 200 MB,`/actuator/health` 30 s 内 200。完整冒烟见
> `backend/scripts/native-smoke.sh`。CI 工作流: `.github/workflows/native.yml`。


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
