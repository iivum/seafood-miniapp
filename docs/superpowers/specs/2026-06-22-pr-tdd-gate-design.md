# PR TDD Gate — Design Spec

**Date:** 2026-06-22  
**Status:** Approved  
**Scope:** `.github/PULL_REQUEST_TEMPLATE.md` + `.github/workflows/pr-lint.yml`

---

## 1. 目标

在每个功能 PR 上强制填写 Test plan，防止测试债务重新累积。属于 `test-suite-roadmap` §5 "可持续"验收条目。

---

## 2. 交付物

| 文件 | 说明 |
|---|---|
| `.github/PULL_REQUEST_TEMPLATE.md` | PR 创建时自动填充的模板 |
| `.github/workflows/pr-lint.yml` | CI lint job，校验 Test plan |

---

## 3. PR 模板

**路径：** `.github/PULL_REQUEST_TEMPLATE.md`

```markdown
## Summary

<!-- 1-3 条要点说明本 PR 做了什么 -->

## Changes

<!-- 改动清单，按模块/层列出 -->

## Test plan

<!-- ≥ 2 个 checkbox，不能全是 TODO -->
- [ ] 
- [ ] 

## Screenshots

<!-- 如有 UI 改动，粘贴截图；无则删除本节 -->

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

---

## 4. pr-lint.yml Workflow

### 触发条件

```yaml
on:
  pull_request:
    types: [opened, edited, synchronize, reopened]
```

### 路径豁免

PR 改动文件列表中，若**无一文件**匹配以下路径前缀，则跳过检查（`exit 0`）：

- `src/`（backend Java 源码）
- `frontend/`（微信小程序源码）
- `admin-ui/src/`（管理后台源码）

纯文档（`docs/`）、CI 配置（`.github/`）、依赖升级（`package.json` 根级）等改动自动豁免。

### 校验规则（按顺序）

1. **PR body 非空**：body 为 `null` 或空字符串 → 失败
2. **`## Test plan` 章节存在**：body 必须含标题 `## Test plan`（大小写敏感）
3. **checkbox 数量 ≥ 2**：正文中 `- [ ]` 或 `- [x]` 合计 ≥ 2 条
4. **不全是空/TODO**：所有 checkbox 文本不能全为空白或字面量 `TODO`

### 失败输出示例

```
❌ PR lint failed: Test plan 需要 ≥ 2 个 checkbox（当前 0 个）
   提示：在 ## Test plan 下添加至少 2 条 "- [ ] 具体测试项"
```

```
❌ PR lint failed: 未找到 ## Test plan 章节
   提示：使用 PR 模板（创建 PR 时自动填充）
```

```
❌ PR lint failed: Test plan 中所有 checkbox 均为空或 TODO
   提示：填写具体测试步骤，例如 "- [ ] 登录后可见仪表盘"
```

### 豁免跳过输出示例

```
ℹ️  PR lint skipped: 改动不包含 src/、frontend/、admin-ui/src/ 下的文件
```

---

## 5. Branch Protection 配置（手动步骤）

PR lint job 落地后，需在 GitHub repo Settings 中：

- **Settings → Branches → Branch protection rules → main**
- 勾选 `Require status checks to pass before merging`
- 搜索并添加 `PR Lint (Test plan gate)` 作为 required check

这是一次性手动操作，不在代码实现范围内。

---

## 6. 不做什么（YAGNI）

- ❌ 不校验 `## Summary` 或 `## Changes` 是否填写（降低摩擦）
- ❌ 不提供 `skip-test-plan` label 豁免（路径豁免已覆盖合法例外）
- ❌ 不解析 checkbox 语义（只计数，不理解内容）
- ❌ 不在 PR comment 里贴详细 lint 报告（job log 已足够）

---

## 7. 验收标准

- [ ] 创建改动 `src/` 的 PR，body 无 Test plan → CI job 红
- [ ] 创建改动 `src/` 的 PR，body 有 ≥ 2 个具体 checkbox → CI job 绿
- [ ] 创建纯文档改动 PR（只改 `docs/`）→ CI job 跳过（neutral）
- [ ] PR body 只有 1 个 checkbox → CI job 红，错误信息明确
- [ ] 所有 checkbox 均为 `- [ ] TODO` → CI job 红
