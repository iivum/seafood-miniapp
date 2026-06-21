## Why

mp 首页 hero banner 当前**不渲染**:`pages/index/index.wxml` 有 `swiper wx:for="{{banners}}"`,但一处未提交的本地编辑删掉了 `BANNERS` 静态常量 + `banners` 数据绑定(原 Sprint 2 修复),`onBannerTap` handler 也随之缺失。C5 几何层抓到该回归(`banner [present] 期望=true 实际=false n=0`)。OD 设计稿首页含醒目的 hero banner(营销引流位),是首页可用性的关键一环。

与其恢复"前端硬编码静态 banner"(改一次内容要发版),本次落地**后端驱动 + admin 可维护**的 banner:单卖家内部运营可在管理后台自助增删改 banner,mp 实时拉取。补齐 DDD 单仓里缺失的 banner 领域,且把"营销位"这一长期会增长的概念放到正确的位置。

## What Changes

- **新增后端 `banner` DDD 模块**(api/application/domain/infra 四层,镜像 `product`):banner 聚合 + 公共读端点 + ADMIN CRUD 端点。
- **公共端点** `GET /api/banners`(匿名,只返 ACTIVE 按 sortOrder)供 mp 拉取;`GET /api/banners/{id}`。
- **ADMIN 端点** `POST/PUT/DELETE /api/banners`(`@PreAuthorize hasRole('ADMIN')`,与 `ProductController` 同款模块内模式)+ `GET /api/banners/all`(admin 看全部含 INACTIVE)。
- **seed**:`banners.json` 夹具(旧 3 张静态 banner + tone/targetProductId)导入 `banners` 集合;`run-visual.sh` seed 步骤同步。
- **mp 接线**:`src/api/banner.js` 拉取 + `pages/index/index.js` onShow setData(banners) + 补 `onBannerTap` → 跳 `product-detail?id=targetProductId`。
- **admin-ui**:新增 `features/banners/` —— banner 管理页(表格 + 增删改表单)+ 路由/导航入口。
- **targetProductId 跨模块校验**:`BannerService` 经 `ProductService` 校验目标商品存在(ApplicationService→ApplicationService),避免悬空链接。

## Capabilities

### New Capabilities
- `banner-management`: 海鲜商城营销 banner 的后端领域 + 公共读 + ADMIN CRUD + admin-ui 管理 + mp 渲染/跳转,数据后端驱动、运营自助可维护。

### Modified Capabilities
<!-- 无既有 spec 的需求级变更:home 页 banner 由"前端静态"变"后端拉取"是实现接线,不改既有 capability 的 spec 级行为。 -->

## Impact

- **后端(新增)**:`backend/src/main/java/com/seafood/banner/{api,application,domain,infra}/` 新模块;`SeafoodApplication` 组件扫描自动纳入;`banners` 新 Mongo 集合 + `MongoIndexInitializer` 显式索引(sortOrder)。
- **后端(触及)**:`BannerService` 依赖 `product` 的 `ProductService`(跨模块只走 ApplicationService);ArchUnit `ArchitectureTest` 自动守 banner 四层;native `BannerDocument` 反射 metadata(挂 `@Tag("native")` IT 收集)。
- **seed**:`backend/seed/fixtures/banners.json` + `seed.sh` + `frontend/e2e/tools/run-visual.sh`。
- **mp**:`frontend/src/api/banner.js`(新)+ `pages/index/index.js`(接线 + onBannerTap)。
- **admin-ui**:`admin-ui/src/features/banners/`(新 feature)+ 路由/导航。
- **测试**:后端 service/controller-slice/repo-IT/domain-property;mp home banner 几何 RED→GREEN + onBannerTap 行为;admin-ui feature 测试。
- **无破坏性变更**:banner 列表为空时 mp swiper 不渲染(wx:for 兜底),向后兼容。
