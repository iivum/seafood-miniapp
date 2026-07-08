/**
 * login.js 行为测试(hand-rolled Page() harness)。
 *
 * 补齐 code review 发现的缺口:login-flow.test.ts 是 grep/源码文本匹配(检查
 * 字符串/相对顺序是否出现),不会真正执行 Page 里注册的方法,抓不出"条件写反"
 * 这类 bug(例如 onWxLogin 把 if (!this.data.agreed) 误写成 if (this.data.agreed),
 * 字符串顺序断言仍然会通过)。这里真正 require login.js、用一个最小 Page() 桩
 * 捕获注册的 options,构造假的页面实例调用真实方法,断言 setData / wx 方法 / authStore
 * 调用,覆盖协议门禁 + Step2 状态机 + 跳过路径的真实运行时行为。
 */
let capturedOptions;
global.Page = jest.fn((options) => {
  capturedOptions = options;
});

jest.mock('../features/auth/store', () => ({
  authStore: {
    loginWithCode: jest.fn(),
    bindPhone: jest.fn(),
  },
}));

require('../../pages-sub/user/login/login.js');
const { authStore } = require('../features/auth/store');

function makePageInstance() {
  const instance = Object.assign({}, capturedOptions);
  instance.data = Object.assign({}, capturedOptions.data);
  instance.setData = function (patch) {
    Object.assign(instance.data, patch);
  };
  return instance;
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

describe('login.js 行为(hand-rolled Page harness)', () => {
  let page;

  beforeEach(() => {
    jest.clearAllMocks();
    page = makePageInstance();
    page.redirect = '/pages/index/index';
  });

  it('onToggleAgree 翻转 data.agreed', () => {
    expect(page.data.agreed).toBe(false);
    page.onToggleAgree();
    expect(page.data.agreed).toBe(true);
    page.onToggleAgree();
    expect(page.data.agreed).toBe(false);
  });

  describe('onWxLogin — 协议门禁', () => {
    it('未勾选协议时不调 wx.login,触发 shake + toast 阻断', () => {
      page.onWxLogin();
      expect(wx.login).not.toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('协议') }),
      );
      expect(authStore.loginWithCode).not.toHaveBeenCalled();
    });

    it('已勾选协议时调 wx.login,成功后进入 Step2 并写入用户信息', async () => {
      page.data.agreed = true;
      wx.login.mockImplementation((opts) => opts.success({ code: 'wx-real-code' }));
      authStore.loginWithCode.mockResolvedValue({ nickname: '张三' });

      page.onWxLogin();
      await flushPromises();

      expect(wx.login).toHaveBeenCalled();
      expect(authStore.loginWithCode).toHaveBeenCalledWith('wx-real-code');
      expect(page.data.step).toBe(2);
      expect(page.data.userNickname).toBe('张三');
      expect(page.data.userAvatarInitial).toBe('张');
    });

    it('wx.login 未返回 code 时走失败分支,停留在 Step1', async () => {
      page.data.agreed = true;
      wx.login.mockImplementation((opts) => opts.success({}));

      page.onWxLogin();
      await flushPromises();

      expect(page.data.step).toBe(1);
      expect(wx.showToast).toHaveBeenCalled();
    });

    it('loginWithCode 失败时走失败分支,停留在 Step1', async () => {
      page.data.agreed = true;
      wx.login.mockImplementation((opts) => opts.success({ code: 'wx-real-code' }));
      authStore.loginWithCode.mockRejectedValue(new Error('后端拒绝'));

      page.onWxLogin();
      await flushPromises();

      expect(page.data.step).toBe(1);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '后端拒绝' }));
    });

    it('登录成功但用户此前已绑定手机号时,直接完成登录,不再弹 Step2(不应该每次登录都重新要求绑定)', async () => {
      page.data.agreed = true;
      wx.login.mockImplementation((opts) => opts.success({ code: 'wx-real-code' }));
      authStore.loginWithCode.mockResolvedValue({ nickname: '张三', phone: '13800001111' });

      page.onWxLogin();
      await flushPromises();

      expect(page.data.step).toBe(1);
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '登录成功' }));
    });
  });

  it('onSkipLogin 直接 reLaunch 到 redirect,不触发任何登录调用', () => {
    page.onSkipLogin();
    expect(wx.reLaunch).toHaveBeenCalledWith({ url: '/pages/index/index' });
    expect(authStore.loginWithCode).not.toHaveBeenCalled();
    expect(wx.login).not.toHaveBeenCalled();
  });

  describe('Step2 — 手机号绑定', () => {
    beforeEach(() => {
      page.data.step = 2;
    });

    it('onGetPhoneNumber 拿到 code 时调 bindPhone,成功后走登录成功收尾', async () => {
      authStore.bindPhone.mockResolvedValue({ phone: '13800001111' });

      page.onGetPhoneNumber({ detail: { code: 'wx-phone-code' } });
      await flushPromises();

      expect(authStore.bindPhone).toHaveBeenCalledWith('wx-phone-code');
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '登录成功' }));
    });

    it('onGetPhoneNumber 拒绝授权(无 code)时不调 bindPhone,只 toast 提示', () => {
      page.onGetPhoneNumber({ detail: { errMsg: 'getPhoneNumber:fail' } });
      expect(authStore.bindPhone).not.toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ icon: 'none' }));
    });

    it('onDevBindPhone 合成 dev- 前缀 code 调 bindPhone(devtools 无法触发真实授权时的覆盖路径)', async () => {
      authStore.bindPhone.mockResolvedValue({ phone: '13800002222' });

      page.onDevBindPhone();
      await flushPromises();

      expect(authStore.bindPhone).toHaveBeenCalledTimes(1);
      expect(authStore.bindPhone.mock.calls[0][0]).toMatch(/^dev-/);
    });

    it('onSkipPhoneBind 不调 bindPhone,直接走登录成功收尾', () => {
      page.onSkipPhoneBind();
      expect(authStore.bindPhone).not.toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '登录成功' }));
    });

    it('onGetPhoneNumber 双击(bindPhone 未 resolve 前再次触发)只调用一次 bindPhone(loading 守卫)', async () => {
      let resolveBindPhone;
      authStore.bindPhone.mockReturnValue(new Promise((resolve) => { resolveBindPhone = resolve; }));

      page.onGetPhoneNumber({ detail: { code: 'wx-phone-code' } });
      page.onGetPhoneNumber({ detail: { code: 'wx-phone-code' } });
      resolveBindPhone({ phone: '13800001111' });
      await flushPromises();

      expect(authStore.bindPhone).toHaveBeenCalledTimes(1);
    });

    it('onDevBindPhone 双击(bindPhone 未 resolve 前再次触发)只调用一次 bindPhone(loading 守卫)', async () => {
      let resolveBindPhone;
      authStore.bindPhone.mockReturnValue(new Promise((resolve) => { resolveBindPhone = resolve; }));

      page.onDevBindPhone();
      page.onDevBindPhone();
      resolveBindPhone({ phone: '13800002222' });
      await flushPromises();

      expect(authStore.bindPhone).toHaveBeenCalledTimes(1);
    });

    it('onGetPhoneNumber 绑定失败后重置 loading,允许再次尝试', async () => {
      authStore.bindPhone.mockRejectedValueOnce(new Error('绑定失败'));
      page.onGetPhoneNumber({ detail: { code: 'wx-phone-code' } });
      await flushPromises();

      authStore.bindPhone.mockResolvedValueOnce({ phone: '13800001111' });
      page.onGetPhoneNumber({ detail: { code: 'wx-phone-code' } });
      await flushPromises();

      expect(authStore.bindPhone).toHaveBeenCalledTimes(2);
    });
  });
});
