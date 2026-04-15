# CLAUDE.md

本文件为 Claude Code 等 AI 编程工具提供项目开发指导。

---

## 项目概述

**海鲜商城小程序** - 微信小程序 + Spring Cloud 微服务电商平台

- **前端**：微信小程序 (TypeScript 5.x, Jest 29.x, ESLint 8.x)
- **后端**：Java 17+, Spring Boot 3.2.4, Spring Cloud 2023.0.0, Gradle 9.x
- **数据库**：MongoDB 6.x
- **服务发现**：Eureka
- **测试覆盖率**：前端 ≥88%, 后端 ≥80%

---

## 运行测试

```bash
# 前端测试
cd frontend
npm test                                    # 运行所有测试
npm test -- src/api/product.test.ts         # 单文件测试
npm test -- --coverage                      # 带覆盖率

# 后端测试
cd backend
./gradlew test                              # 运行所有测试
./gradlew :product-service:test            # 单模块测试
```

---

## 项目架构

```
seafood-miniapp/
├── frontend/                           # 微信小程序
│   ├── src/
│   │   ├── api/                      # API调用层
│   │   ├── modules/                  # 业务模块 (TDD测试优先)
│   │   ├── types/                    # TypeScript类型定义
│   │   └── utils/                    # 工具函数
│   └── pages/                        # 页面目录
│
└── backend/                           # Spring Cloud微服务
    ├── gateway/                      # API网关 - 8080
    ├── product-service/              # 商品服务 - 8081
    ├── order-service/                # 订单服务 - 8082
    ├── user-service/                 # 用户服务 - 8083
    └── common/                       # 公共模块

服务依赖关系：
gateway (8080) → product-service (8081), order-service (8082), user-service (8083)
                         ↓
                   MongoDB (27017)
```

---

## 关键规则

### 代码组织
- 多小文件优于少大文件：单文件 200-400 行，≤800 行
- 高内聚低耦合：按功能/领域组织，而非按类型
- 前后端分离：前端 `frontend/`，后端 `backend/`

### 代码风格
- **前端**：TypeScript strict mode，禁止 `any`（测试文件除外）
- **后端**：Google Java Format，行宽 120 字符
- 无 `console.log` 在生产代码中

### 测试要求
- **TDD 优先**：先写测试 → 实现 → 重构
- 覆盖率：全局 ≥80%，核心模块 ≥90%，关键功能 100%

### 安全要求
- 禁止硬编码密钥，使用环境变量
- 所有用户输入验证和过滤
- XSS 防护，JWT Token 认证（待实现）

---

## 核心模式

### API 响应格式

```typescript
// 成功响应
{ success: true, data: T, error: null, meta: { page, per_page, total } }

// 错误响应
{ success: false, data: null, error: "错误描述", code: "ERROR_CODE" }
```

### 数据模型

```typescript
interface Product {
  id: string; name: string; description: string;
  price: number; stock: number; category: string;
  imageUrl: string; onSale: boolean;
}

interface CartItem {
  id: string; productId: string; name: string;
  price: number; quantity: number; imageUrl: string;
}
```

---

## 可用命令

| 命令 | 说明 |
|------|------|
| `/plan` | 创建实施计划 |
| `/tdd` | 测试驱动开发工作流 |
| `/code-review` | 代码质量审查 |
| `/security-scan` | 安全漏洞扫描 |
| `/build-fix` | 修复构建错误 |

---

## 开发说明

### 环境变量
```bash
# 后端
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/seafood
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# 前端
API_BASE_URL=http://localhost:8080
```

### Docker 部署
```bash
docker-compose up -d              # 启动所有服务
docker-compose logs -f            # 查看日志
docker-compose down               # 停止服务
```

### Git 工作流
- **提交格式**：`feat:` `fix:` `refactor:` `docs:` `test:`
- **分支策略**：`main` → `develop` → `feat/*` 或 `fix/*`
- **PR 要求**：代码审查 + 测试通过 + ESLint 通过

---

## 性能要求

- 首屏加载 < 2秒
- 页面切换 < 300ms
- API 响应 < 500ms

---

## 重要提示

1. **TDD 优先**：所有新功能必须先写测试
2. **类型安全**：严禁 `any`（测试文件除外）
3. **安全审查**：所有代码需通过安全检查

---

## 相关文档

- `ARCHITECTURE.md` - 系统架构详细文档
- `SPEC.md` - 功能规格说明
- `TODO.md` - 开发任务列表
- `docs/` - 其他文档

---

*本文件为 AI 开发辅助文档，具体实现请参考代码注释和测试用例。*
