## 1. 测试先行(TDD)——仓库层

- [x] 1.1 **实现调整**:`ProductDocumentRepositoryIT`/`ProductRepositorySliceTest` 都只走 raw MongoClient(Boot 4.0.6 test starter 不带 `@DataMongoTest`),无法复现本 bug(崩溃点正是 Spring Data document→entity 转换层,raw driver 绕不过去)。改为新建 `ProductCategoryStatusQueryIT`(同目录,`@Tag("native")`,起最小 `@SpringBootTest` + `@EnableMongoRepositories` + 真实生产 `MongoCustomConversions`,指向 Testcontainers Mongo)。用例:集合中混入一条非法 `status` 字符串文档,`findByCategoryAndStatus(category, ACTIVE, pageable)` 仍返回其余合法文档,不抛异常
- [x] 1.2 补用例:`findByCategoryAndStatus` 只返回请求分类 + 请求状态的交集,不多不少
- [x] 1.3 补自定义 Converter 单测(`ProductStatusReadConverterTest`,纯单元测试无需 Spring context):非法字符串 → 转换为 `DISCONTINUED`,合法字符串正常转换;另在 IT 补 `findByCategory_withoutStatusFilter_toleratesBadStatusViaConverter_doesNotThrow` 验证 converter 在真实 Spring Data 转换链路里生效(listAdmin 路径无状态过滤,坏数据文档真的会被拉取转换,是唯一能验证 converter 生效的路径)

## 2. 修复实现

- [x] 2.1 `ProductRepository` 新增 `Page<ProductDocument> findByCategoryAndStatus(String category, ProductStatus status, Pageable pageable)`
- [x] 2.2 `ProductService.listPublic`:分类分支改用 `findByCategoryAndStatus(category, ProductStatus.ACTIVE, pageable)`,删除 `.map(d -> {d.setStatus(ACTIVE); ...})` 覆写逻辑。顺带更新了 2 个existing 测试(`ProductServiceSliceTest.listPublic_nonNullCategory_queriesByCategory` 改名+改 mock 期望;`ProductServiceMutationGapTest.listPublic_withCategory_forcesActiveStatusOnEachDoc` 整条重写——旧测试名字面上就是在锁死"强制覆写成 ACTIVE"这个 bug 行为,必须连测试意图一起改)
- [x] 2.3 新增 `ProductStatusReadConverter`(读方向,package-private,`com.seafood.product.infra`),未识别值映射 `DISCONTINUED` + WARN 日志(含原始值)
- [x] 2.4 新增 `ProductMongoConversionConfig`(`com.seafood.product.infra`,仓库里目前唯一的 `MongoCustomConversions` bean)
- [x] 2.5 跑 1.1-1.3 用例转绿(`ProductStatusReadConverterTest` 3/3、`ProductCategoryStatusQueryIT` 3/3、`ProductServiceSliceTest` 6/6、`ProductServiceMutationGapTest` 24/24)

## 3. 数据治理

- [x] 3.1 `backend/seed/fixtures/products.json` 补 `status` 字段(50 条,按 `onSale` 映射:true→ACTIVE 43 条,false→DISCONTINUED 7 条,均为合法枚举)
- [x] 3.2 `backend/seed/seed.sh` 导入商品前新增 fail-fast 校验:`status` 不在 `{ACTIVE,OUT_OF_STOCK,DISCONTINUED}` 集合内则报错退出,不再让坏数据潜入(直接对应本次事故成因)
- [x] 3.3 更新 memory `c5-visual-test-runbook.md` 中 seed 坑记录——已提前记录且核对一致,无需改动

## 4. E2E 验收(零 mock)

- [x] 4.1 mp 运行时内真实浏览 2026-07-13 E2E 报告中受影响的 3 个分类(鱼类/虾蟹/海藻),确认列表正常渲染——mp-e2e-expert PASS,5/5 分类(含未受影响的贝类/软体做回归确认)均无 errorMessage/空态,双路监控 0 新异常
- [x] 4.2 后端手工注入一条非法 status 文档,确认对应分类查询仍 200(而非 500/403)——curl 直接验证:注入 `status:"INACTIVE"` 商品到鱼类分类,`GET /api/products?category=鱼类` 仍 200 且坏数据文档被正确排除在结果外(已清理测试数据)
- [x] 4.3 更新 memory `mp-e2e-fullstack-2026-07-13.md` 中本条 bug 状态

## 5. 回归

- [x] 5.1 `./gradlew test` 全量通过——683 例,0 失败
- [x] 5.2 `./gradlew check`(ArchUnit + checkNoRefreshScope)通过
- [x] 5.3 `./gradlew nativeTest`——**BUILD FAILED,但与本次改动无关**:native-image 测试二进制里 3 个 test container(jqwik/JUnit Jupiter/ArchUnit JUnit 5)全部因 `junit-platform-launcher` 版本(1.14.4)与 `junit-platform-engine`(6.0.3)错位崩溃,"0 tests failed / 0 tests successful"(压根没跑起来,不是我的新 IT 挂了)。`build.gradle:129-150` 已有详细既存注释记录这是 Spring Boot 4.0.6 依赖管理把 launcher 钉旧版本的已知问题,现有 `dependencySubstitution` 补丁只覆盖 `testRuntimeClasspath`/`pitest` 两个 configuration,未覆盖 nativeTest 用的 native-image 测试 classpath——是既有未修补缺口,不是本次改动引入的回归。已记入 memory,不阻塞本 change。
