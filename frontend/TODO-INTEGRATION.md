# 微信小程序前后端联调测试 - 待修复项

## 测试时间
2026-04-17

## 测试环境
- 工作目录: /Users/iivum/.openclaw/workspace/seafood-miniapp
- 分支: test/wechat-miniapp-integration
- 前端: frontend/
- 后端: Docker 微服务 (product-service:8081, order-service:8082, user-service:8083, gateway:8080)

---

## 后端服务问题

### 1. [严重] user-service JAR 无法启动
**问题描述**: user-service 容器持续重启，错误信息:
```
java.lang.NoClassDefFoundError: org/springframework/boot/SpringApplication
```
**影响**: 用户服务完全不可用，登录、注册、地址管理等用户相关功能无法使用
**可能原因**: JAR 文件构建不完整或损坏
**修复建议**: 
- 重新构建 user-service: `./gradlew :user-service:bootJar`
- 检查 gradle 依赖配置

### 2. [严重] admin-ui JAR 无法启动
**问题描述**: admin-ui 容器持续重启，错误信息:
```
Error: Invalid or corrupt jarfile /app/app.jar
```
**影响**: 商户管理后台完全不可用
**可能原因**: admin-ui 有编译错误，未能成功构建
**编译错误**:
```
OrderDetailDialog.java:149: 错误: 找不到符号
  private VerticalLayout createItemsTable(List<OrderResponse.OrderItemResponse> items) {
OrderDetailDialog.java:195: 错误: 找不到符号
  private VerticalLayout createHistoryTimeline(List<OrderResponse.OrderHistoryResponse> history) {
```
**修复建议**: 修复 admin-ui 中 OrderResponse 类的引用问题

### 3. [严重] Gateway 无法注册到 Eureka Discovery
**问题描述**: Gateway 服务无法连接到 discovery-service
```
DiscoveryClient_GATEWAY-SERVICE - was unable to send heartbeat!
com.netflix.discovery.shared.transport.TransportException: Cannot execute request on any known server
```
**影响**: API Gateway 无法路由请求到后端微服务
**修复建议**: 检查 docker 网络配置和 Eureka 服务注册机制

### 4. [中等] order-service 返回 500 错误
**问题描述**: 订单服务 API 返回 `{"message":"Internal server error"}`
**影响**: 无法创建订单、查询订单
**修复建议**: 检查 MongoDB 连接和订单数据结构

### 5. [中等] product-service 商品数据为空
**问题描述**: `GET /products` 返回 `{"products":[],"totalProducts":0}`
**影响**: 首页无商品展示
**修复建议**: 需要初始化种子数据或检查数据导入脚本

---

## 前端代码问题

### 1. [中等] app.js 中 baseUrl 配置错误
**文件**: frontend/app.js
**问题**: 
```javascript
baseUrl: 'http://localhost:8080/api'  // 直接指向 gateway
```
**建议**: 应配置为实际运行的 API 地址，或使用环境变量

### 2. [中等] 图片资源路径问题
**文件**: frontend/pages/index/index.js
**问题**: 分类图标使用硬编码路径
```javascript
categories: [
  { name: '鱼类', icon: '/images/icons/fish.png' },
  ...
]
```
**建议**: 确认 images/icons/ 目录存在且包含所需图标

### 3. [中等] enablePullDownRefresh 未启用
**文件**: frontend/app.json
**问题**: 全局配置 `enablePullDownRefresh: false`，但 index 页面实现了 `onPullDownRefresh`
**建议**: 在 app.json 中启用下拉刷新或在 index 页面的 json 中单独配置

---

## 页面布局和设计问题

### 1. [低] 分类页面布局
**文件**: frontend/pages/category/category.wxml, category.wxss
**问题**: 需要确认分类导航和商品列表的布局是否合理
**建议**: 在不同屏幕尺寸下测试布局效果

### 2. [低] 购物车页面样式
**文件**: frontend/pages/cart/cart.wxss
**问题**: 需要确认购物车商品列表的间距和对齐
**建议**: 测试不同数量商品显示效果

### 3. [低] 商户端页面入口
**文件**: frontend/app.json
**问题**: 商户端页面作为 subpackage，需要通过特定入口访问
**建议**: 在个人中心添加商户入口按钮，或确认访问流程

---

## API 端点问题

### 当前可用端点
- product-service: http://localhost:8081
  - GET /products - 返回空列表
  - GET /products/{id} - 未测试
  
- order-service: http://localhost:8082
  - GET /orders - 返回 500 错误
  - POST /orders - 未测试
  
- user-service: http://localhost:8083
  - 完全不可用

### Gateway 路由配置
**文件**: backend/gateway/src/main/resources/application.yml
```yaml
routes:
  - id: product-service
    uri: lb://product-service
    predicates:
      - Path=/api/products/**
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/api/orders/**
  - id: user-service
    uri: lb://user-service
    predicates:
      - Path=/api/users/**
```

---

## 测试数据
- 测试用户: testuser1 / password123 (无法登录，因 user-service 不可用)
- 商品数据: 应有 15 条海鲜商品 (当前为空)
- 订单数据: 应有 6 条订单 (当前无法访问)

---

## 建议修复优先级

### P0 - 阻塞性问题 (必须立即修复)
1. 修复 user-service JAR 构建问题
2. 修复 admin-ui 编译错误
3. 解决 Gateway Eureka 注册问题

### P1 - 高优先级
4. 修复 order-service 500 错误
5. 初始化商品种子数据
6. 确认 API baseUrl 配置

### P2 - 中优先级
7. 启用下拉刷新功能
8. 检查图片资源完整性
9. 验证商户端页面入口

### P3 - 低优先级
10. 优化页面布局细节
11. 添加加载状态动画
12. 完善错误提示信息

---

## 后续测试计划
1. 修复 P0 问题后，重新测试登录流程
2. 修复 P1 问题后，测试完整购物流程
3. 修复 P2 问题后，测试商户端功能
4. 所有问题修复后，进行完整端到端测试
