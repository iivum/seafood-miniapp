# Sprint 4 D3 + C1 + C3 — Coverage Dashboard + PIT + k6 Design

> 2026-06-19 · 父路线图:`openspec/changes/test-suite-roadmap/design.md` §3.2 Sprint 4
> 状态: 设计已批准,待 apply

## 1. 背景

Sprint 3 末 4 个子项目归档,Jacoco coverage 79.3%,gate @ 0.79。Sprint 4 父路线图 §3.2 范围是 **D3 + C1 + C3**。本 change 集中做 3 件事:
- **D3 coverage dashboard** — PR comment + README badge + 1 个 self-hosted fallback
- **C1 PIT mutation testing** — `org.pitest:pitest-gradle-plugin` + ≥70% mutation score gate
- **C3 k6 load baseline** — 5 个核心 endpoint 跑 baseline 落库 + nightly CI

顺手收口 0.5% coverage gap(到 80% 阈值,升 gate 真到 0.80)。

## 2. 现状

- 79.3% coverage(Sprint 3 A 续-2 后)
- Jacoco gate @ 0.79(临时)
- 无 Codecov / PR coverage comment
- 无 mutation testing
- 无 k6 baseline(P99 数字未落库)

## 3. 设计

### 3.1 D3 Coverage dashboard

**自托管优先**(不依赖 Codecov 外部服务):
- `.github/workflows/ci.yml` 加 `actions/github-script@v7` step,读 `build/reports/jacoco/test/jacocoTestReport.xml` 解析 per-file coverage,发 PR comment
- `README.md` 加 coverage badge,链接到 `gh-pages` branch 发布的 `build/reports/jacoco/test/html/`
- 跑一次(用现有 Jacoco 79.3% 数据)commit baseline `backend/scripts/coverage-baseline.json`

**Codecov 可选**(如要启用):
- 加 `codecov/codecov-action@v4` step + `CODECOV_TOKEN` secret
- 后续 PR 自动接 Codecov dashboard,免自托管 gh-pages

**降级策略**:本 change 优先自托管(0 外部依赖),Codecov 留 TODO 留给未来。

### 3.2 C1 PIT mutation testing

**`backend/build.gradle` 加 PIT plugin**:
```groovy
plugins {
    ...
    id 'org.pitest' version '1.15.0'  // JDK 25 + Gradle 9 兼容
}

pitest {
    targetClasses = [
        'com.seafood.order.domain.*',
        'com.seafood.order.application.*',
        'com.seafood.product.domain.*',
        'com.seafood.product.application.*',
        'com.seafood.user.domain.*',
        'com.seafood.user.application.*',
        'com.seafood.bff.admin.*',
    ]
    excludedClasses = []  // none — domain/application 100% tested
    timestampedReports = true
    mutationThreshold = 70  // 失败时 exit non-zero
    threads = 4
    // 跳过 controller / repository / config(慢,单元测试不覆盖)
}
```

**CI**:
- `.github/workflows/ci.yml` 加 `backend` job 跑 `./gradlew pitest`
- 慢(5-10 min),可加 `continue-on-error: true` 让 PR 不阻塞,nightly 强制
- `actions/upload-artifact@v4` 上传 `build/reports/pitest/**`,retention 30d

**降级策略**:
- 首次跑只测基线,mutation score < 70% 不 fail(警告),后续 PR 严格 70%
- 改用 `mutationThreshold = 0`(无 gate)起步,跑一次后 baseline 出来再设 70%

### 3.3 C3 k6 load baseline

**`backend/scripts/k6-baseline.js`**:
```js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 10,            // 10 virtual users
    duration: '30s',     // 30 seconds
    thresholds: {
        'http_req_duration{name:GET /api/products}': ['p(99)<500'],
        // ... 5 个 endpoint 各设阈值
    },
};

const BASE = __ENV.BASE || 'http://localhost:8080';

export default function () {
    // 1. GET /api/products (public)
    http.get(`${BASE}/api/products`);
    // 2. POST /api/admin/auth/login → admin token
    const login = http.post(`${BASE}/api/admin/auth/login`, JSON.stringify({...}), {...});
    const token = login.json('accessToken');
    // 3. GET /api/orders (CUSTOMER auth)
    // 4. POST /api/orders
    // 5. GET /api/admin/orders (ADMIN)
    sleep(1);
}
```

**`backend/scripts/k6-run.sh`**:
- 后台起 backend(docker-compose 或 native binary)
- 跑 k6,捕获 stdout
- 解析 per-endpoint p50/p95/p99 → `k6-results.json`

**`backend/scripts/k6-results.json`** (initial baseline placeholder):
```json
{
  "timestamp": "2026-06-19T00:00:00Z",
  "endpoints": {},
  "total_requests": 0,
  "error_rate": 0,
  "note": "Initial baseline placeholder. Run backend/scripts/k6-run.sh to populate."
}
```

**`.github/workflows/nightly.yml`** (新增):
```yaml
name: Nightly k6 baseline
on:
  schedule:
    - cron: '0 2 * * *'
  workflow_dispatch:
jobs:
  k6:
    runs-on: ubuntu-latest
    steps:
      - checkout
      - start backend (docker-compose)
      - run k6
      - upload k6-results.json artifact
      - open PR if regression > 50% vs baseline
```

### 3.4 0.5% coverage gap 收口

**1 个 case 即可**:`OrderService.requestRefund_paidOrder_returnsRefundResponse` 走 PAID → REFUNDING state machine 路径。需要:
- SecurityContextHolder 设 ADMIN(避免 NotFoundException "订单不存在")
- Stub `OrderDocument` status=PAID + totalAmount=100
- 断言返回 RefundResponse.amount=50 + `refundRepository.save` 调一次

升 `backend/build.gradle` Jacoco threshold 0.79 → 0.80 + 删 `coverage-gap.md`。

## 4. 完成判据

- [ ] 0.5% coverage gap 关闭(1-2 case)+ Jacoco threshold 0.79 → 0.80
- [ ] Codecov 集成 OR 自托管 gh-pages dashboard 工作(任一选)
- [ ] README coverage badge 显示
- [ ] PIT plugin 跑出 baseline mutation score
- [ ] k6-baseline.js 写好,跑一次落 `k6-results.json`
- [ ] `.github/workflows/nightly.yml` 新建,定时 2am UTC 跑 k6
- [ ] 所有 pre-existing tests + 新 tests 通过

## 5. 风险 + Fallback

| 风险 | Fallback |
|---|---|
| PIT 1.15.0 与 JDK 25 + Gradle 9 不兼容 | 试 1.16.0;再退 1.14.x |
| PIT 跑超时(>15 min) | 缩 scope 到 `order.domain` + `order.application` 一组 |
| Codecov 配错(action 错 token) | 改用纯自托管 gh-pages,删 Codecov step |
| k6 跑 baseline 时 backend 没起 | docker-compose up + wait-for-healthcheck |
| k6 P99 > 500ms(超过 CLAUDE.md 预算) | 记录 baseline,plan 调优放到后续 sub-change |

## 6. YAGNI

- ❌ Codecov 强制集成(自托管够用)
- ❌ PIT strict 100% mutation score(70% 基线起步)
- ❌ k6 在 PR 跑(nightly 即可)
- ❌ C2 / C4 / C5(明确延后到 Sprint 5+)
- ❌ 改 main 业务代码

## 7. 文件清单

### 新建
- `backend/scripts/k6-baseline.js`
- `backend/scripts/k6-run.sh`
- `backend/scripts/k6-results.json`(initial placeholder)
- `.github/workflows/nightly.yml`

### 改
- `backend/build.gradle` — Jacoco threshold 0.79 → 0.80 + PIT plugin + pitest config
- `.github/workflows/ci.yml` — PIT step + Jacoco PR comment step
- `README.md` — coverage badge
- `backend/src/test/java/...OrderServiceRequestRefundTest.java`(1-2 case)

### 删
- `openspec/changes/sprint-3-coverage-a-cont-2/coverage-gap.md`

## 8. 关联

- **父**:`test-suite-roadmap/design.md` §3.2 Sprint 4
- **前置**:
  - `sprint-2-test-data-builders`(D1)
  - `sprint-2-backend-coverage`(A)
  - `sprint-3-ci-speed`(B)
  - `sprint-3-coverage-a-cont` + `-2`(A 续)— 79.3% baseline
- **后续**:
  - Sprint 4 末完成 → 勾 `test-suite-roadmap/tasks.md` §3 T10
  - Sprint 5+(如需):C2 Spring Cloud Contract / C4 jqwik / C5 visual diff
- **完成判据**:Sprint 4 末 4/4 子项目归档(D1 + A + B + C1 + C3 + D3 全 5 个跑通)
