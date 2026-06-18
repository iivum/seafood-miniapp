# Change: refactor-monolith-rebuild-frontend

## Why

海鲜商城小程序的当前后端基于 Java 25 + Spring Boot 4 + Spring Cloud 2025.1.1 的 7 模块微服务架构。重构有 3 个真实驱动力:

1. **架构过重**:7 模块 + Eureka + Config + WebClient 跨进程调用,与"docker 单机"部署目标严重错配。
2. **运维痛点**:7 进程启动慢、调试链路长、CI/Docker 编排复杂、内存基线 400MB+。
3. **前端薄弱**:Admin UI 的"美观"验收项是当前最薄弱的一环;小程序代码组织按类型而非按 feature,扩展性差。

**关于语言选型的决策记录**:
- 初版提案考虑用 Rust 重写,理由是性能与团队逃离 Spring 注解体系。
- 后因"**微信小程序后端在国内 Rust 社区支持稀薄,1 人项目扛不起生态空白**"否决 Rust。
- 继而考虑 Go,也因团队语言惯性 + 已有 Java/Spring 资产沉淀放弃。
- 最终决策:**留在 Java 25 既有技术栈**,通过**架构收敛 + GraalVM Native 化**满足性能驱动力;通过**单仓替换多进程**满足运维简化驱动力。
- 关键约束:GraalVM Native 编译已在 `build.gradle` 中预留(`apply false`),启用即可获得 < 100ms 启动 + ~80MB 内存的部署形态。

API 兼容要求是"全断",数据兼容要求是"否"——这两点把"绿色重写"的口子打开了。

## What Changes

### 后端:从 7 模块收敛到 1 模块

- **删除** `backend/` 多模块结构(gateway / product-service / order-service / user-service / common / discovery-service / config-service)。
- **新增** `backend/` 单 Spring Boot 模块,包结构按 bounded context 划分:
  - `com.seafood.product` (商品)
  - `com.seafood.order` (订单/购物车)
  - `com.seafood.user` (用户/鉴权)
  - `com.seafood.bff` (管理后台聚合层,3 个端点)
  - `com.seafood.shared` (JWT 工具、错误处理、通用 DTO)
- **不引入** Eureka / Config Service 等价物。Docker 单机用静态配置 + 环境变量。
- MongoDB 沿用(不变数据库)。
- 数据兼容:否,旧数据可丢弃;提供 seed scripts 注入 50 商品 / 5 分类 / 1 admin / 1 测试用户。

### GraalVM Native Image 启用

- `backend/build.gradle` 中 `org.graalvm.buildtools.native` 插件从 `apply false` 改为应用。
- 验证 Spring Boot 4.0.6 在 Native 模式下的反射 / 资源 / 代理配置。
- Dockerfile 改为 multi-stage:`build` 阶段用 GraalVM JDK,`runtime` 阶段用 minimal base。
- 验收:Native binary 启动 < 2s,内存 < 200MB(对比 JVM 模式 5-10s / 400MB+)。

### Java 25 现代化

- 用 `record` 替换 DTO 类(从现有 Lombok POJO 迁移)。
- 用 `sealed interface` + `pattern matching` 表达订单状态机。
- 用虚拟线程(`Thread.ofVirtual()`)处理 I/O 密集型操作。
- 业务逻辑保持原有 DDD 分层(controller / service / repository),不引入新架构。

### Admin UI:技术栈替换

- **删除** `backend/admin-ui/` 内的 Vue 3 + Element Plus 代码(Java 子模块的 GUI 部分)。
- **新增** `admin-ui/` 顶层项目:
  - React 18+ + Vite + TypeScript strict
  - shadcn/ui(组件代码内联,非 npm 依赖)
  - Tailwind CSS
  - React Query(服务端状态)+ Zustand(客户端状态)
  - React Router v6
  - 接入 backend BFF 端点(`/api/admin/**`)
  - 验收"美观"项:shadcn 默认即达 80 分基线
- BFF 调用方式简化:同进程函数调用,不再走 WebClient。

### 小程序:保留原生,目录重构

- **保留** 微信原生架构(TypeScript strict + WXML/WXSS + Jest)。
- **重构** `frontend/` 目录:从按类型(`pages/`, `utils/`, `types/`)改为按 feature(`features/product/`, `features/order/`, `features/auth/`)。
- 引入统一设计令牌(与 admin-ui 共享视觉规范)。
- 不跨端(不引入 Taro/uni-app)。

### 部署

- 单 `docker-compose.yml`,2 个服务:
  - `backend`(GraalVM Native binary,同时托管 admin-ui 静态资源 + API)
  - `mongodb`
- CI:GitHub Actions,跑 `./gradlew test` / `./gradlew nativeCompile` / 前端 `npm test` / `npm run build`。

## Impact

### 新增 specs(后续 `openspec sync-specs` 阶段)

- `backend-api`:后端对外 HTTP 契约
- `admin-ui`:管理后台 UI 行为契约
- `mini-program`:小程序核心流程契约
- `auth`:JWT 鉴权与角色 RBAC

### 受影响代码

- **重构**:`backend/` 从 7 模块 → 1 模块(包结构重排,业务逻辑保留)
- **删除**:`backend/admin-ui/` 中 Vue/Element Plus 代码
- **新增**:`admin-ui/` 顶层 React 项目
- **修改**:`frontend/` 目录结构(feature-based)
- **修改**:`backend/build.gradle`(启用 GraalVM Native 插件)
- **修改**:`docker-compose.yml`(从 7 服务 → 2 服务)

### 风险

- **R1 (中)** Spring Boot 4 + Java 25 + GraalVM Native 是较新组合,反射/资源处理可能有坑。Phase 2 末必须用 Native binary 跑通端到端,否则回退 JVM 部署。
- **R2 (低)** 业务逻辑从跨服务调用改为同进程方法调用,需要重新设计事务边界和错误传播。已有 DDD 分层,影响可控。
- **R3 (中)** "美观"验收主观性强。React + shadcn 是用最少工时达到 80 分的方案;若要求"接近商业 SaaS 视觉",需要追加 1-2 周。
- **R4 (低)** MongoDB schema 需要小幅调整以适配重构后的领域模型。
- **R5 (低) `@RefreshScope` 不兼容 GraalVM Native Image**。本项目已砍 Spring Cloud Config Service,理论上不引入;Code Review + 静态扫描必须拦截该注解出现。

### 不做(Non-goals)

- 不引入 Rust / Go / Node(语言不变)
- 不引入 GraphQL(REST 优先)
- 不上 K8S / Service Mesh / 多区域
- 不做多租户
- 不做 i18n 完整化
- 不重写历史 MongoDB 数据(数据兼容:否)
- 不迁移支付/短信/物流的第三方集成(沿用现有配置)

## 验收(Definition of Done)

- [ ] 1. 生产可跑:`docker compose up` 启动后能完成"商品浏览→登录→下单→订单查询"全流程
- [ ] 2. 测试覆盖率:后端 ≥ 80%,前端 ≥ 88%(与原标准对齐)
- [ ] 3. P99 API 延迟:< 200ms(本机基线)
- [ ] 4. 内存占用峰值:< 200MB(对比 Spring Cloud 7 进程 ~600MB+)
- [ ] 5. 启动到 ready:< 2s(对比 Spring Cloud 7 进程 ~10s+)
- [ ] 6. CI 全绿:`./gradlew test` / `./gradlew nativeCompile` / `npm test` / `npm run build` / `docker build`
- [ ] 7. Admin UI 视觉验收通过(手工 review)
- [ ] 8. 旧多模块结构归档到 `archive/backend-multi-module-2026-06/`

## 时间表(10 周)

| Phase | 周次 | 内容 | 检阅点 |
|-------|------|------|--------|
| 1 | W1-2 | 垂直切片:新单仓跑通"登录 + 商品列表" | 能 demo "登录→商品列表" |
| 2 | W3-6 | 把 7 模块业务逻辑迁入新单仓 + 启用 GraalVM Native | Native binary 启动 < 2s |
| 3 | W5-6 | BFF 3 端点改为同进程函数调用 | 集成测试过 |
| 4 | W7-9 | 小程序重构 + Admin UI 新栈 | UI 走查通过 |
| 5 | W10 | 联调 + E2E + Docker 单机部署 + Native 验证 | 全部 DoD 勾完 |

**截止:2026-08-12**

## 决策记录

| # | 决策 | 值 | 理由 |
|---|------|-----|------|
| 1 | 后端语言 | Java 25(沿用) | 微信小程序后端 Rust 社区支持稀薄;Go 团队学习成本 + 语言惯性 |
| 2 | 后端架构 | 单 Spring Boot 模块(7→1) | docker 单机不需要微服务;运维简化 |
| 3 | 服务发现/配置 | 砍掉 | 同上 |
| 4 | GraalVM Native | 启用 | 用 1 个 binary 替代 JVM 部署,满足性能驱动力 |
| 5 | Admin UI 技术栈 | React 18+ + Vite + shadcn/ui + Tailwind | 美观验收 + 与原 Vue 栈差异化 |
| 6 | 小程序架构 | 微信原生(沿用) + 目录重构 | 必须保持原生(用户约束) |
| 7 | 数据种子策略 | fixtures + seeder | 数据兼容:否,需提供初始数据 |
