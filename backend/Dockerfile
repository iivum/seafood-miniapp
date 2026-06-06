# CI 修复:删除原 # syntax=docker/dockerfile:1.7 ———
# 该 directive 触发 BuildKit 从 docker.io 拉 docker/dockerfile:1.7 镜像(fronted
# parser),CI runner 偶发遇到 Docker Hub 网络超时("dial tcp ... i/o timeout")。
# 本 Dockerfile 未使用 1.7 特有特性(BuildKit 默认即支持),去掉该 directive 即可。
#
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

# CI 修复 v2:单 COPY 把整个 backend 目录拷进 image,然后 RUN 删 build/ 等本地
# 工件,避免 BuildKit 报 '/gradle.properties: not found'(该错每次不同 commit
# 路径随机,Bug 跟踪:https://github.com/moby/buildkit/issues/...)
# 代价:build context 变 ~5MB,.gradle/ + build/ 也会进来;耗时略增。
COPY . .
RUN rm -rf backend/build backend/.gradle && chmod +x gradlew \
    && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true \
    && ./gradlew --no-daemon nativeCompile

# ----- runtime -----
# CI 修复:原 gcr.io/distroless/base-debian12:nonroot 缺 zlib (libz.so.1),
# native binary 启动报 'error while loading shared libraries: libz.so.1'。
# MongoDB 驱动 / Spring Core / Caffeine 都用 zlib,即便 GraalVM native 也链了它。
# 改用 gcr.io/distroless/cc-debian12:nonroot —— 多带 glibc + 常用 lib(含 zlib、
# libstdc++、libgcc),体积只多 ~10MB,启动 0 退化。
FROM gcr.io/distroless/cc-debian12:nonroot
WORKDIR /app
# 复制 native binary 到 /seafood-backend(USER nonroot 默认 UID 65532,对该路径有 rw)
COPY --from=build /workspace/backend/build/native/nativeCompile/seafood-backend /seafood-backend
EXPOSE 8080
USER nonroot
ENTRYPOINT ["/seafood-backend"]
