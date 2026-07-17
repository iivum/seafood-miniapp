## Context

`order-confirm.js` 本地维护 4 个金额字段(`subtotal/shippingFee/discount/orderTotal`):
- `shippingFee`:按用户选择的配送方式(免运费/顺丰12/中通8)本地映射 `SHIPPING_FEE_MAP`
- `discount`:`满 100 减 10` 占位规则(代码注释已标注"Sprint 3 接真实优惠")
- `orderTotal = subtotal + shippingFee - discount`,展示给用户作为"实付"

后端 `CreateOrderRequest` **只有 `items` 字段**,`shippingMethod` 从未传给后端;`OrderService.persistOrder` 的 `total` 计算是 `Σ items.subtotal`,**不含运费、不含优惠**。也就是说,不只是「优惠」金额对不上——**整个「运费 + 优惠」摘要卡都是前端本地幻觉,后端完全不知道、也不参与计算**,持久化的订单金额只是纯商品小计。

E2E 实测的具体现象(优惠导致 135.11 vs 145.11)只是这个更大缺口的一个可见切片。若只修优惠、不修运费,运费部分(用户选顺丰速运多付 12 元的预期)仍会与后端订单金额继续对不上——这是同一类缺口,分两次修没有意义,属于 CLAUDE.md §2 明确警告的"局部最优"。

## Goals / Non-Goals

**Goals:**
- 用户在结算页看到的「运费 + 优惠 + 实付」与后端实际持久化的订单金额**逐分对齐**,不论走哪条建单路径(购物车 / 直接购买)
- 运费与优惠的计算规则单一权威来源在后端,mp 只做展示(禁止本地算价当作最终依据)

**Non-Goals:**
- 不构建通用优惠券/促销引擎(Sprint 3 范围,本 change 只落地已存在且已在 UI/mp spec 里承诺的两条具体规则:配送方式运费映射 + 满 100 减 10)
- 不改变现有 3 种配送方式的价格档位或折扣阈值数值
- 不引入订单创建前的"预览/试算"新端点(见决策 2 的取舍)

## Decisions

**1. `CreateOrderRequest` 新增 `shippingMethod` 字段,后端成为运费与优惠计算的唯一权威**

`OrderService.persistOrder` 改为:`subtotal = Σ items.subtotal`;`shippingFee` 由后端按 `shippingMethod` 查表(与 mp `SHIPPING_FEE_MAP` 同值,后端持有权威副本);`discount` 由后端按 `subtotal >= 100 ? 10 : 0` 计算(与 mp `calcDiscount` 同规则);`totalAmount = subtotal + shippingFee - discount`。`Order` 聚合/`OrderDocument`/`OrderResponse` 增加 `shippingFee`、`discount` 字段(`subtotal` 可从 items 反推,不强制持久化,但为方便订单详情展示,一并持久化)。

- **为什么不是"前端算完直接传 totalAmount 给后端"**:金额是财务事实,绝不能信任客户端传入值——这是电商系统的基本安全底线,即使当前是内部单卖家运营也不能开这个口子。
- **为什么两条规则都下沉,而不是只下沉优惠**:见 Context——运费同样是前端幻觉,不下沉则本次修复后仍会在"选顺丰速运"场景复现同类金额矛盾,等于没修完。

**2. mp 结算页金额展示 = "本地预估" + "创建后权威回填",不新增试算端点**

`order-confirm.js` 的本地 `calcSubtotal/calcDiscount/SHIPPING_FEE_MAP` 计算**保留**,但明确其角色降级为"预估显示"(逐字复用与后端相同的规则常量,两处各自维护一份但数值锁死一致,靠 1.x 测试守住不漂移)。提交订单后,任何展示金额的页面(下单成功页、订单列表、订单详情)**一律读取后端返回的 `OrderResponse.totalAmount`(以及 shippingFee/discount 明细),不再用本地计算值**。

- **为什么不加一个 `POST /api/orders/quote` 试算端点**:当前两条规则都是无状态纯函数(配送方式→固定运费表、满 100 阈值→固定折扣),后端新增端点+前端多一次网络往返的收益,小于"两处各自维护同一份简单规则常量,用测试锁定不漂移"的成本——过度设计。若未来规则变复杂(动态优惠券、限时活动),再上试算端点,不在本 change 范围。
- **为什么不干脆删掉前端预估显示**:mp spec(`mini-program`)已经明确要求结算页展示"商品总额/运费/优惠/实付"四行,用户体验上"选配送方式后立刻看到价格变化"是合理预期(不适合每次选择都等一次网络往返);预估值在规则同步的前提下等于最终值,只有极端并发场景(后端规则热更新窗口)才可能短暂不一致,可接受。

**3. `Order` 聚合金额字段扩展的兼容性**

新增 `shippingFee`/`discount` 字段,`totalAmount` 计算方式改变(纳入运费与优惠)。历史订单(改动前创建)缺这两个字段,读取时按 0 兜底(不回填历史数据,`totalAmount` 保持原值不变——不做历史订单金额重算,避免账实不符的更大问题)。

## Risks / Trade-offs

- [risk] mp 本地规则常量与后端规则常量分处两地,未来只改一处会导致预估/最终值再次分裂 → [mitigation] 两侧各补测试锁定当前数值(mp Jest + 后端单测各自断言具体数字),code review checklist 补一条"改运费/优惠规则需同时改 mp `SHIPPING_FEE_MAP`/`calcDiscount` 与后端对应常量"
- [risk] `totalAmount` 计算口径变化影响下游(退款上限校验、`orders.paid` amountBucket 分桶、CSV 导出)→ [mitigation] 这些下游本身就该用"最终应付金额",纳入运费优惠后语义更正确而非破坏;补 IT 覆盖退款/导出场景金额口径不回归
- [risk] 直接购买路径(`create(userId, items)`,绕开购物车)当前也不带 shippingMethod → [mitigation] 该路径的 `CreateOrderRequest` 同样接收可选 `shippingMethod`(缺省按 `FREE` 兜底,与 mp 默认选中项一致)

## Migration Plan

- `Order`/`OrderDocument` 加字段,MongoDB 文档级新增字段无需 schema 迁移(历史文档缺字段读取按 0 兜底)
- `CreateOrderRequest` 新增可选字段(`shippingMethod` 可空,缺省 FREE),向后兼容旧客户端请求体
- 部署顺序:后端先上线(接受可选字段、新逻辑生效),mp 端跟进传递 `shippingMethod` 并把展示源切到后端返回值;中间窗口期后端按 FREE 兜底,不会报错
- 回滚:还原 `OrderService.persistOrder` 计算逻辑与 DTO 字段即可,无残留状态
