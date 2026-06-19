# Tasks — domain property-based testing(C4)

> 顺序按 design Migration Plan:库选型 spike 先行(C1/C2 教训)。

## 1. 库选型 spike(D1 — 阻塞后续)

- [x] 1.1 `build.gradle` 加 `testImplementation 'net.jqwik:jqwik:1.10.1'`
- [x] 1.2 `JqwikSpikeTest`:trivial `@Property additionIsCommutative(@ForAll int a,b)`
- [x] 1.3 跑通:报告 `tests="1"` + 执行 0.607s(trivial property 跑这么久 = 上千组样本),jqwik 引擎在 junit-platform 6.0.3 被正常发现执行
- [x] 1.4 **判定:jqwik 通过** → 用 jqwik(不需 QuickTheories 回退)。版本线差 5 个大版本(1.14.x vs 6.0.3)但 TestEngine SPI 未崩。spike 探针文件用毕删除

## 2. decrementStock 数值边界 property(打通模式)

- [x] 2.1 `ProductDecrementStockProperties`(product.domain)
- [x] 2.2 property:∀ `0 < qty ≤ stock`(jqwik `@IntRange` + `Assume`)→ `decrementStock(qty).stock() == stock-qty` 且 ≥0
- [x] 2.3 property:∀ `qty > stock` → DomainException;∀ `qty ≤ 0` → DomainException
- [x] 2.4 跑通:3 property,jqwik 跑上千组样本(0.6s 级),0 failures

## 3. OrderStatus 状态机 + Sku/Product 构造 property

- [x] 3.1 `OrderStatusProperties`:∀ 7 状态 `of(code()) == status`(round-trip 恒等)
- [x] 3.2 ∀ 终态(Cancelled/Refunded)× ∀ 目标 → `canTransitionTo` == false;bonus:∀ 状态无自环
- [x] 3.3 `ProductConstructionProperties`:Sku/Product ∀ 合法字段(name 1-100、price>0、stock≥0)→ 构造成功;∀ name>100 / price≤0 / stock<0 → DomainException
- [x] 3.4 全 3 类共 13 property 全绿(OrderStatus 3 + Construction 7 + decrementStock 3),全量 test 绿

## 4. 收尾

- [x] 4.1 README「后端测试」段加 property 测试说明(jqwik + 覆盖的不变量 + 平台兼容)
- [x] 4.2 commit(见下)
- [x] 4.3 回填 roadmap tasks T13:C4 done(jqwik 1.10.1,平台兼容性 spike 通过)
- [x] 4.4 归档 + sync `property-testing` spec 到 `openspec/specs/`
