# PHASE 2 DONE — 收口归档

**日期**: 2026-06-03
**Phase 2 W3-6 范围**: 7 模块业务逻辑迁入新单仓 + 启用 GraalVM Native
**结论**: 业务逻辑完整,JVM 部署就绪;Native 路径单独归档,等生态成熟

---

## 完成清单

| 项 | 状态 | 备注 |
|---|---|---|
| Order module (Cart + Order + 状态机) | ✅ | `sealed` 状态机 + 库存校验 + 跨用户 403 |
| Product admin CRUD (POST/PUT/DELETE) | ✅ | URL 级 RBAC 保护 |
| Auth 端到端 (register/login/refresh) | ✅ | admin @PostConstruct bootstrap |
| MongoDB 集成 | ✅ | docker compose mongodb 7.0 |
| 错误处理统一 (RFC 7807-like) | ✅ | ErrorCode → HTTP status 派发 |
| 12 集成测试通过 (JVM) | ✅ | 5 vertical + 7 order |
| GraalVM Native 二进制 | ⏸️ 暂停 | 详见 NATIVE_STATUS.md |

## 文件树

```
backend-v2/
├── build.gradle              # 4 starter + JJWT + testcontainers
├── settings.gradle
├── docker-compose.yml        # MongoDB 7.0
├── NATIVE_STATUS.md          # Native 路径当前位置
├── PHASE2_DONE.md            # 本文件
├── seed/products.json        # 8 海鲜 fixture
├── .gitignore
├── gradle/ + gradlew + gradlew.bat
├── src/main/java/com/seafood/
│   ├── SeafoodApplication.java
│   ├── shared/
│   │   ├── config/SecurityConfig.java
│   │   ├── config/AdminBootstrap.java
│   │   ├── security/  (JwtTokenProvider, Filter, UserPrincipal, Role, JwtProperties)
│   │   └── error/     (GlobalExceptionHandler, DomainException, NotFoundException, ValidationException, ErrorResponse, ErrorCode)
│   ├── user/
│   │   ├── domain/User.java
│   │   ├── infra/UserMongoRepository.java
│   │   ├── application/AuthService.java
│   │   ├── api/AuthController.java
│   │   └── api/dto/   (4 record)
│   ├── product/
│   │   ├── domain/Product.java
│   │   ├── infra/ProductMongoRepository.java
│   │   ├── application/ProductService.java
│   │   ├── api/ProductController.java
│   │   └── api/dto/   (3 record)
│   └── order/
│       ├── domain/    (Order, OrderItem, OrderStatus, Cart)
│       ├── infra/     (OrderMongoRepository, CartMongoRepository, ProductStockPort)
│       ├── application/(OrderService, CartService)
│       ├── api/       (OrderController, CartController)
│       └── api/dto/   (5 record)
└── src/test/
    ├── resources/application.yml
    └── java/com/seafood/
        ├── VerticalSliceIT.java  # 5 tests
        └── OrderIT.java          # 7 tests
```

## 端点清单 (Phase 2 完成)

### 公开
- `GET  /api/products`         — 商品列表 (分页/分类/搜索)
- `GET  /api/products/{id}`    — 商品详情
- `GET  /actuator/health`

### 认证
- `POST /api/auth/register`    — 用户注册
- `POST /api/auth/login`       — 登录
- `POST /api/auth/refresh`     — 刷新 token

### 顾客 (CUSTOMER 角色)
- `GET  /api/cart`                              — 购物车
- `POST /api/cart/items`                        — 加商品
- `PUT  /api/cart/items/{productId}`            — 改数量
- `DELETE /api/cart/items/{productId}`          — 删商品
- `PATCH /api/cart/items/{productId}/toggle`    — 切换选中
- `POST /api/orders`                            — 结算
- `GET  /api/orders/me`                         — 我的订单
- `GET  /api/orders/{id}`                       — 订单详情
- `PATCH /api/orders/{id}/cancel`               — 取消订单

### 管理员 (ADMIN 角色)
- `POST   /api/products`             — 创建商品
- `PUT    /api/products/{id}`        — 修改商品
- `DELETE /api/products/{id}`        — 删除商品
- `PATCH  /api/orders/{id}/pay?paymentRef=...`  — 标记已支付
- (未来) `ship()`, `complete()` — admin 操作链

## 测试覆盖

```
12/12 tests passing
- 5 vertical slice (auth + product list + error envelopes)
- 7 order flow (cart → checkout → pay → cancel + 跨用户 403 + admin 权限)
```

## 验证命令

```bash
# 启动 MongoDB
cd /Users/linbinghui/agent-work/seafood-miniapp/backend-v2
DOCKER_HOST=unix:///var/run/docker.sock docker compose up -d mongodb

# 跑测试
export JAVA_HOME=/opt/homebrew/opt/graalvm
export PATH=/opt/homebrew/opt/graalvm/bin:$PATH
./gradlew --no-daemon test

# 启动服务
./gradlew --no-daemon bootRun
# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

## 已知技术债 (Phase 3+ 处理)

| 项 | 描述 | 优先级 |
|---|---|---|
| GraalVM Native | Spring Boot 4 + 生态组合未成熟,暂停 | 中(等生态) |
| 小程序重构 | feature-based 目录,统一 design tokens | 高 |
| Admin UI | React + shadcn/ui 重写 | 高 |
| BFF 端点 | 3 个聚合端在 phase 4-5 接入 | 中 |
| CI/CD | GitHub Actions 配置 | 中 |
| Docker 单机部署 | Dockerfile + 文档 | 中 |
| 微信支付回调 | mock 已就位,真实接入 | 低 |
| 监控 (Micrometer + OTel) | 替换 Spring Cloud Sleuth | 中 |

## 关键设计决策

1. **单仓 + bounded context 包结构** — `com.seafood.{shared,user,product,order}`,模块间只能通过 ApplicationService 通信
2. **Sealed status pattern** — `Order.markPaid/ship/complete/cancel` 状态转移有类型检查
3. **Anti-corruption layer** — `ProductStockPort` 隔离 order 模块对 product repo 的直接访问
4. **URL 级 RBAC (无 @PreAuthorize)** — 避免 CGLIB 代理跟未来 Native 冲突
5. **Records 全用** — 不用 Lombok (1.18.30 不支持 Java 25)
6. **No cache** — YAGNI,见 design.md §5.2 触发条件
7. **No microservices** — 单进程,1 个 binary,见 proposal 决策记录
8. **No Eureka / Config** — Docker 单机不需要

## 下一步候选

1. **Phase 4: 前端两件套** (5-6 周) — 小程序重构 + Admin UI 新栈
2. **Git commit + 开新会话** — 收口当前进度
3. **Phase 3: BFF 端点** — 可跳过,功能已在 order 模块内

建议: 开新会话,留 commit 把 Phase 2 锁住,再开前端。

---

*本文档作为 Phase 2 收口产物,记录当前已交付内容和未来 Phase 的入口。*
