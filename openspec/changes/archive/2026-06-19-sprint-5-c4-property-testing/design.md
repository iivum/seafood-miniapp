# Design — domain property-based testing(C4)

## Context

roadmap C4 触发条件「domain 难枚举边界」成立:`Product.decrementStock`(qty/stock 数值关系)、`OrderStatus` 7 态状态机(合法/非法转移 + `of`/`code` round-trip)、`Sku`/`Product` 紧凑构造器(price>0、stock≥0、name≤100、首 SKU 与商品 price/stock 一致)。example-based 测试只验有限点位,PBT 用随机输入逼反例。

工具链:Java 25 + Gradle 9 + junit-platform **6.0.3**(JUnit 6 线)。已知:jqwik 最新 **1.10.1** 依赖 junit-platform **1.14.4**(JUnit 5.14 线)。两条版本线之间 TestEngine SPI 有变动(PIT 踩的 `EngineDiscoveryRequest.getOutputDirectoryCreator` 是 platform 6 新增),jqwik 引擎是编译期绑死旧 SPI,**无法靠 force 版本对齐解决**(不同于 PIT 的 launcher 问题)。

## Goals / Non-Goals

**Goals**
- 对 3 组核心 domain 不变量写 property 测试,随机样本逼反例
- 库选型 spike 先行:jqwik 优先,不兼容则 QuickTheories 兜底,property 真实运行不静默
- 纯 test-scope,零生产改动

**Non-Goals**
- 不对 application/infra 写 property(那是 example-based + Mockito 的地盘)
- 不追求穷尽所有 domain 方法,首批 3 组,后续增量
- 不改 domain 行为、不改现有 example 测试
- 不引入 PBT 的 stateful/model-based 高级特性(YAGNI)

## Decisions

### D1:库选型 spike 先行,jqwik 优先 + QuickTheories 兜底

- **Task 1 spike**:加 jqwik 1.10.1,写一个 trivial `@Property`,跑 `./gradlew test` 看 jqwik 引擎能否在 junit-platform 6.0.3 上被发现并执行多组样本。
- **通** → 用 jqwik:`@Property` + `@ForAll` 生成器,最 idiomatic,失败自动 shrink 到最小反例。
- **不通**(很可能,版本线差 5 个大版本)→ 回退 **QuickTheories**(`org.quicktheories:quicktheories`):它**无自定义 TestEngine**,以普通 `@Test` 内 `qt().forAll(...).check(...)` 运行,由现有 Jupiter 引擎执行 → junit-platform 版本完全无关,彻底绕开风险。
- **理由**:C4 的目标是 property 测试 domain,不是 jqwik 本身。两个库都能表达 ∀-property;QuickTheories 用更少的「框架魔法」换确定的兼容性。

### D2:不变量到 property 的映射

| 目标 | property |
|---|---|
| `decrementStock` | ∀ 0<qty≤stock → result.stock == stock−qty ∧ ≥0;∀ qty>stock ∨ qty≤0 → DomainException |
| `OrderStatus` | ∀ 合法 code:`of(code).code() == code`;∀ 终态 × ∀ 目标:`canTransitionTo` == false |
| `Sku`/`Product` 构造 | ∀ 合法字段 → 构造成功;∀ 违约字段 → DomainException |

生成器约束:stock/price 用有界正数(避免溢出);name 用受控长度区间覆盖边界(0 / 1 / 100 / 101)。

### D3:作用域 = domain 纯函数

property 测试只碰 domain 聚合/值对象的纯逻辑(无 IO、无 Spring),与现有 `ProductTest`/`OrderStatusTest` 等 example 测试并列、互补。文件按 domain 包组织(`product.domain` / `order.domain`),小而聚焦。

## Risks / Trade-offs

- **[jqwik 引擎在 junit-platform 6 上硬崩]** → Mitigation:D1 spike 先验;失败即回退 QuickTheories,change 不卡死。这是首要风险,spike 是 go/no-go。
- **[QuickTheories 也不兼容/停更]** → Mitigation:QuickTheories 是纯 Java 库、无引擎依赖,跑在 @Test 里,几乎不可能被 junit-platform 版本影响;退一万步可手写有界随机循环(种子固定保可复现)。
- **[property 跑得慢拖累 test]** → Mitigation:首批 3 组、每 property 默认样本数(jqwik 1000 / QuickTheories ~1000)毫秒级,domain 纯函数无 IO,可忽略。
- **[随机不可复现导致 flaky]** → Mitigation:两库都支持固定 seed + 失败复现;CI 失败会打印 seed。

## Migration Plan

1. spike jqwik(Task 1 go/no-go)
2. 选定库后写第 1 组 property(decrementStock),打通模式
3. 补 OrderStatus + Sku/Product 两组
4. README 一句 + 归档

**Rollback**:纯 test 依赖 + 新测试文件,摘依赖删文件即可,零生产影响。

## Open Questions

- jqwik 1.10.1 引擎能否在 junit-platform 6.0.3 上被发现执行?→ Task 1 spike 实测,这是选 jqwik 还是 QuickTheories 的唯一决定因素。

## Sources

- [jqwik](https://jqwik.net/) — 1.10.1 最新,依赖 junit-platform 1.14.x
- [QuickTheories](https://github.com/quicktheories/QuickTheories) — 无引擎,@Test 内运行,平台无关
