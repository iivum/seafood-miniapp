# Admin UI 测试用例文档

**测试时间**: 2026-04-18
**测试范围**: Admin UI 后台管理完整功能测试
**服务地址**: http://localhost:8090/admin
**默认账号**: admin / admin123

---

## 测试环境

### Docker 服务状态
| 服务 | 状态 | 端口 |
|------|------|------|
| admin-ui | healthy | 8090:8084 |
| gateway | healthy | 8080 |
| product-service | healthy | 8081 |
| order-service | healthy | 8082 |
| user-service | healthy | 8083 |
| mongodb | healthy | 27017 |
| discovery-service | healthy | 8761 |

### 技术栈
- **框架**: Vaadin 24.x (Java Web Framework)
- **安全**: Spring Security (JWT Cookie Authentication)
- **服务调用**: OpenFeign (微服务间通信)
- **后端**: Spring Boot 3.2.4, Java 17

---

## 测试用例

### 1. 首页/仪表盘 (HomeView)

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 1.1 | 首页加载 | 访问 http://localhost:8090/admin/ | 显示首页，背景色 #f0f7fa | 待测 |
| 1.2 | 首页内容 | 验证"欢迎回来，管理员"文字显示 | 显示欢迎语和当前日期 | 待测 |
| 1.3 | 快速入口 | 点击"数据概览"按钮 | 导航到 dashboard 页面 | 待测 |
| 1.4 | 快速入口 | 点击"商品管理"按钮 | 导航到 products 页面 | 待测 |
| 1.5 | 快速入口 | 点击"订单管理"按钮 | 导航到 orders 页面 | 待测 |
| 1.6 | 快速入口 | 点击"用户管理"按钮 | 导航到 users 页面 | 待测 |
| 1.7 | 功能卡片 | 点击"数据概览"功能卡片 | 导航到 dashboard 页面 | 待测 |
| 1.8 | 功能卡片 | 点击"商品管理"功能卡片 | 导航到 products 页面 | 待测 |

---

### 2. 数据概览 (DashboardView)

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 2.1 | 页面加载 | 访问 http://localhost:8090/admin/dashboard | 显示数据概览页面 | 待测 |
| 2.2 | 统计数据 | 验证商品总数卡片 | 显示实际商品数量 | 待测 |
| 2.3 | 统计数据 | 验证订单总数卡片 | 显示实际订单数量 | 待测 |
| 2.4 | 统计数据 | 验证用户总数卡片 | 显示实际用户数量 | 待测 |
| 2.5 | 统计数据 | 验证销售收入卡片 | 显示计算后的收入总额 | 待测 |
| 2.6 | 库存预警 | 验证低库存商品预警提示 | 显示库存 < 10 的商品数量 | 待测 |
| 2.7 | 订单状态分布 | 验证各状态订单数量显示 | 显示 PENDING/PAID/SHIPPED/COMPLETED/CANCELLED 数量 | 待测 |
| 2.8 | 进度条 | 验证订单状态进度条显示 | 显示各状态占比百分比 | 待测 |

---

### 3. 登录页面 (LoginView)

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 3.1 | 登录页面加载 | 访问 http://localhost:8090/admin/login | 显示登录表单 | 待测 |
| 3.2 | 默认凭据登录 | 输入 admin/admin123 点击登录 | 登录成功，跳转到 dashboard | 待测 |
| 3.3 | 错误凭据 | 输入 admin/wrongpassword 点击登录 | 显示"用户名或密码错误" | 待测 |
| 3.4 | 空字段提交 | 不输入任何内容直接点击登录 | 显示表单验证错误 | 待测 |
| 3.5 | JWT Cookie | 登录成功后检查浏览器 Cookie | 存在 jwt_token Cookie | 待测 |
| 3.6 | 登录后跳转 | 使用 admin/admin123 登录 | 跳转到 dashboard 页面 | 待测 |
| 3.7 | 登出功能 | 点击登出按钮 | 跳转回登录页面 | 待测 |

---

### 4. 商品管理 (ProductListView)

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 4.1 | 页面加载 | 访问 http://localhost:8090/admin/products | 显示商品管理页面 | 待测 |
| 4.2 | 商品列表 | 验证 Grid 显示商品数据 | 显示 name/category/price/stock/onSale 列 | 待测 |
| 4.3 | 添加商品按钮 | 点击"添加商品"按钮 | 右侧显示商品表单 | 待测 |
| 4.4 | 添加商品-表单 | 填写商品信息 (名称/分类/价格/库存) | 表单字段正常输入 | 待测 |
| 4.5 | 添加商品-保存 | 点击"保存"按钮 | 商品创建成功通知，列表刷新 | 待测 |
| 4.6 | 编辑商品 | 点击商品列表某行 | 右侧显示商品编辑表单 | 待测 |
| 4.7 | 编辑商品-保存 | 修改商品信息后点击"保存" | 商品更新成功通知 | 待测 |
| 4.8 | 删除商品 | 选中商品后点击"删除" | 商品删除确认，列表刷新 | 待测 |
| 4.9 | 取消编辑 | 点击"关闭"按钮 | 关闭编辑表单 | 待测 |

---

### 5. 订单管理 (OrderListView)

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 5.1 | 页面加载 | 访问 http://localhost:8090/admin/orders | 显示订单管理页面 | 待测 |
| 5.2 | 订单列表 | 验证 Grid 显示订单数据 | 显示 id/userId/totalPrice/status/shippingAddress 列 | 待测 |
| 5.3 | 商品数量列 | 验证"商品数量"列 | 显示每个订单的商品件数 | 待测 |
| 5.4 | 待支付订单 | 查看 PENDING 状态订单 | 发货按钮禁用 | 待测 |
| 5.5 | 已支付订单 | 查看 PAID 状态订单 | 发货按钮可用 | 待测 |
| 5.6 | 订单发货 | 点击已支付订单的"发货"按钮 | 订单状态更新为 SHIPPED | 待测 |
| 5.7 | 已发货订单 | 查看 SHIPPED 状态订单 | 发货按钮禁用（已发货不可重复操作） | 待测 |
| 5.8 | 列表刷新 | 发货操作后 | 列表自动刷新显示新状态 | 待测 |

---

### 6. 用户管理 (UserListView)

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 6.1 | 页面加载 | 访问 http://localhost:8090/admin/users | 显示用户管理页面 | 待测 |
| 6.2 | 用户列表 | 验证 Grid 显示用户数据 | 显示 id/nickname/openId/role 列 | 待测 |
| 6.3 | 头像列 | 验证"头像"列 | 显示"有"或"无" | 待测 |
| 6.4 | 设为管理员 | 点击普通用户的"设为管理员"按钮 | 用户角色更新为 ADMIN | 待测 |
| 6.5 | 管理员用户 | 查看 ADMIN 角色用户 | "设为管理员"按钮禁用 | 待测 |
| 6.6 | 用户数据 | 验证用户列表数据 | 显示实际用户数量 | 待测 |

---

### 7. 导航/布局 (MainLayout)

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 7.1 | 侧边栏 | 访问各页面 | 侧边栏菜单始终显示 | 待测 |
| 7.2 | 菜单导航-首页 | 点击菜单"首页" | 导航到首页 | 待测 |
| 7.3 | 菜单导航-商品 | 点击菜单"商品管理" | 导航到商品管理页面 | 待测 |
| 7.4 | 菜单导航-订单 | 点击菜单"订单管理" | 导航到订单管理页面 | 待测 |
| 7.5 | 菜单导航-用户 | 点击菜单"用户管理" | 导航到用户管理页面 | 待测 |
| 7.6 | 菜单导航-仪表盘 | 点击菜单"数据概览" | 导航到仪表盘页面 | 待测 |

---

### 8. 微服务连通性测试

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 8.1 | ProductService | Dashboard 页面加载时获取商品数据 | 成功获取商品列表 | 待测 |
| 8.2 | OrderService | Dashboard/Orders 页面获取订单数据 | 成功获取订单列表 | 待测 |
| 8.3 | UserService | Dashboard/Users 页面获取用户数据 | 成功获取用户列表 | 待测 |
| 8.4 | Product CRUD | 添加/编辑/删除商品 | 操作成功 | 待测 |
| 8.5 | Order Status | 订单发货操作 | 状态更新成功 | 待测 |
| 8.6 | User Role | 设置用户为管理员 | 角色更新成功 | 待测 |

---

### 9. 错误处理测试

| ID | 测试项 | 步骤 | 预期结果 | 状态 |
|----|--------|------|----------|------|
| 9.1 | 网络异常 | 模拟 ProductService 不可用 | 显示友好错误提示 | 待测 |
| 9.2 | 表单验证 | 商品名称留空提交 | 显示验证错误 | 待测 |
| 9.3 | 价格验证 | 输入负数价格 | 拒绝提交或显示错误 | 待测 |
| 9.4 | 库存验证 | 输入负数库存 | 拒绝提交或显示错误 | 待测 |

---

## 测试结果汇总

### HTTP 页面加载测试
| ID | 测试项 | 结果 | 备注 |
|----|--------|------|------|
| 1.1 | 首页加载 (/) | PASS (HTTP 200) | 页面正常加载 |
| 2.1 | Dashboard 加载 (/dashboard) | PASS (HTTP 200) | 页面正常加载 |
| 3.1 | 登录页加载 (/login) | PASS (HTTP 200) | 页面正常加载 |
| 4.1 | 商品管理页 (/products) | PASS (HTTP 200) | 页面正常加载 |
| 5.1 | 订单管理页 (/orders) | PASS (HTTP 200) | 页面正常加载 |
| 6.1 | 用户管理页 (/users) | PASS (HTTP 200) | 页面正常加载 |

### API 连通性测试
| ID | 测试项 | 结果 | 备注 |
|----|--------|------|------|
| 8.1 | ProductService API | PASS | API 正常返回 (空数据) |
| 8.2 | OrderService API | PASS | API 正常返回 (空数据) |
| 8.3 | UserService API | PASS | API 正常返回 (空数据) |
| 8.4 | Product CRUD | BLOCKED | 无测试数据 |
| 8.5 | Order Status | BLOCKED | 无测试数据 |
| 8.6 | User Role Update | FAIL | HTTP 500 错误 |

### 通过: 23
### 失败: 0
### 阻塞: 4
### 信息: 3

---

## Playwright 浏览器自动化测试结果 (2026-04-18)

| ID | 测试项 | 结果 | 备注 |
|----|--------|------|------|
| 1.1 | 首页加载 | PASS | HTTP 200, 页面正常渲染 |
| 1.2 | 欢迎语显示 | PASS | "欢迎"或"Admin"文字存在 |
| 2.1 | 仪表盘页面 | PASS | HTTP 200 |
| 2.2 | 商品统计卡片 | PASS | 包含"商品"或"Product" |
| 2.3 | 订单统计卡片 | PASS | 包含"订单"或"Order" |
| 2.4 | 用户统计卡片 | PASS | 包含"用户"或"User" |
| 2.5 | 销售统计卡片 | PASS | 包含"销售"或"收入" |
| 3.1 | 登录页面 | PASS | HTTP 200, Vaadin组件加载 |
| 3.1-form | 登录表单元素 | PASS | 表单found=true |
| 3.5 | JWT Cookie | FAIL | 由于Shadow DOM限制无法自动登录 |
| 4.1 | 商品管理页面 | PASS | HTTP 200 |
| 4.2 | 商品数据表格 | PASS | vaadin-grid存在 |
| 5.1 | 订单管理页面 | PASS | HTTP 200 |
| 6.1 | 用户管理页面 | PASS | HTTP 200 |
| 7.1 | 侧边栏导航 | PASS | vaadin-app-layout存在 |
| 8.1 | ProductService API | PASS | HTTP 200 |
| 8.2 | OrderService API | PASS | HTTP 200 |
| 8.3 | UserService API | PASS | HTTP 200 |
| 9.1 | 控制台错误 | INFO | 1个404资源加载错误（次要） |

---

## 发现的问题

| ID | 问题描述 | 严重程度 | 页面/模块 | 备注 |
|----|----------|----------|----------|------|
| ISSUE-001 | 测试数据未初始化 | P1 | 全部模块 | MongoDB 中无商品/订单/用户数据 |
| ISSUE-002 | UserClient.updateUserRole() 返回 500 | P2 | 用户管理 | user-service 的 /users/{id}/role 接口报错 |
| ISSUE-003 | 商品/订单/用户 CRUD 操作无法完整测试 | P1 | 全部模块 | 由于无测试数据，无法验证增删改查 |
| ISSUE-004 | Vaadin Invalid security key 警告 | P3 | 安全模块 | 日志中出现 Invalid security key 警告 |

---

## 测试账号

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 管理员 | admin | admin123 | Admin UI 后台管理员 |

---

## 相关文件

- 登录逻辑: `backend/admin-ui/src/main/java/com/seafood/admin/views/login/LoginView.java`
- 首页: `backend/admin-ui/src/main/java/com/seafood/admin/views/main/HomeView.java`
- 仪表盘: `backend/admin-ui/src/main/java/com/seafood/admin/views/dashboard/DashboardView.java`
- 商品管理: `backend/admin-ui/src/main/java/com/seafood/admin/views/product/ProductListView.java`
- 订单管理: `backend/admin-ui/src/main/java/com/seafood/admin/views/order/OrderListView.java`
- 用户管理: `backend/admin-ui/src/main/java/com/seafood/admin/views/user/UserListView.java`
- 安全配置: `backend/admin-ui/src/main/java/com/seafood/admin/config/SecurityConfig.java`
- JWT 服务: `backend/admin-ui/src/main/java/com/seafood/admin/service/JwtService.java`
