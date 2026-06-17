# 2026-06-18 · ad 6 屏 vs OD 设计 GAP 报告

> OD 设计源:Open Design 项目 `686e3434-0233-451e-9c99-debee025a336`
> (用户在 OD 重新设计 seafood-miniapp,projectName:
>  "查看... seafood-miniapp 中前端的部分,我要重新设计这个用户端是微信小程序,管理后台是 web 的应用界面")
>
> 自动化测试网已就位:`admin-ui/src/__tests__/ad-od-design.test.tsx`
> TDD RED 阶段:**8 FAIL / 36 PASS / 44 total**
>
> 跑:`cd admin-ui && npx vitest run src/__tests__/ad-od-design.test.tsx`

## 1. OD 设计源(从 OD HTML mockup 抽)

| 屏 | OD HTML 文件 | 关键元素 |
|---|---|---|
| ad-01 | ad-01-login.html | 浏览器框架 .br + 双 pane + 欢迎回来/Account/Password/7 天内免登录/忘记密码 |
| ad-02 | ad-02-dashboard.html | 4 KPI(TODAY · GMV / ORDERS / AVG ORDER / CONVERSION)+ 14d 趋势 chart + 分类销售 + 销量 TOP 10 + 导出报表 |
| ad-03 | ad-03-product-list.html | 新增商品 / 导出 CSV / 5 状态 tab(全部/在售/缺货/已下架) + 5 分类 |
| ad-04 | ad-04-product-form.html | 9 字段(名称/描述/价格/库存/状态/分类/图片/...) + SKU + 创建按钮 |
| ad-05 | ad-05-order-list.html | 5 状态 tab + 批量发货 + 导出 CSV |
| ad-06 | ad-06-order-detail.html | 订单编号/用户/商品/金额/地址/状态 |

## 2. 8 个 RED 详情

### ad-01 login (3 fail)
- ✗ "欢迎回来" 主标题 — 当前 LoginPage 用 "海鲜商城管理后台" + "请使用管理员账号登录"
- ✗ "7 天内免登录" checkbox — 当前 LoginPage 用 "记住我(下次自动填用户名)"
- ✗ "忘记密码?" 链接 — 当前 LoginPage 完全没有

### ad-02 dashboard (4 fail)
- ✗ GMV 销售额 KPI — 当前用"今日订单 / 本周订单 / 本月订单 / 在售商品"
- ✗ AVG ORDER 客单价 KPI — 当前缺
- ✗ CONVERSION 转化率 KPI — 当前缺
- ✗ "导出报表" 按钮 — 当前缺
- (PASS: ORDERS 订单数 / 趋势 / TOP 10 / 分类 — 因为 regex 宽松,匹配到相关 token)

### ad-04 product-form (1 fail)
- ✗ "创建" 提交按钮文案 — 当前 ProductForm 用 "保存" / "发布"

### ad-03 / ad-05 / ad-06 全 PASS
- regex 太宽松(如 ORDERS 匹到"订单",GMV 匹到"销售额"等),实际 OD 元素 vs 当前实现仍有 gap
- **这些 PASS 是 false positive**,需要更精确的 regex 才能识别真 gap

## 3. 完整 OD vs 当前 admin-ui 状态

| 屏 | sprint 1 closure 状态 | OD 设计状态 | 主要 GAP |
|---|---|---|---|
| ad-01 login | 单 pane 表单,基础功能 | 浏览器框架 + 双 pane hook 文案 | 3 元素 |
| ad-02 dashboard | 4 简单 KPI + 7d trend | GMV/ORDERS/AVG/CONVERSION + 14d + TOP10 + 分类 + 导出 | 大 |
| ad-03 product list | DataTable + tab | 同样结构 + 库存图表 | 小 |
| ad-04 product form | 8 字段 + dialog | 9 字段 + SKU | 1 文案 |
| ad-05 order list | 5 状态 tab + 操作 | 同样 + 批量 | 0(false pass) |
| ad-06 order detail | 基础 3 段 | 详细 6 段 | 0(false pass) |

## 4. Sprint 1 closure 验收 vs OD 设计

| 维度 | sprint 1 closure 验收 | OD 设计验收 |
|---|---|---|
| 完成度 | 6 屏业务可达,vitest 89/89 PASS,Playwright 6 屏截图 | OD mockup 完整 9+6 屏,设计语言成型 |
| 视觉 | 按 checkpoint 4 层断言验收(结构/数据/行为/颜色)— **未做 OD 视觉对齐** | 暖橘 OKLch 调色板 + Fraunces serif display + 浏览器框架 + 双 pane hook |
| 业务 | 4 KPI(今日/本周/本月/在售)+ 7d trend + 近期订单 | GMV/ORDERS/AVG/CONVERSION + 14d chart + 分类销售 + 销量 TOP10 + 导出报表 |

**结论**:sprint 1 closure 完成"业务可达",但**视觉/数据都未对齐 OD 设计**。

## 5. Sprint 2 行动清单(从 RED 失败派生)

| 优先级 | 行动 | 估时 |
|---|---|---|
| **P0** | ad-01 login 重做(欢迎回来/7 天/忘记密码 + 浏览器框架) | 0.5d |
| **P0** | ad-02 dashboard KPI 重做(GMV/ORDERS/AVG/CONVERSION + 14d + TOP 10) | 1d |
| P1 | ad-04 product-form 改"创建"按钮文案 + 加 SKU 字段 | 0.5d |
| P1 | ad-03/ad-05/ad-06 更精确 regex 重测,识别 false positive | 0.2d |
| P2 | ad 6 屏全重截图(Playwright after refactor) | 0.5d |
| P2 | token parity 改 hard fail | 0.2h |

## 6. 当前 ad 测试覆盖(已就位)

| 测试 | 类型 | 数量 |
|---|---|---|
| `mp-od-design.test.ts` | mp 8 屏 OD 对齐(frontend) | 38 tests(16 fail) |
| `ad-od-design.test.tsx` | ad 6 屏 OD 对齐(admin-ui) | 44 tests(8 fail) |
| `token-parity.test.ts` | mp 颜色 token parity | 8 tests(全过) |
| `mp-3layer.test.ts` | mp 4 层断言 | 21 tests(15 fail,WebSocket stall) |
| ad 业务 vitest | 6 屏业务 | 89 tests(全过) |

## 7. 下一步

- **不要**在本轮修 ad-01/02/04 改 OD — 工作量大,需 design review
- **修** ad-04 "创建" 文案(1 行)— 最小成本收 1 个 RED
- **更精确** regex 重测 ad-03/05/06(避免 false pass)— 识别真 gap
- **Sprint 2 路线图**:ad 6 屏按 OD 改写(估计 3-4d,需 design owner 拍板细节)
