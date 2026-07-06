# 收藏 + 浏览足迹 设计

## Context

`mp-od-prototype-alignment`(已归档,2026-07-04)对齐 mp-05「我的」页时,OD 原型显示一个完整会员仪表盘(VIP 等级/积分/收藏/足迹/优惠券/余额),但当时确认现有 `User` 域对象(`id/openId/nickname/avatarUrl/role/phone/addresses/createdAt`)完全没有这些字段,决定只做前端视觉近似,记入遗留问题清单,建议"新聚合根设计,另开 change"。

这 5 个子能力(积分/VIP 等级/余额/优惠券/收藏/足迹)彼此独立,量级和业务规则复杂度差异很大——积分/VIP 需要跨 Order/User 模块的累积规则,余额/优惠券涉及真实资金/核销业务规则,收藏/足迹是纯 User 模块内的记录型功能、无跨模块耦合、且 `product-detail.js` 已有现成的 UI hook 点(`onToggleFavorite`/`onFavoriteTap`,目前只是装饰 toast)。经用户确认,本次先做收藏 + 足迹这一对最小可行子集,其余留后续 change。

## Goals / Non-Goals

**Goals:**
- 收藏:用户可对商品收藏/取消收藏,mp-05 显示真实收藏数,新增收藏网格列表页可查看/取消收藏
- 足迹:用户浏览商品详情页时静默记录,mp-05 显示真实足迹数,新增足迹列表页可查看(纯浏览记录,不可操作)
- `product-detail.js` 现有两个收藏 UI 入口(底部操作栏 + 悬浮顶栏)统一驱动同一个真实收藏状态

**Non-Goals:**
- 积分、VIP 等级、余额账本 —— 完全不在这次范围,不新增任何相关字段/端点
- 优惠券数字(mp-05 仪表盘第三个数字位)—— 位置保留但这次不接真实数据
- 收藏/足迹列表页的"从列表直接加购物车"等快捷操作 —— OD 原型未画,YAGNI
- 分页 —— 足迹硬上限 100 条、收藏预计量级也不大(单卖家内部运营小商城),一次性返回全部,不做 Pageable

## Decisions

### D1: 存储方式 —— 收藏嵌入 User,足迹独立集合

`User` 聚合根新增 `favoriteProductIds: List<String>`,跟现有 `addresses: List<Address>` 惯例一致(写频率低、数据量小,适合嵌入)。**排序**:新收藏追加到列表头部(`addFavorite` 把新 id 插入 index 0,不是末尾),`GET /favorites` 按列表原始顺序返回即为"最近收藏优先"——不需要额外存时间戳字段或运行时排序。足迹是高频写(每次进商品详情都记一条)+ 需要按用户裁剪到最近 100 条,嵌入 User 文档会导致该文档频繁增长/裁剪,不符合 MongoDB 单文档最佳实践 —— 新建独立集合 `product_views`,`{userId, productId}` 复合唯一索引。

替代方案考虑:两者都嵌入 User(架构最简单,但足迹的高频写+裁剪逻辑会让 User 文档不必要地承担大量无关读写压力,User 文档本身还要支撑登录/鉴权等高频路径,拒绝);两者都独立建集合(架构最"干净"但收藏本身量小、跟地址一样嵌入更符合已有惯例,没必要为了一致性牺牲简单性,拒绝)。

### D2: 足迹去重 + 裁剪策略

同一商品反复查看只保留最新一条(`upsert` 按 `{userId, productId}` 复合键,命中则刷新 `viewedAt`,不存在则插入),不是每次都新增——避免同一商品被反复刷屏挤占"最近 100 条"的名额。写入后惰性裁剪:按 `userId` 查 `viewedAt` 降序,超出前 100 条的记录全部删除。不用 TTL index(TTL 是按绝对时间过期,这里要的是"每人最近 N 条"这种相对裁剪,语义不同)。

### D3: 失效商品降级 —— 复用 CartService 先例

收藏/足迹列表里若商品已下架/被删除,单行降级显示占位文案 + 灰态图标(不返回商品名称/价格/图片字段的真实值),不让一个失效商品导致整个列表请求失败。实现上复用 `CartService#enrich` 已确立的 try/catch-NotFoundException-then-fallback 模式,不重新发明。

### D4: 幂等性

收藏/取消收藏对已收藏/未收藏的商品重复调用均返回 200(不报错)——前端可能因网络重试触发重复请求,不应该让用户看到莫名其妙的错误提示。`User.addFavorite(productId)` 在 `favoriteProductIds` 已包含该 id 时直接返回原对象(no-op);`removeFavorite` 同理。

### D5: `product-detail.js` 双入口统一

现状:底部操作栏 `onToggleFavorite`(纯本地 `setData` toggle + toast)和悬浮顶栏 `onFavoriteTap`(纯装饰 toast,注释明确写着"两个独立入口,互不影响,这个不接后端")是两个刻意解耦的假实现。收藏能力变真实后,两个按钮概念上做的是同一件事("收藏这个商品"),继续保持解耦会产生真实的 UX 矛盾(点一个显示"已收藏",点另一个显示"功能开发中")。改动:两个 handler 共享同一个 `favorited` 状态字段(从后端返回值计算,不是本地纯 toggle),都触发同一个真实的收藏/取消收藏调用。

### D6: 足迹记录是 best-effort,不影响页面渲染

`product-detail.js#onLoad` 静默调 `POST /api/users/me/views/{id}`,失败不 toast、不阻断商品详情本身的渲染——记录浏览足迹不是用户当前任务的关键路径,失败了用户也不该被打扰。

## API 契约

新增(挂 `com.seafood.user.api` 下,门面复用 `/api/users/me` 前缀,风格对齐现有地址端点):

- `POST /api/users/me/favorites/{productId}` → 200,返回 `{ favoriteProductIds: string[] }`(裸 id 列表,不返回富化商品信息——前端只需要确认 toggle 成功 + 更新本地 favorited 布尔态,真正的富化列表走下面的 `GET /favorites`,不重复做同一件事)
- `DELETE /api/users/me/favorites/{productId}` → 200,同上
- `GET /api/users/me/favorites` → 富化后的商品列表(`FavoriteItemResponse[]`:productId/name/price/imageUrl/available)
- `POST /api/users/me/views/{productId}` → 204(记录 best-effort,前端不关心返回体)
- `GET /api/users/me/views` → 富化后的列表(`ProductViewResponse[]`:productId/name/price/imageUrl/available/viewedAt),按 viewedAt 降序

`GET /api/users/me`(既有端点)响应体新增两个字段:`favoriteCount: int`、`viewCount: int`(供 mp-05 仪表盘直接读,不用为了显示数字额外拉两个列表接口)。

## 前端页面

- `pages-sub/product/product-detail/product-detail.js`:`onToggleFavorite`/`onFavoriteTap` 改真实调用 + 状态统一(D5);`onLoad` 静默记足迹(D6)
- `pages/profile/profile.js`/`.wxml`:用户卡下方收藏/足迹数字接 `UserAPI.me()` 返回的 `favoriteCount`/`viewCount`,可点击跳转
- 新增 `pages-sub/user/favorites/favorites-list`(网格布局,对齐 OD"收藏"展示惯例,含取消收藏按钮 + 失效商品占位)
- 新增 `pages-sub/user/footprints/footprints-list`(列表布局,时间倒序,失效商品占位,无操作按钮——纯浏览记录)
- `app.json` 新增两个分包页面路径(挂已有 `pages-sub/user` 分包下,不新开分包)

## Risks / Trade-offs

- [Risk] `favoriteProductIds` 无上限,理论上可以无限增长,拖慢 User 文档读写 → Mitigation:单卖家内部运营场景,商品总数本身有限(不会有用户收藏出几千条),先不加裁剪逻辑(YAGNI),若未来真的成为问题再补
- [Risk] 足迹裁剪是"写入后立即查询 + 删除多余记录"的惰性策略,高并发下同一用户短时间内连续查看多个商品可能有轻微的裁剪竞态(裁剪判断基于查询时的快照)→ Mitigation:后果最多是短暂多保留 1-2 条超出 100 条上限的记录,不影响正确性,不值得引入分布式锁

## Migration Plan

无数据迁移(纯新增字段/新增集合,MongoDB schemaless,现有 `User` 文档缺 `favoriteProductIds` 时按 Java record 默认值 `List.of()` 处理,不需要迁移脚本)。
