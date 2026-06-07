# CLAUDE.md

本文件为 Claude Code 等 AI 编程工具提供项目开发指导。

---

## 项目概述

**海鲜商城小程序** - 微信小程序 + Spring Boot 单仓电商平台(由原 7 模块 Spring Cloud 收敛而来)

- **前端**：微信小程序 (TypeScript 5.x, Jest 29.x, ESLint 8.x)
- **管理后台**：规划 React 18 + shadcn/ui + Vite(§9 任务,Vue admin-ui 已废弃)
- **后端**：Java 25, Spring Boot 4.0.6, GraalVM Native, Gradle 9.x
- **数据库**：MongoDB 7.x(单库,不分微服务)
- **服务发现/配置**：已砍(单进程不需要)
- **部署**：2 服务 docker-compose(backend Native binary + mongodb)
- **测试覆盖率**：前端 ≥88%, 后端 ≥80%
- **当前分支**:`main`(生产中 7 模块已归档) ↔ `feature/refactor`(单仓,推送待 PR)

---

## 运行测试

```bash
# 前端测试(小程序)
cd frontend
npm test                                    # 运行所有测试
npm test -- --coverage                      # 带覆盖率

# 后端测试(单 Spring Boot 模块,77 例)
cd backend
./gradlew test                              # 全部 + 报告 build/test-results/
./gradlew check                             # 含 checkNoRefreshScope 静态扫描 + ArchUnit
./gradlew compileJava                       # 仅编译,快速语法校验
./gradlew :test --tests "*ProductTest"      # 单类测试
./gradlew test -PexcludeTags=docker         # 跳过 Testcontainers IT(无 Docker 环境)
./gradlew :test --tests "*ArchitectureTest" # 仅 DDD 分层规则,毫秒级
./gradlew nativeTest                        # @Tag("native") 切片,带 GraalVM agent 收集 metadata
./gradlew nativeCompile                     # 用 agent 产出的 metadata 编译 native binary
```

> **nativeTest 切片(Sprint 2 C5 §5.2)**:以下 IT 已打 `@Tag("native")` — 它们是
> `nativeTest` 阶段让 GraalVM tracing agent 收集反射/资源/代理 metadata 的最小
> 代表集:1 个 controller IT (`AdminRateLimitIT`)、1 个 repository IT
> (`ProductDocumentRepositoryIT`)、1 个 security filter IT (`SecurityHeadersIT`)。
> 加新代码路径时,在对应测试上加 `@Tag("native")` 或扩展这 3 个用例之一,再跑
> `./gradlew nativeTest` 让 agent 把新 metadata 写入
> `build/native/agent-output/test/`,然后 commit
> `src/main/resources/META-INF/native-image/` 的更新。

> **JDK 25 toolchain**:`gradle.properties` 已配 `org.gradle.java.installations.paths` 指向 GraalVM Homebrew。本机无 JDK 25 时,`./gradlew test` 直接失败 — 装 GraalVM CE 25+。

---

## 项目架构

```
seafood-miniapp/
├── frontend/                           # 微信小程序
│   ├── src/
│   │   ├── shared/                   # 跨 feature(api/components/hooks/tokens)
│   │   ├── features/{product,cart,order,user,admin}/
│   │   └── pages/                    # WXML/WXSS/.ts 路由入口
│   └── pages/                        # 原生 app.json 路由
│
├── backend/                           # 单 Spring Boot 模块(端口 8080)
│   ├── build.gradle / settings.gradle
│   ├── Dockerfile                     # 多阶段 GraalVM → distroless
│   ├── src/main/java/com/seafood/
│   │   ├── SeafoodApplication.java
│   │   ├── shared/                   # config/security/error/dto/infra
│   │   ├── product/{api,application,domain,infra}/
│   │   ├── order/{api,application,domain,infra}/
│   │   ├── user/{api,application,domain,infra}/
│   │   └── bff/admin/                # /api/admin/** 3 端点
│   ├── src/main/resources/
│   │   ├── application.yml           # JWT fail-fast、虚拟线程
│   │   └── META-INF/native-image/    # 反射/资源/代理 JSON 占位
│   ├── seed/                         # 50 商品 / 5 分类 / 2 用户 fixtures
│   └── scripts/check-no-refresh-scope.sh
│
├── openspec/changes/<name>/           # OpenSpec proposal/design/specs/tasks
└── archive/backend-multi-module-2026-06/  # .gitignore'd;旧 7 模块源备份
```

**包内分层**(每个 bounded context):
```
api         →  Controller + Request/Response DTO (record)
application →  Service + UseCase + 跨模块入口
domain      →  Aggregate Root + Entity + Value Object + Domain Event
infra       →  Repository 实现 + MongoDB Document
```

**跨模块约束**(design §1.3):模块间只通过 ApplicationService 调用,绝不跨过 Repository。

服务依赖关系(单进程):
```
HTTP → [ JwtAuthenticationFilter → @PreAuthorize → Controller
       → ApplicationService(跨模块只调 ApplicationService,不是 Repository)
       → MongoDB ]
```

**管理后台架构**(目标态):
```
React 18 SPA (admin-ui/) → backend BFF (/api/admin/**) → 同进程内 ApplicationService 编排
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
- XSS 防护，JWT Token 认证
- Admin UI 使用 httpOnly Cookie 存储 JWT

### 设计准则
- **微信小程序**：颜色变量在 `app.wxss`，安全区域 `padding-bottom: var(--safe-area-bottom)`
- **Admin UI**：使用 `frontend/admin-design/` 中的设计令牌和 Element Plus 主题
- 详细规范见 [`DESIGN.md`](./docs/DESIGN.md)

---

## 核心模式

### API 响应格式

```typescript
// 成功:直接返 record(不是 {success, data})
// 失败:统一 ErrorResponse 形态(参见 shared/error/ErrorResponse.java)
{
  code: "NOT_FOUND" | "VALIDATION" | "DOMAIN" | "TOKEN_EXPIRED" | "TOKEN_INVALID" | "TOKEN_REUSED",
  message: "人类可读描述",
  fieldErrors: { fieldName: "msg" }  // 仅 VALIDATION 时填充
}
```

HTTP 状态映射:`NOT_FOUND → 404` / `VALIDATION → 400` / `DOMAIN → 409` / `TOKEN_* → 401`

### 数据模型(摘要,详见后端 domain)

```typescript
// 后端 Java record,前端用相同 shape
interface Product {
  id: string; name: string; description: string;
  price: number; stock: number;
  category: "鱼类" | "虾蟹" | "贝类" | "软体" | "海藻";   // sealed interface
  imageUrl: string;
  status: "ACTIVE" | "OUT_OF_STOCK" | "DISCONTINUED";
  createdAt: string; updatedAt: string;
}

interface CartItem {
  productId: string; quantity: number; selected: boolean; addedAt: string;
}

interface Order {
  id: string; userId: string;
  items: Array<{ productId: string; productName: string; unitPrice: number; quantity: number }>;
  totalAmount: number;
  status: "PENDING" | "PAID" | "SHIPPED" | "COMPLETED" | "CANCELLED";
  cancelReason?: string; createdAt: string; updatedAt: string;
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
| `/learn` | 从会话中提取模式 |
| `/skill-create` | 从 Git 历史生成 Skills |

---

## 开发说明

### 环境变量(必填,启动时 fail-fast)

```bash
# 后端 — Sprint 2 起 @Validated 在 binding 阶段 fail-fast(早于 @PostConstruct)
JWT_SECRET=<≥32 字节随机串>           # 缺失/<32B 即 fail-fast。生成:openssl rand -base64 48
JWT_ADMIN_SECRET=<≥32 字节随机串>      # admin-ui 独立签名密钥;MUST 与 JWT_SECRET 不同(@AssertTrue 校验)
MONGODB_URI=mongodb://localhost:27017/seafood   # 必须以 mongodb:// 或 mongodb+srv:// 开头
WECHAT_ENABLED=false                    # dev 期可保持 false,wechat.login code 必须以 dev- 开头
WECHAT_APPID=...                        # WECHAT_ENABLED=true 时必填(@AssertTrue 跨字段校验)
WECHAT_SECRET=...                       # WECHAT_ENABLED=true 时必填

# 前端(微信小程序)
API_BASE_URL=http://localhost:8080
```

> **Sprint 2 BREAKING**:`JWT_ADMIN_SECRET` 现在强制要求 ≥32 字节且不同于 `JWT_SECRET`;
> 此前共用同一密钥的部署会被拒绝启动。两个密钥独立生成:
> ```bash
> openssl rand -base64 48      # → JWT_SECRET
> openssl rand -base64 48      # → JWT_ADMIN_SECRET(再跑一次取不同值)
> ```

### Docker 部署

> **Sprint 2 C5 §5.9**:2 服务 — `backend`(GraalVM Native binary, image
> `seafood-backend:native`,基于 `gcr.io/distroless/base-debian12:nonroot`,**无 JRE**)
> + `mongodb:7`。`mongodb` 与 `backend` 都带 healthcheck,backend 通过
> `depends_on: mongodb: { condition: service_healthy }` 串行启动。

```bash
docker-compose up -d              # 启动所有服务(backend 需先 docker build)
docker-compose logs -f            # 查看日志
docker-compose down               # 停止服务
docker-compose down -v            # 停止 + 清 mongodb_data volume
```

> 启动后 backend RSS 验收 < 200 MB(design §3.1);`/actuator/health` 应在 30 s 内 200;
> `curl http://localhost:8080/api/products?page=0&size=10` 应返回 200 且
> `totalElements > 0`(需先跑 `backend/seed/seed.sh`)。完整冒烟见
> `backend/scripts/native-smoke.sh`。

### Git 工作流
- **提交格式**:`feat(<scope>):` `fix:` `refactor:` `docs:` `test:` `chore:`
- **分支策略**:`main`(生产 7 模块已归档) → `feature/refactor`(单仓改造,5 commits 待 PR) → `feat/*` / `fix/*`
- **PR 要求**:代码审查 + 测试通过 + ESLint 通过 + @RefreshScope 静态扫描通过
- **本地归档**:`archive/backend-multi-module-2026-06/`(已 .gitignore'd,git history 仍保留)

---

## 性能要求

- 首屏加载 < 2秒
- 页面切换 < 300ms
- API 响应 < 500ms

---

## 重要提示

1. **TDD 优先**:所有新功能必须先写测试
2. **类型安全**:严禁 `any`(测试文件除外)
3. **安全审查**:所有代码需通过安全检查
4. **`@RefreshScope` 禁**:GraalVM Native 不兼容,`./gradlew check` 任务拦截;引入 Spring Cloud Config 时尤其注意
5. **跨模块只走 ApplicationService**:绝不跨过 Service 直接调 Repository(design §1.3,便于将来回拆)
6. **JWT_SECRET 必须 ≥32 字节**:HS256 强需求;开发可用 `openssl rand -base64 48`
7. **BFF 当前不缓存**:P99 > 500ms 时再加 Caffeine(design §5.2)

---

## 单仓常见坑(从本次重构沉淀)

| 坑 | 触发 | 解 |
|---|---|---|
| `MongoIndexInitializer` 启动失败 | `auto-index-creation: false` 但 docs 无显式建索引 | 显式用 `MongoPersistentEntityIndexResolver` 启动时建 |
| `assertThatThrownBy(...).hasMessageContaining(...)` 在 record + List.of() 上误判 | 异常 msg 含子串但 assertj 比对方式不同 | 改用 `catch + assertThat(getMessage()).isEqualTo(...)` |
| `findAll(any())` 编译歧义 | `MongoRepository.findAll()` 与 `findAll(Pageable)` 重载 | 用 `any(Pageable.class)` 显式 |
| `bson 5.6 + GraalVM Native` 反射 | `--no-fallback` 下 bson codec 注册失败 | CI 跑 `nativeTest` agent 捕获生成 META-INF/native-image/,别手编 |
| `Order.byCreatedAt` 单分页查全表 | top10 销量聚合 | 暂时 `findTop500ByOrderByCreatedAtDesc`,生产换 Mongo aggregation pipeline |
| `@WebMvcTest` + `@MockBean` 在 Spring Boot 4 不可用 | 包路径变更 | 改用 plain JUnit + Mockito 直接测 Service |

---

## 相关文档

- `openspec/changes/refactor-rust-rebuild-frontend/` - **本次重构的 OpenSpec change**(proposal/design/4 specs/63 tasks)
- `docs/DESIGN.md` - 设计系统规范(待按新单仓重写)
- `frontend/admin-design/` - 小程序设计令牌(shared with admin-ui,§8/§9 重构)
- `backend/seed/seed.sh` - MongoDB 种子数据(50 商品 / 5 分类 / 2 用户)
- `backend/scripts/check-no-refresh-scope.sh` - GraalVM Native 兼容性扫描

---

*本文件为 AI 开发辅助文档，具体实现请参考代码注释和测试用例。*

---

## CI/CD

Sprint 2 起拆为 3 个独立 workflow,按需并行触发:

| Workflow | 触发 | 职责 |
|---|---|---|
| `.github/workflows/ci.yml` (jvm-check) | PR + push to main/develop | `./gradlew check`(含 ArchUnit、`checkNoRefreshScope`、JVM 测试);frontend `npm test`;best-effort `nativeCompile` |
| `.github/workflows/native.yml` (native) | PR 改 `backend/**` / `Dockerfile` / `docker-compose.yml`;push to main | GraalVM `nativeTest` → `nativeCompile` → docker build → Trivy 扫 `seafood-backend:native` |
| `.github/workflows/security.yml` (security) | PR + push to main / `feat/**` / `fix/**` | OWASP Dep-Check(SCA)+ TruffleHog(secret scan PR diff) |

**SARIF 报告**:Trivy 和 OWASP Dep-Check 都通过 `github/codeql-action/upload-sarif@v3` 上传,在 GitHub 仓库 **Security → Code scanning** 标签页查看历史告警与去重结果。Dependabot 每周一凌晨扫一次,安全更新即时触发(不受周计划约束),分组 PR(`spring-boot` / `testcontainers`)降低噪声。
