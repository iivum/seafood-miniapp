/**
 * address-edit.js tests.
 */

// Mock wx global
global.wx = {
  showToast: jest.fn(),
  navigateBack: jest.fn(),
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  getStorageSync: jest.fn(),
  setStorageSync: jest.fn(),
  request: jest.fn(),
};

// Mock getApp (singleton)
const mockApp = { globalData: { userInfo: { id: 'user-1', nickname: 'Test' } } };
global.getApp = jest.fn(() => mockApp);

// Mock request
const mockRequest = jest.fn().mockResolvedValue({});
jest.mock('../../../../utils/request.js', () => mockRequest);

// Capture Page config
let pageConfig;
global.Page = (config) => { pageConfig = config; };

// Load the module
require('../address-edit.js');

describe('address-edit', () => {
  let ctx;

  beforeEach(() => {
    jest.clearAllMocks();
    ctx = {
      data: { ...pageConfig.data },
      setData: jest.fn(function(patch) { Object.assign(this.data, patch); }.bind(ctx)),
      onLoad: pageConfig.onLoad,
      loadAddressDetail: pageConfig.loadAddressDetail,
      updateRegionDisplay: pageConfig.updateRegionDisplay,
      onRegionChange: pageConfig.onRegionChange,
      onInputChange: pageConfig.onInputChange,
      saveAddress: pageConfig.saveAddress,
      validateForm: pageConfig.validateForm,
    };
    ctx.setData = jest.fn(function(patch) { Object.assign(this.data, patch); }.bind(ctx));
  });

  it('onLoad sets isEdit when id provided', () => {
    ctx.onLoad({ id: 'a1' });
    expect(ctx.data.isEdit).toBe(true);
    expect(ctx.data.address.id).toBe('a1');
    expect(mockRequest).toHaveBeenCalled();
  });

  it('onLoad does not set isEdit when no id', () => {
    ctx.onLoad({});
    expect(ctx.data.isEdit).toBe(false);
    expect(mockRequest).not.toHaveBeenCalled();
  });

  it('loadAddressDetail fetches address', () => {
    mockRequest.mockResolvedValueOnce({ id: 'a1', name: 'Home' });
    ctx.loadAddressDetail('a1');
    expect(mockRequest).toHaveBeenCalledWith({ url: '/addresses/a1', needAuth: true });
  });

  it('onRegionChange updates regionIndex', () => {
    ctx.data.regionList = ['北京', '北京市', '朝阳区'];
    ctx.onRegionChange({ detail: { value: [1, 2, 3] } });
    expect(ctx.setData).toHaveBeenCalled();
  });

  it('updateRegionDisplay sets region when address has province/city/district', () => {
    ctx.updateRegionDisplay({ province: '北京', city: '北京市', district: '朝阳区' });
    expect(ctx.setData).toHaveBeenCalled();
  });

  it('updateRegionDisplay does nothing when address lacks province', () => {
    ctx.updateRegionDisplay({});
    expect(ctx.setData).not.toHaveBeenCalled();
  });

  describe('validateForm', () => {
    it('returns errors for empty form', () => {
      const errors = pageConfig.validateForm({});
      expect(errors.length).toBeGreaterThan(0);
    });

    it('returns error for short name', () => {
      const errors = pageConfig.validateForm({ name: '张', phone: '13800138000', province: '北京', detailAddress: '详细地址测试' });
      expect(errors).toContain('请输入正确的收件人姓名');
    });

    it('returns error for invalid phone', () => {
      const errors = pageConfig.validateForm({ name: '张三', phone: '123', province: '北京', detailAddress: '详细地址测试' });
      expect(errors).toContain('请输入正确的手机号');
    });

    it('returns error for unselected region', () => {
      const errors = pageConfig.validateForm({ name: '张三', phone: '13800138000', province: '请选择', detailAddress: '详细地址测试' });
      expect(errors).toContain('请选择所在地区');
    });

    it('returns error for short detail address', () => {
      const errors = pageConfig.validateForm({ name: '张三', phone: '13800138000', province: '北京', detailAddress: '短' });
      expect(errors).toContain('请输入详细的收货地址');
    });

    it('returns empty array for valid form', () => {
      const errors = pageConfig.validateForm({ name: '张三', phone: '13800138000', province: '北京', detailAddress: '详细地址测试内容' });
      expect(errors).toEqual([]);
    });
  });

  describe('saveAddress', () => {
    it('shows errors when validation fails', () => {
      ctx.saveAddress({ detail: { value: {} } });
      expect(ctx.setData).toHaveBeenCalled();
      const lastCall = ctx.setData.mock.calls[ctx.setData.mock.calls.length - 1][0];
      expect(lastCall.errorMsg).toBeTruthy();
    });

    it('sends request when validation passes', () => {
      mockRequest.mockResolvedValueOnce({});
      ctx.data.address = { id: 'a1' };
      ctx.data.isEdit = true;
      ctx.saveAddress({
        detail: {
          value: { name: '张三', phone: '13800138000', province: '北京', region: ['北京', '北京市', '朝阳区'], detailAddress: '详细地址测试内容' },
        },
      });
      expect(mockRequest).toHaveBeenCalled();
    });
  });
});
