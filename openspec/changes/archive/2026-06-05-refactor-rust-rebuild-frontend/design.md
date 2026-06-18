# Design: refactor-monolith-rebuild-frontend

> 配套 `proposal.md` 的技术设计文档。范围:Java 25 + Spring Boot 4 单仓 + GraalVM Native + 前后端重建。

---

## 1. 架构对比

### 1.1 当前架构 (待替换)

```
┌────────────────────────────────────────────────────────────┐
│                    docker-compose                          │
│                                                            │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐        │
│  │gateway  │  │product- │  │order-   │  │user-    │        │
│  │ :8080   │  │service  │  │service  │  │service  │        │
│  │         │  │ :8081   │  │ :8082   │  │ :8083   │        │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘        │
│       │            │            │            │             │
│       │    ┌───────┴────┐  ┌────┴────┐  ┌────┴────┐        │
│       │    │ discovery  │  │ config  │  │ common  │        │
│       │    │  (Eureka)  │  │ service │  │ module  │        │
│       │    └────────────┘  └─────────┘  └─────────┘        │
│       │                                                     │
└───────┼─────────────────────────────────────────────────────┘
        │ WebClient 跨进程
        ▼
   ┌─────────┐
   │ MongoDB │
   └─────────┘

进程数: 7
启动时间: ~10s (各服务并行)
内存基线: ~600MB 总和
跨服务调用: WebClient + 序列化 + 重试
```

### 1.2 目标架构

```
┌────────────────────────────────────────────────────────────┐
│                    docker-compose                          │
│                                                            │
│  ┌────────────────────────────────────────────────────┐    │
│  │           backend  (GraalVM Native binary)         │    │
│  │                                                    │    │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌──────┐   │    │
│  │  │ product │  │  order  │  │  user   │  │ bff  │   │    │
│  │  │ module  │  │ module  │  │ module  │  │ admin│   │    │
│  │  └─────────┘  └─────────┘  └─────────┘  └──────┘   │    │
│  │                                                    │    │
│  │  shared: JWT 错误处理 配置 DTO MongoDB 客户端       │    │
│  │  + 静态资源服务(托管 admin-ui 构建产物)            │    │
│  └───────────────────────────┬────────────────────────┘    │
│                              │                             │
└──────────────────────────────┼─────────────────────────────┘
                               │ 同进程方法调用 (bff → 商品/订单/用户)
                               │
                       ┌───────▼───────┐
                       │   MongoDB     │
                       └───────────────┘

进程数: 2 (backend + mongodb)
启动时间: < 2s (Native binary)
内存基线: < 200MB
跨"模块"调用: 函数调用 + 强类型签名
```

### 1.3 进程内模块边界 (Bounded Context)

虽然物理上是一个进程,但**包结构和接口契约**严格按 bounded context 划分:

```
com.seafood.product    ←→   com.seafood.order     (通过 OrderService 公开的接口)
com.seafood.user       ←→   com.seafood.product    (无直接依赖,共享 UserDTO)
com.seafood.bff.admin  ←→   上述三者 (通过各自 ApplicationService)
```

**关键约束**:模块间不能跨过 ApplicationService 直接访问 Repository。这样将来若要拆回微服务,只需把"方法调用"换成"HTTP 调用"。

---

## 2. 包结构

```
backend/
├── build.gradle                # GraalVM native 启用
├── settings.gradle             # 单 module: 'seafood-backend'
├── Dockerfile                  # multi-stage: build (GraalVM JDK) → runtime (distroless)
├── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── java/com/seafood/
│   │   │   ├── SeafoodApplication.java
│   │   │   ├── shared/
│   │   │   │   ├── config/
│   │   │   │   │   ├── MongoConfig.java
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── WebConfig.java
│   │   │   │   │   └── AppProperties.java          # @ConfigurationProperties
│   │   │   │   ├── security/
│   │   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   │   ├── JwtProperties.java
│   │   │   │   │   ├── UserPrincipal.java
│   │   │   │   │   └── Role.java                    # enum: CUSTOMER, ADMIN
│   │   │   │   ├── error/
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   ├── DomainException.java
│   │   │   │   │   ├── NotFoundException.java
│   │   │   │   │   ├── ValidationException.java
│   │   │   │   │   └── ErrorResponse.java           # record
│   │   │   │   ├── dto/
│   │   │   │   │   ├── PageRequest.java
│   │   │   │   │   ├── PageResponse.java
│   │   │   │   │   └── ApiResponse.java
│   │   │   │   └── infra/
│   │   │   │       └── MongoIndexInitializer.java   # 启动时建索引
│   │   │   ├── product/
│   │   │   │   ├── api/
│   │   │   │   │   ├── ProductController.java       # /api/products/**
│   │   │   │   │   └── dto/                          # ProductRequest/Response record
│   │   │   │   ├── application/
│   │   │   │   │   ├── ProductService.java          # @Service, 跨模块入口
│   │   │   │   │   └── ProductQueryService.java
│   │   │   │   ├── domain/
│   │   │   │   │   ├── Product.java                 # aggregate root (record)
│   │   │   │   │   ├── ProductId.java
│   │   │   │   │   ├── ProductCategory.java         # sealed interface
│   │   │   │   │   ├── ProductStatus.java
│   │   │   │   │   └── event/ProductEvent.java
│   │   │   │   └── infra/
│   │   │   │       ├── ProductRepository.java       # interface
│   │   │   │       ├── ProductMongoRepository.java  # Spring Data impl
│   │   │   │       └── ProductDocument.java         # @Document
│   │   │   ├── order/
│   │   │   │   ├── api/                              # /api/orders/**, /api/cart/**
│   │   │   │   ├── application/                      # OrderService, CartService
│   │   │   │   ├── domain/
│   │   │   │   │   ├── Order.java                   # aggregate root
│   │   │   │   │   ├── OrderItem.java
│   │   │   │   │   ├── OrderStatus.java             # sealed interface
│   │   │   │   │   ├── Cart.java
│   │   │   │   │   └── event/OrderEvent.java
│   │   │   │   └── infra/
│   │   │   ├── user/
│   │   │   │   ├── api/                              # /api/users/**, /api/auth/**
│   │   │   │   ├── application/                      # UserService, AuthService
│   │   │   │   ├── domain/
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── Customer.java                # record
│   │   │   │   │   ├── Admin.java
│   │   │   │   │   └── Address.java
│   │   │   │   └── infra/
│   │   │   └── bff/
│   │   │       └── admin/
│   │   │           ├── AdminBffController.java       # /api/admin/**
│   │   │           ├── AdminBffService.java          # 编排 product/order/user
│   │   │           ├── AdminCacheService.java       # Caffeine (本地内存,非 Redis)
│   │   │           └── dto/
│   │   │               ├── OrderDetailResponse.java
│   │   │               ├── ProductStatsResponse.java
│   │   │               └── DashboardResponse.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-docker.yml
│   │       ├── native-image/
│   │       │   ├── reflect-config.json               # GraalVM 反射配置
│   │       │   ├── resource-config.json
│   │       │   └── proxy-config.json
│   │       └── static/admin/                          # admin-ui 构建产物
│   └── test/
│       ├── java/com/seafood/                         # 单元 + 集成测试
│       └── resources/
└── seed/
    ├── seed.sh                                       # 启动后注入种子数据
    └── fixtures/
        ├── products.json
        ├── categories.json
        └── users.json
```

### 2.1 模块内部约定

每个 bounded context 内部统一分层:

```
api         →  Controller, Request/Response DTO (record)
application →  Service, UseCase, 跨聚合协调
domain      →  Aggregate Root, Entity, Value Object, Domain Event
infra       →  Repository 实现, MongoDB Document, 外部适配
```

**依赖方向**:`api → application → domain ← infra`。
**禁止**:`api` 直接调 `infra`;`application` 直接 new 一个 Repository(用构造注入);`domain` 依赖 Spring 注解。

---

## 3. GraalVM Native Image 配置

### 3.1 build.gradle 关键改动

```groovy
plugins {
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'org.graalvm.buildtools.native' version '0.11.5'  // 之前 apply false
}

graalvmNative {
    binaries {
        main {
            imageName = 'seafood-backend'
            mainClass = 'com.seafood.SeafoodApplication'
            buildArgs += [
                '--no-fallback',
                '-H:+ReportExceptionStackTraces',
                '--initialize-at-build-time=org.springframework',
                '--initialize-at-build-time=com.mongodb',
            ]
        }
    }
}
```

### 3.2 反射/资源/代理配置

Spring Boot 4 + GraalVM 25 已经能自动处理大部分场景,但仍有几处需要手工配置。

**reflect-config.json** (针对 `record` 和自定义 Jackson 序列化):

```json
[
  {
    "name": "com.seafood.product.api.dto.ProductResponse",
    "allDeclaredFields": true,
    "allDeclaredMethods": true,
    "allDeclaredConstructors": true
  },
  {
    "name": "com.seafood.order.domain.OrderStatus",
    "allDeclaredFields": true,
    "allPublicMethods": true
  }
]
```

> 实际通过 `graalvm-native-image-tester` 插件或 nativeTest 阶段生成,**不手工维护**。

### 3.3 已知 Native 模式陷阱

| 坑 | 触发条件 | 解决 |
|---|---|---|
| MongoDB driver 反射 | 序列化 BSON | 升级到 `mongodb-driver-sync` 5.2+ |
| Jackson record 支持 | Java 17+ records | Spring Boot 4 默认开启 |
| 启动时区 | `TimeZone.getDefault()` | `--initialize-at-build-time=java.util.TimeZone` |
| 配置文件 YAML 解析 | SnakeYAML | 需要 `-H:+AddAllCharsets` + reflect-config |
| **`@RefreshScope` 注解** | **任何使用 Spring Cloud Config 的 Bean** | **Native Image 不支持。代码中禁止出现该注解,需重启刷新配置。本项目已砍 Config Service,理论上不会用到;Code Review 必须拦截** |

### 3.4 Dockerfile (multi-stage)

```dockerfile
# Build stage
FROM ghcr.io/graalvm/native-image:ol9-java25 AS build
WORKDIR /app
COPY . .
RUN ./gradlew nativeCompile

# Runtime stage
FROM gcr.io/distroless/base-debian12
COPY --from=build /app/build/native/nativeCompile/seafood-backend /app/seafood-backend
EXPOSE 8080
ENTRYPOINT ["/app/seafood-backend"]
```

最终 binary ~80MB,无 JVM 依赖。

---

## 4. 鉴权与 JWT 流程

### 4.1 Token 结构

```json
// Access token (15 min)
{
  "sub": "user_id",
  "role": "CUSTOMER" | "ADMIN",
  "iat": 1717372800,
  "exp": 1717373700,
  "jti": "uuid"
}

// Refresh token (7 d)
{
  "sub": "user_id",
  "type": "refresh",
  "jti": "uuid",
  "iat": ...,
  "exp": ...
}
```

签名 HS256,密钥从 `JWT_SECRET` 环境变量读取 (启动时校验,缺失即 fail-fast)。

### 4.2 Filter Chain 顺序

```
HTTP request
  │
  ▼
JwtAuthenticationFilter            ← 解析 Authorization header, 设置 SecurityContext
  │   (skip: /api/auth/login, /api/auth/refresh, /api/products/**, /api/admin/auth/**)
  │
  ▼
RoleAuthorizationFilter            ← 校验 @PreAuthorize 注解
  │
  ▼
Controller
```

### 4.3 端点访问矩阵

| 端点前缀 | 角色 | 备注 |
|---|---|---|
| `POST /api/auth/login` | 公开 | |
| `POST /api/auth/refresh` | 公开 | |
| `GET  /api/products/**` | 公开 | |
| `POST/PUT/DELETE /api/products/**` | ADMIN | |
| `GET/POST/PUT/DELETE /api/cart/**` | CUSTOMER | |
| `GET/POST /api/orders/**` | CUSTOMER (own) / ADMIN (all) | |
| `GET /api/admin/**` | ADMIN | BFF 聚合 |
| `/api/admin/auth/login` | 公开 | 与小程序登录分离,独立密钥 |

### 4.4 RBAC 实现

用 Spring Security 6 的 `@PreAuthorize` + method security:

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/api/admin/dashboard")
public DashboardResponse dashboard() { ... }

@PreAuthorize("hasRole('CUSTOMER') and #userId == authentication.principal.id")
@GetMapping("/api/orders")
public List<OrderResponse> myOrders(@PathVariable String userId) { ... }
```

---

## 5. BFF 聚合层

### 5.1 3 个端点 (原 Gateway 职责下沉到本进程)

> **当前决策:不缓存**。admin 后台访问量低,先跑通再说。当 P99 > 500ms 或 DB 查询成为瓶颈时再考虑加 Caffeine/Redis。

```
GET /api/admin/orders/{id}/detail
  ── input: orderId
  ── output: { order, customer, items[].product }
  ── 调用链: orderService.get(orderId) → userService.get(order.customerId) → productService.batchGet(items[].productId)

GET /api/admin/products/stats
  ── output: { total, onSale, outOfStock, byCategory: { "鱼类": 12, "虾蟹": 8, ... } }
  ── 调用链: productService.count, productService.groupByCategory

GET /api/admin/dashboard
  ── output: { orderStats: {today, week, month}, productStats, topProducts[] }
  ── 调用链: 同上 + orderService.aggregate
```

### 5.2 性能策略 (当前:不缓存)

```
现状                  触发条件 (满足任一即加缓存)
──────────          ─────────────────────────────
不缓存              P99 延迟 > 500ms
                   DB 查询 QPS > 1000
                   单次聚合涉及 > 5 个 collection

加什么: 先 Caffeine (单进程), 跨实例再换 Redis
```

### 5.3 错误处理

`GlobalExceptionHandler` 统一处理:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("NOT_FOUND", e.getMessage(), null));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> validation(ValidationException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION", e.getMessage(), e.getFieldErrors()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> domain(DomainException e) {
        return ResponseEntity.status(409)
            .body(new ErrorResponse("DOMAIN", e.getMessage(), null));
    }
}
```

---

## 6. 数据模型

### 6.1 MongoDB Collections

```javascript
// products
{
  _id: ObjectId,
  name: String,                  // indexed (text)
  description: String,
  price: BigDecimal,
  stock: Int,
  category: String,              // indexed
  imageUrl: String,
  onSale: Boolean,               // indexed
  createdAt: Instant,
  updatedAt: Instant
}

// orders
{
  _id: ObjectId,
  userId: ObjectId,              // indexed
  status: String,                // indexed, enum: PENDING/PAID/SHIPPED/COMPLETED/CANCELLED
  items: [{
    productId: ObjectId,
    name: String,                // 快照
    price: BigDecimal,            // 快照
    quantity: Int
  }],
  totalAmount: BigDecimal,
  createdAt: Instant,            // indexed
  updatedAt: Instant
}

// users
{
  _id: ObjectId,
  openId: String,                // unique, indexed (微信 openid)
  unionId: String,               // indexed (sparse)
  nickname: String,
  avatarUrl: String,
  role: String,                  // CUSTOMER | ADMIN
  phone: String,                 // indexed (sparse)
  addresses: [{
    id: String,                  // UUID
    name: String, phone: String,
    province: String, city: String,
    detail: String, isDefault: Boolean
  }],
  createdAt: Instant
}

// carts (per user, 1 document)
{
  _id: userId,                   // 用 userId 作 _id
  items: [{
    productId: ObjectId,
    quantity: Int,
    selected: Boolean,
    addedAt: Instant
  }],
  updatedAt: Instant
}
```

### 6.2 索引

启动时 `MongoIndexInitializer` 检查并创建:

```java
@Component
public class MongoIndexInitializer {
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        productCollection.createIndex(Indexes.text("name", "description"));
        productCollection.createIndex(Indexes.ascending("category"));
        productCollection.createIndex(Indexes.ascending("onSale"));
        orderCollection.createIndex(Indexes.ascending("userId"));
        orderCollection.createIndex(Indexes.ascending("status"));
        orderCollection.createIndex(Indexes.descending("createdAt"));
        userCollection.createIndex(Indexes.ascending("openId"), new IndexOptions().unique(true));
    }
}
```

### 6.3 Aggregate Root 设计 (示例: Order)

```java
public final class Order {
    private final OrderId id;
    private final UserId userId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    // 业务方法, 状态转移在这里
    public void markPaid(PaymentRef ref, Instant when) {
        if (this.status != OrderStatus.PENDING) {
            throw new DomainException("只有待支付订单可以标记已支付");
        }
        this.status = OrderStatus.PAID;
        this.paymentRef = ref;
        this.updatedAt = when;
        registerEvent(new OrderPaidEvent(this.id, when));
    }

    public void cancel(String reason, Instant when) {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.COMPLETED) {
            throw new DomainException("已发货/已完成订单不能取消");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = when;
        registerEvent(new OrderCancelledEvent(this.id, reason, when));
    }
}
```

---

## 7. 前端架构

### 7.1 小程序 (frontend/) — feature-based 重构

**原结构** (按类型):
```
frontend/
├── pages/         (所有页面堆一起)
├── utils/
├── types/
└── api/
```

**新结构** (按 feature):
```
frontend/
├── src/
│   ├── app.ts                  # App 入口
│   ├── shared/                 # 跨 feature 复用
│   │   ├── api/                # request, auth header
│   │   ├── components/         # 通用 UI (Button, Empty, Loading)
│   │   ├── hooks/              # useRequest, useAuth
│   │   ├── tokens/             # 设计令牌 (颜色/间距)
│   │   └── types/              # 通用 DTO
│   ├── features/
│   │   ├── product/
│   │   │   ├── api.ts
│   │   │   ├── types.ts
│   │   │   └── components/     # ProductCard, ProductList
│   │   ├── cart/
│   │   │   ├── api.ts
│   │   │   ├── store.ts        # 轻量 store (无 Redux)
│   │   │   └── components/
│   │   ├── order/
│   │   ├── user/               # 含登录、地址管理
│   │   └── admin/              # 管理后台入口 (跳转 admin-ui)
│   └── pages/                  # 只放 .wxml/.wxss/.ts, 引用 features
└── pages/                      # 原生 app.json 路由
```

设计令牌与 admin-ui 共享同一份 tokens (CI 同步)。

### 7.2 Admin UI (admin-ui/) — React 18 + shadcn/ui

```
admin-ui/
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── router.tsx              # React Router v6
│   ├── lib/
│   │   ├── api.ts              # Axios instance
│   │   ├── query-client.ts     # React Query
│   │   └── utils.ts            # cn, formatDate
│   ├── components/
│   │   ├── ui/                 # shadcn 生成 (Button, Card, Dialog...)
│   │   ├── layout/             # AppShell, Sidebar, Topbar
│   │   └── domain/             # ProductForm, OrderDetail, UserTable
│   ├── features/
│   │   ├── auth/
│   │   ├── products/
│   │   ├── orders/
│   │   ├── users/
│   │   └── dashboard/
│   ├── stores/                 # Zustand (auth state, ui state)
│   └── types/
├── tailwind.config.ts
├── components.json             # shadcn 配置
└── vite.config.ts
```

**关键集成**:
- 后端用 Spring 静态资源托管 `classpath:/static/admin/*` → 访问 `/admin/` 直接命中
- API 反向代理:开发用 Vite proxy,生产直接同源

### 7.3 shadcn/ui 引入

```bash
npx shadcn@latest init           # 生成 components.json + tsconfig path
npx shadcn@latest add button card dialog form input select
# 组件代码复制到 src/components/ui/, 可任意修改
```

---

## 8. 测试策略

### 8.1 覆盖率目标

| 层 | 工具 | 目标 |
|---|---|---|
| Domain | JUnit 5 | 95% (纯逻辑,无 Spring) |
| Application | JUnit 5 + Mockito | 85% |
| API | @WebMvcTest + MockMvc | 80% |
| Infra | @DataMongoTest | 70% (集成覆盖到) |
| E2E | Playwright (admin) + 微信开发者工具 (mini-program) | 关键路径 100% |

### 8.2 集成测试:Testcontainers MongoDB

```java
@SpringBootTest
@Testcontainers
class OrderServiceIT {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired OrderService orderService;

    @Test
    void createOrder_reducesStock() { ... }
}
```

### 8.3 E2E:Playwright + 微信开发者工具

Admin UI E2E:
```ts
test('admin can list orders', async ({ page }) => {
  await page.goto('/admin/login');
  await page.fill('[name=username]', 'admin');
  await page.fill('[name=password]', 'admin123');
  await page.click('button[type=submit]');
  await page.goto('/admin/orders');
  await expect(page.getByRole('table')).toBeVisible();
});
```

小程序 E2E:用 `mcp__weapp-dev` 工具集(已在 MCP 配置中)。

---

## 9. 迁移计划 (Cutover)

### 9.1 三步切流

```
Step 1: 双跑 (Week 6-7)
  ├─ 新单仓部署到不同端口 (8081)
  ├─ 旧 7 服务继续在 8080
  ├─ Admin UI 切到新端口
  └─ 小程序不动, 仍走 8080

Step 2: 小程序切流 (Week 8)
  ├─ 跑通小程序所有 E2E (新端口)
  ├─ DNS / API base 切换
  ├─ 保留 8080 作为 fallback 24h
  └─ 监控 1 天

Step 3: 旧服务下线 (Week 8 末)
  ├─ 关闭 gateway/product/order/user
  ├─ docker-compose 切到新版本
  └─ 旧代码 git tag: v2.0-multi-module-archived
```

### 9.2 回滚策略

每一步切流前都要能 30 秒内回滚:

```
Step 1 回滚: nginx upstream 切回 8080
Step 2 回滚: DNS / API base 切回 8080
Step 3 回滚: git revert + 重启旧 docker-compose
```

### 9.3 数据迁移

**不迁移**。旧 MongoDB 数据归档到 `archive/seafood-mongo-2026-08-12.bson`。
新系统从 fixtures 重新 seed,包括 admin 账号 (admin/admin123,登录后强制改密码)。

---

## 10. 开放风险

| # | 风险 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|
| 1 | Spring Boot 4 + Java 25 + GraalVM 三者组合较新,反射配置不全 | 中 | 高 | Phase 2 末必须用 Native binary 跑通核心 E2E;不通过则回退 JVM 部署 |
| 2 | 旧服务有些用 Spring Cloud Sleuth 做链路追踪,Native 模式不兼容 | 中 | 中 | 改用 Micrometer Tracing,OpenTelemetry exporter |
| 3 | Admin UI 的 shadcn 默认样式达不到"美观"验收 | 中 | 中 | 提前选 1 个产品作为视觉参考,定义 design tokens |
| 4 | 旧业务代码迁移时丢失细节(异常处理、边界条件) | 高 | 中 | 先做 vertical slice 对比旧实现行为,再批量迁移 |
| 5 | 微信支付回调的签名验证库 Native 不兼容 | 低 | 高 | Phase 2 显式验证,失败则该端点用 JVM 容器单独跑 |
| 6 | 1 人项目中途被打断 | 中 | 高 | 每个 Phase 结束 git tag,确保可恢复 |
| 7 | **`@RefreshScope` 注解不兼容 GraalVM Native** | 低 | 中 | **本项目已砍 Config Service,理论上不引入。Code Review + 静态扫描拦截,见 §3.3** |

---

## 11. 不在本文档范围

- 详细的 API 端点契约:见后续 `specs/backend-api/spec.md`
- 前端组件库具体用法:见 admin-ui / 微信小程序各自 README
- 性能压测结果:Phase 5 末产出 `TEST_REPORT.md`
- 部署文档:Phase 5 末产出 `DEPLOY.md`

---

*本设计文档与 proposal.md 配套,实施时以 proposal 为决策依据,以本文档为实现参考。*
