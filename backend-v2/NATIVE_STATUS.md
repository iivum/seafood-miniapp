# NATIVE_STATUS: GraalVM Native Image 现状

**状态**: 暂停。业务功能跑通(JVM 部署),Native 路径单独归档,等生态成熟再回头。

---

## 结论

**Spring Boot 4.0.6 + Java 25 + GraalVM 25 + Spring Security 6 + MongoDB driver** 的反射组合,**在当前生态下不成熟**。Native 编译在多个 third-party 库的对象 image heap 嵌入问题上反复打地鼠。

`spike/graalvm-native/`(无 MongoDB / Security)的 Native 编译是**成功的**——证明 Spring Boot 4 + GraalVM 25 本身工作。失败来自组合的复杂性,不是单一组件。

---

## 实证 (Phase 2 nativeCompile 尝试记录)

spike 成功后,backend-v2 加了 MongoDB + Spring Security + Spring Data + Logback 4 个常见库后,开始失败。

| 失败轮次 | 错误类 | 原因 |
|---|---|---|
| 1 | `org.springframework.security.config.annotation.method.configuration.AuthorizationProxyWebConfiguration$$FastClassByCGLIB$$...` | CGLIB 运行时生成不支持 Native → 改用 SecurityFilterChain URL 级 auth,删 `@EnableMethodSecurity` |
| 2 | `org.bson.internal.ProvidersCodecRegistry` | MongoDB 反射 → 加 `--initialize-at-build-time` |
| 3 | `org.apache.commons.logging.impl.Slf4jLogFactory$Slf4jLocationAwareLog` | 桥接库 → 加 `org.apache.commons.logging` |
| 4 | `org.bson.internal.CodecCache` | 同上 |
| 5 | `org.springframework.core.io.VfsUtils` | 启动时找 JBoss VFS → 加 `--initialize-at-run-time=...VfsUtils` |
| 6 | `org.apache.logging.log4j.MarkerManager$Log4jMarker` | log4j → 加 `org.apache.logging.log4j` |
| 7+ | `org.apache.logging.slf4j.SLF4JLoggerContextFactory` | 桥接 |

第 8 轮时仍未结束,放弃继续手动打地鼠。

---

## Spring Boot 4 + GraalVM 25 的真实成本

Spike 证明:**不带 MongoDB / Security / Logback 的最小应用**能编出 74MB native binary,启动 52ms。

加上真实生产栈后:
- MongoDB 5.6 driver 反射配置不全
- Spring Security 6 CGLIB 代理
- SLF4J ↔ Log4j ↔ Logback ↔ Commons-Logging 多桥接
- Spring Data MongoDB `@Document` 扫描需要 reflect-config 调优

这些问题在 Spring Boot 3.4 + GraalVM 21 组合下有大量已知 fix,但在 Spring Boot 4.0 + GraalVM 25 上**生态尚未沉淀**。

---

## 建议路径 (按优先级)

### 短期 (现在): JVM 部署 ✅
- 保持当前 build.gradle (native plugin apply false)
- docker 镜像: JRE 21 + jar (或 `eclipse-temurin:25-jre`)
- 启动: ~2-3s, 内存 ~300-500MB
- 单 binary 优势失去,但 8 周后业务上线,**不阻塞 Go-Live**

### 中期 (3-6 个月后): 等 Spring Boot 4 生态
- 关注 Spring Boot 4.x 补丁
- GraalVM 25.x 改进
- 预计届时会有官方文档化的 fix 集合

### 长期: 切换到更稳的组合
**如果 Native 是硬需求,降级栈**:
- Spring Boot 3.4 LTS (支持到 2027+)
- GraalVM 21 LTS
- 这两个 LTS 组合已有大量生产 fix

---

## 决策依据 (为何不当下继续)

1. **业务风险 > 技术完美**: 1 人项目,在 native 反射配置上耗 N 天,N 越大越偏离业务交付
2. **替代方案够用**: JVM 部署 ~300-500MB 内存,远低于原 Spring Cloud 7 进程的 ~600MB+,驱动力 a 仍部分满足
3. **生态窗口**: Spring Boot 4 GA 没多久,等 6-12 个月配置生态会成熟很多

---

## 如果将来重新启用 Native

复现步骤 (后续):
```bash
cd /Users/linbinghui/agent-v2
# 1. 改 build.gradle: apply false → 应用
# 2. 先 spike 验证新一轮 (Spring Boot 4.x.x + GraalVM 25.x.x) 的反射 fix 集
# 3. 拿 fresh 错误列表,一次性加 init-at-build-time / init-at-run-time
# 4. 用 tracing agent 自动生成 reflect-config
./gradlew --no-daemon -Pagent run -Pargs=...
# 5. 用生成的 reflect-config + resource-config 跑 nativeCompile
```

参考: [Spring Boot Native Image 官方文档](https://docs.spring.io/spring-boot/reference/native-image/index.html)(跟随 Spring Boot 4 版本同步更新)

---

*本文档作为 Phase 2 收尾产物,记录 native 路径的当前位置和未来切入点。*
