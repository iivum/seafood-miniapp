# Runbook: `/actuator/prometheus` 不可达

**触发**:Prometheus scrape `seafood-backend:9090/actuator/prometheus` 返非 200,或
Grafana dashboard 5 个业务 counter(`orders_*` / `products_queried` / `users_login_attempts`)
持续 `No data`。

**优先级**:P1(生产告警 dashboard 失效,影响业务可观测性,数据采集链路非关键路径)。

---

## 1. 快速定位:从哪条路径在失败?

management 端口 9090 是 cluster-internal 隔离(design §D2),
只有 Prometheus / k8s sidecar / 容器内 curl 能访问;**host 浏览器看不到**。
先用容器内 TCP 检查 binary 是否在 9090 listen:

```bash
# 替换 <backend-container> 为 docker compose ps | grep seafood 得到的实际容器名
docker exec <backend-container> sh -c "ls /proc/$(pgrep -f seafood-backend | head -1)/net/tcp 2>/dev/null | head -1"
```

正常:binary 进程应 listen `0:24 0A 00000000:2384`(`2384` = 9090 hex 转换)。
无此行 → binary 没起来 / management port 配错 → 跳 §3。

如果 TCP 看起来正常,看 §2 检查 HTTP 层。

## 2. 容器内 HTTP curl

`backend/scripts/native-smoke.sh` 用 `docker exec seafood-backend curl ...` 探 9090,
但 `seafood-backend:native` 镜像基于 `gcr.io/distroless/base-debian12:nonroot`,
**无 curl / wget / sh**(连 shell 都没)。要容器内探 HTTP,有 2 选:

### 2a. 用现有 MetricsEndpointIT 当 contract 守门

```bash
cd backend
./gradlew :test --tests "com.seafood.shared.observability.MetricsEndpointIT" \
    -PexcludeTags=docker --rerun-tasks
```

期望 8/8 绿(`prometheusEndpointReturns200OnManagementPort` 等)。

### 2b. 临时换 alpine 镜像(只用于排查)

```bash
# 在 docker-compose.yml 把 backend 临时改 image: alpine:3.19
# 加 command: sh -c "apk add --no-cache curl; tail -f /dev/null"
# 然后 docker exec seafood-backend curl -v http://localhost:9090/actuator/prometheus
```

⚠️ 此法仅在排查时用,完成排查后 revert,避免 distroless image 体积被破坏。

## 3. 9090 TCP 没 listen — binary 启动问题

可能性按概率排:

| 现象 | 检查 | 修法 |
|---|---|---|
| 容器秒退 | `docker logs seafood-backend --tail 50` | 通常 `ADMIN_BOOTSTRAP_PASSWORD` 缺失 / `MONGODB_URI` 拼错 / Spring context fail-fast。修 env,`docker compose up -d --build` |
| 启动 OK 但 9090 没 listen | `docker inspect seafood-backend -f '{{.Config.Env}}'` | `MANAGEMENT_SERVER_PORT` 被 env 误覆盖成其它值。`grep -E 'MANAGEMENT' .env*` 看是否漏了 |
| 9090 listen 但 scrape 报 connection refused | 容器网络问题 | `docker network inspect seafood-miniapp_seafood-network` 看 backend service 是否 joined;Prometheus container 也应 joined 到同一 network |
| 9090 listen + 网络 OK 但返 404 | actuator 端点未 enable | `application.yml` 检查 `management.endpoints.web.exposure.include` 必须含 `prometheus`;本项目默认 `health,prometheus,info` |

## 4. binary 健康但 scrape 返 401/403

`SecurityConfig` 4 条 actuator 路径(`/actuator/health`, `/actuator/health/**`, 
`/actuator/info`, `/actuator/prometheus`) permitAll,设计意图是不需要 JWT。
如果 scrape 仍返 401/403:

1. 检查 Prometheus scrape config 是否误带 `bearer_token_file`(本项目不应需要)
2. `docker exec ... env | grep SECURITY` 排查是否 env 误注入额外 Security filter
3. 查看本仓库 `openspec/specs/metrics-export/spec.md` "Network exposure boundary"
   场景契约(9090 在 docker 网络内 cluster-internal 隔离,Prometheus 配 mTLS 而非 JWT
   是 Sprint 3 选型)

## 5. binary 健康 + scrape 200 但 5 业务 counter 看不到

可能:`@Service` 注册晚 / counter 名拼错 / `MeterRegistry` bean 未注入。

1. `./gradlew :test --tests "*ProductServiceTest" --tests "*OrderServiceTest" --tests "*AuthServiceLockoutTest"` 验 counter 名称
2. 看 `MetricsCardinalityTest`(ArchUnit 规则)是否最近 fail — 业务 counter 名字错会被 ArchUnit 拒
3. `docker exec seafood-backend  curl http://localhost:9090/actuator/prometheus 2>/dev/null` 
   (本镜像无 curl,所以用 §2b 的 alpine 替代)→ 查 `orders_created_total` / `orders_paid_total` 
   / `products_queried_total` / `users_login_attempts_total` 4 个 series

---

## 相关文档

- `CLAUDE.md` "可观测性"段 — 5 counter 名称表 + 标签约束
- `openspec/specs/metrics-export/spec.md` — Prometheus endpoint + business counter 契约
- `backend/scripts/native-smoke.sh` — RSS 测量 + 9090 探针脚本
- `.github/workflows/native.yml` "Smoke test" step — CI 端到端
