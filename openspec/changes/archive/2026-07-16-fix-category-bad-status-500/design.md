## Context

`ProductDocument.status` 是直类型化的 `ProductStatus` 枚举字段(`ACTIVE/OUT_OF_STOCK/DISCONTINUED`)。Spring Data MongoDB 默认按 `enum.name()` 做字符串↔枚举转换,是**严格转换**——集合中出现任何不在枚举里的字符串值,转换在 document→entity 阶段直接抛 `IllegalArgumentException`,发生在应用代码(Service/Mapper)接触到数据之前,无法在业务层兜底捕获。

当前 `ProductService.listPublic`:
```java
Page<ProductDocument> page = (category == null || category.isBlank())
    ? repo.findByStatus(ProductStatus.ACTIVE, pageable)
    : repo.findByCategory(category, pageable)
            .map(d -> { d.setStatus(ProductStatus.ACTIVE); return d; });
```
无分类过滤走 `findByStatus`(查询级过滤,正确);带分类过滤走 `findByCategory`(**无状态过滤**,取该分类下所有文档)后用 `.map()` **强制覆写**每条返回文档的内存态 status 为 ACTIVE。这是双重问题:①即使某商品实际是 `DISCONTINUED`/`OUT_OF_STOCK`,分类浏览也会把它包装成 ACTIVE 展示给顾客(数据说谎);②Mongo→entity 转换发生在 `.map()` 之前,只要该分类里有一条脏数据,整页查询直接抛异常,`.map()` 完全救不了。

`listAdmin` 同样用 `findByCategory`(无状态过滤),但这是**有意为之**——管理后台需要看到所有状态(含下架/停售)的商品,不应加 ACTIVE 过滤。

## Goals / Non-Goals

**Goals:**
- 公共分类浏览(`listPublic`)不再因单条脏数据整体 500,且不再对状态"说谎"
- 管理侧分类浏览(`listAdmin`)保留"看到所有状态"的语义,但同样不因脏数据整体崩溃(admin 恰恰需要看到并修复这条脏数据,而不是被挡在外面)

**Non-Goals:**
- 不做"防止脏数据写入"的入口校验加固(枚举字段本身已是强类型,写路径通过应用代码不会产生非法值;本次脏数据来自手工 seed 误写,不代表写路径有漏洞)
- 不改变 `ProductStatus` 枚举的合法取值集合

## Decisions

**1. `listPublic` 带分类:改用查询级状态过滤,删除 `.map()` 覆写 hack**

新增 `ProductRepository.findByCategoryAndStatus(String category, ProductStatus status, Pageable pageable)`,`listPublic` 分类分支改为 `repo.findByCategoryAndStatus(category, ProductStatus.ACTIVE, pageable)`,与无分类分支的 `findByStatus` 语义对齐。

- **为什么不是应用层过滤(查询全部再 filter)**:query-level 过滤在 Mongo 侧就排除了非 ACTIVE(含脏数据)文档,脏数据文档根本不会进入 Spring Data 的实体转换环节,天然免疫崩溃,且更省心(不用多拉数据再丢弃,分页 total 也准确)。
- **为什么删 `.map()` 覆写**:它是数据说谎,不是过滤;修复崩溃的同时必须一并去掉,否则修完崩溃问题还留着"下架商品显示为在售"的正确性问题。

**2. `listAdmin`(及其他无状态过滤的分类查询):注册宽松的自定义 Mongo Converter,兜底非法值**

新增 `Converter<String, ProductStatus>`(读方向),注册进 `MongoCustomConversions` bean:未识别的字符串值映射为 `DISCONTINUED`(语义上"不在公开可见范围",与该字段本身"不出现在公共列表"的既有注释一致)并 `WARN` 日志记录原始值 + 文档 id,而不是抛异常。

- **备选方案 A(否决)**:给 admin 分类查询也加状态过滤——违背"admin 需要看到所有状态"的产品要求,而且看不到脏数据反而没法定位修复。
- **备选方案 B(否决)**:`status` 字段改存 `String`,应用层手动转换——改动面更大(涉及 Document/Mapper/所有读写点),且失去 Spring Data 对枚举字段的类型安全;宽松 Converter 只在读方向兜底,侵入面最小。
- **为什么选自定义 Converter 而非仅两处 try-catch**:Converter 注册一次即对所有读路径(现在的 `findAll`/`findByCategory`/未来任何新查询)生效,不用每新增一个查询方法就重新处理脏数据风险;这是防御纵深,不是单点补丁。

**3. 数据治理:同步修 seed 脚本**

本次崩溃由 `backend/seed/seed.sh` 误写 `status:'INACTIVE'`(非法枚举)触发。`fix-mp-address-form-validation` 不涉及 seed,但本 change 顺带把 seed fixtures 的 status 值改为合法枚举(`DISCONTINUED`/`OUT_OF_STOCK`),避免同类问题在下次本地/CI seed 时复现。已同步更新 memory `c5-visual-test-runbook.md` 记录合法枚举值。

## Risks / Trade-offs

- [risk] `DISCONTINUED` 兜底值可能掩盖数据质量问题,长期不易发现 → [mitigation] WARN 日志包含文档 id,后续可加 `MetricsCardinalityTest` 同类的告警/巡检(不在本 change 范围,记录为后续待办)
- [risk] 新增 `findByCategoryAndStatus` 是 Spring Data 派生查询方法,签名细节需与现有 `findByStatus`/`findByCategory` 保持一致的命名约定 → [mitigation] 命名遵循 Spring Data 官方派生查询语法(AndStatus 后缀),IT 测试覆盖验证生成的查询正确

## Migration Plan

- 纯代码修改,无数据迁移;自定义 Converter 只影响读路径解释方式,不改写已存库数据
- 部署顺序无特殊要求,新代码上线后现有脏数据(若还有)自动按新语义处理,无需停机或回填
- 回滚:恢复原 `findByCategory` + `.map()` 写法(不推荐,回滚等于重新引入两个 bug)
