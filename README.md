# 🦐 海鲜商城小程序

微信小程序 + Spring Boot 单仓电商平台。

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

- [CLAUDE.md](CLAUDE.md) — AI 编程工具的项目级指引(开发规则 / 测试要求 / 性能预算 / 常见坑)
- [`docs/`](docs/) — 设计与运营文档(`DESIGN.md` 设计系统,`RUNBOOK.md` 运维手册,`CONTRIBUTING.md` 贡献指南,等)
- [`openspec/specs/`](openspec/specs/) — 系统行为契约的 single source of truth(`admin-ui` / `auth` / `backend-api` / `mini-program` / `developer-docs`)
- [`.github/workflows/`](.github/workflows/) — CI pipeline 行为与触发条件(见各 yaml 注释)

## 📝 License

MIT
