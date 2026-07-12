# mp UI 微图标 emoji → van-icon 替换 设计

## Context

对 `frontend/` 全量做了一次 emoji-as-icon 扫描(4-byte UTF-8 astral-plane 字节级扫描 + 常见 dingbat 符号定向搜索,排除 `node_modules`/`e2e`/测试快照),确认现状用纯文本 emoji 字符充当图标的位置分三类,彼此技术方案不同:

1. **UI 微图标**(搜索/定位/勾选/警告/编辑/删除/空状态)—— 分布在首页/分类/购物车/订单确认/订单列表/地址/收藏/登录 共 9 个文件,纯前端静态标记问题
2. **分类导航缩略图**(首页 + 分类页的 4 个分类圆形导航,当前用 🦐/🐟/🐚/🦑 文本)—— OD 设计里这些根本不是"图标",而是真实海鲜实拍照片(`assets/icons/category/cat-*.jpg`),且 OD 是 5 个分类、mp 现状是 4 个,wxml 注释里已经标了"先修 4→5 类目数据 bug"这个待处理的分类数量不一致问题
3. **首页 Banner 的 `emoji` 字段**—— 后端 `Banner` 聚合根的必填领域字段(`@NotBlank`,贯穿 domain/application/infra/api 四层 + admin-ui 表单),不是纯前端图标问题,改动会牵涉后端 schema + 管理后台表单

用户确认本次**只做第 1 类(UI 微图标)**,第 2/3 类各自的改动性质和影响面都不同,留作后续独立 change。

**范围修正**:写实现计划时逐文件全文重读,发现最初的定向扫描漏了几个不属于 emoji/常见 dingbat 词表、但同属"纯 Unicode 字符充当图标"这一类问题的字符:`‹`(返回箭头,`order-confirm.wxml`/`address-list.wxml`/`favorites-list.wxml` 三处标题栏的返回按钮)、`⌕`(`order-list.wxml` 顶部搜索图标)、`⌂`(`order-list.wxml` 商家行的店铺符号)、`♥`(`favorites-list.wxml` 收藏格子上的实心取消收藏按钮)。这 4 类全部落在本次已经要改的 4 个文件内(`order-confirm`/`order-list`/`address-list`/`favorites-list`),不新增文件,并入本次范围一起做,已补进下面的 D3 语义映射表和改动文件清单。

## Goals / Non-Goals

**Goals:**
- 上述 9 个文件里所有充当"图标"用途的 emoji/dingbat 字符,替换成基于 `@vant/weapp`(本仓已有依赖,`profile.wxml` 已在用 `van-icon`)的图标字体渲染
- 顺手修复一个直接关联的历史死代码:`shared-empty` 在 index/category/cart/order-list 四个页面实际注册的是 `@vant/weapp/empty/index`(真正的 vant 组件),但调用方一直在传一个该组件根本不存在的 `icon` prop(⚠️/🦐/🛒/📦)—— 这些 prop 从未生效过,借这次机会用 `van-empty` 的具名 slot 把它们变成真正渲染的图标,而不是留着这段死代码或者干脆删掉了事
- `src/shared/components/Empty/` 缺失 `index.json`(WeChat 自定义组件必须有 `{"component": true}` 才能被框架识别为 Component 而非 Page)—— 这次一并补上,顺便注册 `van-icon`

**Non-Goals:**
- 分类导航缩略图(emoji → 真实照片)—— 涉及分类数量 4→5 的既有 bug,留后续 change
- Banner `emoji` 领域字段 —— 后端 domain 变更 + admin-ui 表单改动,跨栈跨模块,留后续 change
- 不引入新的图标资源文件/图标字体生成工具链 —— 全部复用 `@vant/weapp` 已有的图标字体

## Decisions

### D1: 技术方案 —— 复用 `@vant/weapp` 的 `van-icon`,不新建资源

对比过三个方案:(a) `van-icon` 复用现有依赖,(b) 从 OD mockup 导出真实 SVG 为本地 PNG/SVG 资源,(c) 用 OD 图形自建图标字体。选 (a):零新增资源文件、零新增依赖、和 `profile.wxml` 已有的 `van-icon` 用法保持一致,`search`/`location-o`/`bell`/`success`/`edit`/`delete-o`/`warning-o`/`cart-o`/`orders-o`/`like-o`/`arrow-left`/`shop-o`/`like` 这 12 个图标名已在本仓安装的 `@vant/weapp@1.11.7` 图标字体(`node_modules/@vant/weapp/lib/icon/index.wxss`)里逐一核实存在。方案 (b)/(c) 能做到像素级复刻 OD 手绘图形,但要么需要新建"小尺寸 UI 图标 PNG"资源流水线(本仓目前只有 tabbar 那种大图标 PNG 先例,没有小图标先例),要么需要图标字体生成工具链,对这几个通用图形(搜索/定位/勾选/警告/爱心)收益有限,拒绝。

### D2: 空状态图标 —— 不用 `van-empty` 内置预设字符串,改用具名 slot 塞 `van-icon`

`van-empty` 自带的 `image` 预设值(`error`/`search`/`default`/`network`)在源码(`node_modules/@vant/weapp/lib/empty/index.wxs`)里实际解析成 `https://img.yzcdn.cn/vant/empty-image-*.png` —— 一个外部有赞 CDN 域名。生产环境小程序要求所有 `<image>` 加载的域名必须在微信公众平台后台的"download 合法域名"白名单里,否则图片静默不显示。为避免引入这个不受控的外部网络依赖,空状态一律不使用这 4 个预设字符串,改用 `van-empty` 已支持的具名 slot(`<van-icon slot="image" name="..." />`),图标来源仍是本地图标字体,不发任何网络请求。**必须显式传 `image=""`**——`van-empty` 组件的 `image` prop 默认值是字符串 `'default'`(非空、真值),不显式覆盖的话,组件自带的 `<image wx:if="{{image}}">` 分支依然会渲染,和 slot 里的 `van-icon` 同时出现两个图标叠在一起,且那个默认分支本身还是会去请求外部 CDN——`image=""` 是让它连同这条网络请求一起关掉的必需条件,不是可选项。

### D3: 图标语义映射 —— 复用小词表,不为每个空状态场景造一个新名字

| 场景 | van-icon name |
|---|---|
| 搜索框图标 | `search` |
| 定位图标 | `location-o` |
| 通知铃铛 | `bell` |
| 勾选(购物车复选框/收货方式单选/地址单选/登录 Step2 成功提示) | `success` |
| 编辑(地址卡片) | `edit` |
| 删除(地址卡片) | `delete-o` |
| 错误态(加载失败) | `warning-o` |
| 无结果/筛选后为空/通用商品空态 | `search`(和搜索图标共用同一个名字,语义都是"没找到") |
| 空购物车 | `cart-o` |
| 空订单列表 | `orders-o` |
| 空收藏列表 | `like-o` |
| 空地址列表 | `location-o`(和定位图标共用同一个名字) |
| 返回箭头(标题栏返回按钮) | `arrow-left` |
| 搜索(订单列表顶部,原 `⌕`) | `search`(和搜索图标共用同一个名字) |
| 店铺符号(订单卡片商家行,原 `⌂`) | `shop-o` |
| 取消收藏(收藏格子实心爱心按钮,原 `♥`) | `like`(实心变体,和空态用的 `like-o` 描边变体区分"已收藏可点击移除" vs "尚未收藏的空状态提示") |

12 个不重复的图标名覆盖 15 处替换点,刻意压缩词表而不是每个场景发明一个专属图标名,降低认知负担。

### D4: 测试方式 —— 沿用本仓已有的 WXML 源码文本断言风格(参考 `login-flow.test.ts`)

每个改动文件写:(a) 一条"不再包含指定 emoji 字符"的断言,(b) 一条"包含指定 `van-icon name="..."` "的断言。TDD 顺序:先写断言、跑 RED(emoji 还在源码里 / van-icon 还没出现,确认测试真的在测这件事而不是碰巧通过)、再改 wxml、跑 GREEN。`shared-empty` 死 prop 场景额外加一条断言,确认旧的 `icon="..."` 属性已被删除(证明是替换掉死代码,不是新旧并存)。

不用真正执行 WXML 渲染做视觉断言(本仓约定的方式,`login-flow.test.ts` 已验证过这类源码文本匹配足以覆盖"该出现的标记出现了/该删除的字符删除了"这类问题)。

### D5: 视觉风险 —— 圆形背景图标可能和既有手绘 CSS 圆圈重叠,留到实现阶段用已有 C5 流程验证

`success` 图标本身自带一个填充圆形背景,而购物车复选框/收货方式单选/地址单选这几处的容器本身已经是手绘的 CSS 圆圈(`hover-class`/`border-radius` 实现)。两者叠加是否会出现"双层圆圈"视觉问题,仅看源码无法判断,只能实机渲染后看。计划里会加一步用本仓已有的 C5 感知 diff(`npm run test:visual`)或几何验证流程截图检查;如果确实叠加,退化方案是给 `van-icon` 加 `custom-class` 裁掉图标自带的圆形背景,或换成不带圆形背景的图标名(比如某些图标字体会同时提供 filled 和 outline 两个变体)。

## 改动文件清单

**微图标替换(6 类语义,分布见下)**

| 文件 | 行(约) | 改动 |
|---|---|---|
| `pages/index/index.wxml` | 17, 26, 30 | 📍→`location-o`,🔔→`bell`,🔍→`search` |
| `pages/category/category.wxml` | 22 | 🔍→`search` |
| `pages/cart/cart.wxml` | 48, 57, 79 | 📍→`location-o`(需拆出独立 icon 节点,原来和文字拼在同一个 `<text>` 里),✓→`success`(×2) |
| `pages-sub/order/order-confirm/order-confirm.wxml` | 26, 53, 89, 100, 111 | ‹→`arrow-left`,📍→`location-o`(同上需拆分),✓→`success`(×3) |
| `pages-sub/order/order-list/order-list.wxml` | 18, 77 | ⌕→`search`,⌂→`shop-o` |
| `pages-sub/user/address/address-list.wxml` | 33, 82, 92, 98 | ‹→`arrow-left`,✏️→`edit`,🗑️→`delete-o`,✓→`success` |
| `pages-sub/user/favorites/favorites-list.wxml` | 4, 32 | ‹→`arrow-left`,♥→`like` |
| `pages-sub/user/login/login.wxml` | 47 | ✓→`success` |

**空状态图标(死 prop 修复 + 真实渲染替换)**

| 文件 | 行(约) | 改动 |
|---|---|---|
| `pages/index/index.wxml` | 146, 156 | `shared-empty icon="⚠️"` → slot `warning-o`;`icon="🦐"` → slot `search` |
| `pages/category/category.wxml` | 58 | `shared-empty icon="⚠️"` → slot `warning-o` |
| `pages/cart/cart.wxml` | 28 | `shared-empty icon="🛒"` → slot `cart-o` |
| `pages-sub/order/order-list/order-list.wxml` | 46, 56 | `shared-empty icon="⚠️"` → slot `warning-o`;`icon="📦"` → slot `orders-o` |
| `pages-sub/user/favorites/favorites-list.wxml` | 38 | 🤍(直接文本节点,非 `shared-empty`)→ 直接换成 `<van-icon name="like-o">` |
| `pages-sub/user/address/address-list.wxml` | 105 | 📭(同上,直接文本节点)→ `<van-icon name="location-o">` |
| `src/shared/components/Empty/index.wxml` | 3 | 硬编码 📭 → `<van-icon name="search">` |

**配套改动**
- 上述涉及的每个页面/组件 `.json` 补 `"van-icon": "@vant/weapp/icon/index"` 到 `usingComponents`(多数页面目前完全没有这条注册)
- 新建 `src/shared/components/Empty/index.json`(`{"component": true, "usingComponents": {"van-icon": "@vant/weapp/icon/index"}}`),该组件此前完全没有 json 配置文件

## Risks / Trade-offs

- [Risk] `success` 等自带圆形背景的图标可能和既有手绘 CSS 圆圈叠成"双层圆圈"(见 D5)→ Mitigation:实现阶段用已有 C5 视觉验证流程截图核实,发现问题就加 `custom-class` 裁剪或换图标名
- [Risk] vant 图标字体的视觉风格是 vant 自己的线性图标语言,不是 OD 手绘图形的像素级复刻 → Mitigation:用户已确认接受这个取舍(方案 A),两者同属"简洁线性单色图标"这一类,满足"不用 emoji、符合 OD 设计精神"的核心诉求

## Migration Plan

无数据迁移、无 API 契约变更 —— 纯前端 WXML/JSON 静态标记改动,不涉及后端、不涉及现有用户数据。
