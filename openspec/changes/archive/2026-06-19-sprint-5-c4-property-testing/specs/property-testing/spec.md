## ADDED Requirements

### Requirement: PBT 库在项目工具链上可运行

property 测试 SHALL 在 Java 25 + Gradle 9 + junit-platform 6.0.3 工具链上被发现并执行;若选定的 PBT 库引擎与 junit-platform 6 不兼容,MUST 回退到无自定义引擎的方案(跑普通 `@Test`),不得让 property 测试静默不运行。

#### Scenario: property 测试被测试运行器发现并执行

- **WHEN** 执行 `./gradlew test`
- **THEN** domain property 测试被运行(测试报告里出现对应用例),且每个 property 跑了多组随机样本(非单点)

#### Scenario: 引擎不兼容时回退而非静默跳过

- **WHEN** 选定 PBT 库的 TestEngine 在 junit-platform 6 上无法被发现/执行
- **THEN** 改用无引擎方案(PBT 库以普通 `@Test` 内循环运行),property 仍真实执行,不出现「0 tests found」式静默

### Requirement: decrementStock 数值边界 property

`Product.decrementStock` SHALL 有 property 覆盖其数值契约:对随机生成的 stock 与 quantity,结果与异常行为符合不变量。

#### Scenario: 合法扣减保持库存不变量

- **WHEN** 随机生成 `0 < quantity ≤ stock`
- **THEN** `decrementStock(quantity)` 返回的 stock 等于 `原 stock − quantity` 且 ≥ 0

#### Scenario: 超量扣减恒拒绝

- **WHEN** 随机生成 `quantity > stock`(或 `quantity ≤ 0`)
- **THEN** `decrementStock` 抛 `DomainException`

### Requirement: OrderStatus 状态机 property

`OrderStatus` SHALL 有 property 覆盖状态机与编码契约。

#### Scenario: code 与 of 互逆(round-trip)

- **WHEN** 对每个合法状态取 `code()` 再 `of(code)`
- **THEN** 得到等价的状态(round-trip 恒等)

#### Scenario: 终态不可转出

- **WHEN** 当前状态是终态(如 `Cancelled` / `Refunded`)
- **THEN** 对任意目标状态 `canTransitionTo` 恒为 false

### Requirement: Sku / Product 构造校验 property

`Sku` 与 `Product` 的紧凑构造器 SHALL 有 property 覆盖其校验契约。

#### Scenario: 合法输入恒构造成功

- **WHEN** 随机生成满足全部约束的字段(name 非空且 ≤100、price>0、stock≥0,首 SKU 与商品 price/stock 一致)
- **THEN** 构造成功不抛异常

#### Scenario: 非法输入恒抛 DomainException

- **WHEN** 随机生成违反任一约束的字段(空 name / name>100 / price≤0 / stock<0)
- **THEN** 构造抛 `DomainException`
