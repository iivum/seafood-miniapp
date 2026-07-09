# shared-empty 死代码修复(message/retry-text/bind:retry 从未生效)设计

## Context

`shared-empty` 目前在不同页面的 `usingComponents` 里解析成两个不同的组件:

- `src/features/product/components/ProductCard`/`ProductList` 指向本地手写组件 `src/shared/components/Empty`(`properties: { message, retryable }`,真正支持文案 + 重试)。
- `pages/index`、`pages/category`、`pages/cart`、`pages-sub/order/order-list` 这 4 个页面指向 `@vant/weapp/empty/index`(vant 官方组件,`properties` 只有 `description`/`image` 两个,没有 `message`/`retry-text`,也没有 `retry` 事件)。

这 4 个页面的 7 处 `<shared-empty>` 调用一直在传 `message="..."`、`retry-text="重新加载"`(或"去逛逛"/"查看全部")、`bind:retry="onXXX"`——因为背后组件从来没支持过这些 prop/事件,这些属性从创建以来就是死代码:错误态/空态**没有文案、没有可用的重试按钮**,用户看到的只有一个孤零零的图标。

这个问题是在 `mp-icon-emoji-replacement`(PR #48,把这 4 页 `shared-empty` 上死掉的 `icon="⚠️"` 之类的 emoji prop 换成 `van-icon` 具名 slot)的最终 review 里发现的——当时判断"不是这次改动引入的回归",记录为后续 change,现在就是这个 change。**这次会直接改动 PR #48 里刚碰过的那几行**:PR #48 用 `image="" + <van-icon slot="image">` 这种 vant 特有的绕过写法让图标先能显示出来;这次把 `shared-empty` 指回真正支持这些 prop 的本地组件,把 `message`/`retry-text`/`bind:retry` 一并修好,PR #48 那段绕过写法会被替换掉。

## Goals / Non-Goals

**Goals:**
- 4 个页面(index/category/cart/order-list)共 7 处 `<shared-empty>` 调用的 `message`/`retry-text`/`bind:retry` 全部变成真正生效
- 全仓库统一成一个 `shared-empty` 实现(本地 `src/shared/components/Empty`),消除"同名组件在不同页面解析成两个不同东西"的困惑
- `ProductCard`/`ProductList` 现有用法(只传 `message`/`retryable`/`bind:retry`,不传新增的 `icon`/`retryText`)行为不变,零改动

**Non-Goals:**
- 不改 `@vant/weapp` 依赖本身,不 fork/patch vant 的 `van-empty` 组件
- 不新增视觉验证(这次是纯功能修复——文案/按钮从"不出现"变成"出现",不是图标视觉微调,不需要 C5 感知 diff)
- 不处理"分类导航缩略图"/Banner emoji 字段——和上次一样,继续不在范围内

## Decisions

### D1: 本地 Empty 组件扩展两个可选 prop(`icon`/`retryText`),向后兼容

`src/shared/components/Empty` 目前:`properties: { message: String, retryable: Boolean }`,wxml 硬编码图标(`<van-icon name="search">`,PR #48 刚从 emoji 改过来的)和按钮文案(`重试`)。新增:

- `icon: { type: String, value: 'search' }` —— 传给内部 `<van-icon name="{{icon}}">`,替代硬编码
- `retryText: { type: String, value: '重试' }` —— 渲染到按钮文案,替代硬编码

两者都给了和现状(硬编码值)完全一致的默认值,所以 `ProductCard`/`ProductList` 现有调用(不传这两个新 prop)行为分毫不差,零改动、零回归风险。

### D2: 4 个页面的 `shared-empty` 改指向本地组件,调用点去掉 vant 专用绕过写法

`.json` 改动(4 个文件):
```json
"shared-empty": "@vant/weapp/empty/index"
```
改成:
```json
"shared-empty": "/src/shared/components/Empty/index"
```
(和 `ProductCard`/`ProductList` 已有写法完全一致)

wxml 改动(7 处调用),以 index.wxml 错误态为例,PR #48 现状:
```wxml
<shared-empty
  image=""
  message="{{errorMessage || '加载失败'}}"
  retry-text="重新加载"
  bind:retry="onRetry"
>
  <van-icon slot="image" name="warning-o" size="48px" />
</shared-empty>
```
改成:
```wxml
<shared-empty
  icon="warning-o"
  message="{{errorMessage || '加载失败'}}"
  retry-text="重新加载"
  bind:retry="onRetry"
></shared-empty>
```
去掉 `image=""`(vant 特有,本地组件不需要)和具名 slot 子节点(本地组件内部用 `icon` prop 直接渲染,不需要调用方塞 slot),`message`/`retry-text`/`bind:retry` 这三个属性名本来就是对的,原样保留——它们从一开始就该这么写,只是背后组件一直不支持。

页面的 `van-icon` 注册(Task 1-5 加的)继续保留——同一页面上其他直接使用 `<van-icon>` 的地方(搜索/定位/勾选等)还需要它,不能删。

### D3: category 页空状态维持无重试按钮

`category.wxml` 的空状态(该分类暂无商品)原本就没传 `retry-text`/`bind:retry`,只传 `message`。`retryable` 走本地组件默认值 `false`,继续不显示重试按钮——和现状(视觉上)一致,不需要特殊处理,只是从"消息和按钮都不渲染"变成"消息渲染、按钮本来就没打算给"。

### D4: 测试

- 组件级(扩展 PR #48 新建的 `src/shared/components/Empty/index.test.ts`):断言 `icon`/`retryText` 两个新 prop 存在且默认值正确,wxml 里 `name="{{icon}}"`/`{{retryText}}` 绑定存在
- 页面级(扩展 PR #48 新建的 4 个 `icon-emoji-*.test.ts`):原有"`image=""` + `van-icon slot="image"`"断言改成"`icon="..."` 属性值断言";新增一条".json 的 shared-empty 指向本地组件路径"的断言(取代原来"指向 vant 组件"的隐含假设)
- 不新增行为层(Page 实例)测试——这次改动纯粹是模板/组件 prop 层面,现有的源码文本断言风格(这仓库贯穿始终的约定)足够覆盖

## 改动文件清单

| 文件 | 改动 |
|---|---|
| `src/shared/components/Empty/index.js` | 新增 `icon`/`retryText` 两个 properties |
| `src/shared/components/Empty/index.ts` | `EmptyProps` interface 加 `icon?`/`retryText?` |
| `src/shared/components/Empty/index.wxml` | `name="search"` → `name="{{icon}}"`,`重试` → `{{retryText}}` |
| `src/shared/components/Empty/index.test.ts` | 新增 2 个 prop 的断言 |
| `pages/index/index.json` + `.wxml` | shared-empty 指向本地组件;2 处 shared-empty 调用去 slot 化 |
| `pages/category/category.json` + `.wxml` | 同上;2 处调用(1 处有 retry,1 处无) |
| `pages/cart/cart.json` + `.wxml` | 同上;1 处调用 |
| `pages-sub/order/order-list/order-list.json` + `.wxml` | 同上;2 处调用 |
| `src/__tests__/icon-emoji-index.test.ts` | 更新 shared-empty 相关断言 |
| `src/__tests__/icon-emoji-category.test.ts` | 同上 |
| `src/__tests__/icon-emoji-cart.test.ts` | 同上 |
| `src/__tests__/icon-emoji-order-list.test.ts` | 同上 |

## Risks / Trade-offs

- [Risk] 4 个页面现在从"完全不渲染文案/按钮"变成"渲染文案/按钮",视觉上是新增内容,理论上可能挤占空间导致轻微布局变化 → Mitigation:这些内容本来就该在那里(是 bug 修复不是新功能),且本地组件的 `.empty`/`.empty__message`/`.empty__retry` 这套 wxss 早已存在(`ProductCard`/`ProductList` 场景一直在用),不是没验证过的新样式
- [Risk] 本地组件目前的 wxss 没有专门测过 `retryText` 更长文案("重新加载"/"去逛逛"/"查看全部" 都比默认的"重试"长)是否会撑破按钮 → Mitigation:纯 CSS 风险,不阻塞功能正确性,后续如需要可以单独微调,不在这次改动范围内深究

## Migration Plan

无数据迁移、无 API 契约变更——纯前端 WXML/JSON/JS 组件属性改动。这次改动会修改 PR #48(尚未合并)里刚提交的部分代码行,基于 PR #48 分支继续开发,不是基于 main。
