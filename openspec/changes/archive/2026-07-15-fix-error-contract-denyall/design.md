## Context

`GlobalExceptionHandler`(`@RestControllerAdvice`)只显式处理 6 类已知异常(`NotFoundException/ValidationException/DomainException/RateLimitedException/AccountLockedException/MethodArgumentNotValidException`)。任何其它未预期异常(如本次 E2E 撞到的商品状态枚举反序列化 `IllegalArgumentException`)不被捕获,沿调用栈向上抛出后由 Spring Boot 默认机制重定向到内部 `/error` 路径,交给 `BasicErrorController` 渲染。

问题:`/error` 未出现在 `SecurityConfig` 授权白名单里,而 Spring Security 的 filter chain 在 DispatcherServlet 转发到 `/error` **之前**先拦截该请求路径,命中末尾的 `anyRequest().denyAll()`,直接返 **403 + 空 body**——真实的 500 类错误被伪装成了一个语义完全不相关的授权拒绝,且丢失了 `{code,message}` 错误契约。

## Goals / Non-Goals

**Goals:**
- 控制器/服务/仓库调用栈内抛出的任意未预期异常,最终都以 `500 + {code:"INTERNAL", message}` 契约返回,不经过被 `denyAll()` 拦截的路径
- `/error` 路径本身即便被触达(非 ControllerAdvice 可覆盖的异常来源,如 filter 层异常、404 无匹配 handler),也不应裸露 403 空 body

**Non-Goals:**
- 不改变已有 6 类已知异常的处理方式与状态码
- 不对外暴露堆栈跟踪或内部实现细节(500 body 只含通用 message)

## Decisions

**1. 主修复:`GlobalExceptionHandler` 新增兜底 `@ExceptionHandler(Exception.class)`**

在 6 个具体 handler 之外新增最低优先级的 `Exception.class` 兜底方法,返回 `500 + ErrorResponse("INTERNAL", "服务器内部错误", null)`。

- **为什么这是主修复**:`@RestControllerAdvice` 拦截发生在 DispatcherServlet 分发 controller 方法期间,这正是本次 E2E 撞到的异常来源(service 层反序列化异常,同步抛出在请求线程内)。加了这个兜底后,这类异常**根本不会走到 `/error` 重定向**,天然绕开 `denyAll()` 陷阱,是最直接、影响面最小的修复。
- **不記录堆栈到 body**:避免信息泄漏;完整异常仍走 SLF4J 正常记录(既有全局日志基础设施),运维可查。

**2. 防御纵深:`/error` 授权放行 + 最小化契约渲染**

`/error` 加入 `permitAll()`(不影响其安全性——该路径本身不含业务数据,只做错误渲染)。同时确认/补一个轻量 `@Controller("/error")` 或自定义 `ErrorAttributes`,使得**万一**异常来源不经过 ControllerAdvice(如 Spring Security filter 内部异常、404 无 handler 匹配的内部转发),也能渲染出 `{code,message}` 形状而非 Spring Boot 默认的 `{timestamp,status,error,path}` 形状。

- **为什么不是只做这一层**:如果只放行 `/error` 而不加兜底 `@ExceptionHandler`,body 形状仍是 Spring Boot 默认结构,不符合 `{code,message}` 契约——放行本身不解决契约问题,只解决"404 权限乱码" 类次生问题。两层缺一都不完整。
- **为什么不做更重的方案(如自定义 `ErrorController` 完整实现)**:主修复(方案 1)已覆盖绝大多数真实场景(同步业务异常);`/error` 真正被触达的场景很窄(filter 异常、无 handler 404),用最小化的 `ErrorAttributes` 覆盖足够,不需要重新实现完整错误渲染管线。

**3. `ErrorResponse.code` 新增 `INTERNAL` 枚举值**

现有 `code ∈ {NOT_FOUND, VALIDATION, DOMAIN, TOKEN_*}` 均对应明确业务语义(4xx),缺一个通用 5xx 语义值。新增 `INTERNAL`,前端/mp 侧可按现有错误处理模式统一处理未分类错误。

## Risks / Trade-offs

- [risk] 兜底 `Exception.class` handler 可能意外吞掉本该由更具体 handler 处理的异常(如果异常类型层级判断有误)→ [mitigation] Spring `@ExceptionHandler` 按最具体匹配优先级分派,只要具体 handler 的异常类型声明不变,兜底 handler 只接住"未被任何已知类型覆盖"的异常,不冲突
- [risk] `/error` permitAll 后如果未来有人往 `/error` 相关渲染里加了业务数据,会绕过授权检查 → [mitigation] `/error` 渲染逻辑保持最小化(只出 code/message),code review 时关注该文件改动

## Migration Plan

- 纯代码修改,无数据/配置迁移
- 部署无特殊顺序要求,新版本上线立即生效
- 回滚:还原 `GlobalExceptionHandler` 与 `SecurityConfig` 两处改动即可,无残留状态
