# 🦐 海鲜商城小程序

![Coverage](https://img.shields.io/badge/coverage-80%25-brightgreen)
![Mutation](https://img.shields.io/badge/mutation-72%25-green)

微信小程序 + Spring Boot 单仓电商平台。

> **测试有效性**:行覆盖率 ≥80%(Jacoco,`check` 主链强制);变异分 ≥70%(PIT,作用 `order`/`product` 核心包)。变异测试验证"断言真的在卡行为",运行慢(~1min),不进 PR 主链,只手动 `./gradlew pitest` 或 nightly CI 触发。

## 📦 项目结构

```
seafood-miniapp/
├── frontend/      # 微信小程序 (TypeScript + Jest)
├── backend/       # Spring Boot 单模块 (Java + MongoDB)
├── openspec/      # OpenSpec changes + specs(行为契约的 SOT)
├── docs/          # 项目文档(设计 / Runbook / 贡献指南)
└── .github/       # CI workflows(行为见 yaml 注释)
```

更多目录细节见 `CLAUDE.md` 的"项目架构"段。

## 🚀 快速开始

### 后端 + MongoDB(本地 Docker 拉起,推荐)

```bash
docker-compose up -d
docker-compose logs -f
docker-compose down         # 停
docker-compose down -v      # 停 + 清 MongoDB 数据
```

启动后验收:`/actuator/health` 30 s 内 200;`curl http://localhost:8080/api/products?page=0&size=10` 返回 200 且 `totalElements > 0`(需先 `docker compose exec -T mongodb mongosh seafood --quiet < backend/seed/seed.js` 灌种子数据)。完整冒烟脚本: `backend/scripts/native-smoke.sh`。

### 后端测试

```bash
cd backend
./gradlew test                # 全部 + 报告 build/test-results/
./gradlew check               # 含 checkNoRefreshScope 静态扫描 + ArchUnit
./gradlew nativeTest          # GraalVM agent 收集 native image metadata
./gradlew test -PexcludeTags=docker   # 无 Docker 环境跳过 Testcontainers IT
```

### 前端测试

```bash
cd frontend
npm test
npm test -- --coverage
```

## 🛠️ 技术栈

版本与依赖以仓库内 SOT 文件为准,本 README 不重复:

- 后端依赖: 见 [`backend/build.gradle`](backend/build.gradle) + [`backend/gradle.properties`](backend/gradle.properties)
- 前端依赖: 见 [`frontend/package.json`](frontend/package.json)
- 容器镜像与启动命令: 见 [`docker-compose.yml`](docker-compose.yml) + [`backend/Dockerfile`](backend/Dockerfile)
- 运行环境(JDK / Node): 见 `.github/workflows/*.yml` 中各 step 的 `setup-*-action` 版本

## 📖 文档

- [CLAUDE.md](CLAUDE.md) — AI 编程工具的项目级指引(开发规则 / 测试要求 / 性能预算 / 常见坑 / 可观测性)
- [`docs/`](docs/) — 设计与运营文档(`DESIGN.md` 设计系统,`runbooks/` 运维手册,等)
- [`openspec/specs/`](openspec/specs/) — 系统行为契约的 single source of truth(`admin-ui` / `auth` / `backend-api` / `mini-program` / `developer-docs` / `metrics-export` / `structured-logging`)
- [`.github/workflows/`](.github/workflows/) — CI pipeline 行为与触发条件(见各 yaml 注释)

## 🔭 Operations

### Metrics 端点(Prometheus 文本格式)

Management 端口 `9090` 暴露 `/actuator/prometheus`(物理隔离于业务端口 `8080`,容器内可达)。

`prometheus.yml` 最小可用 scrape 段(由 Task #7 实施者扩展):

```yaml
scrape_configs:
  - job_name: seafood-backend
    metrics_path: /actuator/prometheus
    static_configs:
      # 容器内 DNS,k8s / docker compose 都能解析
      - targets: ['seafood-backend:9090']
```

### 业务 counters

5 个 ApplicationService 边界埋点 — 详见 [CLAUDE.md "可观测性"段](CLAUDE.md#可观测性openspec-setup-observability-stack-pr-1pr-2pr-3):

| Counter | Tags | 含义 |
|---|---|---|
| `orders.created` | `paymentMethod` | 下单成功次数 |
| `orders.cancelled` | `reason` (user/timeout/admin/other) | 取消次数 |
| `orders.paid` | `paymentMethod`,`amountBucket` (lt100/100to500/500to2000/gte2000) | 支付成功 + 金额分桶 |
| `products.queried` | `category` (5 个 sealed interface) | 商品浏览次数 |
| `users.login.attempts` | `result` (success/failed/locked) | 登录尝试次数 |

示例 PromQL(过去 5min 订单数 / 平均订单金额 / 登录失败率):

```promql
# 5min 内下单数
sum(rate(orders_created_total[5m]))

# 5min 内订单平均金额(用 bucket 中点估计)
sum(rate(orders_paid_total[5m]) * on() group_left() label_replace(
  vector(50, 300, 1250, 3000),
  "amountBucket", "$1", "le", "350", "550", "2050", "+Inf"
)) / sum(rate(orders_paid_total[5m]))

# 5min 登录失败率
sum(rate(users_login_attempts_total{result="failed"}[5m]))
  / sum(rate(users_login_attempts_total[5m]))
```

### 日志 schema

prod profile 输出 Logstash JSON 单行;Loki/ES/SLS 直消费:

```json
{
  "@timestamp": "2026-06-08T11:24:21.123Z",
  "@version": "1",
  "level": "INFO",
  "level_value": 20000,
  "logger_name": "com.seafood.order.application.OrderService",
  "thread_name": "tomcat-handler-0",
  "message": "order created: o-123",
  "requestId": "01931a45-7c80-7000-9b3e-3f8a1c5e4d20",
  "tags": ["COMMONS-LOGGING"]
}
```

dev profile 保留人类可读 pattern + `[<requestId>]` 段。`LOG_FORMAT=json` 环境变量在任意 profile 下强制切 JSON。

### 故障排查

`/actuator/prometheus` 不可达? 跳 [`docs/runbooks/actuator-prometheus-unreachable.md`](docs/runbooks/actuator-prometheus-unreachable.md) 跑 5 步排查。

## 📝 License

MIT
