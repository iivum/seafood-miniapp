# Design: 引入 Feature Flag 平台

## Context

当前发布是 "all or nothing":代码合入 main 即对 100% 用户生效,新功能 / 改版 / 灰度均无手段。Phase 1 可观测性补齐后,事故可定位但**无法事前预防** — 需要灰度基建(1% → 10% → 50% → 100%)把爆炸半径压到最小。

后续 Phase 2 任务(React 后台迁移 #2、小程序 E2E #3)上线,都依赖灰度能力才能安全发版(尤其 admin-ui 后台首次上线,需要按 admin 角色白名单放量)。

## Goals / Non-Goals

**Goals:**

- 单一 flag 平台(后端 + 小程序 + admin 共享同一数据源,确保一致)
- 三种灰度策略:百分比 / 白名单 / 地域(接口预留,本期只实现前两个)
- 启动时全量预加载到内存 + `@Scheduled` 定时 60s 刷新(避免每次请求打 DB)
- GraalVM Native 兼容(无动态 class loading,flag 是数据而非代码)
- Admin 后台可视化管理(BFF 3 端点 + 审计 log)
- 强过期机制:`expiresAt` 自动 disable,防配置漂移

**Non-Goals:**

- A/B 测试统计显著性分析(只做发布控制,不做实验分析 — 后续可接 GrowthBook)
- 复杂 targeting 规则(本期只支持百分比 + 白名单,后续可扩展)
- 客户端实时推送(本期用 60s 轮询,准确且简单)
- 多租户 / 多项目隔离(单租户单项目)
- Server-side flag SDK for other languages(只做 Java 后端 + 小程序)

## Decisions

### 1. 存储:复用现有 MongoDB `feature_flags` collection

**为什么不引入 Redis?** 避免新基础设施,flag 读写量小(预估 100 flags × 每分钟 60s 刷新 = 100 ops/min),MongoDB 完全扛得住,且复用现有的 MongoIndexInitializer / native-image metadata。

**为什么不放配置文件?** 配置不支持运行时变更,做不到"灰度发布"。

Schema:

```javascript
// feature_flags collection
{
  _id: ObjectId,
  flagKey: "new-checkout",         // 唯一,kebab-case
  enabled: true,                    // 总开关
  rolloutPercentage: 10,            // 0-100
  userSegments: ["vip-user-123"],  // 白名单,优先级高于百分比
  expiresAt: ISODate("2026-12-31"), // null 表示永不过期
  description: "新版结算页灰度",     // admin UI 展示
  createdBy: "linbinghui",
  updatedAt: ISODate,
  createdAt: ISODate
}

// feature_flag_audits collection (append-only,物理不可删)
{
  _id: ObjectId,
  flagKey: "new-checkout",
  action: "ENABLE" | "DISABLE" | "PERCENTAGE_CHANGE" | "WHITELIST_ADD" | ...,
  before: {...},
  after: {...},
  actor: "linbinghui",
  timestamp: ISODate
}
```

### 2. 后端 SDK:方法注入优先,注解可选

```java
// 核心 API(推荐用这个)
featureFlagService.isEnabled("new-checkout", userId);   // boolean
featureFlagService.getVariant("checkout-style", userId, "A");  // A/B string

// 注解(简单场景,隐藏判断逻辑,debug 难,不推荐)
@FeatureFlag("new-checkout")
public CheckoutResult checkout(...) {...}
```

**为什么方法注入优先?** 注解把判断藏起来,debug 时不知道 flag 当前状态;方法调用可在 IDE 里直接看,加 log 也容易。

### 3. 启动预加载 + 定时刷新

```java
@Component
public class FeatureFlagCache {
    private volatile Map<String, FeatureFlag> cache = Map.of();

    @PostConstruct
    void load() { refresh(); }

    @Scheduled(fixedDelay = 60_000)
    void refresh() {
        cache = repository.findAllEnabled().stream()
            .collect(toMap(FeatureFlag::flagKey, identity()));
    }
}
```

- 读路径:内存 `Map.get`,O(1),无 DB 访问
- 写路径:admin 后台更新 → `@Scheduled` 60s 后自动同步(可优化为 WebSocket / Redis pub-sub,本期不做)
- 启动 fail-fast:如果 MongoDB 不可用,启动直接失败(不进入"全默认 enable"或"全默认 disable"灰色态)

### 4. 灰度算法:MurmurHash3

```java
public boolean isInRollout(String userId, int percentage) {
    int hash = Math.abs(MurmurHash3.hash32x86(userId.getBytes()));
    return (hash % 100) < percentage;
}
```

**为什么用 MurmurHash3?** 简单 hash(`userId.hashCode() % 100`)分布不均,某些 ID 段可能 100% 中或 0% 中;MurmurHash3 雪崩特性好,分布均匀。

### 5. 小程序客户端:启动拉一次 + onShow 刷新

```typescript
// app.ts
onLaunch() {
  this.refreshFeatureFlags();
}
onShow() {
  this.refreshFeatureFlags();
}
async refreshFeatureFlags() {
  const flags = await request({ url: '/api/featureflags/client' });
  wx.setStorageSync('feature_flags', flags);
}

// 在业务代码里用
const enabled = wx.getStorageSync('feature_flags')?.['new-checkout']?.enabled ?? false;
```

- 公共端点 `/api/featureflags/client` 只返客户端可见 flag(服务端 flag 不返,避免泄漏)
- 本地缓存到 `wx.storage`,启动 / onShow 触发刷新(后台切回前台会刷新)
- 用户切换 / 重启小程序会重新拉

### 6. Admin BFF:3 个端点 + RBAC

```yaml
# /api/admin/feature-flags
GET    /api/admin/feature-flags              # 列表(分页 + 搜索)
PUT    /api/admin/feature-flags/{flagKey}    # 更新(必须带 audit)
GET    /api/admin/feature-flags/{flagKey}/audit  # 审计 log(分页)
```

- RBAC:`@PreAuthorize("hasRole('ADMIN')")`,只 admin 角色能改
- 写操作必填 `actor`(从 JWT 拿),审计 log 不可删

### 7. GraalVM Native 兼容

- flag 是数据,不涉及动态类加载,无 `Class.forName` / 反射
- `MongoIndexInitializer` 注册 `feature_flags` collection
- `@Scheduled` 在 Spring 6 + Boot 4 已 native-compatible
- `nativeTest` 阶段让 GraalVM agent 采集 metadata(Spring `@Scheduled` + MongoDB 反射)

## Risks / Trade-offs

| 风险 | 严重度 | 缓解 |
|---|---|---|
| 配置漂移(flag 多了没人清理) | 高 | `expiresAt` 强制过期 + `@Scheduled` 扫描 disable + admin UI 红色告警 |
| 内存全量加载 flag 多了 OOM | 中 | 当前预估 100 flags,内存可忽略;后续 1000+ 考虑分片 + LRU |
| 60s 刷新延迟 | 中 | 写后 60s 才生效,这是有意的"防手抖"机制,管理员可强制立即刷新(BFF 加 `POST /refresh` 端点) |
| 灰度 hash 不均 | 中 | MurmurHash3 雪崩特性,实测分布 ±2% 偏差 |
| 审计合规 | 高 | `feature_flag_audits` 物理不可删(无 update / delete API) + 写后 immutable |
| 跨 bounded context 访问污染 | 中 | featureflag 是独立 bounded context,严格 DDD 四层,API 层通过 ApplicationService 调用 |
| Admin 后台 UI 暂未迁移 | 中 | 本期只做 BFF + 持久层,UI 留接口,React 后台迁移时再接 |
| 小程序缓存一致性问题 | 低 | 60s 容忍度对核心功能够用;支付/订单等关键 flag 在 client 端额外实时拉 |

## Open Questions

1. **MurmurHash3 vs FNV-1a**:两者分布都好,MurmurHash3 雪崩更好但稍慢(每用户 1 次判断,无影响),选 MurmurHash3 — 待 Phase 3 灰度规模化时再回归
2. **Admin UI 时机**:本期只做 BFF + DB,UI 留接口待 React 后台迁移(Phase 1 #2)— 但 BFF 接口规范要写好(spec)
3. **客户端实时拉取**:是否对关键 flag(支付)绕过 60s 缓存,直接实时查? — 短期不,后续可加 `?realtime=true` query param
4. **多环境 flag 隔离**:dev / staging / prod 是否用同一份 flag? — 短期是(用 prefix 区分 `dev-new-checkout` / `prod-new-checkout`),后续可拆 collection
5. **A/B 测试灰度统计**:是否对接数数 / 神策? — 后续 task,本期只做发布控制,不做实验分析
