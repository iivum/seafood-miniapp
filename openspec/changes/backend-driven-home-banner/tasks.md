# Tasks — 后端驱动 home banner(banner-management)

> TDD:每个实现任务先写失败测试再实现。后端 Gradle:
> `JAVA_HOME=/opt/homebrew/opt/graalvm/libexec/graalvm.jdk/Contents/Home ./gradlew test -x processTestAot -PexcludeTags=docker`

## 1. 后端 domain(banner 聚合)

- [x] 1.1 `BannerTone`(ACCENT|SOFT)、`BannerStatus`(ACTIVE|INACTIVE)枚举
- [x] 1.2 `BannerTest`:构造校验(空 title/subtitle、负 sortOrder 拒;合法构造 status 默认 ACTIVE)+ 行为方法 `deactivate()/activate()` → 失败
- [x] 1.3 `Banner` 聚合(record + 紧凑构造器校验 + 行为方法,无 status setter)→ 1.2 绿
- [x] 1.4 `BannerConstructionProperties`(jqwik):合法字段域内永远构造成功 / 非法边界永远拒

## 2. 后端 infra

- [x] 2.1 `BannerDocument`(@Document "banners",sortOrder @Indexed 升序)+ `BannerMapper`(Document↔domain)
- [x] 2.2 `BannerRepository`(Mongo;`findByStatusOrderBySortOrderAsc`)
- [x] 2.3 `MongoIndexInitializer` 加 banners sortOrder 显式索引(沿用 auto-index-creation:false)
- [x] 2.4 `BannerDocumentRepositoryIT`(@Tag("native");save/findByStatus 排序)→ 跑通

## 3. 后端 application

- [x] 3.1 `BannerServiceTest`:`listActive()` 只返 ACTIVE 按 sortOrder;`create/update` 在 targetProductId 非空时经 mock `ProductService.get` 校验,不存在抛 DOMAIN;`listAll()` 返全部 → 失败
- [x] 3.2 `BannerService` 实现(注入 `ProductService` 跨模块校验,不碰 Repository 之外的 product 类)→ 3.1 绿
- [ ] 3.3(可选)`banners.queried` counter 埋点在 listActive 边界(对齐 design §D2 可观测;非高基数 tag)

## 4. 后端 api

- [x] 4.1 DTO:`BannerResponse`(record `from(Banner)`)、`BannerRequest`(record + @Valid 约束)
- [x] 4.2 `BannerControllerTest`(MockMvc slice):公共 `GET /api/banners` 匿名只返 ACTIVE;`GET /{id}` 404;`POST/PUT/DELETE` 无 ADMIN→401/403、有 ADMIN→成功;`GET /all` ADMIN 返全部 → 失败
- [x] 4.3 `BannerController` `@RequestMapping("/api/banners")`(公共读 + `@PreAuthorize ADMIN` 写 + `/all`)→ 4.2 绿
- [x] 4.4 SecurityConfig URL 规则核对:`/api/banners` GET 放行匿名,写/all 需鉴权(与 products 同档)
- [x] 4.5 `./gradlew check`:ArchUnit 4 条 + MetricsCardinality + checkNoRefreshScope 全绿

## 5. seed

- [x] 5.1 `backend/seed/fixtures/banners.json`(3 条:旧静态 tone/emoji/title/subtitle + 合理 targetProductId + status=ACTIVE + sortOrder)
- [x] 5.2 `seed.sh` 导入 banners 集合
- [x] 5.3 `frontend/e2e/tools/run-visual.sh` seed 步骤纳入 banners(与 products 同批)

## 6. mp 接线

- [x] 6.1 `src/api/banner.js` `loadBanners()` → `GET /api/banners`(复用 request 封装)
- [x] 6.2 `pages/index/index.js`:onShow 拉 banners → setData;新增 `onBannerTap(e)` 读 data-banner-id → 查 targetProductId → navigateTo product-detail(无则 noop)
- [x] 6.3 几何验证:起后端+seed,`npm run test:geometry mp-01-home` → `banner [present]` GREEN
- [x] 6.4 感知复跑 `npm run test:visual mp-01-home`:记录 diff% 下降(banner 出现)

## 7. admin-ui

- [x] 7.1 先读 `admin-ui/src/features/refunds/api.ts` 复用 admin 请求封装/JWT 接法
- [x] 7.2 `features/banners/api.ts`:listAll/create/update/delete via `/api/banners`
- [x] 7.3 banner 管理页(shadcn Table 列全部 + Dialog 表单:tone/emoji/title/subtitle/targetProductId/sortOrder/active + 删除确认)
- [x] 7.4 路由 + 侧栏导航项
- [x] 7.5 admin-ui 测试(api + 页面渲染/交互)+ feature ≥80% 覆盖

## 8. native + 收尾

- [~] 8.1 **CI 责任**:`BannerDocumentRepositoryIT` 已挂 `@Tag("native")`,nativeTest agent 在 native.yml CI 收 banners 集合 codec/枚举反射 metadata(CLAUDE.md「别手编」—— 不本地手写 `META-INF/native-image/`)
- [x] 8.2 全量绿:`./gradlew check`(ArchUnit + checkNoRefreshScope + jacoco ≥80% 门)BUILD SUCCESSFUL;admin-ui vitest 23/24 文件过(剩 1 是 pre-existing ad-od-design RED,与 banner 无关)
- [x] 8.3 回填:CLAUDE.md 架构图加 banner 模块;home banner 几何 RED→GREEN(见 C5 change `sprint-5-c5-visual-verification` tasks 下游注记)
- [ ] 8.4 归档本 change(specs sync 到 openspec/specs/)— 走 `/opsx:archive`
