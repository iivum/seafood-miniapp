# CLAUDE.md

本文件为 Claude Code 等 AI 编程工具提供项目开发指导。每行都要 earn its place — 通用模式 Claude 已掌握,本文档只记**本项目独有**。

---

## 会话开始必读(违反即返工)

### 流程:每个任务 30 秒必查 skill

**黄金规则** — 任何动作前,只要 1% 概率某个 skill 适用 → **必须**先用 Skill 工具 invoke。

| 场景 | 必查 skill |
|---|---|
| 建 / 加 / 改功能 | `superpowers:brainstorming` |
| 多步任务 | `superpowers:writing-plans` |
| 执行 plan(独立 session + review checkpoint) | `superpowers:executing-plans` |
| 执行 plan(当前 session 派 subagent 跑独立 task) | `superpowers:subagent-driven-development` |
| 需要工作空间隔离 / 执行 plan 前 | `superpowers:using-git-worktrees` |
| 写实现代码 | `superpowers:test-driven-development` |
| Bug / 测试失败 | `superpowers:systematic-debugging` |
| 声称完成 / commit / PR 前 | `superpowers:verification-before-completion`(跑命令看输出,不靠"应该") |
| 全部完成、决定 merge / PR / 清理 | `superpowers:finishing-a-development-branch` |
| 提 PR 前 / 收 review | `superpowers:requesting-code-review` / `superpowers:receiving-code-review` |
| Java 代码 / Java PR | `iivum-java-style`(必查 `ddd-review-checklist.md`) |
| CI 报错 | `seafood-ci-hardening` |
| mp E2E 静默失败 | `seafood-mp-e2e-debug` |
| 2+ 独立并行 | `superpowers:dispatching-parallel-agents` |
| 创建 / 编辑 / 验证 skill | `superpowers:writing-skills` |

**反模式(出现立即停下)**: "简单跳过" / "我熟悉" / "先收集上下文" / "不算 task"

### 本仓硬规则

1. **DDD 分层不可越**:`api → application → domain → infra` 四层 + `bff` 第 5 层(只调 ApplicationService 跨模块组合,不可触 `infra`)。ArchUnit `ArchitectureTest` 守 4 条:① `api↛infra` ② `bff↛infra` ③ `domain↛org.springframework.*`(JVM-pure,除 `@Document`/`@Id` mapping) ④ controllers 不可持 `*Repository` 字段/构造器参数
2. **跨模块只走 ApplicationService**:不可直调 Repository(design §1.3)
3. **TDD 优先 + 严禁 `any`(测试除外)+ strict mode**;覆盖率全局 ≥80%,核心 ≥90%
4. **`@RefreshScope` 禁**:GraalVM Native 不兼容,`./gradlew check` 拦截;改用 `EnvironmentChangeEvent`
5. **JWT_SECRET ≥32B + JWT_ADMIN_SECRET 同长且不同**(Sprint 2 BREAKING,`@AssertTrue` 启动期 fail-fast)
6. **MONGODB_URI 必须 `mongodb://` 或 `mongodb+srv://` 开头**(`@Validated`)
7. **无硬编码密钥, 无 console.log**(日志走 SLF4J)
8. **BFF 当前不缓存**:P99 > 500ms 时再加 Caffeine(design §5.2)
9. **文件**:多小优于少大(200-400 行,≤800);高内聚按领域组织,非按类型

---

## 项目概述

**海鲜商城** - 微信小程序 + Spring Boot 4.0.6 + GraalVM Native 单仓电商。原 7 模块 Spring Cloud 已于 `feature/refactor` 分支收敛到单仓并合并到 main(`archive/backend-multi-module-2026-06/` 留底);单仓重构已完成,当前开发在 `feat/sprint-1-closure` 活跃分支上进行 Sprint 1 闭合

- 前端:微信小程序(TS 5.x / Jest 29.x)+ 管理后台(React 18 + shadcn/ui + Vite,`admin-ui/`,**单卖家内部运营不做外部入驻**)
- 后端:Java 25 + Spring Boot 4.0.6 + GraalVM Native + Gradle 9.x,MongoDB 7.x 单库
- 部署:2 服务 docker-compose(backend Native + mongodb);`admin-ui/` 部署方式 Sprint 1 末决策

---

## 运行测试

```bash
# 后端 — Spring Boot 4 + Gradle 9,77 例
cd backend
./gradlew test                              # 全部 + 报告 build/test-results/
./gradlew check                             # + ArchUnit + checkNoRefreshScope
./gradlew :test --tests "*ProductTest"      # 单类
./gradlew nativeTest                        # @Tag("native") 切片 + GraalVM agent 收 metadata
./gradlew nativeCompile                     # 用 agent 产出 metadata 编 native binary
./gradlew test -PexcludeTags=docker         # 无 Docker 环境跳过 IT

# 前端
cd frontend && npm test                     # 全部
```

> 本机无 GraalVM CE 25+ 时 `./gradlew test` 直接失败(JDK 25 toolchain 锁版本);`gradle.properties` 已配 `org.gradle.java.installations.paths` 指向 Homebrew GraalVM。

> **nativeTest 切片**:8 个 IT 打 `@Tag("native")`,作为 GraalVM tracing agent 收集反射/资源/代理 metadata 的代表集 — **security** (`AdminRateLimitIT` / `SecurityHeadersIT`)、**product infra** (`ProductDocumentRepositoryIT`)、**observability** (`MetricsEndpointIT` / `StructuredLoggingDevIT` / `StructuredLoggingProdIT` / `StructuredLoggingLogFormatJsonIT` / `RequestIdFilterOrderIT`)。新增 native 关键路径要在这 8 个用例之一上加 `@Tag("native")` 或扩写,然后 commit `src/main/resources/META-INF/native-image/` 更新。

---

## 架构

```
seafood-miniapp/
├── frontend/                  # 微信小程序 (TS strict)
├── admin-ui/                  # React 18 + shadcn/ui (Sprint 1+)
├── backend/                   # 单 Spring Boot 模块(端口 8080)
│   └── src/main/java/com/seafood/
│       ├── shared/            # config/security/error
│       ├── product/{api,application,domain,infra}/     # DDD 四层
│       ├── order/{api,application,domain,infra}/
│       ├── user/{api,application,domain,infra}/
│       └── bff/admin/         # /api/admin/** 第 5 层:只调 ApplicationService 跨模块组合
└── openspec/changes/<name>/   # proposal/design/specs/tasks
```

**包内分层**: api(Controller+DTO record) / application(Service+UseCase) / domain(Aggregate+VO+Event) / infra(Repository+Document)
**跨模块**: ApplicationService → ApplicationService,**绝不**跨 Repository(design §1.3,便于将来回拆)

---

## DDD 项目自动加载(强制)

**写代码 / 重构**:立刻 Skill 工具调 `iivum-java-style` → 加载 `references/ddd-patterns.md` → 方案前过 §5 自检清单(实体行为内聚?值对象不可变?聚合边界合理?)→ 写完回复**显式列出**适用原则(简短一行)。

**Code review / review PR**:立刻 `iivum-java-style` + 加载 `references/ddd-review-checklist.md` → 按 §0 15 分钟扫描法(分层污染 → 聚合红旗 → 应用服务过厚 → 事件命名 → 行为方法 vs setter)→ 反馈按严重度分组 🚨 Blocker(架构腐败根源) / ⚠️ Major(长期债务) / 💡 Suggestion / ❓ Question → Blocker/Major 必含**四要素**(问题行号 / 影响 / 建议 / 参考章节)→ 与通用 review 一起跑时 DDD 项**独立成段**(架构反馈需单独被看见)→ 遵守 §15 自查(不甩黑话、不教条化、Blocker > 5 条时建议同步设计讨论)。

**反向例外**:简单 CRUD / 用户明示"不用 DDD" / 非 Java-Kotlin → 跳过 checklist 或只取严重度框架。

---

## API 响应格式

成功直接返 record;失败统一 `{ code, message, fieldErrors? }`,`code ∈ {NOT_FOUND, VALIDATION, DOMAIN, TOKEN_*}`(HTTP `404/400/409/401`),`fieldErrors` 仅 `VALIDATION` 填。详细见 `backend/shared/error/ErrorResponse.java`。

---

## 视觉验证(感知 diff 主 + 4 层辅;C5 sprint-5-c5-visual-verification)

> **更新(C5)**:旧说法「像素 diff 对抗锯齿/DPR 极度敏感,故只用 4 层、不截图对图」**已废弃**。
> 实测现代做法可解 AA/DPR 敏感:**归一化尺寸(sips)+ AA-tolerant 感知 diff(odiff)+ 阈值容差**。
> 4 层断言验"元素/数据/token 在不在",**抓不住"渲染出来是不是坏的/偏离 OD"** —— 这是它的盲区,
> 感知 diff 正补这一层。两者互补:感知 diff 为主门抓视觉偏离,4 层为辅验结构/数据/token。

**感知层(C5,已落地 4 tab 页)** — mp 实截图 vs OD 设计 golden 的 odiff 比对,抓"现状偏离 OD/不可用":
- SoT = Open Design 项目 `686e3434` 的 9 张 mp HTML mockup → 渲染成 `frontend/e2e/od-golden/<screen>.png`
- 跑:`cd frontend && npm run test:visual`(详见 `frontend/e2e/tools/README.md`)
- diff% > 阈值 → RED(驱动逐屏修);产 `<screen>-diff.png` 定位偏离
- ⚠️ 截图捕获必须 `reLaunch`(非 switchTab):switchTab 到已激活 tab 不重跑 onLoad → 截陈旧空态假信号
- ⚠️ 有意义信号需后端起 + seed:native 镜像 arm64 不匹配本机用 `seafood-backend:jvm`;fixtures stale 缺 `status` 字段须 `updateMany` 补 ACTIVE(否则 `listPublic` 返 0 条)

**几何层(C5,已落地 home/category)** — 量 mp 实际渲染结构不变量(present/count/columns),**AA/DPR/设备框完全免疫**,剥离感知层的框/图片噪声、锁定"布局崩没崩":
- SoT = `frontend/e2e/od-geometry/<screen>.json`(OD 期望不变量);跑 `npm run test:geometry`
- ⚠️ automator 0.12.1 元素句柄(`page.$`/`$$`/`element.size()/offset()`)在本环境**超时挂死**,`page.outerWxml` undefined → **唯一可行** = mp 原生 `wx.createSelectorQuery().boundingClientRect()` 经 `mp.evaluate` 在 mp 运行时内跑
- 例:home 几何锁定 `grid 实际 1 列(应 2)+ banner 缺失`,chips/header 正常
- 余 5 分包带参页几何 + 取代旧静态 `mp-od-design.test.ts` 留下游

**4 层断言(辅,`frontend/e2e/`)**:
1. **结构** — `page.outerWxml()` 抓节点/class/文案
2. **数据** — `page.data()` + `fromBackend: { path, fields: [...] }` 直击后端字段
3. **行为** — `miniProgram.on('console'|'exception')`,`expect(exceptions).toEqual([])`
4. **颜色** — chroma.js `deltaE()` 验 token parity + `chroma.contrast()` 验 WCAG AA

**mp 前置**:`cli auto --project frontend --auto-port 9420` 起 DevTools 自动化端口(感知层脚本可自起),跑:
```bash
TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 npx jest e2e/ --runInBand   # 4 层
cd frontend && npm run test:visual                                 # 感知 diff
```
> 有意义的逐屏信号需后端起着 + seed(否则 mp 渲染 loading/空态,diff 必然很大)。

**gotcha**:
- mp `getApp()` 模块加载返 undefined → `utils/request.js` 用 `getAppSafe()` 延迟
- mp API 返 `Page<T> { content[] }` → `src/api/product.js` 加 converter
- WCAG AA = 4.5:1,`sprint-1-closure` commit `273763b` 已修 status badge + 立即购买按钮 ratio

---

## 可观测性(端口物理隔离,design §D2)

- 业务端口 **8080** — 对外暴露 `/api/**`,**不**注册 `/actuator/**`
- 管理端口 **9090** — `management.server.port: 9090`,**仅容器内可达**;docker-compose 不映射 9090
- 5 counter(埋点在 ApplicationService 边界):`orders.created` / `orders.cancelled` / `orders.paid` / `products.queried` / `users.login.attempts`
- `MetricsCardinalityTest`(ArchUnit)禁高基数 tag:`userId` / `orderId` / `productId` / `email` + 动态拼字符串
- 日志:dev 人类可读 + requestId;prod `LOG_FORMAT=json` 切 logstash JSON

---

## 安全架构(Sprint 2 §2.1-2.3)

- **6 个基线安全响应头**集中 `backend/shared/security/SecurityHeadersProperties.java`(`@ConfigurationProperties("security.headers")`):HSTS / X-Content-Type-Options / X-Frame-Options / Referrer-Policy / Permissions-Policy / CSP
- **唯一写入点** = `SecurityHeadersFilter`;ArchUnit `SecurityHeaderArchitectureTest` 强制白名单外任何类不可调 `HttpServletResponse#setHeader`(白名单还含 `AdminRateLimitFilter` 写 `Retry-After`、`RequestIdFilter` 写 `X-Request-Id`)
- **新响应头** → 加进 `SecurityHeadersProperties` 字段,不要在业务代码里 setHeader

---

## 环境变量 + 部署

```bash
# 后端 — @Validated 启动期 fail-fast
JWT_SECRET=<≥32B>          # 缺失/<32B 即失败;openssl rand -base64 48
JWT_ADMIN_SECRET=<≥32B>    # 必须不同于 JWT_SECRET(Sprint 2 BREAKING)
MONGODB_URI=mongodb://localhost:27017/seafood
WECHAT_ENABLED=false       # dev 期可 false,但 wechat login code 必须以 dev- 开头
WECHAT_APPID=...           # WECHAT_ENABLED=true 时必填
WECHAT_SECRET=...          # WECHAT_ENABLED=true 时必填

# 前端
API_BASE_URL=http://localhost:8080

# 部署
docker-compose up -d       # 2 服务:backend (seafood-backend:native, distroless nonroot) + mongodb:7
docker-compose down -v     # 清 mongodb_data volume
```

启动验收:`/actuator/health` 30s 内 200;`curl /api/products?page=0&size=10` 返 200 且 `totalElements > 0`(需先跑 `backend/seed/seed.sh`)。完整冒烟见 `backend/scripts/native-smoke.sh`。

---

## Git 工作流

- **提交**: `feat(<scope>):` / `fix:` / `refactor:` / `docs:` / `test:` / `chore:`
- **分支**: `main`(单仓 + Sprint 0/1 v2-visual 已合 PR #17-#25)→ 当前活跃 `feat/sprint-1-closure`→ `feat/*` / `fix/*` 后续 Sprint 切分
- **PR**:code review + tests + ESLint + `checkNoRefreshScope`
- **历史归档**: 原 7 模块代码 `archive/backend-multi-module-2026-06/`(.gitignore'd,git history 保留)
- 本地归档: `archive/backend-multi-module-2026-06/`(.gitignore'd)

---

## 单仓常见坑

| 坑 | 解 |
|---|---|
| `MongoIndexInitializer` 启动失败 | `auto-index-creation: false` + 显式 `MongoPersistentEntityIndexResolver` |
| `findAll(any())` 编译歧义 | 用 `any(Pageable.class)` 显式 |
| `bson 5.6 + GraalVM Native` 反射 | CI 跑 `nativeTest` agent 收 `META-INF/native-image/`,别手编 |

---

## 性能预算

RSS < 200 MB(`nativeCompile` 实测 ~84 MiB);启动 < 2 s(native ~0.3s / JVM ~1s);p50 日志开销 < 2 ms;API < 500ms。

---

## 相关文档

- `openspec/changes/refactor-rust-rebuild-frontend/` — 本次重构 OpenSpec change
- `docs/redesign/` — 设计审计 + 路线图(6 份 .md,含 14 屏拆解 + MoSCoW + Sprint 切分)
- CI: `ci.yml`(jvm-check)/ `native.yml`(native+Trivy)/ `security.yml`(Dep-Check+TruffleHog)→ SARIF 上传 GitHub Security tab
