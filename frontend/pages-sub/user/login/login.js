/**
 * Login page — P2 登录改造。
 *
 * 根因:旧 open-type="getPhoneNumber" 需企业资质 + 真机授权,开发者工具登录走不通。
 *
 * 改造:
 *   - 2 个 button:开发者登录(dev-login,code 以 dev- 开头) + 微信登录(wx.login code)
 *   - 登录成功跳回 query.redirect 目标(Task 4/5 都用 ?redirect=/... 触发)
 *   - 调 authStore.loginWithCode(code):内部共线 /auth/wechat-login,避免 wx.login 二次调用
 */
const { authStore } = require('../../../src/features/auth/store');

Page({
  data: {
    loading: false,
  },

  onLoad(query) {
    // Task 4/5 跳过来都带 ?redirect=/...,登录成功需跳回原目标
    this.redirect = (query && query.redirect) || '/pages/index/index';
  },

  /**
   * 开发者登录:本地 + e2e 用。
   * 后端识别 code 以 'dev-' 开头即走 dev-login 测试用户通道
   * (WechatCodeExchanger,backend/src/main/java/com/seafood/user/application/)。
   */
  onDevLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: () => {
        // dev-login 协议:不依赖 wx.login 返回的 code(开发工具/真机都可能不返回真值),
        // 合成一个 dev- 前缀 code,后端在 wechat.enabled=false 时认这个走通。
        const devCode =
          'dev-' +
          Date.now() +
          '-' +
          Math.random().toString(36).slice(2, 8);
        authStore
          .loginWithCode(devCode)
          .then(() => this.handleLoginSuccess())
          .catch((err) => this.handleLoginFail(err));
      },
      fail: (err) => this.handleLoginFail(err),
    });
  },

  /**
   * 微信登录:真机扫码,wx.login 拿真 code → 后端 jscode2session。
   * 仅在 wechat.enabled=true 时走真接口;否则后端要求 code 必须以 dev- 开头。
   */
  onWxLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: ({ code }) => {
        if (!code) {
          this.handleLoginFail(new Error('wx.login 未返回 code'));
          return;
        }
        authStore
          .loginWithCode(code)
          .then(() => this.handleLoginSuccess())
          .catch((err) => this.handleLoginFail(err));
      },
      fail: (err) => this.handleLoginFail(err),
    });
  },

  handleLoginSuccess() {
    this.setData({ loading: false });
    wx.showToast({ title: '登录成功', icon: 'success' });
    setTimeout(() => {
      // 优先 navigateBack 回到原页(保留 addToCart/order-list 的 history stack);
      // 兜底 reLaunch 到 redirect 目标(深链直跳场景)。
      const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
      if (pages.length > 1) {
        wx.navigateBack();
      } else {
        wx.reLaunch({ url: this.redirect });
      }
    }, 500);
  },

  handleLoginFail(err) {
    this.setData({ loading: false });
    console.error('login 失败', err);
    const message =
      (err && (err.message || err.errMsg)) || '登录失败,请重试';
    wx.showToast({ title: message, icon: 'none' });
  },
});
