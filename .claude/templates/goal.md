# `/goal` 模板 — seafood-miniapp 适配版

> 适用于 Claude Code `v2.1.139+`(本机 2.1.162 支持)。
> 飞书/cc-connect 转发 Claude Code 命令,直接把 `/goal ...` 发到 bot 即可。
> 条件最长 4000 字符。

---

## 0. 三要素速查(写 condition 前自检)

| 要素 | 含义 | seafood-miniapp 常见落点 |
|---|---|---|
| **可量化终态** | 跑完一轮后 transcript 里能看到的客观结果 | `npm test` 退出码、`git diff --stat` 输出、文件行数、build log |
| **声明怎么证** | 显式说"用 X 命令/Y 输出证明" | `cd admin-ui && pnpm test` 退出 0 / `git status --porcelain` 为空 |
| **要守住的约束** | 过程中不能动什么 | 不准改 `package.json`、不准动其他测试文件、不准 force push |

> ⚠️ 评估器是 **Haiku 小模型,只读 transcript,不调工具**。所以:
> - 条件不能是「你觉得好了就行」
> - 必须让 Claude 自己跑命令把结果打到对话里,evaluator 才看得见
> - 终态必须是「输出一行字就能验证」的形式

---

## 1. 元模板(占位符,任何任务都能套)

```text
/goal [终态1:可验证的客观结果,用命令退出码/输出/文件状态描述] AND
       [终态2:同上,可加可] AND
       ...,
       验证方式:Claude 必须输出 [命令] 的退出码和关键行(例如 `exit=0` / `0 failing`),
       约束:[不准改哪些文件/不准引入哪些依赖/不准 force push],
       [可选] 不超过 [N] 轮 或 [N] 分钟
```

**自检清单**(写完对着过一遍):
- [ ] 至少 1 个「可量化终态」(`exit 0` / `< N 行` / `git status` 干净)
- [ ] 显式给了「怎么证明」(跑哪个命令)
- [ ] 列了至少 1 条「不准动」的约束
- [ ] (长任务)加了 turn/time 上限
- [ ] 没有「你觉得好就行」这种主观判定

---

## 2. seafood-miniapp 实战示例(直接复制改字段就能用)

### 2.1 后端重构:合并/拆分模块

```text
/goal backend/src/main/java/com/seafood 下的所有 .java 文件,
       单文件行数 ≤ 400 行(检查方式:`find backend/src/main/java -name '*.java' -exec wc -l {} +` 输出最大行 ≤ 400),
       且 `./gradlew :backend:compileJava :backend:test` 退出码 0,
       且 `git status --porcelain` 只显示预期的文件列表(我已在前一条消息里列了),
       不准新增依赖、不准动 build.gradle/gradle.properties,
       超过 30 轮就停
```

### 2.2 前端单测覆盖率提升

```text
/goal admin-ui/src 下所有 .ts/.tsx 文件被 vitest 覆盖,
       `cd admin-ui && pnpm test:coverage` 退出码 0,
       报告中 lines/branches/functions/statements 四项均 ≥ 80%(从 stdout 的 'All files' 那行读),
       现有测试不得被删除或 skip,
       不准引入新的运行时依赖(只能在 devDependencies 加 @vitest/* 之类),
       超过 40 轮就停
```

### 2.3 修一个具体 bug

```text
/goal backend 修复海鲜订单的浮点精度 bug(backend/src/order/Calc.java:142),
       验证:`cd backend && ./gradlew :backend:test --tests "*OrderCalc*"` 退出码 0,
       且我手动指定的 3 个回归用例(已贴在前一条消息里)全部通过,
       且 `git diff --stat backend/src/order/Calc.java` 改动行数 ≤ 50 行,
       不准改测试文件、不准改 build.gradle,
       超过 15 轮就停
```

### 2.4 写/补 E2E 测试

```text
/goal admin-ui/e2e/ 下每个被列在 CLAUDE.md §验收清单里的页面至少有 1 个 Playwright 截图测试,
       验证:`cd admin-ui && pnpm exec playwright test` 退出码 0,
       且 `find admin-ui/e2e -name '*.spec.ts' | wc -l` 等于我指定的页面数,
       现有 e2e 不得被删除或 .skip(),
       不准动 playwright.config.ts 的 viewport 和 baseURL,
       超过 25 轮就停
```

### 2.5 文档更新

```text
/goal seafood-miniapp/CLAUDE.md 已经按 "Onboarding" 章节要求更新了新成员上手步骤,
       改动只限这一个文件(`git diff --stat` 输出只有 CLAUDE.md),
       且我贴在前一条消息的 5 个验收点全部出现在文档里(Claude 自行 grep 验证),
       不准新增章节、不准删章节、不准动其他 .md,
       超过 10 轮就停
```

### 2.6 GraalVM Native Image 验收(CLAUDE.md §7.1)

```text
/goal backend GraalVM native image 验收达标,
       验证:
         1) `cd backend && ./gradlew :backend:nativeCompile` 退出码 0
         2) 产物 `build/native/nativeCompile/backend` 存在(用 `ls -la` 验证)
         3) `./backend --version` 启动 < 2s(`time` 命令的 real < 2.0s)
         4) 启动后 RSS < 200MB(用 `ps -o rss= -p $PID` 验证)
         5) `curl localhost:8080/actuator/health` 返回 `{"status":"UP"}`,
       不准改 `application.yml` 的服务端口、不准改 native-image 配置除非显式声明,
       超过 40 轮就停
```

---

## 3. 飞书侧的使用小贴士

1. **避免一条 IM 消息里塞超大 condition**(飞书卡片有长度限制,4000 字符也容易被 IM 截断)
   - 长 condition 拆成:第一条 `请按 ~/.claude/templates/goal.md §2.6 执行`,第二条贴自定义参数
2. **`/goal` 状态在 IM 里没有原生指示器**
   - 让 Claude 每轮结束后**自己 echo 一行** `◎ progress: turn N/M, last_check=<退出码>`
   - 飞书卡片会自动 update,你直接看卡片就知道进度
3. **想中途换任务** → 在飞书发 `/goal clear` 或 `/clear`(后者连会话也清)
4. **想批量跑多个 goal** → 在第一条 goal 完成后手动发下一条 `/goal ...`,**不要**在同一条 IM 里写多段 `/goal`
5. **goal 跨 session 保留** —— 如果你 `exit` Claude Code 后再 `--resume`,未达成的 goal 会继续跑;达成/clear 的不会恢复

---

## 4. 反模式(不要这么写)

| ❌ 反例 | 为什么坏 |
|---|---|
| `/goal 帮我把代码改好` | 没有可验证终态,evaluator 没法判 |
| `/goal until you think it's done` | evaluator 不知道"你觉得"是什么 |
| `/goal all tests pass`(没说在哪) | 范围不明确,backend? admin-ui? e2e? |
| `/goal rewrite backend`(没说改什么) | 范围爆炸,30 轮都打不住 |
| `/goal 改完顺手优化一下` | 「顺手优化」无定义,scope creep 温床 |
| `/goal run all the linters` | evaluator 不知道"all"包不包含 prettier/spotless/checkstyle |

---

## 5. `/goal` vs `/loop` vs Stop hook(三选一)

| 想让 Claude ... | 用 |
|---|---|
| 「做到 X 为止」(有明确验收) | `/goal` ← **大多数情况选这个** |
| 「每隔 5 分钟跑一次 prompt」 | `/loop "..." --interval 5m` |
| 「每轮结束后跑一个我自己的检查脚本」 | 自己写 Stop hook(`.claude/settings.json` 里配) |

seafood-miniapp 的常规 CI 监控类(每隔 10 分钟看 CI 红没红)用 `/loop`;**修 bug / 重构 / 加测试** 用 `/goal`。
