# Spec: visual-verification

## Purpose

mp 视觉验证能力(C5)——以 Open Design(OD)mockup 为真值,对微信小程序各屏做几何主门 + 感知辅助的渲染验证,取代原静态源文件 grep,形成 RED→GREEN 的逐屏修复闭环。详见归档 change `sprint-5-c5-visual-verification` 与 runbook `c5-visual-test-runbook`。

## Requirements

### Requirement: 以 OD mockup 为真值生成可提交的 golden

视觉验证 SHALL 以 Open Design 项目的 mp HTML mockup 为 OD 真值,通过脚本渲染至固定 mp viewport 产出可提交进仓的 golden 产物(每屏一张参照图 + 一份关键区域几何),使验证不依赖测试期连 OD MCP。

#### Scenario: golden 生成脚本产出参照与几何

- **WHEN** 运行 golden 生成脚本(连 OD MCP + Playwright 可用时)
- **THEN** 对每个纳入的 mp 屏产出 `od-golden/<screen>.png`(固定 viewport/DPR 渲染)与 `od-geometry/<screen>.json`(关键区域比例化 bbox),并提交进仓

#### Scenario: 验证不依赖测试期 OD MCP

- **WHEN** 验证测试运行
- **THEN** 只读取已提交的 golden + geometry,不要求测试期连 OD MCP / Open Design 守护进程

### Requirement: 几何层主门抓布局不可用

视觉验证 MUST 提供几何主门:量取 mp 运行时关键区域的 element rect,与 OD geometry 比对(比例化 + 容差);布局偏离(位置/尺寸/重叠/溢出)超容差时 MUST 失败。几何比对对字体抗锯齿/DPR 免疫。

#### Scenario: 关键区域几何符合 OD 时通过

- **WHEN** mp 某屏关键区域(如顶部栏/banner/网格/tabbar)的比例化 bbox 落在 OD geometry 容差内
- **THEN** 几何层通过

#### Scenario: 布局崩/不可用时几何失败

- **WHEN** mp 某屏关键区域位置或尺寸严重偏离 OD(重叠/错位/尺寸错)
- **THEN** 几何层失败,并指出偏离的区域与实际 vs 期望 bbox

### Requirement: 感知层比对实截图与 OD golden

视觉验证 SHALL 提供感知层:用感知 diff(AA-tolerant)比对 mp 实截图与 OD golden,带容差与动态文本区 masking;外观偏离超阈值时 MUST 失败。若跨引擎噪声盖过信号,感知层 MAY 降级为辅助(几何层仍为主门),但 MUST NOT 静默关闭。

#### Scenario: 外观符合 OD 时感知层通过

- **WHEN** mp 实截图与 OD golden 的感知差异(忽略 masked 动态区)低于阈值
- **THEN** 感知层通过

#### Scenario: 外观明显偏离时感知层失败

- **WHEN** 非 masked 区域的感知差异超阈值
- **THEN** 感知层失败,产出 diff 图定位偏离区域

### Requirement: TDD 修复闭环取代静态 grep 验证

视觉验证 SHALL 支持 TDD 闭环(现状偏离屏 RED → 修复 → GREEN → golden 防偏),并取代原 `mp-od-design.test.ts` 的静态源文件 grep;项目视觉验证文档 SHALL 同步更新为几何+感知主门。

#### Scenario: 现状偏离屏初始为 RED

- **WHEN** 某 mp 屏当前实现与 OD 有较大偏差,运行视觉验证
- **THEN** 该屏几何/感知层失败(RED),失败信息可定位偏离点以驱动修复

#### Scenario: 旧静态 grep 验证被取代

- **WHEN** 视觉验证工具落地
- **THEN** `mp-od-design.test.ts` 静态 grep 被移除/取代,CLAUDE.md 视觉验证描述更新为几何+感知主门(token/结构降为辅助层)

### Requirement: 多状态页面按状态而非按路由生成 golden

当同一 mp 页面(同一路由)存在多个用户可达且视觉上有意义的独立状态时,视觉验证 SHALL 为每个状态单独生成一份 golden(与适用时的几何产物),而非仅覆盖该路由的默认渲染态。此前纳入的 9 屏均为单状态页,未曾需要这条约定;登录页(Step1 微信登录引导 / Step2 手机号绑定引导)是第一个需要它的场景。

#### Scenario: 页面状态机存在多个独立态

- **WHEN** 某 mp 页面的状态机存在多个用户可达且视觉上有意义的独立态(如登录页 `data.step` 的 1/2 两值)
- **THEN** 每个态各产出一份按状态命名的 `od-golden/<screen>-<state>.png`,验证脚本对每个态分别执行感知 diff 断言
