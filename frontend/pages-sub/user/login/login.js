/**
 * Login page — align-mp-login-with-od:对齐 OD mp-10-login.html。
 *
 * 两步状态机:
 *   Step1 —— 用户协议/隐私政策勾选(未勾选点击登录 shake + toast 阻断)→
 *            微信一键登录(wx.login 拿 code → authStore.loginWithCode)
 *   Step2 —— 微信授权成功后引导绑定手机号(可跳过),真实 getPhoneNumber
 *            需企业资质仅真机/生产可用;devtools/e2e 用视觉收敛的开发者
 *            测试入口合成 dev- 前缀 code 走同一条 authStore.bindPhone 路径
 *
 * 开发者登录(dev-login,e2e/本地用)保留,行为不变,只是视觉收敛为不显眼入口。
 */
const { authStore } = require('../../../src/features/auth/store');

Page({
  data: {
    loading: false,
    agreed: false,
    shakeConsent: false,
    step: 1,
    userNickname: '',
    userAvatarInitial: '',
  },

  onLoad(query) {
    // Task 4/5 跳过来都带 ?redirect=/...,登录成功需跳回原目标
    this.redirect = (query && query.redirect) || '/pages/index/index';
  },

  onToggleAgree() {
    this.setData({ agreed: !this.data.agreed });
  },

  /** 未勾选协议时点击登录:shake 一次 + toast 提示,不调 wx.login。 */
  triggerConsentShake() {
    this.setData({ shakeConsent: false });
    setTimeout(() => {
      this.setData({ shakeConsent: true });
      setTimeout(() => this.setData({ shakeConsent: false }), 400);
    }, 0);
    wx.showToast({ title: '请先阅读并同意用户协议和隐私政策', icon: 'none' });
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
   * 微信一键登录:需先勾选用户协议/隐私政策;真机扫码,wx.login 拿真 code →
   * 后端 jscode2session。成功后进入 Step2(手机号绑定引导),不直接跳首页。
   */
  onWxLogin() {
    if (this.data.loading) return;
    if (!this.data.agreed) {
      this.triggerConsentShake();
      return;
    }
    this.setData({ loading: true });
    wx.login({
      success: ({ code }) => {
        if (!code) {
          this.handleLoginFail(new Error('wx.login 未返回 code'));
          return;
        }
        authStore
          .loginWithCode(code)
          .then((user) => this.enterPhoneBindStep(user))
          .catch((err) => this.handleLoginFail(err));
      },
      fail: (err) => this.handleLoginFail(err),
    });
  },

  /** 暂不登录,先逛逛:直接进入首页,不触发任何登录调用。 */
  onSkipLogin() {
    wx.reLaunch({ url: this.redirect });
  },

  enterPhoneBindStep(user) {
    const nickname = (user && user.nickname) || '微信用户';
    this.setData({
      loading: false,
      step: 2,
      userNickname: nickname,
      userAvatarInitial: nickname.slice(0, 1),
    });
  },

  /**
   * 真实微信手机号授权回调(button open-type="getPhoneNumber")。
   * 用户拒绝授权/前端拿不到 code 时降级为 toast 提示,不阻断——用户仍可点
   * "暂不绑定"跳过,登录态不受影响。
   */
  onGetPhoneNumber(e) {
    const detail = (e && e.detail) || {};
    if (!detail.code) {
      wx.showToast({ title: detail.errMsg || '未获取到手机号授权', icon: 'none' });
      return;
    }
    authStore
      .bindPhone(detail.code)
      .then(() => this.handleLoginSuccess())
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '手机号绑定失败', icon: 'none' });
      });
  },

  /**
   * 开发者:测试手机号绑定。devtools 无法触发真实 getPhoneNumber 授权流程
   * (需企业资质),合成 dev- 前缀 code 走后端 dev 模式,供本地/e2e 覆盖 Step2。
   */
  onDevBindPhone() {
    const devCode =
      'dev-' +
      Date.now() +
      '-' +
      Math.random().toString(36).slice(2, 8);
    authStore
      .bindPhone(devCode)
      .then(() => this.handleLoginSuccess())
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '手机号绑定失败', icon: 'none' });
      });
  },

  /** 暂不绑定,进入首页:不调用 bindPhone,登录态(已由 Step1 建立)不受影响。 */
  onSkipPhoneBind() {
    this.handleLoginSuccess();
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
