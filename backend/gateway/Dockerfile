# ===========================================
# Gateway Dockerfile - 统一 AOT 缓存构建
# Spring Boot 4.0.6 + JDK 25
# ===========================================

# ---- 阶段 1: AOT 缓存构建 ----
FROM eclipse-temurin:25-jdk AS aot-builder

WORKDIR /build

# 复制依赖定义
COPY gradle.properties build.gradle settings.gradle ./
COPY gradle gradle

# 下载并缓存 Gradle 依赖
RUN ./gradlew dependencies --no-daemon || true

# 复制源码
COPY src src
COPY bootJar.main.resources bootJar.main.resources 2>/dev/null || true

# 构建 AOT 优化产物
RUN ./gradlew :gateway:bootJar --no-daemon

# ---- 阶段 2: 运行镜像 ----
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# 安全更新
RUN apk upgrade --no-cache

# 创建非 root 用户
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 复制 JAR 文件
COPY --from=aot-builder /build/gateway/build/libs/*-boot.jar /app/app.jar

# 设置权限
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xmx384m", "-Xms192m", "-jar", "/app/app.jar"]
