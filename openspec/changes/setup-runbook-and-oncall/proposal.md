# Proposal: 建立 Runbook 与 On-Call 制度

## Why

项目从开发模式过渡到运营模式,需要**标准化故障响应流程**:
- 当前故障发生 → 临时拉人 → 翻聊天记录 → 试错 → 恢复,平均 30+ 分钟
- 知识只在某个人脑子里,新成员 on-board 周期长
- 缺少"事故复盘"机制,同类故障反复发生

**为什么现在做**:Phase 1 可观测性补齐后,告警能发出来,但**没有人 / 流程响应等于没接**。Runbook + On-Call 是把可观测性从"看得到"变成"能恢复"的关键一环。

## What Changes

### Runbook 沉淀

`docs/runbooks/` 目录,每份 Runbook 包含:
- **症状**:告警名称 / 用户反馈 / 监控曲线
- **影响范围**:影响哪些接口 / 用户 / 业务
- **排查步骤**:从现象到根因的命令清单(可直接复制)
- **修复 SOP**:标准操作流程(可执行脚本优先)
- **升级路径**:处理不了的联系谁、Slack 频道
- **事后**:复盘模板链接

**Top 10 优先 Runbook**(Phase 2 一次性产出):
1. MongoDB 连接池耗尽
2. JWT_SECRET 误删 / 长度不足导致启动失败
3. native binary OOM(RSS > 200MB 上限)
4. Trivy CVE 紧急修复(高危漏洞扫描命中)
5. 微信小程序审核被拒
6. 订单状态机异常(PAID 但库存未扣)
7. 支付回调丢失(微信 notify 未到)
8. 限流误伤(正常流量被 429)
9. 索引缺失导致慢查询(P99 > 500ms)
10. docker-compose 启动顺序问题(mongodb 慢启动导致 backend 健康检查失败)

### On-Call 排班

- 小团队主备制(主 + 备 2 人/周),不设人海
- 排班表:`docs/oncall/schedule.md`,每周一 09:00 自动同步
- 飞书机器人:`@oncall 当前主:` 查询在岗人

### 告警分级与 SLA

| 级别 | 定义 | 首次响应 | 解决 SLA |
|---|---|---|---|
| **P0** | 服务不可用 / 数据丢失 | 5 分钟 | 1 小时 |
| **P1** | 功能降级 / 部分用户受影响 | 30 分钟 | 4 小时 |
| **P2** | 体验问题 / 性能下降 | 工作时间 | 下一个工作日 |

### Postmortem(事故复盘)

`docs/postmortems/<date>-<incident-name>.md`:
- **Blameless 文化**:不追责个人,只追系统性问题
- **时间线**:`HH:MM` 粒度记录发现 → 定位 → 缓解 → 恢复
- **根因分析**:5 Whys / 鱼骨图
- **改进项**:Action Items 必须有 owner + 截止日期
- **季度回顾**:季度末统计高频改进项,沉淀为下次架构升级输入

### 告警通道

- **钉钉 / 飞书机器人**:接 Prometheus AlertManager / Sentry webhook
- **P0 升级电话**:`docs/oncall/phone-tree.md`,主备 5 分钟不响应 → 升级经理
- **演练**:每季度一次故障演练(Chaos Engineering,见 Phase 3 #6)

## Capabilities

- **New Capabilities**:
  - `runbook-and-oncall` — 运维流程与故障响应基础设施(Runbook 库 + On-Call 排班 + 告警分级 + Postmortem 模板)
- **Modified Capabilities**: 无

## Impact

### 新增文件
- `docs/runbooks/` — 10 份 Runbook
- `docs/postmortems/_template.md` — Postmortem 模板
- `docs/oncall/schedule.md` — 排班表(月度)
- `docs/oncall/phone-tree.md` — 升级电话树
- `docs/runbooks/README.md` — 索引 + 编写规范

### 修改文件
- `CLAUDE.md` — 增"运维与故障响应"章节,链接 Runbook
- `.github/workflows/ci.yml` — 加 `validate-docs` job(link 校验,防止 broken link)
- (可选)`.github/dependabot.yml` — 增"安全更新"分组,触发后自动通知 oncall

### 依赖
- **零新增代码依赖**(纯文档 + 流程)
- 飞书 / 钉钉 webhook secret(可选,环境变量注入)

### 风险
- **形式主义**:Runbook 写了不更新 → 每次故障后强制 review 对应 Runbook
- **On-Call 疲劳**:小团队轮值过频 → 月度回顾,必要时扩招或合并角色
- **Blameless 难落地**:初期容易追责 → 季度回顾时由 GM 强调文化

### 前置依赖
- **Phase 1 #1 可观测性**:必须有告警通道(Sentry / Prometheus AlertManager)才能触发
- 已具备:`backend/scripts/native-smoke.sh`(部分 Runbook 内容来源)、团队沟通工具(飞书/钉钉)

### 验收(本次 PR)
- [ ] 10 份 Runbook 全部入库,经过模拟演练
- [ ] On-Call 排班表上线,首周有人值班
- [ ] Postmortem 模板 review 通过
- [ ] 模拟一次故障演练(故意停 mongodb 30 秒),验证全流程可走通
