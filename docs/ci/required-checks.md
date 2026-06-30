# CI 必需检查(Required Status Checks)配置指引

> 背景:PR #41(Feature Flag 平台)合并时其 PR CI 已经是红的 —— `Backend (Java 25 +
> GraalVM)` job 因 `OpenApiContractIT` 契约漂移失败,但因为它**不是必需检查**,红着也能
> merge,于是把 `main` 带红。根因不是测试,而是**缺少分支保护门**。本文档记录如何补上。
>
> 注:分支保护属于仓库 Settings,无法通过代码 / API token 改,需 repo admin 在 GitHub UI
> (或用带 admin 权限的 `gh` / REST API)手动配置一次。

## 一次性配置(GitHub UI)

`Settings → Branches → Branch protection rules → main`(没有就 *Add rule*):

1. ✅ **Require status checks to pass before merging**
2. ✅ **Require branches to be up to date before merging**(可选,但推荐)
3. 在 *Status checks that are required* 里搜索并勾选下列 **check 名**(必须跑过一次才会出现在列表里):

   | 必需 check | 来源 workflow | 为什么必需 |
   |---|---|---|
   | `Backend (Java 25 + GraalVM)` | `ci.yml` | 单测 + ArchUnit + 契约漂移门 + 覆盖率门 —— **本次事故的直接拦截点** |
   | `Mini-Program (Jest)` | `ci.yml` | 前端单测 |
   | `Admin UI (Vitest + coverage gate)` | `ci.yml` | admin 单测 + 覆盖率门 |
   | `Design Tokens (v2 OKLch parity)` | `ci.yml` | token parity 护栏 |
   | `PR Lint (Test plan gate)` | `pr-lint.yml` | PR 模板 Test plan 校验 |
   | `TruffleHog (PR diff)` | `security.yml` | 密钥泄漏扫描(秒级) |
   | `GraalVM native build (Ubuntu)` | `native.yml` | native 编译可用性 |

## 显式**不**设为必需

- **`OWASP Dependency-Check`** —— 已移到 nightly(`security.yml` 的 `schedule` + main push +
  手动 dispatch,不在 PR 上跑)。NVD 全量扫描冷启动 30–45min,做 PR 必需门会经常顶满 timeout
  阻断合并;它是供应链漂移**监控**,nightly 时效足够,真实 CVE 仍上传 SARIF 到 Code Scanning。
- **`PIT Mutation`** —— 慢,nightly 跑,不进 PR 主链(见 `nightly.yml`)。

## 配套已落地的自动化(本 PR)

- **自愈式契约门**:`OpenApiContractIT` 漂移时把 springdoc 实际生成的 spec
  (`generated-openapi.json`)+ 行级 diff 落盘,CI 以 `openapi-contract-drift` artifact 上传。
  作者下载后覆盖 `backend/src/test/resources/contract/openapi.json` 即修复,无需本地
  JDK25 + Docker 重生成。
- **OWASP 移 nightly**(见上)。
- **aliyun 镜像 CI 关闭**:`settings.gradle` / `build.gradle` 里 aliyun 镜像只在本地(非 CI)
  启用 —— GitHub runner 在美国,aliyun 既慢又间歇 5xx(PR #42 因此连吃 3 次 re-run)。

## 用 gh CLI 配置(可选,等价于上面 UI)

```bash
gh api -X PUT repos/iivum/seafood-miniapp/branches/main/protection \
  -H "Accept: application/vnd.github+json" \
  -f 'required_status_checks[strict]=true' \
  -f 'required_status_checks[checks][][context]=Backend (Java 25 + GraalVM)' \
  -f 'required_status_checks[checks][][context]=Mini-Program (Jest)' \
  -f 'required_status_checks[checks][][context]=Admin UI (Vitest + coverage gate)' \
  -f 'required_status_checks[checks][][context]=Design Tokens (v2 OKLch parity)' \
  -f 'required_status_checks[checks][][context]=PR Lint (Test plan gate)' \
  -f 'required_status_checks[checks][][context]=TruffleHog (PR diff)' \
  -f 'required_status_checks[checks][][context]=GraalVM native build (Ubuntu)' \
  -f 'enforce_admins=false' \
  -f 'required_pull_request_reviews[required_approving_review_count]=1' \
  -f 'restrictions=null'
```
