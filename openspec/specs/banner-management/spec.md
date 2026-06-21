# Spec: banner-management

## Purpose

海鲜商城首页营销 banner 的后端驱动领域 + 运营自助维护。补齐单仓 DDD 里缺失的营销位限界上下文:`banner` 模块(api/application/domain/infra 四层,镜像 product)提供公共读端点供 mp 首页 hero 轮播拉取,ADMIN CRUD 端点供 admin-ui 自助增删改;banner 点击可跳对应商品详情(targetProductId,经 ProductService 跨模块校验)。取代被删的前端硬编码静态 banner,数据后端驱动、改一次内容无需发版。源自 change `backend-driven-home-banner`(Sprint 5,C5 逐屏修复衍生)。

## Requirements

### Requirement: Banner 领域模型
系统 SHALL 以 `Banner` 聚合表达营销 banner,字段含 id、tone(ACCENT|SOFT)、emoji、title、subtitle、targetProductId(可空)、sortOrder(≥0)、status(ACTIVE|INACTIVE)、createdAt、updatedAt。聚合 SHALL 在构造期校验不变量,并以行为方法(而非 setter)改变状态。

#### Scenario: 构造合法 banner
- **WHEN** 以非空 title/subtitle、合法 tone、sortOrder≥0 构造 Banner
- **THEN** 聚合创建成功,status 默认 ACTIVE

#### Scenario: 非法字段拒绝
- **WHEN** 以空 title 或负 sortOrder 构造 Banner
- **THEN** 抛领域校验异常(对应 API 层 VALIDATION/DOMAIN 错)

#### Scenario: 行为方法切换状态
- **WHEN** 对 ACTIVE banner 调 `deactivate()`
- **THEN** status 变 INACTIVE,且无公开 setter 直改 status

### Requirement: 公共 banner 读取
系统 SHALL 提供匿名可访问的 `GET /api/banners`,只返回 status=ACTIVE 的 banner,按 sortOrder 升序;并提供 `GET /api/banners/{id}`。

#### Scenario: 列出启用 banner
- **WHEN** 匿名请求 `GET /api/banners`
- **THEN** 返回所有 ACTIVE banner,按 sortOrder 升序;INACTIVE 不出现

#### Scenario: 空列表
- **WHEN** 无任何 ACTIVE banner
- **THEN** 返回空数组(HTTP 200),mp swiper 不渲染任何 item

#### Scenario: 取单个不存在 banner
- **WHEN** `GET /api/banners/{id}` 的 id 不存在
- **THEN** 返回 NOT_FOUND(HTTP 404)

### Requirement: ADMIN banner 管理
系统 SHALL 提供 ADMIN 限定的 banner 增删改:`POST /api/banners`、`PUT /api/banners/{id}`、`DELETE /api/banners/{id}`,均 `@PreAuthorize hasRole('ADMIN')`;并提供 `GET /api/banners/all`(ADMIN)返回全部 banner 含 INACTIVE。

#### Scenario: 非 ADMIN 写被拒
- **WHEN** 匿名或非 ADMIN 调 `POST/PUT/DELETE /api/banners`
- **THEN** 返回 401/403,banner 不被修改

#### Scenario: ADMIN 创建 banner
- **WHEN** ADMIN 以合法 BannerRequest 调 `POST /api/banners`
- **THEN** banner 持久化,返回 BannerResponse;随后出现在 `GET /api/banners/all`

#### Scenario: ADMIN 查看全部含停用
- **WHEN** ADMIN 调 `GET /api/banners/all`
- **THEN** 返回 ACTIVE + INACTIVE 全部 banner

### Requirement: targetProductId 存在性校验
当 banner 设置了 targetProductId,系统 SHALL 经 ProductService(跨模块只走 ApplicationService)校验该商品存在;不存在则拒绝写入,避免悬空跳转。

#### Scenario: 指向有效商品
- **WHEN** 创建/更新 banner 的 targetProductId 指向存在的商品
- **THEN** 写入成功

#### Scenario: 指向不存在商品
- **WHEN** 创建/更新 banner 的 targetProductId 指向不存在的商品
- **THEN** 返回 DOMAIN/VALIDATION 错(HTTP 409/400),不写入

#### Scenario: 不设跳转
- **WHEN** banner 的 targetProductId 为空
- **THEN** 跳过校验,写入成功(banner 纯展示)

### Requirement: mp 首页 banner 渲染与跳转
mp 首页 SHALL 从 `GET /api/banners` 拉取 banner 渲染到 swiper;点击 banner SHALL 跳转到对应商品详情(若有 targetProductId)。

#### Scenario: 首页渲染 banner
- **WHEN** 首页加载且后端有 ACTIVE banner
- **THEN** swiper 渲染对应数量的 banner item,C5 几何层 `banner [present]` = GREEN

#### Scenario: 点击带跳转的 banner
- **WHEN** 用户点击带 targetProductId 的 banner
- **THEN** 跳转 `product-detail?id=targetProductId`

#### Scenario: 点击纯展示 banner
- **WHEN** 用户点击无 targetProductId 的 banner
- **THEN** 不跳转(或轻提示),不报错

### Requirement: admin-ui banner 管理页
admin-ui SHALL 提供 banner 管理 feature:列出全部 banner、创建/编辑(tone/emoji/title/subtitle/targetProductId/sortOrder/active)、删除,经 `/api/banners` 系列端点(携 ADMIN JWT)。

#### Scenario: 运营增删改 banner
- **WHEN** 运营在 banner 管理页创建/编辑/删除 banner
- **THEN** 变更经 ADMIN 端点持久化,mp 下次拉取即生效(无缓存)

#### Scenario: 列表展示全部
- **WHEN** 打开 banner 管理页
- **THEN** 经 `GET /api/banners/all` 展示全部 banner 含 INACTIVE,可切换启停
