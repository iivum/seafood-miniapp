# 海鲜商城管理后台 - Vue 3 + Element Plus

基于 Vue 3 + TypeScript + Element Plus 重构的管理后台前端项目。

## 技术栈

- **框架**: Vue 3.5.x + TypeScript
- **UI 组件**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router 4.x
- **HTTP 客户端**: Axios
- **构建工具**: Vite

## 项目结构

```
vue/
├── src/
│   ├── api/           # API 接口层
│   │   ├── axios.ts  # Axios 实例配置
│   │   ├── product.ts
│   │   ├── order.ts
│   │   ├── user.ts
│   │   └── config.ts
│   ├── components/    # 公共组件
│   │   ├── ProductForm.vue
│   │   ├── OrderDetailDialog.vue
│   │   ├── ShipOrderDialog.vue
│   │   └── ConfigForm.vue
│   ├── layouts/       # 布局组件
│   │   └── MainLayout.vue
│   ├── router/        # 路由配置
│   │   └── index.ts
│   ├── stores/        # Pinia 状态管理
│   │   ├── auth.ts
│   │   ├── product.ts
│   │   ├── order.ts
│   │   ├── user.ts
│   │   └── config.ts
│   ├── styles/        # 全局样式
│   │   └── main.css
│   ├── types/         # TypeScript 类型定义
│   │   └── index.ts
│   ├── views/         # 页面视图
│   │   ├── LoginView.vue
│   │   ├── DashboardView.vue
│   │   ├── ProductListView.vue
│   │   ├── OrderListView.vue
│   │   ├── UserListView.vue
│   │   └── ConfigListView.vue
│   ├── App.vue        # 根组件
│   └── main.ts        # 入口文件
├── index.html
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## 快速开始

```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 类型检查
npm run type-check

# 构建生产版本
npm run build
```

## 功能模块

### 1. 登录页面 (LoginView)
- 玻璃态登录表单设计
- JWT Token 认证
- 错误提示

### 2. 数据概览 (DashboardView)
- 商品/订单/用户统计卡片
- 订单状态分布
- 进度条可视化

### 3. 商品管理 (ProductListView)
- 商品列表展示
- 添加/编辑/删除商品
- 搜索过滤
- 库存预警提示

### 4. 订单管理 (OrderListView)
- 订单列表展示
- 状态筛选
- 订单详情查看
- 发货处理

### 5. 用户管理 (UserListView)
- 用户列表展示
- 头像显示
- 角色管理（设为/取消管理员）

### 6. 配置中心 (ConfigListView)
- 微服务配置管理
- 服务/环境筛选
- 配置加密支持
- 添加/编辑/删除配置

## 设计特点

- **深色侧边栏**: 现代感的深色导航栏
- **浅色内容区**: 清晰的视觉层次
- **Hover 动画**: 悬停状态和过渡效果
- **响应式设计**: 适配不同屏幕尺寸
- **统一风格**: Element Plus 自定义主题

## 与后端集成

项目通过 Axios 与 Spring Cloud 微服务集成：

- Product Service (:8081)
- Order Service (:8082)
- User Service (:8083)
- Config Service (通过 gateway :8080)

Vite 开发服务器配置了代理，将 `/api` 请求转发到后端服务。
