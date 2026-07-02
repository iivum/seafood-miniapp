/**
 * profile.js 测试(此前零覆盖 —— mp-od-prototype-alignment mp-05 视觉对齐时补齐)。
 *
 * 核心回归锁:onContactService / onAboutUs 两个此前指向不存在页面
 * (/pages/service/chat、/pages/about/about,均未在 app.json 注册)的死链接,
 * 改造成「功能开发中」toast 占位(同 order-list.js review/openRefundSheet 既有模式),
 * 不再触发 mp 运行时页面跳转失败。
 */

global.wx = {
  showToast: jest.fn(),
  showModal: jest.fn(),
  navigateTo: jest.fn(),
};

const mockLogout = jest.fn().mockResolvedValue(undefined);
const mockGetState = jest.fn(() => ({ user: null, isAuthenticated: false }));
jest.mock('../../../src/features/auth/store', () => ({
  authStore: {
    getState: (...a) => mockGetState(...a),
    logout: (...a) => mockLogout(...a),
  },
}));

let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};
require('../profile.js');

describe('profile', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    mockGetState.mockReturnValue({ user: null, isAuthenticated: false });
    ctx = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
      setData: jest.fn(function (patch) {
        Object.assign(this.data, patch);
      }),
    };
    ctx.setData = ctx.setData.bind(ctx);
    for (const key of Object.keys(pageConfig)) {
      if (typeof pageConfig[key] === 'function') ctx[key] = pageConfig[key].bind(ctx);
    }
  });

  describe('onShow / refreshUserInfo', () => {
    it('未登录时 userInfo 为 null', () => {
      ctx.onShow();
      expect(ctx.data.userInfo).toBeNull();
    });

    it('已登录时 userInfo 取自 authStore', () => {
      const user = { nickname: '林一帆', avatarUrl: 'https://x/a.png', role: 'CUSTOMER' };
      mockGetState.mockReturnValue({ user, isAuthenticated: true });
      ctx.onShow();
      expect(ctx.data.userInfo).toEqual(user);
    });
  });

  describe('死链接修复回归锁(mp-05 视觉对齐)', () => {
    it('onContactService 显示「联系客服开发中」toast,不跳转到不存在的 /pages/service/chat', () => {
      ctx.onContactService();
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中'), icon: 'none' }),
      );
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });

    it('onAboutUs 显示「关于我们开发中」toast,不跳转到不存在的 /pages/about/about', () => {
      ctx.onAboutUs();
      expect(wx.showToast).toHaveBeenCalledWith(
        expect.objectContaining({ title: expect.stringContaining('开发中'), icon: 'none' }),
      );
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });
  });

  describe('goToOrderList', () => {
    it('未登录时提示先登录,不跳转', () => {
      ctx.goToOrderList();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '请先登录' }));
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });

    it('已登录时跳订单列表', () => {
      mockGetState.mockReturnValue({ user: { nickname: 'x' }, isAuthenticated: true });
      ctx.goToOrderList();
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/order/order-list/order-list' }),
      );
    });
  });

  describe('onLogin', () => {
    it('未登录时跳登录页', () => {
      ctx.onLogin();
      expect(wx.navigateTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages-sub/user/login/login' }),
      );
    });

    it('已登录时提示已登录,不跳转', () => {
      mockGetState.mockReturnValue({ user: { nickname: 'x' }, isAuthenticated: true });
      ctx.onLogin();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '您已登录' }));
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });
  });

  describe('onLogout', () => {
    it('未登录时提示尚未登录,不弹确认框', () => {
      ctx.onLogout();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '您尚未登录' }));
      expect(wx.showModal).not.toHaveBeenCalled();
    });

    it('已登录时确认后调用 authStore.logout', async () => {
      mockGetState.mockReturnValue({ user: { nickname: 'x' }, isAuthenticated: true });
      wx.showModal.mockImplementation((opts) => opts.success({ confirm: true }));
      ctx.onLogout();
      await Promise.resolve();
      await Promise.resolve();
      expect(mockLogout).toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已退出登录' }));
    });

    it('已登录但取消确认框时不调用 logout', () => {
      mockGetState.mockReturnValue({ user: { nickname: 'x' }, isAuthenticated: true });
      wx.showModal.mockImplementation((opts) => opts.success({ confirm: false }));
      ctx.onLogout();
      expect(mockLogout).not.toHaveBeenCalled();
    });
  });
});
