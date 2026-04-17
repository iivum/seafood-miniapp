# 测试-修复-TDD开发循环计划

**项目**: seafood-miniapp 海鲜商城小程序  
**创建日期**: 2026-04-18  
**模式**: 计划模式（仅规划，不执行）

---

## 目标

1. 生成完整测试用例（正常场景、异常场景、边界条件、安全漏洞）
2. 执行 admin-ui 和微信小程序的测试-修复循环
3. 所有开发任务指派 Claude Code 执行
4. 使用 git worktree 并行开发（最多 3 个并行）
5. 最终合并到 main 分支
6. 准备验收文档供人工审核

---

## 并行任务分组

### 第一轮并行（3 个 worktree）

| 分支 | 负责人 | 任务范围 |
|------|--------|----------|
| `test/admin-ui-comprehensive` | Claude Code #1 | Admin UI 完整功能测试 + 问题修复 |
| `test/wechat-miniprogram-comprehensive` | Claude Code #2 | 微信小程序完整功能测试 + 问题修复 |
| `test/backend-api-validation` | Claude Code #3 | 后端 API 测试 + 数据验证 |

### 第二轮并行（按需）

| 分支 | 负责人 | 任务范围 |
|------|--------|----------|
| `fix/admin-ui-issues` | Claude Code #4 | 修复 Admin UI 测试发现的问题 |
| `fix/wechat-miniprogram-issues` | Claude Code #5 | 修复微信小程序测试发现的问题 |

---

## 测试用例生成策略

### Admin UI 测试用例

#### 1. 页面访问测试
- [ ] `curl http://localhost:8090/` → 302 重定向到 /admin/
- [ ] `curl http://localhost:8090/admin` → 不带斜杠访问
- [ ] `curl http://localhost:8090/admin/` → 正常显示
- [ ] `curl http://localhost:8090/admin/login` → 登录页
- [ ] `curl http://localhost:8090/admin/dashboard` → 仪表盘
- [ ] `curl http://localhost:8090/admin/products` → 商品管理
- [ ] `curl http://localhost:8090/admin/orders` → 订单管理
- [ ] `curl http://localhost:8090/admin/users` → 用户管理

#### 2. 功能测试
- [ ] 管理员登录：POST `/admin/login` with admin/admin123
- [ ] 商品 CRUD：创建、读取、更新、删除
- [ ] 订单 CRUD：查看、修改状态
- [ ] 用户 CURD：查看、设置管理员、取消管理员
- [ ] 404 页面：访问不存在的页面

#### 3. 异常场景
- [ ] 无效凭据登录
- [ ] 商品更新失败场景
- [ ] 订单状态非法转换
- [ ] 用户名重复

#### 4. 边界条件
- [ ] 商品价格 = 0
- [ ] 商品库存 = 0
- [ ] 订单商品数量极大
- [ ] 用户手机号格式错误
- [ ] 地址信息为空

#### 5. 安全漏洞
- [ ] 未授权访问管理接口
- [ ] SQL 注入测试
- [ ] XSS 注入测试
- [ ] 暴力破解登录（频率限制）

---

### 微信小程序测试用例

#### 1. 页面加载测试
- [ ] 首页商品列表
- [ ] 分类页面
- [ ] 购物车页面
- [ ] 个人中心页面

#### 2. 功能测试

##### 首页
- [ ] 搜索框输入和搜索
- [ ] 热门商品点击
- [ ] 分类导航点击
- [ ] 轮播图展示

##### 分类页
- [ ] 分类选择
- [ ] 分类商品展示
- [ ] 异形屏适配

##### 购物车
- [ ] 添加商品
- [ ] 删除商品
- [ ] 修改数量
- [ ] 选择/取消选择
- [ ] 价格计算
- [ ] 运费计算

##### 个人中心
- [ ] 登录状态判断
- [ ] 订单列表
- [ ] 收货地址管理

##### 登录
- [ ] 微信授权登录
- [ ] 手机号登录（当前实现）
- [ ] 登录状态持久化

#### 3. 异常场景
- [ ] 网络错误处理
- [ ] 商品售罄
- [ ] 购物车为空
- [ ] 未登录访问需要认证的页面

#### 4. 边界条件
- [ ] 商品名称超长
- [ ] 价格精度问题
- [ ] 购物车商品数量上限
- [ ] 收货地址数量上限

#### 5. 安全漏洞
- [ ] 敏感数据泄露
- [ ] 支付金额篡改
- [ ] 未授权访问他人订单

---

## Claude Code 任务分配

### Claude Code #1: Admin UI 完整测试 + 修复

```
分支: test/admin-ui-comprehensive
工作目录: /Users/iivum/.openclaw/workspace/seafood-miniapp
```

**任务**:
1. 创建 git worktree `test/admin-ui-comprehensive` 从 main
2. 生成 Admin UI 完整测试用例文档
3. 执行所有手动测试
4. 记录发现的问题到 TODO.md
5. 切换到 `fix/admin-ui-issues` 分支修复问题
6. 修复完成后提交 PR 到 main

### Claude Code #2: 微信小程序完整测试 + 修复

```
分支: test/wechat-miniprogram-comprehensive
工作目录: /Users/iivum/.openclaw/workspace/seafood-miniapp
```

**任务**:
1. 创建 git worktree `test/wechat-miniprogram-comprehensive` 从 main
2. 生成微信小程序完整测试用例文档
3. 使用 weixin-devtools-mcp 执行自动化测试
4. 记录发现的问题到 TODO.md
5. 切换到 `fix/wechat-miniprogram-issues` 分支修复问题
6. 修复完成后提交 PR 到 main

### Claude Code #3: 后端 API 验证

```
分支: test/backend-api-validation
工作目录: /Users/iivum/.openclaw/workspace/seafood-miniapp
```

**任务**:
1. 创建 git worktree `test/backend-api-validation` 从 main
2. 验证所有 API 端点
3. 测试正常、异常、边界条件
4. 验证数据一致性
5. 提交发现的问题

---

## 执行流程

```
1. [Hermes] 创建计划文档
       ↓
2. [Claude Code #1] Admin UI 测试 → 问题列表
       ↓
3. [Claude Code #2] 微信小程序测试 → 问题列表
       ↓
4. [Claude Code #3] 后端 API 验证 → 问题列表
       ↓
5. [Hermes] 合并所有问题到 TODO.md
       ↓
6. [Claude Code #4] 修复 Admin UI 问题
       ↓
7. [Claude Code #5] 修复微信小程序问题
       ↓
8. [Hermes] 验收测试
       ↓
9. [Claude Code #6] 合并所有分支到 main
       ↓
10. [Claude Code #7] 更新文档
```

---

## 已知问题清单（需修复）

### Admin UI 问题
1. admin-ui 的 url 问题: `/admin/` 正常, `/admin` 失败
2. 默认的 404 page 未设置
3. 管理后台未实现用户鉴权
4. 管理后台所有按钮文本不可见
5. 商品更新失败
6. 点击菜单后没有回到首页的方式
7. 商品列表只返回 10 item
8. 所有菜单不支持筛选、排序、搜索
9. 用户管理不能做 CURD
10. 订单管理不能做 CURD
11. 商品管理未实现图片预览和字段校验
12. 用户管理未实现头像预览和手机号展示
13. 用户管理设置/取消管理员失败
14. 订单管理中 Shipping Address、Status、Total Price 未映射为可读数据
15. 订单管理排序按钮文本变白色
16. 订单管理未实现状态筛选
17. 测试数据过少
18. 商品管理中同名不同商品无法区分

### 微信小程序问题
1. 所有页面点击后都有控制台报错
2. 首页搜索栏搜索后未正常展示商品
3. 搜索框内有内容时未正确展示
4. 点击搜索框无法正常输入
5. 点击热门商品后未正常联动
6. 轮播图没有内容
7. 搜索框不需要搜索按钮
8. 点击分类后未正常展示商品
9. 分类页面布局未适配异形屏
10. 分类页面图标未正常展示
11. 分类页面点击分类后没有商品
12. 购物车价格运费计算错误
13. 购物车选中商品后选择器状态未变化
14. 未登录不应该能管理购物车
15. 点击登录文案下不需要立即登录文案
16. 登录页面不需要手机号验证码登录
17. 需要前后端集成微信小程序登录

---

## 验收文档结构

```
ACCEPTANCE_CHECKLIST.md
├── 一、Docker 服务状态
├── 二、Admin UI 管理后台
│   ├── 2.1 页面访问
│   ├── 2.2 功能测试
│   ├── 2.3 异常场景
│   └── 2.4 安全测试
├── 三、微信小程序 API 联调
│   ├── 3.1 后端 API 测试
│   └── 3.2 前端测试
├── 四、微信小程序页面
│   ├── 4.1 页面加载
│   ├── 4.2 异形屏适配
│   └── 4.3 核心功能
├── 五、性能测试
├── 六、安全测试
└── 七、验收结论
```

---

## 风险和注意事项

1. **微信登录**: 需要真实微信 App ID 才能测试
2. **微信支付**: 需要真实商户号
3. **真机测试**: 部分功能需要真机验证
4. **Skyline 渲染**: 需手动验证
5. **并行任务数**: 最多 3 个并行，避免资源竞争
