# GraalVM Native Image 配置

> 参见 design.md §3.2-3.3 + specs/auth §Native Image safety

## 维护策略:自动生成,不要手编

这 4 个 JSON 由 `nativeTest` 阶段自动产出覆盖:

```bash
./gradlew nativeTest        # 运行带 native agent 的测试,生成配置
./gradlew nativeCompile     # 真正编译
```

如果 `nativeCompile` 启动报 `Class ... is not reflected`(找不到反射条目),
**先扩测试覆盖那个路径,再重跑 `nativeTest`** — 别手加 JSON。
`nativeTest` 失败排查手册:design §3.3 已知 Native 模式陷阱表。

## 已知风险(全项目拦截)

- `@RefreshScope` 注解 → 由 `scripts/check-no-refresh-scope.sh` + Gradle `checkNoRefreshScope` 任务拦截
- `mongodb-driver-sync` < 5.2 → 锁版本 ≥ 5.2(由 spring-boot-dependencies BOM 控制)
- `TimeZone.getDefault()` → 已加 `--initialize-at-build-time=java.util.TimeZone`
- YAML 解析 → 已加 `-H:+AddAllCharsets`
