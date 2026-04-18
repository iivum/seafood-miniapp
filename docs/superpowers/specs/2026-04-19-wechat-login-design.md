# 微信登录功能设计

## 概述

实现微信小程序一键登录功能，包括：
- 后端集成 WxJava 4.x SDK
- 手机号授权登录
- JWT Token 管理（AccessToken + RefreshToken）
- 登录态前后端协同管理

## 技术架构

| 层级 | 技术选型 |
|------|----------|
| 前端 | 微信小程序 button (open-type="getPhoneNumber") |
| 后端 | Spring Boot 3.2 + WxJava 4.x → user-service |
| Token管理 | JWT (AccessToken 1h, RefreshToken 7d) + Redis |
| 存储 | MongoDB (用户) + Redis (Token/Session) |

## 后端设计

### 新增文件

#### 1. WxJavaConfig.java
WxJava SDK 配置类，注册为 Spring Bean。

```java
@Configuration
public class WxJavaConfig {
    @Value("${wx.appid}")
    private String appId;

    @Value("${wx.secret}")
    private String appSecret;

    @Bean
    public WxMaService wxMaService() {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(appId);
        config.setAppSecret(appSecret);
        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}
```

#### 2. WeChatPhoneLoginRequest.java
登录请求 DTO。

```java
@Data
@Schema(description = "微信手机号登录请求")
public class WeChatPhoneLoginRequest {
    @Schema(description = "微信登录code", required = true)
    private String code;

    @Schema(description = "加密手机号数据", required = true)
    private String encryptedData;

    @Schema(description = "解密偏移量", required = true)
    private String iv;
}
```

#### 3. WxJavaService.java
微信 API 调用服务。

```java
@Service
public class WxJavaService {
    private final WxMaService wxMaService;

    // 1. code 换取 session_key
    public String getSessionKey(String code) {
        WxMaJscode2SessionResult result = wxMaService.getUserService()
            .getSessionInfo(code, "zh_CN");
        return result.getSessionKey();
    }

    // 2. 解密手机号
    public String decryptPhoneNumber(String sessionKey, String encryptedData, String iv) {
        // 使用 WxMaCryptTool 解密
    }
}
```

### 修改文件

#### AuthenticationService.java
新增 `weChatPhoneLogin` 方法。

```java
public LoginResponse weChatPhoneLogin(String code, String encryptedData, String iv) {
    // 1. 获取 session_key
    String sessionKey = wxJavaService.getSessionKey(code);

    // 2. 解密手机号
    String phone = wxJavaService.decryptPhoneNumber(sessionKey, encryptedData, iv);

    // 3. 根据手机号查找或创建用户
    User user = userRepository.findByPhone(phone)
        .orElseGet(() -> createNewUser(phone));

    // 4. 生成 JWT
    return generateLoginResponse(user);
}
```

### application.yml 配置

```yaml
wx:
  appid: ${WX_APPID}
  secret: ${WX_SECRET}
```

环境变量 `WX_APPID` 和 `WX_SECRET` 在启动时注入。

### API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /auth/wx-phone-login | 微信手机号登录 |
| POST | /auth/logout | 登出 |
| POST | /auth/refresh | 刷新Token |

## 前端设计

### login.wxml 改动

```xml
<button
  class="wechat-login-btn"
  open-type="getPhoneNumber"
  bindgetphonenumber="onGetPhoneNumber"
  loading="{{isLoading}}">
  微信一键登录
</button>
```

### login.js 改动

```javascript
onGetPhoneNumber: function(e) {
  if (e.detail.code) {
    // 有 code 说明用户同意授权手机号
    this.wxPhoneLogin(e.detail.code, e.detail.encryptedData, e.detail.iv);
  }
},

wxPhoneLogin: function(code, encryptedData, iv) {
  // 调用后端 API
  request({
    url: '/auth/wx-phone-login',
    method: 'POST',
    data: { code, encryptedData, iv }
  }).then(res => {
    // 存储 token
    wx.setStorageSync('token', res.token);
    app.globalData.token = res.token;
    app.globalData.userInfo = res.data;
  });
}
```

### app.js 改动

```javascript
// 完善 wxLogin 方法（用于纯openId登录场景）
wxLogin: function() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: loginRes => {
        // 调用后端接口用 code 换 token
        request({
          url: '/auth/wx-login',
          method: 'POST',
          data: { code: loginRes.code }
        }).then(res => {
          wx.setStorageSync('token', res.token);
          this.globalData.token = res.token;
          this.globalData.userInfo = res.data;
          resolve(res);
        }).catch(reject);
      },
      fail: reject
    });
  });
},

// 完善 logout 方法
logout: function() {
  request({ url: '/auth/logout', method: 'POST' })
    .finally(() => {
      this.globalData.token = null;
      this.globalData.userInfo = null;
      wx.removeStorageSync('token');
    });
}
```

### request.js 改动

自动携带和刷新 Token。

```javascript
const request = (options) => {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token');
    wx.request({
      ...options,
      header: {
        'Authorization': token ? `Bearer ${token}` : '',
        ...options.header
      },
      success: (res) => {
        if (res.statusCode === 401) {
          // Token 过期，尝试刷新
          return refreshToken().then(() => {
            // 重试原请求
            return request(options);
          }).catch(() => {
            // 刷新失败，跳转登录
            wx.navigateTo({ url: '/pages-sub/user/login/login' });
            reject(new Error('登录已过期'));
          });
        }
        resolve(res.data);
      },
      fail: reject
    });
  });
}
```

## 登录流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        微信登录流程                               │
└─────────────────────────────────────────────────────────────────┘

前端                           后端                         微信
  │                             │                            │
  │  1. wx.login()             │                            │
  │───────────────────────────>│                            │
  │                             │                             │
  │                             │  2. code + appId + secret   │
  │                             │───────────────────────────>│
  │                             │  3. session_key           │
  │                             │<───────────────────────────│
  │                             │                            │
  │  4. button 点击             │                            │
  │  getPhoneNumber            │                            │
  │<───────────────────────────│                            │
  │                             │                            │
  │  5. encryptedData + iv + code                            │
  │───────────────────────────>│                            │
  │                             │                             │
  │                             │  6. 解密手机号              │
  │                             │  7. 查询/创建用户           │
  │                             │  8. 生成JWT                │
  │                             │                            │
  │  9. token + userInfo        │                            │
  │<───────────────────────────│                            │
  │                             │                            │
  │  10. 存储token，跳转首页   │                            │
```

## 登出流程

```
前端                           后端                         Redis
  │                             │                            │
  │  1. 调用 /auth/logout       │                            │
  │  (携带 token)               │                            │
  │───────────────────────────>│                            │
  │                             │                             │
  │                             │  2. Token 加入黑名单       │
  │                             │───────────────────────────>│
  │                             │                            │
  │                             │  3. 删除 RefreshToken     │
  │                             │───────────────────────────>│
  │                             │                            │
  │  4. 清除本地Storage        │                            │
  │  5. 跳转登录页             │                            │
```

## Token 管理策略

| Token | 有效期 | 存储位置 | 用途 |
|-------|--------|----------|------|
| AccessToken | 1小时 | Redis + 客户端 | API 访问认证 |
| RefreshToken | 7天 | Redis (HttpOnly) | 刷新 AccessToken |

### Token 刷新流程

```
1. 前端请求 API，返回 401
2. 前端调用 /auth/refresh，携带 RefreshToken
3. 后端验证 RefreshToken 有效
4. 生成新的 AccessToken 返回
5. 前端用新 Token 重试原请求
```

### 登出处理

1. 后端将 Token 加入黑名单（Redis）
2. 删除 Redis 中的 RefreshToken
3. 前端清除本地存储

## 数据库模型

### User 实体

```java
@Document
public class User {
    @Id
    private String id;           // 用户ID
    private String openId;       // 微信OpenID
    private String phone;         // 手机号
    private String nickname;     // 昵称
    private String avatarUrl;    // 头像URL
    private UserRole role;       // 角色
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

## 错误处理

| 错误码 | 描述 | 处理方式 |
|--------|------|----------|
| 1001 | 微信code无效 | 提示重试 |
| 1002 | 手机号解密失败 | 提示重试 |
| 1003 | 用户被禁用 | 提示联系客服 |
| 401 | Token无效 | 跳转登录页 |

## 测试计划

### 后端测试
- [ ] WxJavaService 单元测试（Mock 微信API）
- [ ] AuthenticationService 手机号登录测试
- [ ] Token 刷新接口测试
- [ ] 登出接口测试

### 前端测试
- [ ] 登录流程 E2E 测试
- [ ] Token 过期刷新测试
- [ ] 登出流程测试

## 部署配置

环境变量：
```bash
WX_APPID=wx1234567890abcdef
WX_SECRET=abcdef1234567890abcdef
```

## 风险与注意事项

1. **AppSecret 安全**：生产环境务必使用环境变量，不要提交到代码仓库
2. **session_key 存储**：解密后的 session_key 不要持久化存储，用完即删
3. **Token 安全**：RefreshToken 建议 HttpOnly Cookie 传输
4. **错误日志**：禁止将用户手机号打印到日志
