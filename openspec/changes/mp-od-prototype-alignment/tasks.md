## 1. mp-01 home ✅

- [x] 1.1 诊断：跑 `npm run test:visual mp-01-home` + `npm run test:geometry mp-01-home`，记录当前感知 diff% + 几何层各项状态 + 偏离区域。结果：感知 66.99% RED（比 C5 的 61% 还差）；几何层 4/4 GREEN（banner/category-row/section-header/grid-columns）
- [x] 1.2 对照 golden + diff 图列偏离点：顶部定位/搜索栏整块缺失、filter chip 行整块缺失、hero banner 视觉语言完全不同（浅色小卡片 vs OD 深色沉浸大卡片带 LIVE/价格/CTA）、品类导航从圆形图片图标变成扁平文字 chip、section header 文案/查看全部链接缺失。发现现有 spec 里 mp-01 requirement 文字描述本身与 OD golden 不符（过时）
- [x] 1.3 写 task brief（`.superpowers/sdd/mp-od-1-home-brief.md`），派 implementer subagent 修复（commit `3e47552`：新增顶部栏+搜索复用死代码、banner 前端近似深色重样式、修复分类硬编码 4→5 真实数据 bug、filter chip 行仅"全部"功能性、section header 动态文案，34 个新测试，327/327 全绿）；task reviewer 复查：spec 6/6 符合，代码质量 Approved（1 条 Important：`.home-chip` class 语义偷换影响 geometry 检查）
- [x] 1.4 复验：控制器直接修了 reviewer 指出的 geometry selector 问题（`category-row` 改指向 `.home-category`，新增 `filter-chip-row` 检查），复验时额外发现 banner tone class 大小写不匹配的真 bug（wxml 绑定后端大写枚举 `ACCENT`/`SOFT`，CSS 写小写 `--accent`/`--soft`，导致深色样式从未生效），已修（commit `e645a8d`）。最终：几何层 5/5 GREEN；感知层 66.99%→56.26%，剩余差距逐项核对为预期内容差异（OD mockup 冻结文案/图片 vs 真实 seed 数据：banner 文案、产品图、"55 款"vs"9 款"），非结构/样式偏差
- [x] 1.5 commit 完成（`3e47552` + `e645a8d`），ledger 见下方

## 2. mp-02 category

- [ ] 2.1 诊断：跑 `npm run test:visual mp-02-category` + `npm run test:geometry mp-02-category`，记录当前状态（C5 baseline 66% RED，category 曾有前后端分类契约 bug 已修，需确认现状）
- [ ] 2.2 对照 `frontend/e2e/od-golden/mp-02-category.png` + diff 图，列出偏离点清单
- [ ] 2.3 写 task brief（偏离清单 + diff 图 + `frontend/pages/category/category.*` + mp-02 spec requirement 原文），派 subagent 修复，task reviewer 复查
- [ ] 2.4 复验：重跑 harness，确认 ≤5%（或几何全绿）
- [ ] 2.5 commit，更新 ledger

## 3. mp-03 product-detail

- [ ] 3.1 诊断：跑 `npm run test:visual mp-03-product-detail` + `npm run test:geometry mp-03-product-detail`，记录当前状态（C5 baseline 67% RED，最差的数据屏之一）
- [ ] 3.2 对照 `frontend/e2e/od-golden/mp-03-product-detail.png` + diff 图，列出偏离点清单
- [ ] 3.3 写 task brief（偏离清单 + diff 图 + `frontend/pages-sub/product/product-detail/*` + mp-03 spec requirement 原文），派 subagent 修复，task reviewer 复查
- [ ] 3.4 复验：重跑 harness，确认 ≤5%（或几何全绿）
- [ ] 3.5 commit，更新 ledger

## 4. mp-04 cart

- [ ] 4.1 诊断：跑 `npm run test:visual mp-04-cart` + `npm run test:geometry mp-04-cart`，记录当前状态（C5 baseline 55% RED）
- [ ] 4.2 对照 `frontend/e2e/od-golden/mp-04-cart.png` + diff 图，列出偏离点清单
- [ ] 4.3 写 task brief（偏离清单 + diff 图 + `frontend/pages/cart/cart.*` + mp-04 spec requirement 原文），派 subagent 修复，task reviewer 复查
- [ ] 4.4 复验：重跑 harness，确认 ≤5%（或几何全绿）
- [ ] 4.5 commit，更新 ledger

## 5. mp-06 order-confirm

- [ ] 5.1 诊断：跑 `npm run test:visual mp-06-order-confirm` + `npm run test:geometry mp-06-order-confirm`，记录当前状态（C5 baseline 35% RED + 已知"空购物车直达"问题，需确认根因是否仍存在）
- [ ] 5.2 若空购物车问题仍存在：用 `superpowers:systematic-debugging` 定位根因（购物车/直购状态传递链路），确认是前端状态传递 bug 还是后端数据问题
- [ ] 5.3 对照 `frontend/e2e/od-golden/mp-06-order-confirm.png` + diff 图，列出偏离点清单（含 5.2 的根因结论）
- [ ] 5.4 写 task brief（偏离清单 + diff 图 + 根因结论 + `frontend/pages-sub/order/order-confirm/*` + mp-06 spec requirement 原文），派 subagent 修复样式 + 空购物车 bug，task reviewer 复查
- [ ] 5.5 复验：重跑 harness 确认 ≤5%（或几何全绿）；手动走一遍 direct-buy 入口（product-detail 立即购买）确认不再出现空购物车
- [ ] 5.6 commit，更新 ledger

## 6. mp-07 address

- [ ] 6.1 诊断：跑 `npm run test:visual mp-07-address` + `npm run test:geometry mp-07-address`，记录当前状态；同时确认 `AddressController` 是否已解决 C5 记录的 403 问题（`GET /api/addresses` 手动请求验证）
- [ ] 6.2 若 403/空态问题仍存在：用 `superpowers:systematic-debugging` 定位根因（Controller 路由/鉴权/前端请求路径）
- [ ] 6.3 对照 `frontend/e2e/od-golden/mp-07-address.png` + diff 图，列出偏离点清单（含 6.2 的根因结论）
- [ ] 6.4 写 task brief（偏离清单 + diff 图 + 根因结论 + `frontend/pages-sub/user/address/*` + mp-07 spec requirement 原文），派 subagent 修复样式 + 后端/鉴权 bug，task reviewer 复查
- [ ] 6.5 复验：重跑 harness 确认 ≤5%（或几何全绿）；确认地址列表在真实登录态下正常加载（非 403/空）
- [ ] 6.6 commit，更新 ledger

## 7. mp-08 order-list

- [ ] 7.1 诊断：跑 `npm run test:visual mp-08-order-list` + `npm run test:geometry mp-08-order-list`，记录当前状态（C5 baseline 29% RED）
- [ ] 7.2 对照 `frontend/e2e/od-golden/mp-08-order-list.png` + diff 图，列出偏离点清单
- [ ] 7.3 写 task brief（偏离清单 + diff 图 + `frontend/pages-sub/order/order-list/*` + mp-08 完整布局 spec requirement 原文，注意与既有 action-row requirement 的边界），派 subagent 修复，task reviewer 复查
- [ ] 7.4 复验：重跑 harness，确认 ≤5%（或几何全绿）
- [ ] 7.5 commit，更新 ledger

## 8. mp-09 order-detail

- [ ] 8.1 诊断：跑 `npm run test:visual mp-09-order-detail` + `npm run test:geometry mp-09-order-detail`，记录当前状态（C5 baseline 30% RED）
- [ ] 8.2 对照 `frontend/e2e/od-golden/mp-09-order-detail.png` + diff 图，列出偏离点清单
- [ ] 8.3 写 task brief（偏离清单 + diff 图 + `frontend/pages-sub/order/order-detail/*` + mp-09 spec requirement 原文），派 subagent 修复，task reviewer 复查
- [ ] 8.4 复验：重跑 harness，确认 ≤5%（或几何全绿）
- [ ] 8.5 commit，更新 ledger

## 9. mp-05 profile

- [ ] 9.1 诊断：跑 `npm run test:visual mp-05-profile` + `npm run test:geometry mp-05-profile`，记录当前状态（C5 baseline 71% RED，9 屏中最差）
- [ ] 9.2 对照 `frontend/e2e/od-golden/mp-05-profile.png` + diff 图，列出偏离点清单
- [ ] 9.3 写 task brief（偏离清单 + diff 图 + `frontend/pages/profile/profile.*` + mp-05 spec requirement 原文），派 subagent 修复，task reviewer 复查
- [ ] 9.4 复验：重跑 harness，确认 ≤5%（或几何全绿）
- [ ] 9.5 commit，更新 ledger

## 10. 收尾

- [ ] 10.1 全量复跑 `npm run test:visual` + `npm run test:geometry`（无 screen 参数，跑全部 9 屏），确认无回归
- [ ] 10.2 汇总 9 屏最终 diff% / 几何状态表，写入本 change 的完成记录（tasks.md 底部或 design.md 附录）
- [ ] 10.3 检查 `CLAUDE.md`「视觉验证」章节是否需要同步更新（如 9 屏 GREEN 状态、验证方式变化）
- [ ] 10.4 用 `superpowers:requesting-code-review` 走一次全量 diff 的最终 review（既有分散在各屏 commit 的 task review 之外，做一次跨屏一致性检查：token 用法、组件复用、命名风格）
- [ ] 10.5 全部完成后 `/opsx:archive mp-od-prototype-alignment`
