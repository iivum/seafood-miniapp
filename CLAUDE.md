# CLAUDE.md - 海鲜商城小程序

> 本文件为 Claude Code 等 AI 编程工具提供项目开发指导。

---

## 项目概述

| 项目 | 说明 |
|------|------|
| **名称** | 海鲜商城小程序 (Seafood E-commerce Mini Program) |
| **类型** | 微信小程序 + Spring Cloud 微服务 |
| **状态** | 开发中 |

**技术栈：**
- 前端：微信小程序 (TypeScript 5.x, Jest 29.x, ESLint 8.x)
- 后端：Java 17+, Spring Boot 3.2.4, Spring Cloud 2023.0.0, Gradle 9.x
- 数据库：MongoDB 6.x
- 服务发现：Eureka

**目录结构：**
```
seafood-miniapp/
├── frontend/              # 微信小程序
│   ├── src/
│   │   ├── api/         # API调用层
│   │   ├── modules/     # 业务模块 (TDD测试优先)
│   │   ├── types/       # TypeScript类型定义
│   │   └── utils/       # 工具函数
│   └── pages/           # 页面
└── backend/             # Spring Cloud微服务
    ├── gateway/         # API网关 - 8080
    ├── product-service/ # 商品服务 - 8081
    ├── order-service/   # 订单服务 - 8082
    └── user-service/    # 用户服务 - 8083
```

---

## 关键规则

### 1. 代码组织
- 多小文件优于少大文件：单个文件 200-400 行，控制在 800 行以内
- 高内聚低耦合：按功能/领域组织，而非按类型
- 前后端分离：前端 `frontend/`，后端 `backend/`

### 2. 代码风格
- **前端**：TypeScript strict mode，禁止 `any`（测试文件除外）
- **后端**：Google Java Format，行宽 120 字符
- 使用 MapStruct 进行对象映射，Lombok 简化代码
- 无 `console.log` 在生产代码中

### 3. 测试要求
- TDD 优先：先写测试 → 实现 → 重构
- 覆盖率：全局 ≥80%，核心模块 ≥90%，关键功能 100%

### 4. 安全要求
- 禁止硬编码密钥，所有敏感信息使用环境变量
- 所有用户输入必须验证和过滤
- XSS 防护：参数 HTML 转义
- JWT Token 认证（待实现）

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
  id: string;
  name: string;
  description: string;
  price: number;
  stock: number;
  category: string;
  imageUrl: string;
  onSale: boolean;
}

interface CartItem {
  id: string;
  productId: string;
  name: string;
  price: number;
  quantity: number;
  imageUrl: string;
}

interface Cart {
  id: string;
  items: CartItem[];
  totalPrice: number;
  totalItems: number;
  selectedItems: string[];
}
```

### 前端 API 层

```typescript
// src/api/product.ts
export const ProductAPI = {
  async getProducts(params: {
    page?: number;
    pageSize?: number;
    category?: string;
    keyword?: string;
  }): Promise<PaginatedProducts> {
    return request.get('/products', { params });
  },
};
```

### 后端 Controller

```java
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public ApiResponse<PagedResult<ProductDTO>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(productService.findProducts(page, pageSize));
    }
}
```

### 服务依赖关系

```
                ┌─────────────────┐
                │  Gateway 8080  │
                └────────┬────────┘
                         │
    ┌────────────────────┼────────────────────┐
    │                    │                    │
┌───▼────┐        ┌─────▼─────┐      ┌──────▼──────┐
│Product │        │  Order    │      │    User     │
│  8081  │        │   8082    │      │    8083    │
└────────┘        └───────────┘      └─────────────┘
                         │
                   ┌─────▼─────┐
                   │  MongoDB  │
                   │   27017   │
                   └───────────┘
```

---

## 环境变量

```bash
# 后端
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/seafood
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka/

# 前端
API_BASE_URL=http://localhost:8080
```

---

## 可用命令

| 命令 | 说明 |
|------|------|
| `/plan` | 创建实施计划 |
| `/tdd` | 测试驱动开发工作流 |
| `/code-review` | 代码质量审查 |

### 前端
```bash
cd frontend && npm test                  # 运行测试
npm test -- --coverage                   # 带覆盖率
npm run lint && npm run type-check       # 检查
```

### 后端
```bash
cd backend && ./gradlew build            # 构建
./gradlew :product-service:bootRun       # 运行服务
./gradlew test                          # 测试
```

### Docker
```bash
docker-compose up -d                     # 启动
docker-compose logs -f                   # 日志
```

---

## Git 工作流

**提交格式 (Conventional Commits)：**
```
feat: 新功能 | fix: 修复bug | refactor: 重构
docs: 文档 | test: 测试 | style: 格式
```

**分支策略：**
```
main ──────────────────────────────────┐
  └─ develop ─── feat/user-login       │
              └── feat/product-detail  │
```

**PR 要求：** 代码审查 + 测试通过 + ESLint 通过

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

*详细文档：ARCHITECTURE.md, SPEC.md, TODO.md*
