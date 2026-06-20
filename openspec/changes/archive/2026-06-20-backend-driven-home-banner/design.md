## Context

mp 首页 hero banner 因一处未提交本地编辑删除了静态 `BANNERS` 而不渲染(C5 几何层抓到)。决定不恢复前端硬编码,而落地后端驱动 + admin 可维护的 banner。后端单仓 DDD 现有 `product/order/user` 三领域 + `bff/admin` 第 5 层。`ProductController` 是"实体 CRUD 放各自模块 controller"的范本(`/api/products` 公共读匿名 + 写操作 `@PreAuthorize ADMIN`);`bff/admin` 只放跨模块/聚合的 admin 操作(duplicate/export/batch/dashboard)。mp 经 `src/api/*.js` 拉后端;admin-ui 是 React18+shadcn+Vite,`features/<name>/{api.ts,...}` 结构。

mp wxml swiper 期望 banner 字段:`id / tone / emoji / title / subtitle`,点击 `onBannerTap(data-banner-id)`。

## Goals / Non-Goals

**Goals:**
- banner 数据后端驱动,运营经 admin-ui 自助增删改,mp 实时拉取渲染
- banner 点击跳对应商品详情(targetProductId)
- 补齐 DDD `banner` 领域,严格遵 4 层 + 跨模块只走 ApplicationService
- C5 几何层 `banner [present]` RED→GREEN 作为验收门

**Non-Goals:**
- banner 图片上传(OD 设计是 emoji+gradient tone,无图;复用 product 上传目录是 YAGNI)
- banner 排期/定时上下线(date range)—— 仅 active/inactive 即时切换
- banner 点击跳分类/自由 URL(本次只支持跳商品详情)
- banner 缓存(遵 design §5.2,P99>500ms 再加)

## Decisions

### D1:banner 自成 DDD 模块(镜像 product),非塞进 product
banner 是独立营销概念,自成 `banner/{api,application,domain,infra}` 边界清晰、便于将来回拆。ArchUnit `ArchitectureTest` 自动守 4 条(api↛infra / domain↛spring / controller 不持 Repository 等)。

### D2:实体 CRUD 放 `BannerController`(模块内),非 bff/admin
遵 `ProductController` 范本:`@RequestMapping("/api/banners")` 公共 `GET`(匿名,active 按 sortOrder)+ `GET /{id}`,写操作 `POST`/`PUT /{id}`/`DELETE /{id}` 加 `@PreAuthorize hasRole('ADMIN')`,admin 看全部 `GET /all`(ADMIN)。bff/admin 只在需要跨模块聚合时才用 —— banner CRUD 单模块,不进 bff。

### D3:targetProductId 经 ProductService 跨模块校验
`BannerService` 注入 `product` 的 `ProductService`,写入时若 targetProductId 非空则 `productService.get(id)` 校验存在,不存在抛 DOMAIN 错。跨模块只走 ApplicationService→ApplicationService(design §1.3),不碰 ProductRepository。

### D4:数据形态 + 索引
`BannerDocument`(@Document "banners"):id(String @Id)、tone(枚举存字符串)、emoji、title、subtitle、targetProductId(可空)、sortOrder(int @Indexed 升序)、status(枚举)、createdAt/updatedAt(Instant)。`MongoIndexInitializer` 显式建 sortOrder 索引(沿用 auto-index-creation:false 约定)。seed `banners.json` 3 条(旧静态 + tone/targetProductId)。

### D5:mp 接线最小化
`src/api/banner.js` `loadBanners()` → `GET /api/banners`(复用 `utils/request` + Page 转换器若有)。`pages/index/index.js` onShow 拉取 setData(banners);新增 `onBannerTap(e)` 读 `data-banner-id` → 查对应 banner 的 targetProductId → `wx.navigateTo(product-detail?id=...)`,无则 noop/轻提示。字段与 wxml 已对齐。

### D6:admin-ui banner feature
`features/banners/`:`api.ts`(list-all/create/update/delete via `/api/banners`,携 admin JWT)+ 管理页(shadcn Table 列全部 + Dialog 表单:tone select / emoji / title / subtitle / targetProductId / sortOrder / active toggle + 删除确认)+ 路由 + 侧栏导航项。与既有 `features/orders`、`features/refunds` 同结构。

### D7:测试策略(TDD,覆盖 ≥80% / domain&app ≥90%)
- 后端:`BannerTest`(domain 校验 + 行为)、`BannerConstructionProperties`(jqwik)、`BannerServiceTest`(含 targetProductId 校验路径,mock ProductService)、`BannerControllerTest`(MockMvc slice:公共读/admin 写鉴权/404)、`BannerDocumentRepositoryIT`(@Tag("native") 收 native metadata)。
- mp:home banner 几何 `npm run test:geometry mp-01-home` → GREEN;感知 home 复跑(banner 出现 diff% 应下降);onBannerTap 行为(console 无异常)。
- admin-ui:`features/banners` 的 api + 页面渲染/交互测试。

## Risks / Trade-offs

- **[跨模块校验加耦合]** banner→product 依赖。Mitigation:只走 ProductService(可回拆),不碰 Repository;校验失败返明确 DOMAIN 错。
- **[admin-ui 鉴权/JWT 接法未细看]** 需对齐既有 feature(如 refunds)的 admin 请求封装。Mitigation:实现时先读 `features/refunds/api.ts` 复用其 client/拦截器。
- **[native 反射面]** 新 Document + 枚举进 GraalVM。Mitigation:`BannerDocumentRepositoryIT` 挂 `@Tag("native")`,nativeTest agent 收 metadata 后 commit `META-INF/native-image/`。
- **[seed fixtures 同 product 的 stale 陷阱]** banners.json 字段须含 status=ACTIVE(否则 listActive 返空)。Mitigation:fixtures 直接写 status + Instant 友好格式,run-visual.sh seed step 纳入。
- **[targetProductId 引用的商品后被删]** 悬空链接。Mitigation:本次只在写入期校验;运行期 mp 跳到不存在商品由 product-detail 自身的 NOT_FOUND 处理(可接受)。
