# CLAUDE.md - 海鲜商城小程序

## 项目

微信小程序 + Spring Cloud 微服务海鲜电商平台

**技术栈：**
- 前端：微信小程序 (TypeScript, Jest, ESLint)
- 后端：Spring Boot 3.2.4, Spring Cloud 2023.0.0, Gradle 9.x
- 数据库：MongoDB 6.x
- 架构：Eureka服务发现, API网关, 微服务分离

**目录结构：**
```
seafood-miniapp/
├── frontend/           # 微信小程序
│   ├── src/api/       # API调用层
│   ├── src/modules/   # 业务模块 (TDD优先)
│   ├── src/types/     # TypeScript类型
│   └── pages/         # 页面
└── backend/           # Spring Cloud微服务
    ├── gateway/       # 端口8080
    ├── product-service/   # 端口8081
    ├── order-service/     # 端口8082
    └── user-service/      # 端口8083
```

---

## 关键规则

### 代码
- 单文件≤400行, 核心≤200行
- TypeScript禁止`any`, 测试文件除外
- 后端使用Google Java Format, 行宽120

### 测试
- TDD优先：先写测试 → 实现 → 重构
- 覆盖率：全局≥80%, 核心≥90%, 关键功能100%

### 安全
- 禁止硬编码密钥，使用环境变量
- 所有输入验证，XSS防护
- JWT Token认证（待实现）

---

## API约定

**Base URL：** `http://localhost:8080`

**响应格式：**
```typescript
{ success: true, data: T, error?: string, meta?: PageMeta }
```

**分页响应：**
```typescript
{ products: Product[], page, totalPages, totalProducts, hasNext, hasPrev }
```

---

## 命令

```bash
# 前端
cd frontend && npm test                    # 测试
npm test -- --coverage                     # 覆盖率
npm run lint && npm run type-check         # 检查

# 后端
cd backend && ./gradlew build              # 构建
./gradlew :product-service:bootRun         # 运行单个服务

# Docker
docker-compose up -d                       # 启动
docker-compose logs -f                     # 日志
```

---

## Git

**提交格式：** `feat:` `fix:` `refactor:` `docs:` `test:`

**分支：** `main` → `develop` → `feat/*` 或 `fix/*`

**要求：** ESLint通过 → 类型检查 → 测试通过 → 审查合并

---

## 重要提示

1. **TDD优先**：所有新功能必须先写测试
2. **类型安全**：严禁`any`（测试文件除外）
3. **性能目标**：首屏<2s, 页面切换<300ms

---

*详细文档：ARCHITECTURE.md, SPEC.md, TODO.md*
