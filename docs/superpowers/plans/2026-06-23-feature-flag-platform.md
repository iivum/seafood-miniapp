# Feature Flag 平台实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入基于 MongoDB 的 Feature Flag 平台，支持百分比灰度 + 白名单，小程序客户端 SDK + Admin 管理端点

**Architecture:** 新增 featureflag bounded context（4 层 DDD）；小程序启动时拉 flag 缓存到 wx.storage

**Tech Stack:** Java 25, Spring Boot 4.0.6, MongoDB, GraalVM Native, 微信小程序 JS

## Global Constraints
- hash(userId + flagKey) % 100 < rolloutPercentage → enabled（deterministic）
- domain 层纯 Java，不 import Spring（ArchUnit 守护）
- 启动预加载 + @Scheduled 60s 刷新（不用 @RefreshScope，GraalVM 不兼容）
- GraalVM Native：新 collection 必须注册到 MongoIndexInitializer
- 新增 counter：不需要（flag 本身不是业务事件）
- 不新增 Maven 依赖，复用现有 MongoDB / Spring

---

## Task 1：domain 层 — FeatureFlag 聚合根 + FlagValue 值对象

**包路径：** `com.seafood.featureflag.domain`

**约束：** 纯 Java 25，零 Spring import（ArchUnit `domain↛org.springframework.*` 守护）

### 步骤

- [ ] **RED** — 写单元测试 `FeatureFlagTest`
  - 文件：`backend/src/test/java/com/seafood/featureflag/domain/FeatureFlagTest.java`
  - 测试用例：
    - `isEnabled_returnsFalse_whenDisabled()` — `enabled=false` 时任何 userId 均 false
    - `isEnabled_returnsTrue_whenInWhitelist()` — userId 在 `userSegments` 时 true，不管 rolloutPercentage
    - `isEnabled_rollout_deterministic()` — 相同 userId + flagKey 多次调用结果一致
    - `isEnabled_rollout_percentage0_alwaysFalse()` — rolloutPercentage=0 时 false
    - `isEnabled_rollout_percentage100_alwaysTrue()` — rolloutPercentage=100 时 true
    - `isEnabled_returnsTrue_whenNullUserId_andPercentage100()` — userId 为 null 时走百分比（匿名用户）
    - `isExpired_returnsTrue_whenExpiresAtInPast()` — expiresAt 早于 now 时 flag 视为 disabled
    - `constructor_rejectsNullFlagKey()` — flagKey 为 null 抛 DomainException
    - `constructor_rejectsBlankFlagKey()` — flagKey 为空字符串抛 DomainException
    - `constructor_rejectsPercentageOutOfRange()` — rolloutPercentage < 0 或 > 100 抛 DomainException

- [ ] **实现** — 创建 domain 类
  - `backend/src/main/java/com/seafood/featureflag/domain/FeatureFlag.java`
    ```java
    // Java 25 record — 不可变聚合根
    public record FeatureFlag(
        String flagKey,           // 唯一 kebab-case
        boolean enabled,          // 总开关
        int rolloutPercentage,    // 0-100
        List<String> userSegments, // 白名单
        Instant expiresAt,        // null = 永不过期
        String description,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
    )
    ```
    - 紧凑构造器校验 flagKey 非空、rolloutPercentage 0-100、userSegments defensive copy
    - `isEnabled(String userId)` — 先判 enabled + expiresAt，再白名单，最后 MurmurHash3 百分比
    - `disable()` — 返回 `enabled=false` 的新 record
    - `updateRollout(int newPct)` — 返回更新 rolloutPercentage 的新 record
    - `addToWhitelist(String userId)` — 返回追加白名单的新 record
    - `removeFromWhitelist(String userId)` — 返回移除白名单的新 record

  - `backend/src/main/java/com/seafood/featureflag/domain/FlagValue.java`
    ```java
    // 值对象：isEnabled 判断结果 + 来源（WHITELIST / ROLLOUT / DISABLED / EXPIRED）
    public record FlagValue(boolean enabled, EvalReason reason) {
        public enum EvalReason { WHITELIST, ROLLOUT, DISABLED, EXPIRED }
    }
    ```

  - `backend/src/main/java/com/seafood/featureflag/domain/MurmurHash3Util.java`
    - 纯 Java 实现（无外部依赖），`hash32(String input)` 返回非负 int
    - 输入 `userId + ":" + flagKey` 保证 userId 相同但 flagKey 不同时 hash 不同

- [ ] **GREEN** — 运行测试全通过
  ```bash
  cd backend && ./gradlew :test --tests "com.seafood.featureflag.domain.*"
  ```
- [ ] **commit** — `feat(featureflag): domain 层 FeatureFlag 聚合根 + FlagValue + MurmurHash3`

---

## Task 2：infra 层 — FeatureFlagDocument + FeatureFlagRepository

**包路径：** `com.seafood.featureflag.infra`

**约束：** 遵循 `ProductDocument` 模式；`@Document(collection = "feature_flags")`

### 步骤

- [ ] **RED** — 写集成测试 `FeatureFlagRepositoryIT`
  - 文件：`backend/src/test/java/com/seafood/featureflag/infra/FeatureFlagRepositoryIT.java`
  - 注解：`@DataMongoTest`，需 Docker（打 `@Tag("docker")`）
  - 测试用例：
    - `findByFlagKey_returnsDocument()` — 保存后按 flagKey 查到
    - `findAllByEnabledTrue_returnsOnlyEnabled()` — 过滤 enabled=false 的文档
    - `flagKey_isUnique()` — 重复 flagKey 存储报 duplicate key 错误
    - `save_setsUpdatedAt()` — 保存后 updatedAt 不为 null

  - 写 Audit 集成测试 `FeatureFlagAuditRepositoryIT`
    - `findByFlagKeyOrderByTimestampDesc_returnsPaged()` — 分页查审计记录
    - `auditRecord_cannotBeUpdated()` — append-only 验证（通过无 update 方法的接口约束体现）

- [ ] **实现**
  - `backend/src/main/java/com/seafood/featureflag/infra/FeatureFlagDocument.java`
    - `@Document(collection = "feature_flags")`
    - 字段：`id`、`flagKey`（`@Indexed(unique = true)`）、`enabled`、`rolloutPercentage`、`userSegments`、`expiresAt`、`description`、`createdBy`、`createdAt`、`updatedAt`
    - 标准 getter/setter（同 `ProductDocument` 风格）

  - `backend/src/main/java/com/seafood/featureflag/infra/FeatureFlagAuditDocument.java`
    - `@Document(collection = "feature_flag_audits")`
    - 字段：`id`、`flagKey`、`action`（String, e.g. `"ENABLE"`）、`before`（`Object`/Map）、`after`（`Object`/Map）、`actor`、`timestamp`
    - 只有 getter，无 setter（append-only 语义）

  - `backend/src/main/java/com/seafood/featureflag/infra/FeatureFlagRepository.java`
    ```java
    public interface FeatureFlagRepository extends MongoRepository<FeatureFlagDocument, String> {
        Optional<FeatureFlagDocument> findByFlagKey(String flagKey);
        List<FeatureFlagDocument> findAllByEnabledTrue();
        boolean existsByFlagKey(String flagKey);
    }
    ```

  - `backend/src/main/java/com/seafood/featureflag/infra/FeatureFlagAuditRepository.java`
    ```java
    public interface FeatureFlagAuditRepository extends MongoRepository<FeatureFlagAuditDocument, String> {
        Page<FeatureFlagAuditDocument> findByFlagKeyOrderByTimestampDesc(String flagKey, Pageable pageable);
    }
    ```

  - `backend/src/main/java/com/seafood/featureflag/infra/FeatureFlagMapper.java`
    - `toDomain(FeatureFlagDocument)` → `FeatureFlag`
    - `toDocument(FeatureFlag)` → `FeatureFlagDocument`

- [ ] **GREEN** — 运行集成测试
  ```bash
  cd backend && ./gradlew :test --tests "com.seafood.featureflag.infra.*"
  ```
- [ ] **commit** — `feat(featureflag): infra 层 Document + Repository + Mapper`

---

## Task 3：application 层 — FeatureFlagService + FeatureFlagCache

**包路径：** `com.seafood.featureflag.application`

**约束：**
- `FeatureFlagCache` 持有 `volatile Map<String, FeatureFlag>` 内存缓存，`@PostConstruct` 预加载，`@Scheduled(fixedDelay = 60_000)` 刷新
- 启动 fail-fast：MongoDB 不可用时 `@PostConstruct` 抛出异常，阻断 ready
- 不引入 `@RefreshScope`（GraalVM 不兼容，CLAUDE.md 硬规则）

### 步骤

- [ ] **RED** — 写单元测试 `FeatureFlagServiceTest`
  - 文件：`backend/src/test/java/com/seafood/featureflag/application/FeatureFlagServiceTest.java`
  - Mock `FeatureFlagCache`、`FeatureFlagRepository`、`FeatureFlagAuditRepository`
  - 测试用例：
    - `isEnabled_delegatesToCache()` — service 委托 cache 查询，不直接查 DB
    - `isEnabled_returnsFalse_whenFlagNotFound()` — 缓存里没有该 flagKey 时返回 false（默认关闭）
    - `enable_setsEnabledTrue_andSavesAudit()` — 更新 enabled=true 并写 audit
    - `disable_setsEnabledFalse_andSavesAudit()` — 更新 enabled=false 并写 audit
    - `updateRollout_updatesPercentage_andSavesAudit()` — 改百分比并写 audit
    - `addToWhitelist_addsUserId_andSavesAudit()` — 加白名单并写 audit
    - `removeFromWhitelist_removesUserId_andSavesAudit()` — 去白名单并写 audit
    - `update_throwsNotFound_whenFlagKeyNotExist()` — flagKey 不存在抛 NotFoundException
    - `listAll_returnsPaged()` — 管理员分页列表
    - `getAuditLog_returnsPaged()` — 分页查审计记录

  - 写单元测试 `FeatureFlagCacheTest`
    - `load_populatesCacheOnPostConstruct()` — @PostConstruct 加载后缓存非空
    - `refresh_updatesCache()` — refresh 后缓存反映 repository 最新数据
    - `get_returnsEmpty_whenFlagNotInCache()` — 缓存无该 key 返回 Optional.empty()
    - `load_throwsException_whenMongoUnavailable()` — MongoDB 故障时 fail-fast

- [ ] **实现**
  - `backend/src/main/java/com/seafood/featureflag/application/FeatureFlagCache.java`
    ```java
    @Component
    public class FeatureFlagCache {
        private volatile Map<String, FeatureFlag> cache = Map.of();

        @PostConstruct
        void load() { refresh(); }

        @Scheduled(fixedDelay = 60_000)
        void refresh() {
            cache = repository.findAllByEnabledTrue().stream()
                .collect(toUnmodifiableMap(
                    d -> FeatureFlagMapper.toDomain(d).flagKey(),
                    d -> FeatureFlagMapper.toDomain(d)));
        }

        public Optional<FeatureFlag> get(String flagKey) {
            return Optional.ofNullable(cache.get(flagKey));
        }
    }
    ```

  - `backend/src/main/java/com/seafood/featureflag/application/FeatureFlagService.java`
    - `isEnabled(String flagKey, String userId)` — 从 cache 取 FeatureFlag，调 `flag.isEnabled(userId)`，flag 不存在返 false
    - `enable(String flagKey, String actor)` / `disable(...)` — 更新 DB + 写审计 + 触发 cache 立即刷新
    - `updateRollout(String flagKey, int percentage, String actor)` — 更新百分比
    - `addToWhitelist(String flagKey, String userId, String actor)` / `removeFromWhitelist(...)` — 更新白名单
    - `listAll(Pageable)` — 返回 `Page<FeatureFlagResponse>`（管理员列表）
    - `get(String flagKey)` — 返回单条（管理员查详情）
    - `getAuditLog(String flagKey, Pageable)` — 分页审计记录
    - `listClientFlags()` — 返回 `List<ClientFlagResponse>`（只含 flagKey + enabled 两字段，给小程序公共端点用）

  - `backend/src/main/java/com/seafood/featureflag/application/AuditAction.java`
    ```java
    public enum AuditAction {
        ENABLE, DISABLE, PERCENTAGE_CHANGE, WHITELIST_ADD, WHITELIST_REMOVE
    }
    ```

  - 同包 DTO record（仅供 application → api 传递，不跨模块）：
    - `FeatureFlagResponse` — 全字段
    - `ClientFlagResponse` — `(String flagKey, boolean enabled)`

- [ ] **GREEN** — 运行单元测试
  ```bash
  cd backend && ./gradlew :test --tests "com.seafood.featureflag.application.*"
  ```
- [ ] **还需确认** — `@EnableScheduling` 已在 `SeafoodApplication.java` 上，若未加则本 task 加上
- [ ] **commit** — `feat(featureflag): application 层 FeatureFlagService + FeatureFlagCache`

---

## Task 4：api 层 — 公共端点 + Admin 端点

**包路径：** `com.seafood.featureflag.api`

**约束：**
- Controller 不持有 Repository（ArchUnit `controllers↛*Repository` 守护）
- Admin 端点必须 `@PreAuthorize("hasRole('ADMIN')")`
- 公共端点 `GET /api/featureflags` 不鉴权，只返客户端可见字段（`flagKey` + `enabled`）

### 步骤

- [ ] **RED** — 写 Controller 层测试 `FeatureFlagControllerTest` + `AdminFeatureFlagControllerTest`
  - 文件（Spring MockMvc / `@WebMvcTest`）：
    - `backend/src/test/java/com/seafood/featureflag/api/FeatureFlagControllerTest.java`
      - `getClientFlags_returns200_withFlagList()` — 公共端点返回 200 + 列表
      - `getClientFlags_notRequireAuthentication()` — 未登录也返回 200
    - `backend/src/test/java/com/seafood/featureflag/api/AdminFeatureFlagControllerTest.java`
      - `listFlags_returns200_withAdminRole()` — ADMIN 角色可访问
      - `listFlags_returns403_withoutAdminRole()` — 无 ADMIN 角色返回 403
      - `enableFlag_returns200_andWritesAudit()` — PUT enable 成功
      - `disableFlag_returns200_andWritesAudit()` — PUT disable 成功
      - `updateRollout_returns400_whenPercentageOutOfRange()` — 参数校验
      - `getAuditLog_returns200_paged()` — 审计记录分页正确

- [ ] **实现**
  - `backend/src/main/java/com/seafood/featureflag/api/FeatureFlagController.java`
    ```java
    @RestController
    @RequestMapping("/api/featureflags")
    public class FeatureFlagController {
        // GET /api/featureflags  — 公共，无鉴权
        // 返回 List<ClientFlagResponse>，只含 flagKey + enabled
        @GetMapping
        public List<ClientFlagResponse> listClientFlags() { ... }
    }
    ```

  - `backend/src/main/java/com/seafood/featureflag/api/AdminFeatureFlagController.java`
    ```java
    @RestController
    @RequestMapping("/api/admin/feature-flags")
    @PreAuthorize("hasRole('ADMIN')")
    public class AdminFeatureFlagController {
        // GET  /api/admin/feature-flags             — 列表（分页 + 可选 flagKey 搜索）
        // GET  /api/admin/feature-flags/{flagKey}   — 单条详情
        // PUT  /api/admin/feature-flags/{flagKey}   — 更新（body: UpdateFlagRequest）
        // GET  /api/admin/feature-flags/{flagKey}/audit — 审计记录（分页）
    }
    ```

  - `backend/src/main/java/com/seafood/featureflag/api/dto/UpdateFlagRequest.java`
    ```java
    // Jakarta Validation 校验
    public record UpdateFlagRequest(
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPercentage,
        List<String> addToWhitelist,
        List<String> removeFromWhitelist
    ) {}
    ```

  - actor 从 JWT 中提取（同现有 admin 端点模式，通过 `@AuthenticationPrincipal` 取 `username`）

- [ ] **GREEN** — 运行 Controller 测试
  ```bash
  cd backend && ./gradlew :test --tests "com.seafood.featureflag.api.*"
  ```
- [ ] **commit** — `feat(featureflag): api 层公共端点 + Admin 端点`

---

## Task 5：bff 层 — AdminBffController 扩展（如需）

**包路径：** `com.seafood.bff.admin`

**约束：** bff 层只调 ApplicationService，不可直接调 FeatureFlagRepository（ArchUnit `bff↛infra` 守护）

### 步骤

- [ ] **评估** — 查看 `AdminBffController` 现有实现，判断是否需要 bff 聚合多个服务调用
  - 文件：`backend/src/main/java/com/seafood/bff/admin/AdminBffController.java`
  - 如果 Feature Flag 管理端点已由 `AdminFeatureFlagController` 直接承担（Task 4），bff 层本期可跳过
  - 如果 dashboard 或其他 bff 端点需要引用 flag 状态，则在此 task 加入

- [ ] **如需 bff 扩展**
  - 在 `AdminBffController` 或新的 `AdminBffFeatureFlagController` 中添加聚合端点
  - 测试：`AdminBffFeatureFlagControllerTest`
    - `getFeatureFlagSummary_includesAuditCount()` — bff 聚合 flag + audit 统计

- [ ] **commit** — `feat(bff): AdminBff 集成 FeatureFlagService`（如有改动）

---

## Task 6：GraalVM Native — MongoIndexInitializer 注册 + nativeTest 更新

**文件：** `backend/src/main/java/com/seafood/shared/infra/MongoIndexInitializer.java`

**约束：** `feature_flags` 使用 annotation-derived（performance-only）；`feature_flag_audits` 只有 `flagKey` 降序索引（同样 performance-only）

### 步骤

- [ ] **RED** — 写 `MongoIndexInitializerTest` 新 case（若已有该测试类则添加用例）
  - 验证 `feature_flags` collection 索引被注册（通过 spy 或 integration test）
  - `featureFlagAudits_flagKeyIndex_isCreated()` — audit collection 有 flagKey 索引

- [ ] **实现** — 修改 `MongoIndexInitializer.java`
  - 在 `init()` 方法中添加（紧跟 BannerDocument 之后）：
    ```java
    ensureAnnotationDerived(FeatureFlagDocument.class);
    ensureAnnotationDerived(FeatureFlagAuditDocument.class);
    // feature_flag_audits：flagKey 降序（查询按 flagKey + 时间倒序）
    ensureOptional("feature_flag_audits",
        new Index().on("flagKey", Direction.ASC)
                   .on("timestamp", Direction.DESCENDING)
                   .named("idx_flagKey_timestamp_desc"));
    ```
  - import 两个新 Document 类

- [ ] **nativeTest 更新** — 在 `FeatureFlagServiceIT` 或已有 native test 上打 `@Tag("native")`
  - 只需一个轻量 `@Tag("native")` 方法，让 GraalVM agent 采集新的 MongoDB 反射 metadata
  - 跑 `./gradlew nativeTest` 确认 agent 生成 `src/main/resources/META-INF/native-image/` 更新

- [ ] **GREEN** — 运行完整检查
  ```bash
  cd backend && ./gradlew check
  ```
- [ ] **commit** — `feat(featureflag): MongoIndexInitializer 注册 feature_flags + nativeTest`

---

## Task 7：小程序客户端 SDK — featureflag.js

**文件：** `frontend/utils/featureflag.js`（复用已有 `utils/request.js` 请求工具）

**约束：** TS strict，无新依赖，wx.storage 缓存，onShow 刷新

### 步骤

- [ ] **RED** — 写 Jest 单元测试 `featureflag.test.js`
  - 文件：`frontend/utils/__tests__/featureflag.test.js`
  - Mock `wx.getStorageSync` / `wx.setStorageSync` / `request`
  - 测试用例：
    - `isEnabled_returnsFalse_whenFlagsNotCached()` — 未缓存时默认 false
    - `isEnabled_returnsTrue_whenFlagEnabled()` — 缓存中 flag enabled=true
    - `isEnabled_returnsFalse_whenFlagDisabled()` — 缓存中 flag enabled=false
    - `isEnabled_returnsFalse_whenFlagKeyNotInCache()` — flagKey 不存在时 false
    - `refreshFlags_callsApiAndUpdatesStorage()` — 调用 /api/featureflags 并写入 storage
    - `refreshFlags_doesNotUpdateStorage_onApiError()` — API 失败时保留旧缓存，不抛异常

- [ ] **实现**
  - `frontend/utils/featureflag.js`
    ```javascript
    const STORAGE_KEY = 'feature_flags';
    const API_PATH = '/api/featureflags';

    // 从 wx.storage 读缓存判断
    export function isEnabled(flagKey) {
      const flags = wx.getStorageSync(STORAGE_KEY);
      if (!flags || !Array.isArray(flags)) return false;
      const flag = flags.find(f => f.flagKey === flagKey);
      return flag ? flag.enabled === true : false;
    }

    // 拉取最新 flags 并更新缓存（API 失败时静默保留旧缓存）
    export async function refreshFlags() {
      try {
        const flags = await request({ url: API_PATH, method: 'GET' });
        wx.setStorageSync(STORAGE_KEY, flags);
      } catch (e) {
        // 保留旧缓存，不上抛
      }
    }
    ```

  - 修改 `frontend/app.js`（或 `app.ts`）：
    - `onLaunch` 中调用 `refreshFlags()`
    - `onShow` 中调用 `refreshFlags()`
    ```javascript
    import { refreshFlags } from './utils/featureflag';

    App({
      onLaunch() { refreshFlags(); },
      onShow() { refreshFlags(); }
    });
    ```

- [ ] **GREEN** — 运行前端单元测试
  ```bash
  cd frontend && npm test -- --testPathPattern=featureflag
  ```
- [ ] **commit** — `feat(featureflag): 小程序客户端 SDK featureflag.js + app.js 集成`

---

## 验收清单（全部 task 完成后）

- [ ] `cd backend && ./gradlew check` — 全通过（包含 ArchUnit 四层检查）
- [ ] `cd frontend && npm test` — 全通过
- [ ] 启动后端，验证 `GET /api/featureflags` 返回 200（空数组）
- [ ] 通过 `POST` Mongo shell 插入一条 flag，等待 ≤60s 后 `GET /api/featureflags` 自动反映
- [ ] Admin 端点 `GET /api/admin/feature-flags` 无 JWT 返回 401，有 ADMIN JWT 返回 200
- [ ] `PUT /api/admin/feature-flags/test-flag` 写入后审计记录可查
- [ ] GraalVM Native（可选）：`./gradlew nativeTest` 通过，`nativeCompile` 产物无报错启动
- [ ] 小程序 onLaunch 控制台无 JS 错误，`wx.getStorageSync('feature_flags')` 返回非空数组

---

## 文件清单（新增）

```
backend/src/main/java/com/seafood/featureflag/
├── domain/
│   ├── FeatureFlag.java
│   ├── FlagValue.java
│   └── MurmurHash3Util.java
├── infra/
│   ├── FeatureFlagDocument.java
│   ├── FeatureFlagAuditDocument.java
│   ├── FeatureFlagRepository.java
│   ├── FeatureFlagAuditRepository.java
│   └── FeatureFlagMapper.java
├── application/
│   ├── FeatureFlagCache.java
│   ├── FeatureFlagService.java
│   ├── AuditAction.java
│   ├── FeatureFlagResponse.java
│   └── ClientFlagResponse.java
└── api/
    ├── FeatureFlagController.java
    ├── AdminFeatureFlagController.java
    └── dto/
        └── UpdateFlagRequest.java

backend/src/test/java/com/seafood/featureflag/
├── domain/FeatureFlagTest.java
├── infra/FeatureFlagRepositoryIT.java
├── infra/FeatureFlagAuditRepositoryIT.java
├── application/FeatureFlagServiceTest.java
├── application/FeatureFlagCacheTest.java
├── api/FeatureFlagControllerTest.java
└── api/AdminFeatureFlagControllerTest.java

frontend/utils/featureflag.js
frontend/utils/__tests__/featureflag.test.js
```

**修改文件：**
- `backend/src/main/java/com/seafood/shared/infra/MongoIndexInitializer.java`（Task 6）
- `backend/src/main/java/com/seafood/SeafoodApplication.java`（加 `@EnableScheduling`，如未有）
- `frontend/app.js` / `app.ts`（Task 7，加 refreshFlags 调用）
