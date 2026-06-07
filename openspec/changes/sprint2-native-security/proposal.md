## Why

Sprint 1 交付了测试基础(Testcontainers + ArchUnit + JsonTest + 1 个 `@ConfigurationPropertiesTest`),但后端距离生产就绪还差 4 道关:① `nativeCompile` 仍未在 CI 跑通(`build.gradle:85-95` 注释里写明只完成 Phase 1,Phase 2 nativeTest agent + META-INF 同步未做),docker-compose 的 native binary 部署路径无 CI 验证;② 没有任何供应链扫描(OWASP Dep-Check、Trivy 镜像扫描、secret scanning、依赖更新机器人都缺位);③ 运行时安全停留在"JWT 能签发能校验",缺 token 轮换/吊销、HTTP 安全响应头、admin 路径限流、登录失败锁定;④ 启动期 fail-fast 仅覆盖 `JwtProperties` 1 个 prefix,`MONGODB_URI`、`WECHAT_*`、`spring.data.mongodb.*` 等关键配置异常时仍可能延后到首次请求才报错,且无任何日志脱敏。Sprint 1 design 的 "Non-Goals" 段已明确把这 4 项列入 Sprint 2/3,本次一次性收口。

## What Changes

- **Native 构建闭环**: CI 新增 `./gradlew nativeTest` job(agent 模式收集反射/资源/proxy);把生成的 JSON 自动合并到 `backend/src/main/resources/META-INF/native-image/` 并提交;CI 跑 `./gradlew nativeCompile` 产出 `seafood-backend` ELF;docker-compose 切到 native binary 后端跑 1 轮 smoke(启动 < 2s、`/actuator/health` 200、`/api/products` 端到端通)。
- **供应链安全**: 引入 OWASP Dependency-Check Gradle 插件,`./gradlew dependencyCheckAnalyze` 在 CI 跑,CVSS ≥ 7 即 fail;CI 跑 Trivy `image:` 扫描 native binary 镜像;启用 GitHub Advanced Security secret scanning + push protection;`.github/dependabot.yml` 接入 Gradle / Docker 周度更新。
- **运行时安全加固**: 新增 `TokenRevocationService`(MongoDB `revoked_tokens` 集合 + jti 黑名单 + TTL 索引) → 登出/管理员强制下线写入;`SecurityHeadersFilter` 统一注入 `Content-Security-Policy`、`Strict-Transport-Security`、`X-Frame-Options`、`X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy`;`AdminRateLimitFilter` 对 `/api/admin/**` 按 IP+account 加 fixed-window 限流(限 60 rpm,Caffeine 内存计数;PR review #27:原 proposal/spec 误标"token bucket",实际实现是固定窗口,见 design §4 decision 4);`LoginAttemptService` 对账号连续 5 次失败锁 15 分钟。
- **配置安全 fail-fast**: 扩展 `JwtProperties` 校验(密钥 ≥32 字节、`adminSecret` 必填且与 user 密钥不同);新增 `MongoProperties`、`WechatProperties` 两个 `@ConfigurationProperties` 类并配 `@Validated`;启动期所有 `@ConfigurationProperties` 都走 JSR-303 校验,失败即 fail-fast 退出非零;新增 `SensitiveValueMasker` 序列化器,Actuator `/configprops` 与日志输出对 `*secret`、`*password`、`*uri` 自动脱敏。
- **BREAKING**: 无生产 API 变更。但 `JWT_ADMIN_SECRET` 缺失或与 `JWT_SECRET` 相同时,后端将拒绝启动(此前会用同一密钥静默兜底)。需在部署环境补环境变量。

## Capabilities

### New Capabilities

- `native-build`: GraalVM Native binary 在 CI 中可重复构建并跑端到端 smoke;META-INF 反射/资源/proxy JSON 由 agent 收集而非手写;docker-compose 部署 native binary 后启动时间、内存、首请求延迟符合预算。
- `supply-chain-security`: 依赖与镜像层面的漏洞扫描在 CI 拦截;密钥泄漏由 GitHub push protection 阻止;依赖更新自动 PR。
- `runtime-security`: HTTP 安全响应头、JWT 吊销、admin 路径限流、登录失败锁定 4 项运行时防护一次性补齐,Filter 链统一由 `shared/security/` 暴露。
- `config-validation`: 所有 `@ConfigurationProperties` 在启动期完成 JSR-303 校验,关键密钥与连接串缺失或弱值即 fail-fast;敏感字段在 `/actuator/configprops` 与日志中脱敏。

### Modified Capabilities

- `auth`: 增加 token 吊销与登录失败锁定要求(新增 scenarios,不破坏现有签发/校验流程)。
- `backend-api`: 增加 HTTP 安全响应头与 admin 路径限流要求(横切关注,所有端点行为不变,响应头新增)。

## Impact

- **Build/CI**:`backend/build.gradle` 增 OWASP Dep-Check 插件、`nativeTest` task 在 CI 启用;`.github/workflows/` 新增 `native.yml` + `security.yml`;`.github/dependabot.yml` 新增。
- **Production code**:`backend/src/main/java/com/seafood/shared/security/` 新增 `TokenRevocationService`、`SecurityHeadersFilter`、`AdminRateLimitFilter`、`LoginAttemptService`、`SensitiveValueMasker`;`shared/config/` 新增 `MongoProperties`、`WechatProperties`,扩展 `JwtProperties`。
- **Resources**:`backend/src/main/resources/META-INF/native-image/reflect-config.json` 等由 agent 重生成并入库;`application.yml` 新增 `security.headers.*`、`security.rate-limit.*`、`security.login-lock.*` 默认值。
- **Tests**:`backend/src/test/java/com/seafood/shared/security/`、`shared/config/`、`architecture/`(新增 ArchUnit 规则:`SecurityHeadersFilter` 必须装配)。
- **Docs**:`CLAUDE.md` 「环境变量」段补 `JWT_ADMIN_SECRET` 强制约束;`docker-compose.yml` 切换镜像到 `seafood-backend:native`。
- **运行时**: 无 API 形态变更,响应头新增不影响小程序与 admin-ui 调用。`JWT_ADMIN_SECRET` 必填是唯一的 BREAKING,需运维同步。
