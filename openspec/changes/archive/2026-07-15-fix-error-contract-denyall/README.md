# fix-error-contract-denyall

P1:未处理异常经 /error 转发被 SecurityConfig anyRequest().denyAll() 拦成 403 空 body,违反 {code,message} 错误契约,所有模块的 500 类错误均被吞(2026-07-13 E2E 发现)
