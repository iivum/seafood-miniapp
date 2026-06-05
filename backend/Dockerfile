# syntax=docker/dockerfile:1.7
# =============================================================
# GraalVM Native 多阶段构建 — Sprint 2 C5 §5.5
# -------------------------------------------------------------
# stage 1: ghcr.io/graalvm/native-image-community:25
#          → ./gradlew nativeCompile → build/native/nativeCompile/seafood-backend
# stage 2: gcr.io/distroless/base-debian12:nonroot
#          → 仅复制 binary,无 JVM 依赖,USER nonroot
# 验收(参见 design.md §3.4 + spec/native-build):
#   - 启动 < 2s
#   - RSS < 200MB
#   - image 无 openjdk / java 二进制(distroless 不带)
# =============================================================

# ----- build -----
FROM ghcr.io/graalvm/native-image-community:25 AS build
WORKDIR /workspace/backend

# 依赖层缓存:先只 copy 构建脚本
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

# 复制源码并 nativeCompile
COPY src ./src
RUN ./gradlew --no-daemon nativeCompile

# ----- runtime -----
FROM gcr.io/distroless/base-debian12:nonroot
WORKDIR /app
# 复制 native binary 到 /seafood-backend(USER nonroot 默认 UID 65532,对该路径有 rw)
COPY --from=build /workspace/backend/build/native/nativeCompile/seafood-backend /seafood-backend
EXPOSE 8080
USER nonroot
ENTRYPOINT ["/seafood-backend"]
