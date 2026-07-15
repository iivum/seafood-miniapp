# Proposal: fix-error-contract-denyall

## Why

2026-07-13 E2E 实测:后端任意未处理异常经 Spring `/error` 转发时,被 `SecurityConfig` 末尾的 `anyRequest().denyAll()` 拦截,客户端收到 **403 + 空 body**——既非真实状态码(应 500),也无 `{code,message}` 错误体。这违反 CLAUDE.md/`api-contract` 规定的统一错误契约,且是**跨模块**缺陷:所有模块的 5xx 类错误都被吞,前端与运维完全无法定位(本次实测把商品反序列化 500 伪装成了 403 空响应)。

## What Changes

- `/error` 路径在授权规则中显式放行(`permitAll`,Spring 官方推荐处理方式),使异常能到达统一错误处理并按契约渲染
- 确认/补齐兜底 `@ExceptionHandler`(或 `ErrorController`):未分类异常统一返回 `500 + {code:"INTERNAL", message}`,不泄漏堆栈
- 回归测试加在跑真 filter chain 的 IT(参照 `SecurityFilterChainOrderIT` 模式):人为触发未处理异常,断言状态码 500 且 body 含 `{code,message}`,**不是** 403 空 body(controller/slice 测试绕过 filter chain 抓不到,memory 已有先例)
- 审计 `ErrorResponse.code` 枚举是否需要新增 `INTERNAL`(现有 `NOT_FOUND/VALIDATION/DOMAIN/TOKEN_*` 无 5xx 语义)

## Capabilities

- **New Capabilities**: 无
- **Modified Capabilities**:
  - `api-contract`:补充「未处理异常(5xx)同样必须返回 `{code,message}` 错误体;/error 转发不得被授权兜底拦截」的要求

## Impact

- 后端:`shared/config/SecurityConfig.java`(授权规则)、`shared/error/`(兜底 handler + ErrorResponse code 枚举)
- API:所有端点的 5xx 行为从「403 空 body」变为「500 + 契约错误体」——对前端是行为修正而非破坏
- 安全评审点:放行 `/error` 不暴露业务数据(仅错误渲染),需在 PR 中说明;`anyRequest().denyAll()` 兜底原则保持不变
- 证据:E2E 报告 bug② 次生现象;backend 日志 12:20:34 `IllegalArgumentException` 对应外部 403
