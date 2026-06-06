# =============================================================
# GraalVM Native binary packager (Sprint 2 C5 §5.5)
# -------------------------------------------------------------
# P0 #2 修复:binary 不再在 Dockerfile 内重新编译。
# 原 Dockerfile 在 build stage 重跑 ./gradlew --no-daemon nativeCompile
# (4m47s,占整 docker build 5m40s 的 84%),而 .github/workflows/native.yml
# 已经在前置 "nativeCompile" step 产出
# backend/build/native/nativeCompile/seafood-backend,本地再编一次完全是浪费。
#
# CI 设计说明:
# - binary 来源:.github/workflows/native.yml 的 "nativeCompile" step 已经
#   编好,产物在 backend/build/native/nativeCompile/seafood-backend。
# - 本 Dockerfile 仅做"binary packager":把已存在的 native binary 拷进
#   distroless image,无 Gradle、无 native-image 工具链、无 JDK。
# - 本地构建:开发者先跑 ./gradlew nativeCompile,再用
#   `docker build -f Dockerfile backend/` 把 binary 打进 image。
# - 验收同 design §3.4 + spec/native-build:
#   * 启动 < 2s
#   * RSS < 200MB
#   * image 无 openjdk / java 二进制(distroless 不带)
#   * USER nonroot(UID 65532)保最小攻击面
#
# CI 修复(distroless 镜像选型历史):
#   gcr.io/distroless/base-debian12:nonroot 缺 zlib (libz.so.1),native binary
#   启动报 'error while loading shared libraries: libz.so.1'。改用
#   gcr.io/distroless/cc-debian12:nonroot —— 多带 glibc + 常用 lib
#   (含 zlib、libstdc++、libgcc),体积只多 ~10MB,启动 0 退化。
# =============================================================

# ----- runtime -----
FROM gcr.io/distroless/cc-debian12:nonroot
WORKDIR /app
# 从 build context 直接 COPY 预编译好的 native binary(由 native.yml 前置
# step 产出)。build context 根 = backend/,故源路径是
# build/native/nativeCompile/seafood-backend。
COPY build/native/nativeCompile/seafood-backend /seafood-backend
EXPOSE 8080
USER nonroot
ENTRYPOINT ["/seafood-backend"]
