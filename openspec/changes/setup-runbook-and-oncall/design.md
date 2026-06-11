# Design: 建立 Runbook 与 On-Call 制度

## Context

项目从开发模式过渡到运营模式,但当前故障响应流程缺失:

- 故障发生 → 临时拉人 → 翻聊天记录 → 试错 → 恢复,平均 30+ 分钟
- 知识只在某个人脑子里,新成员 on-board 周期长
- 缺少"事故复盘"机制,同类故障反复发生

Phase 1 可观测性补齐后,告警能发出来,但**没有人 / 流程响应等于没接**。Runbook + On-Call 是把可观测性从"看得到"变成"能恢复"的关键一环。

**关键基建已具备**:Phase 1 的 Prometheus / Sentry / AlertManager / Slack 通道都已就绪。

## Goals / Non-Goals

**Goals:**

- 10 份 Top Runbook 沉淀(MongoDB / JWT / Native OOM / Trivy / 微信审核 / 订单状态 / 支付回调 / 限流 / 索引 / docker-compose)
- On-Call 主备轮值(小团队 2 人/周)
- 3 级告警 SLA(P0 / P1 / P2)
- Blameless Postmortem 模板
- 故障演练(下季度再做 1 次,本期不出)

**Non-Goals:**

- 全公司级 SRE 体系(只做小团队轻量级)
- 24/7 值班(工作时间为主,P0 升级电话兜底)
- 自动化故障修复(只做到 SOP + 脚本,不做到 auto-heal)
- 商业告警 SaaS 对接(PagerDuty 等,本项目用现成飞书/钉钉 webhook)
- Chaos Engineering 自动化(Phase 3 #6 任务,本期手动演练也延后,跟 Phase 3 合并)

## Decisions

### 1. Runbook 格式与目录

```
docs/
├── runbooks/
│   ├── README.md                     # 索引 + 编写规范
│   ├── 01-mongo-pool-exhausted.md
│   ├── 02-jwt-secret-invalid.md
│   ├── 03-native-oom.md
│   ├── 04-trivy-cve-emergency.md
│   ├── 05-miniapp-review-rejected.md
│   ├── 06-order-state-inconsistent.md
│   ├── 07-payment-callback-lost.md
│   ├── 08-rate-limit-false-positive.md
│   ├── 09-missing-index-slow-query.md
│   └── 10-docker-compose-startup-order.md
├── postmortems/
│   ├── _template.md                  # Blameless 模板
│   └── <YYYY-MM-DD>-<incident-name>.md
└── oncall/
    ├── schedule.md                   # 排班表(月度)
    └── phone-tree.md                 # 升级电话树
```

每份 Runbook 固定 6 节:

```markdown
# Runbook: <症状>

## 症状
- 告警名称 / 用户反馈 / 监控曲线特征
- 常见误报场景

## 影响范围
- 影响哪些接口 / 用户 / 业务
- P0 / P1 / P2 等级评估

## 排查步骤
1. 步骤一(可直接复制运行的命令)
2. 步骤二
3. ...

## 修复 SOP
- 标准操作流程
- 回滚命令
- 验证恢复的检查清单

## 升级路径
- 30 分钟未解决 → 升级到谁
- Slack 频道 / 联系电话
- 经理 / 总监联系

## 事后
- Postmortem 模板链接
- 对应监控 / 告警 / 文档 review
```

### 2. On-Cell 排班:小团队主备制

- 2 人/周(主 + 备),覆盖工作日 09:00-21:00 + 周末(电话升级)
- 月度排班表 `docs/oncall/schedule.md`,每周一 09:00 切换
- 飞书机器人查询:

```bash
# docs/oncall/alert-bot.md 描述命令
/oncall 当前主:    # 返回本周主 + 备
/oncall 升级电话:  # 返回 phone-tree
```

### 3. 告警分级与 SLA

| 级别 | 定义 | 首次响应 | 解决 SLA | 通道 |
|---|---|---|---|---|
| **P0** | 服务不可用 / 数据丢失 | 5 分钟 | 1 小时 | 飞书 @ 主+备 + 电话升级 |
| **P1** | 功能降级 / 部分用户受影响 | 30 分钟 | 4 小时 | 飞书 @ 主+备 |
| **P2** | 体验问题 / 性能下降 | 工作时间 | 下一个工作日 | 飞书工作群 |

告警源 → 分级映射:

| 告警源 | 触发条件 | 级别 |
|---|---|---|
| Sentry | 后端 5xx 错误率 > 1% | P0 |
| Sentry | 后端 5xx 错误率 0.1-1% | P1 |
| Prometheus | P99 延迟 > 1s | P1 |
| Prometheus | P99 延迟 500ms-1s | P2 |
| Sentry | 小程序白屏率 > 0.5% | P0 |
| Trivy | 严重 CVE 命中 | P0(夜间 → 飞书工作群) |

### 4. Postmortem 模板(Blameless)

```markdown
# Postmortem: <事故名>(YYYY-MM-DD)

## 摘要
- 一句话描述事故
- 影响范围(用户数 / 订单数 / 持续时间)
- 级别(P0/P1/P2)

## 时间线
- HH:MM  监控告警触发 / 用户首次反馈
- HH:MM  oncall 主响应
- HH:MM  定位到根因
- HH:MM  临时缓解(降级 / 回滚)
- HH:MM  永久修复
- HH:MM  服务完全恢复

## 根因分析
- 直接原因(技术)
- 触发原因(什么改动 / 流量 / 操作)
- 系统性原因(为什么这个改动没被 catch)

## 影响
- 多少用户 / 多少订单 / 多少损失
- 公开 / 内部

## 改进项(Action Items)
| # | 改进 | Owner | 截止 | 状态 |
|---|---|---|---|---|
| 1 | 补充监控 | xx | YYYY-MM-DD | TODO |
| 2 | 加强测试 | xx | YYYY-MM-DD | TODO |
| 3 | 更新 Runbook | xx | YYYY-MM-DD | TODO |

## 复盘会议
- 时间 / 参与人
- 关键讨论

## 签字
- oncall 主 / 备 / 经理 / 总监
```

**Blameless 文化**:不追责个人,只追系统性问题。**为什么 5 Whys 失败?**(因为追究"哪个工程师没改对"= 找替罪羊)。**为什么没有 catch?**(因为系统没设防 = 系统问题)。

### 5. 故障演练剧本(下季度再做)

**目标**(下季度):验证 On-Call 唤醒 → Runbook 翻阅 → 缓解 → 恢复全流程

**本期不做演练**,理由:
- 团队新接 On-Call 制度,需要 1-2 周适应期,先跑流程不演练
- 演练需要多人配合(主 + 备 + 观察员),本期时间窗口紧
- 演练发现的 Runbook 缺陷,留到下季度一并修复

**剧本草案**(下季度执行):
- `docker compose stop mongodb`(30 秒)→ backend 健康检查失败 → Prometheus 告警触发 → 飞书机器人 @ 主+备 → 主查 Runbook #01(mongo)→ 启动 mongodb → 验证恢复

**检查清单**(下季度演练时用):
- [ ] 告警是否在 5 分钟内发出?
- [ ] 主 + 备是否收到飞书通知?
- [ ] Runbook #01 路径是否清晰可查?
- [ ] 启动 mongodb 命令是否一键执行?
- [ ] 恢复后告警是否消失?
- [ ] Postmortem 模板是否立即可用?

## Risks / Trade-offs

| 风险 | 严重度 | 缓解 |
|---|---|---|
| 形式主义(Runbook 写了不更新) | 高 | 故障后强制 review 对应 Runbook,否则下次同样翻车;季度过期检查 |
| On-Call 疲劳(小团队轮值过频) | 中 | 月度回顾,平衡值班频次;必要时扩招或合并角色 |
| Blameless 文化难落地 | 中 | 季度 GM 强调;Postmortem 模板强制"系统性原因"节 |
| 知识沉淀衰减 | 中 | 季度 Runbook 过期检查(> 6 个月没更新的强制 review) |
| 演练中断生产 | 中 | 演练用 staging 环境(本期 docker-compose 本地起),不在生产做 |
| 排班冲突(请假 / 调休) | 中 | phone-tree 升级兜底,主备 1 人都不可达时由经理接管 |

## Open Questions

1. **On-Call 补贴**:是否给值班人员发值班费 / 调休? — 后续 HR 决策,本期不涉及
2. **Runbook 工具**:是否用专用工具(Confluence / Notion)还是 git 仓库? — 本期 git(`docs/runbooks/`),后续可迁
3. **Postmortem 公开**:内部 wiki 公开还是只核心团队看? — 本期内部公开(Blameless 文化要求透明)
4. **演练频率**:每月 / 每季度 / 每次发版前? — 本期不演练,下季度先做 1 次验证流程;后续频率由 oncall 决定
5. **告警去重**:Prometheus 5 分钟内重复触发同一告警,如何避免骚扰? — AlertManager `repeat_interval: 4h` 默认 4 小时不重复,本期用默认
6. **告警通道降级**:飞书挂了怎么办? — phone-tree 电话兜底;后续可加短信 / 邮件降级
