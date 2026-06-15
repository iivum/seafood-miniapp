/**
 * address-list.js tests.
 * Mocks wx APIs and app globals to test Page lifecycle and methods.
 */

// Mock wx global
global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  showModal: jest.fn(),
  getStorageSync: jest.fn(),
  setStorageSync: jest.fn(),
  request: jest.fn(),
};

// Mock getApp (singleton so module-load and test access same object)
const mockApp = { globalData: { userInfo: { id: 'user-1', nickname: 'Test' } } };
global.getApp = jest.fn(() => mockApp);

// Mock getCurrentPages
global.getCurrentPages = jest.fn(() => [
  { route: 'pages/cart/cart' },
  { route: 'pages-sub/user/address/address-list' },
]);

// Mock request
const mockRequest = jest.fn().mockResolvedValue([]);
jest.mock('../../../../utils/request.js', () => mockRequest);

// Capture Page config
let pageConfig;
global.Page = (config) => { pageConfig = config; };

// Load the module
require('../address-list.js');

describe('address-list', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    ctx = {
      data: { ...pageConfig.data },
      setData: jest.fn(function(patch) { Object.assign(this.data, patch); }.bind(ctx)),
      loadAddresses: pageConfig.loadAddresses,
      addNewAddress: pageConfig.addNewAddress,
      editAddress: pageConfig.editAddress,
      deleteAddress: pageConfig.deleteAddress,
      selectAddress: pageConfig.selectAddress,
      setDefaultAddress: pageConfig.setDefaultAddress,
      onLoad: pageConfig.onLoad,
      onShow: pageConfig.onShow,
    };
    ctx.setData = jest.fn(function(patch) { Object.assign(this.data, patch); }.bind(ctx));
  });

  it('onLoad sets selectMode from options', () => {
    ctx.onLoad({ selectMode: 'true', selectedAddress: '{"id":"a1"}' });
    expect(ctx.data.selectMode).toBe(true);
    expect(ctx.data.selectedAddress).toEqual({ id: 'a1' });
  });

  it('onLoad calls loadAddresses', () => {
    ctx.onLoad({});
    expect(mockRequest).toHaveBeenCalled();
  });

  it('onShow calls loadAddresses', () => {
    ctx.onShow();
    expect(mockRequest).toHaveBeenCalled();
  });

  it('loadAddresses shows toast when no userInfo', () => {
    // getApp returns same singleton; modify its globalData directly
    const appInstance = getApp();
    appInstance.globalData.userInfo = null;
    ctx.loadAddresses();
    expect(wx.showToast).toHaveBeenCalledWith({ title: '请先登录', icon: 'none' });
    appInstance.globalData.userInfo = { id: 'user-1', nickname: 'Test' };
  });

  it('loadAddresses fetches addresses for user', () => {
    mockRequest.mockResolvedValueOnce([{ id: 'a1' }]);
    ctx.loadAddresses();
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/addresses/user/user-1',
      needAuth: true,
    });
  });

  it('addNewAddress navigates to edit page', () => {
    ctx.addNewAddress();
    expect(wx.navigateTo).toHaveBeenCalledWith({
      url: '/pages-sub/user/address/address-edit',
    });
  });

  it('editAddress navigates with id', () => {
    ctx.editAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    expect(wx.navigateTo).toHaveBeenCalledWith({
      url: '/pages-sub/user/address/address-edit?id=a1',
    });
  });

  it('selectAddress does nothing when not in selectMode', () => {
    ctx.data.selectMode = false;
    ctx.selectAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    expect(wx.navigateBack).not.toHaveBeenCalled();
  });

  it('selectAddress navigates back in selectMode', () => {
    ctx.data.selectMode = true;
    ctx.selectAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    expect(wx.navigateBack).toHaveBeenCalled();
  });

  it('setDefaultAddress calls PUT endpoint', () => {
    mockRequest.mockResolvedValueOnce({});
    ctx.setDefaultAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/addresses/a1/default',
      method: 'PUT',
      needAuth: true,
    });
  });

  it('deleteAddress shows modal and deletes on confirm', () => {
    wx.showModal.mockImplementation((opts) => {
      opts.success({ confirm: true });
    });
    mockRequest.mockResolvedValueOnce({});
    ctx.deleteAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    expect(wx.showModal).toHaveBeenCalled();
    expect(mockRequest).toHaveBeenCalledWith({
      url: '/addresses/a1',
      method: 'DELETE',
      needAuth: true,
    });
  });

  it('deleteAddress does nothing on cancel', () => {
    wx.showModal.mockImplementation((opts) => {
      opts.success({ confirm: false });
    });
    ctx.deleteAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    expect(wx.showModal).toHaveBeenCalled();
    expect(mockRequest).not.toHaveBeenCalled();
  });

  it('selectAddress navigates back when no prev page', () => {
    getCurrentPages.mockReturnValueOnce([{ route: 'pages/index/index' }]);
    ctx.data.selectMode = true;
    ctx.selectAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    expect(wx.navigateBack).toHaveBeenCalled();
  });

  it('loadAddresses handles request failure', async () => {
    mockRequest.mockRejectedValueOnce(new Error('network error'));
    ctx.loadAddresses();
    // Wait for the promise to reject
    await new Promise(r => setTimeout(r, 50));
    expect(wx.showToast).toHaveBeenCalledWith({ title: '加载地址失败', icon: 'none' });
  });

  it('setDefaultAddress handles failure', async () => {
    mockRequest.mockRejectedValueOnce(new Error('network error'));
    ctx.setDefaultAddress({ currentTarget: { dataset: { address: { id: 'a1' } } } });
    await new Promise(r => setTimeout(r, 50));
    expect(wx.showToast).toHaveBeenCalledWith({ title: '设置失败', icon: 'none' });
  });
});
