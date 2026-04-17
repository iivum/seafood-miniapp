# 联调测试待修复项 - 2026-04-17

## Admin UI 联调测试 - 第1轮

### ✅ 已通过测试
1. Gateway API 集成
   - `/api/orders` - 返回 [] (正常)
   - `/api/products` - 返回产品列表 (正常)
   - `/api/users` - 返回用户列表 (正常)

2. Admin UI 静态资源
   - `/admin/login` - 返回登录页面 HTML (200)
   - `/admin/VAADIN/static/themes/lumo/styles.css` - CSS 加载 (200)
   - `/admin/VAADIN/build/*.js` - JS 文件存在

3. Direct Service Access
   - `localhost:8081/api/products` - 正常
   - `localhost:8082/api/orders/all` - 正常
   - `localhost:8083/api/users` - 正常

### ❌ 待修复问题

#### P0 - Critical
**问题 1: Admin UI Vaadin JavaScript Bundle 错误**
- 错误: `Cannot destructure property 'appId' of 't' as it is undefined`
- 位置: Vaadin TypeScript bundle 初始化
- 影响: 页面路由失败，/admin/login 无法正确渲染视图
- 症状:
  - 控制台显示 401 和路由解析失败警告
  - "Path '/admin/login' is not properly resolved due to an error"
  - JS 异常: Cannot destructure property 'appId'
- 可能原因:
  1. Vaadin Flow 初始化时 appId 为 undefined
  2. productionMode: true 与开发构建不兼容
  3. index.html 缺少 appId 占位符

#### P1 - High
**问题 2: SecurityConfig 冗余规则**
- 当前添加了 `/VAADIN/**`, `/frontend/**`, `/build/**` permitAll
- 正确路径应该是 `/admin/VAADIN/**`
- 需要清理

#### P2 - Medium
**问题 3: OrderController 端点路径不一致**
- `@RequestMapping("/api/orders")` 但还有额外路径
- 应统一 API 路径风格

---

## WeChat MiniApp 联调测试 - 待进行

待完成 Admin UI 后开始

---

## 测试命令

```bash
# 验证服务状态
docker ps | grep seafood

# 启动所有服务
cd /Users/iivum/.openclaw/workspace/seafood-miniapp && docker-compose up -d

# 等待启动
sleep 30

# API 测试
curl http://localhost:8080/api/orders
curl http://localhost:8080/api/products
curl http://localhost:8080/api/users

# Admin UI 测试
curl http://localhost:8090/admin/login
curl http://localhost:8090/admin/VAADIN/static/themes/lumo/styles.css
```

---

## 修复进度

- [x] 第1轮测试完成
- [ ] 问题1修复 (appId 错误)
- [ ] 问题2修复 (SecurityConfig)
- [ ] 问题3修复 (OrderController)
- [ ] 第2轮测试
- [ ] WeChat MiniApp 联调测试
- [ ] 最终验收
