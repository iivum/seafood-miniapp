## 1. 测试先行(TDD)

- [x] 1.1 新建 `GlobalExceptionHandlerContractIT`(走真 filter chain,同 `SecurityFilterChainOrderIT` 的最小 `@SpringBootTest` 组装模式,非 `@WebMvcTest` slice)。用例:`TestApp` 的 `/api/products` 端点抛 `IllegalArgumentException`,断言响应是 `500 + {code:"INTERNAL", message}`,**不是** 403 空 body。RED 阶段实测:异常真的没人接住直接往上抛(`ServletException` 包着 `IllegalArgumentException`)
- [x] 1.2 补用例:`/api/banners` 端点抛既有 `NotFoundException`,断言行为不回归(404 + `code=NOT_FOUND`)——修复前已经是绿的,作为回归基线保留
- [x] 1.3 补用例:直接 `GET /error`,断言状态码不是 403(RED 阶段实测确实是 403,证实 bug)
- [x] 1.3a 追加用例(实现过程中发现需要,补齐 design 决策 2 的验证缺口):`GET /error` 响应 body 断言 `code`/`message` 存在、`timestamp`/`path` 不存在(不是 Spring Boot 默认形状)

## 2. 修复实现

- [x] 2.1 `GlobalExceptionHandler` 新增 `@ExceptionHandler(Exception.class)` 兜底方法,返回 `500 + ErrorResponse("INTERNAL", "服务器内部错误", null)`,置于其余 handler 之后(最低优先级)
- [x] 2.2 `ErrorResponse.code` 是纯 `String` 字段(非枚举类型),无需改类型定义,直接用 `"INTERNAL"` 字面量(与其余 code 值一致的用法模式)
- [x] 2.3 `SecurityConfig` 授权规则:`/error` 加入 `permitAll()`
- [x] 2.4 新增 `ContractErrorAttributes`(`DefaultErrorAttributes` 子类,Boot 4 该类挪到了 `org.springframework.boot.webmvc.error` 包),覆盖 `getErrorAttributes`,把 Spring Boot 默认渲染的 `{timestamp,status,error,path}` 形状替换为 `{code,message}`(404→`NOT_FOUND`,其余→`INTERNAL`)
- [x] 2.5 跑 1.1-1.3a 用例转绿(4/4 pass)
- [x] 2.6 **实现过程中撞见的真实回归**:全量测试暴露 10 个既有测试(`*_asCustomer_returns403` 系列,跨 Banner/AdminBff/AdminOrder/AdminProduct/AdminRefund/AdminFeatureFlag/Order 等 controller)从 403 变成 500——`@PreAuthorize` 拒绝抛出的 `AccessDeniedException` 被 `Exception.class` 兜底吞掉了(design.md 风险清单里明确预判过这个风险,实测真的命中)。修法:`GlobalExceptionHandler` 补一条比 `Exception.class` 更具体的 `@ExceptionHandler(AccessDeniedException.class)`,返回 `403 + {code:"FORBIDDEN"}`,同 bean 内 Spring 按精确匹配优先。10 个既有测试转绿 + 在自己的 IT 里补 `methodLevelPreAuthorizeDenial_returns403_notSwallowedByCatchAll` 用例专门锁死这条,不只依赖"顺带修好"的既有测试

## 3. E2E 验收(零 mock)

- [x] 3.1 复测 2026-07-13 E2E 报告中商品分类查询触发的次生 403——**原始触发点已被 `fix-category-bad-status-500` 修复,无法再自然复现(好事)**,改为直接对真实部署的后端(`seafood-backend:jvm`,含本次两个修复)curl 验证机制本身:`GET /error` 从 403 空 body 变为 `500 + {"code":"INTERNAL","message":"服务器内部错误"}`;顺带验证未鉴权访问 `/api/admin/products` 仍正确 403、商品 404 仍返回 `{code:"NOT_FOUND"}`,确认修复未破坏既有行为
- [x] 3.2 更新 memory `mp-e2e-fullstack-2026-07-13.md` 中本条 bug 状态

## 4. 回归

- [x] 4.1 `./gradlew test` 全量通过——688 例,0 失败(含修复过程中发现并解决的 10 例 AccessDeniedException 回归)
- [x] 4.2 `./gradlew check`(ArchUnit,含 `SecurityHeaderArchitectureTest` 白名单检查)通过——确认本次改动不违反安全头唯一写入点约束
- [x] 4.3 `SecurityFilterChainOrderIT`(覆盖 addresses/favorites/product-views/users-me-phone 四个历史踩过同类漏配坑的端点)5/5 pass,未受影响
