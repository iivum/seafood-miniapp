## Why

核心 domain 有一批数值/状态边界不变量,example-based 测试只能验有限几个点位:`Product.decrementStock`(qty 与 stock 的关系)、`OrderStatus` 7 态状态机的合法/非法转移、`Sku`/`Product` 紧凑构造器的校验(price>0、stock≥0、name 长度、首 SKU 与商品价格/库存一致)。这些「难枚举边界」正是 roadmap C4 的触发条件 —— property-based testing 用随机生成的大量输入逼出 example 测试漏掉的反例,把不变量当契约来验。

## What Changes

- 引入 property-based testing 到核心 domain 层,对不变量写 ∀-量化的 property(随机输入下不变量恒成立 / 非法输入恒抛 `DomainException`)
- 库选型 **spike 先行**:jqwik 1.10.1 优先(最 idiomatic),但其引擎绑 junit-platform 1.14.x,项目在 6.0.3 —— 兼容性未验证;**不兼容则回退 QuickTheories**(无自定义引擎,跑普通 `@Test`,平台版本无关)
- 首批 3 组 domain 不变量:`Product.decrementStock` 数值边界、`OrderStatus` 状态机、`Sku`/`Product` 构造校验
- 纯 test-scope,native 无关;不碰 application/infra(那是 example-based 的地盘)

## Capabilities

### New Capabilities
- `property-testing`: domain 不变量的 property-based 测试接入(库选型与回退)、首批覆盖的不变量、随机生成器约束、以及与现有 example-based 测试的边界

### Modified Capabilities
<!-- 无:新增 property 测试,不改 domain 行为也不改现有测试 -->

## Impact

- `backend/build.gradle`:新增 PBT 库 testImplementation(jqwik 或 QuickTheories,spike 定)
- 新增 domain property 测试类(`product.domain` / `order.domain`)
- **兼容性风险(C1/C2 教训,spike 先行)**:jqwik 引擎 vs junit-platform 6.0.3 可能在引擎发现阶段硬崩(TestEngine SPI 编译期绑死,无法靠 force 版本解决);QuickTheories 兜底
- 无生产代码改动,无 API 变更,无 BREAKING
