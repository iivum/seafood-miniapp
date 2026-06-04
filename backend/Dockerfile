# syntax=docker/dockerfile:1.7
# GraalVM Native 多阶段构建(design §3.4)
# 最终 binary ~80MB,无 JVM 依赖;启动 < 2s、内存 < 200MB(设计验收指标)

# ----- build -----
FROM ghcr.io/graalvm/native-image:ol9-java25 AS build
WORKDIR /app

# 依赖层缓存:先只 copy 构建脚本,拉依赖
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

# 复制源码并构建
COPY src ./src
RUN ./gradlew --no-daemon nativeCompile

# ----- runtime -----
FROM gcr.io/distroless/base-debian12
WORKDIR /app
COPY --from=build /app/build/native/nativeCompile/seafood-backend /app/seafood-backend
EXPOSE 8080
# 不需要 USER 切换:distroless 默认非 root
ENTRYPOINT ["/app/seafood-backend"]
