/**
 * cart.js tests —— mp-04 OD 对齐第二轮(brief
 * `.superpowers/sdd/mp-od-4-cart-styling-brief.md`)。
 *
 * TDD 覆盖两类真 bug:
 *  1. 默认收货地址从不自动选中(brief §2)—— onShow 时 selectedAddress 仍是
 *     null 应查地址列表,自动选中 isDefault:true 的一条。
 *  2. checkbox 死绑定(诊断阶段发现,经协调者确认与 mp-01/02/03 同类问题
 *     一并修)—— cart.wxml 引用的 onItemCheckTap/onSelectAllTap 此前在本文件
 *     从未定义,点击完全无响应。
 */

// Mock wx global
global.wx = {
  showToast: jest.fn(),
  navigateTo: jest.fn(),
  getStorageSync: jest.fn(),
  setStorageSync: jest.fn(),
};

// Mock getApp (singleton so module-load and test access same object)
const mockApp = { globalData: { userInfo: { id: 'user-1', nickname: 'Test' } } };
global.getApp = jest.fn(() => mockApp);

// Mock request —— 真实 utils/request.js 导出 { request, authRequest }(对象,非裸函数),
// 与 address-list.js 同款用法对齐(自定位 §2 brief 要求"不要重新发明")。
const mockRequest = jest.fn().mockResolvedValue([]);
jest.mock('../../../utils/request.js', () => ({ request: mockRequest, authRequest: jest.fn() }));

// Mock cartStore
const mockCartStore = {
  refresh: jest.fn(),
  updateItem: jest.fn(),
  removeItem: jest.fn(),
  toggleItem: jest.fn(),
};
jest.mock('../../../src/features/cart/store', () => ({ cartStore: mockCartStore }));

// Mock utils/cart.js (local storage fallback)
jest.mock('../../../utils/cart.js', () => ({ getCart: jest.fn(() => []) }));

// Capture Page config
let pageConfig;
global.Page = (config) => {
  pageConfig = config;
};

require('../cart.js');

describe('cart', () => {
  let ctx;

  const makeCtx = () => {
    const c = {
      data: JSON.parse(JSON.stringify(pageConfig.data)),
    };
    c.setData = jest.fn(function (patch) {
      Object.assign(c.data, patch);
    });
    Object.keys(pageConfig).forEach((key) => {
      if (typeof pageConfig[key] === 'function' && key !== 'data') {
        c[key] = pageConfig[key].bind(c);
      }
    });
    return c;
  };

  beforeEach(() => {
    jest.clearAllMocks();
    mockRequest.mockResolvedValue([]);
    mockCartStore.refresh.mockResolvedValue({ items: [] });
    mockApp.globalData.userInfo = { id: 'user-1', nickname: 'Test' };
    ctx = makeCtx();
  });

  describe('checkbox 交互(死绑定修复)', () => {
    beforeEach(() => {
      ctx.data.cartItems = [
        { id: 'p1', productId: 'p1', name: '波士顿龙虾', price: 100, quantity: 1 },
        { id: 'p2', productId: 'p2', name: '基围虾', price: 50, quantity: 2 },
      ];
      ctx.data.selectedItems = [];
    });

    it('onItemCheckTap 是一个已定义的函数(此前 wxml 引用但本文件从未定义)', () => {
      expect(typeof ctx.onItemCheckTap).toBe('function');
    });

    it('onItemCheckTap 勾选未选中的商品行,驱动 selectedItems + 总价联动', () => {
      ctx.onItemCheckTap({ currentTarget: { dataset: { id: 'p1' } } });
      expect(ctx.data.selectedItems).toEqual(['p1']);
      expect(ctx.data.selectedPrice).toBe('100.00');
    });

    it('onItemCheckTap 再次点击取消勾选', () => {
      ctx.data.selectedItems = ['p1'];
      ctx.onItemCheckTap({ currentTarget: { dataset: { id: 'p1' } } });
      expect(ctx.data.selectedItems).toEqual([]);
    });

    it('onSelectAllTap 是一个已定义的函数(此前 wxml 引用但本文件从未定义)', () => {
      expect(typeof ctx.onSelectAllTap).toBe('function');
    });

    it('onSelectAllTap 全部未选时点击 -> 全选', () => {
      ctx.onSelectAllTap();
      expect(ctx.data.selectedItems.sort()).toEqual(['p1', 'p2']);
      expect(ctx.data.isAllSelected).toBe(true);
    });

    it('onSelectAllTap 已全选时点击 -> 全部取消', () => {
      ctx.data.selectedItems = ['p1', 'p2'];
      ctx.onSelectAllTap();
      expect(ctx.data.selectedItems).toEqual([]);
      expect(ctx.data.isAllSelected).toBe(false);
    });

    it('computeTotals 把 selected 标记写回每个 cartItems 行,供 wxml 直接绑 item.selected', () => {
      ctx.onItemCheckTap({ currentTarget: { dataset: { id: 'p1' } } });
      const p1 = ctx.data.cartItems.find((i) => i.id === 'p1');
      const p2 = ctx.data.cartItems.find((i) => i.id === 'p2');
      expect(p1.selected).toBe(true);
      expect(p2.selected).toBe(false);
    });
  });

  describe('renderCart 默认选中态(item 3——对齐后端 Cart.addItem 的 selected:true 默认值)', () => {
    it('后端返回 selected:true 的行,首次渲染即自动进入 selectedItems(默认勾选)', () => {
      mockCartStore.refresh.mockResolvedValue({
        items: [
          { productId: 'p1', productName: '龙虾', unitPrice: 100, quantity: 1, selected: true },
        ],
      });
      ctx.refreshCart();
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(ctx.data.selectedItems).toEqual(['p1']);
          expect(ctx.data.cartItems[0].selected).toBe(true);
        });
    });

    it('手动取消勾选后,数量变更触发的 re-render 不应把取消勾选的行重新勾上', () => {
      mockCartStore.refresh.mockResolvedValue({
        items: [
          { productId: 'p1', productName: '龙虾', unitPrice: 100, quantity: 1, selected: true },
          { productId: 'p2', productName: '基围虾', unitPrice: 50, quantity: 2, selected: true },
        ],
      });
      return ctx
        .refreshCart()
        .then(() => {})
        .catch(() => {})
        .then(() => Promise.resolve())
        .then(() => {
          // 首次渲染两行都默认勾选
          expect(ctx.data.selectedItems.sort()).toEqual(['p1', 'p2']);
          // 用户手动取消 p1
          ctx.onItemCheckTap({ currentTarget: { dataset: { id: 'p1' } } });
          expect(ctx.data.selectedItems).toEqual(['p2']);
          // 再次拉取(模拟数量变更后 renderCart 被重新调用),后端仍然返回 selected:true(无持久化取消能力)
          mockCartStore.refresh.mockResolvedValue({
            items: [
              { productId: 'p1', productName: '龙虾', unitPrice: 100, quantity: 1, selected: true },
              { productId: 'p2', productName: '基围虾', unitPrice: 50, quantity: 3, selected: true },
            ],
          });
          return ctx.refreshCart();
        })
        .then(() => {
          // p1 之前被用户手动取消,不应该被重新勾上
          expect(ctx.data.selectedItems).toEqual(['p2']);
        });
    });
  });

  describe('onShow 默认地址自动选中(brief §2 真 bug)', () => {
    it('selectedAddress 为 null 时查地址列表,自动选中 isDefault:true 的一条', () => {
      mockRequest.mockResolvedValue([
        { id: 'a1', name: '张三', phone: '13800000000', isDefault: false },
        { id: 'a2', name: '李四', phone: '13900000000', isDefault: true },
      ]);
      ctx.onShow();
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(mockRequest).toHaveBeenCalledWith({ url: '/addresses', needAuth: true });
          expect(ctx.data.selectedAddress).toEqual({
            id: 'a2',
            name: '李四',
            phone: '13900000000',
            isDefault: true,
          });
        });
    });

    it('没有默认地址时 selectedAddress 保持 null(维持既有空态,不是错误)', () => {
      mockRequest.mockResolvedValue([{ id: 'a1', name: '张三', phone: '13800000000', isDefault: false }]);
      ctx.onShow();
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(ctx.data.selectedAddress).toBeNull();
        });
    });

    it('地址列表为空数组时 selectedAddress 保持 null', () => {
      mockRequest.mockResolvedValue([]);
      ctx.onShow();
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(ctx.data.selectedAddress).toBeNull();
        });
    });

    it('selectedAddress 已有值时不重新查询地址列表(不覆盖用户已选)', () => {
      ctx.data.selectedAddress = { id: 'a9', name: '已选地址' };
      ctx.onShow();
      expect(mockRequest).not.toHaveBeenCalled();
      expect(ctx.data.selectedAddress).toEqual({ id: 'a9', name: '已选地址' });
    });

    it('未登录时不查询地址列表(既有登录守卫优先)', () => {
      mockApp.globalData.userInfo = null;
      ctx.onShow();
      expect(mockRequest).not.toHaveBeenCalled();
      expect(wx.showToast).toHaveBeenCalledWith({ title: '请先登录', icon: 'none' });
    });

    it('查询地址列表失败时不抛异常,selectedAddress 保持 null', () => {
      mockRequest.mockRejectedValue(new Error('network error'));
      ctx.onShow();
      return Promise.resolve()
        .then(() => Promise.resolve())
        .then(() => {
          expect(ctx.data.selectedAddress).toBeNull();
        });
    });
  });

  describe('refreshCart 离线兜底(既有行为,补覆盖率)', () => {
    it('cartStore.refresh 失败时降级到本地缓存并标记 isError', () => {
      const cartUtilJs = require('../../../utils/cart.js');
      cartUtilJs.getCart.mockReturnValue([{ id: 'local-1', name: '本地商品', price: 10, quantity: 1 }]);
      mockCartStore.refresh.mockRejectedValue(new Error('network down'));
      return ctx.refreshCart().then(() => {
        expect(ctx.data.isError).toBe(true);
        expect(ctx.data.errorMessage).toBe('network down');
        expect(ctx.data.cartItems.map((i) => i.id)).toEqual(['local-1']);
        expect(ctx.data.isLoading).toBe(false);
      });
    });

    it('cartStore.refresh 失败且无 message 时使用默认错误文案', () => {
      const cartUtilJs = require('../../../utils/cart.js');
      cartUtilJs.getCart.mockReturnValue([]);
      mockCartStore.refresh.mockRejectedValue('plain string error');
      return ctx.refreshCart().then(() => {
        expect(ctx.data.errorMessage).toBe('加载购物车失败');
      });
    });
  });

  describe('数量 stepper / 删除(既有行为,补覆盖率)', () => {
    beforeEach(() => {
      ctx.data.cartItems = [{ id: 'p1', productId: 'p1', name: '龙虾', price: 100, quantity: 2 }];
      mockCartStore.updateItem.mockResolvedValue({ items: [] });
      mockCartStore.removeItem.mockResolvedValue({ items: [] });
    });

    it('onQuantityChange 调 callStore(updateItem) 传入解析后的数量', () => {
      ctx.onQuantityChange({ currentTarget: { dataset: { id: 'p1' } }, detail: { value: '5' } });
      expect(mockCartStore.updateItem).toHaveBeenCalledWith('p1', 5);
    });

    it('onQuantityChange 非法输入兜底为 1', () => {
      ctx.onQuantityChange({ currentTarget: { dataset: { id: 'p1' } }, detail: { value: 'abc' } });
      expect(mockCartStore.updateItem).toHaveBeenCalledWith('p1', 1);
    });

    it('onMinus 数量 > 1 时减一', () => {
      ctx.onMinus({ currentTarget: { dataset: { id: 'p1' } } });
      expect(mockCartStore.updateItem).toHaveBeenCalledWith('p1', 1);
    });

    it('onMinus 数量已是 1 时不调用(下限保护)', () => {
      ctx.data.cartItems = [{ id: 'p1', productId: 'p1', name: '龙虾', price: 100, quantity: 1 }];
      ctx.onMinus({ currentTarget: { dataset: { id: 'p1' } } });
      expect(mockCartStore.updateItem).not.toHaveBeenCalled();
    });

    it('onPlus 加一', () => {
      ctx.onPlus({ currentTarget: { dataset: { id: 'p1' } } });
      expect(mockCartStore.updateItem).toHaveBeenCalledWith('p1', 3);
    });

    it('onPlus 商品行不存在时不调用', () => {
      ctx.onPlus({ currentTarget: { dataset: { id: 'not-exist' } } });
      expect(mockCartStore.updateItem).not.toHaveBeenCalled();
    });

    it('onRemove 调 callStore(removeItem)', () => {
      ctx.onRemove({ currentTarget: { dataset: { id: 'p1' } } });
      expect(mockCartStore.removeItem).toHaveBeenCalledWith('p1');
    });

    it('onToggleSelected 调 callStore(toggleItem)(未接入 wxml,详见文件头已知缺口)', () => {
      mockCartStore.toggleItem.mockResolvedValue({ items: [] });
      ctx.onToggleSelected({ currentTarget: { dataset: { id: 'p1' } } });
      expect(mockCartStore.toggleItem).toHaveBeenCalledWith('p1');
    });
  });

  describe('callStore(既有行为,补覆盖率)', () => {
    it('未知 action 直接返回,不抛异常', () => {
      expect(() => ctx.callStore('notAnAction', 'p1')).not.toThrow();
    });

    it('成功时用返回的 cart 重新 renderCart', () => {
      mockCartStore.updateItem.mockResolvedValue({ items: [{ productId: 'p1', unitPrice: 1, quantity: 1, selected: true }] });
      return ctx.callStore('updateItem', 'p1', 2).then(() => {
        expect(ctx.data.cartItems.some((i) => i.id === 'p1')).toBe(true);
      });
    });

    it('失败时 toast 展示错误信息', () => {
      mockCartStore.updateItem.mockRejectedValue(new Error('boom'));
      return ctx.callStore('updateItem', 'p1', 2).then(() => {
        expect(wx.showToast).toHaveBeenCalledWith({ title: 'boom', icon: 'none' });
      });
    });

    it('失败且无 message 时用默认文案', () => {
      mockCartStore.updateItem.mockRejectedValue('plain error');
      return ctx.callStore('updateItem', 'p1', 2).then(() => {
        expect(wx.showToast).toHaveBeenCalledWith({ title: '操作失败', icon: 'none' });
      });
    });
  });

  describe('onCheckout(既有行为,补覆盖率)', () => {
    it('购物车为空时不跳转', () => {
      ctx.data.cartItems = [];
      ctx.onCheckout();
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });

    it('未登录时跳登录页并带 redirect', () => {
      ctx.data.cartItems = [{ id: 'p1' }];
      mockApp.globalData.userInfo = null;
      ctx.onCheckout();
      expect(wx.navigateTo).toHaveBeenCalledWith({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart'),
      });
    });

    it('已登录且非空购物车时跳订单确认页', () => {
      ctx.data.cartItems = [{ id: 'p1' }];
      ctx.onCheckout();
      expect(wx.navigateTo).toHaveBeenCalledWith({ url: '/pages-sub/order/order-confirm/order-confirm' });
    });
  });

  describe('selectAddress(既有行为,补覆盖率)', () => {
    it('未登录时 toast 提示,不跳转', () => {
      mockApp.globalData.userInfo = null;
      ctx.selectAddress();
      expect(wx.showToast).toHaveBeenCalledWith({ title: '请先登录', icon: 'none' });
      expect(wx.navigateTo).not.toHaveBeenCalled();
    });

    it('已登录且未选地址时跳转带空 selectedAddress', () => {
      ctx.data.selectedAddress = null;
      ctx.selectAddress();
      expect(wx.navigateTo).toHaveBeenCalledWith({
        url: '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=',
      });
    });

    it('已登录且已选地址时跳转带编码后的 selectedAddress', () => {
      ctx.data.selectedAddress = { id: 'a1' };
      ctx.selectAddress();
      expect(wx.navigateTo).toHaveBeenCalledWith({
        url:
          '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=' +
          encodeURIComponent(JSON.stringify({ id: 'a1' })),
      });
    });
  });

  describe('cart.wxml 收货地址详情字段契约（3.8 修复）', () => {
    it('地址详情文案渲染 domain 返回字段 selectedAddress.detail，不是 mp 请求字段 selectedAddress.detailAddress（detailAddress 从来不是后端返回字段，此前渲染必然是 undefined 插值）', () => {
      const fs = require('fs');
      const path = require('path');
      const wxml = fs.readFileSync(path.resolve(__dirname, '../cart.wxml'), 'utf8');

      expect(wxml).toMatch(
        /\{\{selectedAddress\.province\}\}\{\{selectedAddress\.city\}\}\{\{selectedAddress\.district\}\}\{\{selectedAddress\.detail\}\}/
      );
      expect(wxml).not.toMatch(/\{\{selectedAddress\.detailAddress\}\}/);
    });
  });
});
