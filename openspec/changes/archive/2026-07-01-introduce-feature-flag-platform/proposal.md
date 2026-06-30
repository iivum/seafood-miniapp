# Proposal: 引入 Feature Flag 平台

## Why

当前发布是 "all or nothing":代码合入 main 即对 100% 用户生效,新功能 / 改版 / 灰度均无手段。Phase 1 可观测性补齐后,事故可定位但**无法事前预防** — 需要灰度基建(1% → 10% → 50% → 100%)把爆炸半径压到最小。

**为什么现在做**:Phase 1 完成后,管理后台 React 迁移(#2)与小程序 E2E(#3)上线,都依赖灰度能力才能安全发版(尤其 admin-ui 后台首次上线,需要按 admin 角色白名单放量)。Feature Flag 是 Phase 2 后续工作的前置基建。

## What Changes

- **Flag 存储**:沿用 MongoDB 复用现有基础设施,新建 `feature_flags` collection
  - Schema:`{ flagKey, enabled, rolloutPercentage, userSegments[], expiresAt, createdBy, updatedAt }`
- **后端 SDK 封装**:
  - `FeatureFlagService.isEnabled(flagKey, userId)` — 核心判断方法
  - `@FeatureFlag("new-checkout")` 注解 + AOP 切面
  - 启动时一次性预加载所有 flag 到内存,`@Scheduled` 定时 60s 刷新(避免每次请求打 DB)
- **小程序集成**:
  - 启动时调用 `GET /api/featureflags`(公共端点,不鉴权,只返客户端可见 flag)
  - 本地缓存到 `wx.storage` + `wx.setStorage` 版本号
  - 启动 / onShow 触发刷新
- **灰度策略**:
  - **百分比**:`hash(userId) % 100 < rolloutPercentage`
  - **白名单**:`userId in userSegments` 直接开
  - **地域**:`geoCode in userSegments`(Phase 1 不实施,留接口)
- **Admin 后台 UI**(待 Phase 1 #2 React 迁移后接入,本次只做 BFF + DB + 权限):
  - 列表 + 编辑(开关 / 百分比 / 白名单)
  - 审计 log(谁改了 flag,改了啥)
- **GraalVM Native 兼容**:flag 是数据而非动态代码,无 class loading 风险
  - 写时:注册 `feature_flags` collection 到 `MongoIndexInitializer`
  - `nativeTest` 阶段让 GraalVM agent 收集新增反射 metadata

## Capabilities

- **New Capabilities**:
  - `feature-flag-platform` — Flag 基础架构(MongoDB schema + 后端 SDK + 小程序客户端 + BFF 三个管理端点)
- **Modified Capabilities**:
  - `admin-bff` — 增 3 个端点:`GET /api/admin/feature-flags`、`PUT /api/admin/feature-flags/{key}`、`POST /api/admin/feature-flags/{key}/audit`

## Impact

### 新增 bounded context
```
backend/src/main/java/com/seafood/featureflag/
├── api/        # FeatureFlagController (public) + AdminFeatureFlagController
├── application/  # FeatureFlagService + AuditService
├── domain/     # FeatureFlag (Aggregate Root) + FlagValue (Value Object)
└── infra/      # FeatureFlagDocument + FeatureFlagRepositoryImpl
```

### 新增前端模块
- `frontend/src/features/featureflag/` — 小程序客户端 SDK + useFeatureFlag hook

### 修改文件
- `backend/src/main/java/com/seafood/SeafoodApplication.java` — 加 `@EnableScheduling`
- `backend/src/main/resources/application.yml` — 加 `featureflag.refresh-interval`、`featureflag.client-endpoint-enabled`
- `frontend/src/app.ts` — 启动时调用 feature flags 公共端点
- `.openspec/specs/admin-bff/spec.md` — 增 3 个端点说明(在 Modified Capabilities 部分)
- 文档:`docs/feature-flags/usage.md`(开发者使用手册)+ `docs/feature-flags/admin.md`(运营操作手册)

### 依赖
- **零新增 npm/maven 依赖**:复用现有 MongoDB / Spring / 小程序 wx API

### 风险
- **配置漂移**:flag 多了之后容易没人清理 → 通过 `expiresAt` 强制过期,过期自动 disable
- **灰度算法不均**:简单 hash 不均 → 用 murmur3 或 FNV,后续切 MurmurHash3
- **审计合规**:谁在什么时候改了 flag 涉及合规,审计 log 必须不可删

### 前置依赖
- **Phase 1 #1 可观测性**:灰度发布需要看板监控各分桶指标
- **Phase 1 #2 React 后台**:管理 UI 依赖 admin-ui 迁移,本次 PR 只做 BFF + 持久层
