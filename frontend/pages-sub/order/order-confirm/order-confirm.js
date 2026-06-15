/**
 * Order confirm / detail page — 路线图 3.14 / 3.17 / 3.18 OD v2 重构。
 *
 * 3.17:4 金额项联动实时算(商品总额 = Σ item.price × item.quantity;
 *       运费 = 配送方式映射;优惠 = 满 100 减 10 占位;实付 = 总额 + 运费 - 优惠)
 * 3.18:配送方式 3 选(免运费 / 顺丰 12 / 中通 8)+ 备注 max 50 字
 */
const { orderStore } = require('../../../src/features/order/store');
const { cartStore } = require('../../../src/features/cart/store');
const { paymentModule } = require('../../../src/modules/payment/payment.js');

// 3.17 配送方式 → 运费映射
const SHIPPING_FEE_MAP = {
  FREE: 0,
  SF: 12,
  ZTO: 8,
};

// 3.17 优惠规则(占位,Sprint 3 接真实优惠):满 100 减 10
function calcDiscount(subtotal) {
  return subtotal >= 100 ? 10 : 0;
}

Page({
  data: {
    order: null,
    selectedAddress: null,
    cartItems: [],
    // 3.18 配送方式 3 选
    shippingMethod: 'FREE',
    shippingFee: 0,
    // 3.18 备注 max 50 字
    remark: '',
    // 3.17 4 金额项
    subtotal: 0,
    discount: 0,
    orderTotal: 0,
    isCreating: false,
    isPaying: false,
    errorMessage: '',
  },

  onLoad: function (options) {
    if (options && options.id) {
      this.loadExistingOrder(options.id);
    } else {
      this.refreshCartPreview();
    }
  },

  onShow: function () {
    if (this.data.order) return;
    const pages = getCurrentPages();
    const currentPage = pages[pages.length - 1];
    if (currentPage && currentPage.selectedAddressFromList) {
      this.setData({ selectedAddress: currentPage.selectedAddressFromList });
      currentPage.selectedAddressFromList = null;
    }
  },

  loadExistingOrder: function (id) {
    orderStore
      .loadById(id)
      .then((order) => this.setData({ order }))
      .catch((err) => {
        this.setData({
          errorMessage: (err && err.message) || '加载订单失败',
        });
      });
  },

  refreshCartPreview: function () {
    // Use the new cart store to render the order preview.
    cartStore
      .refresh()
      .then((cart) => {
        const items = (cart.items || []).map((it) => ({
          id: it.productId,
          name: it.productName || it.productId,
          price: it.unitPrice || 0,
          quantity: it.quantity,
          imageUrl: it.imageUrl || '',
        }));
        this.setData({
          order: {
            id: null,
            totalAmount: 0,
            items: items.map((it) => ({
              productId: it.id,
              productName: it.name,
              unitPrice: it.price,
              quantity: it.quantity,
            })),
            status: 'PENDING',
          },
          cartItems: items,
        });
        this.recalcAmounts();
      })
      .catch(() => {
        // best-effort: leave order as null so the empty-state renders
      });
  },

  /**
   * 3.17 实时算 4 金额项(总额 / 运费 / 优惠 / 实付)。
   * 调用时机:refreshCartPreview + onSelectShipping + onRemarkInput(实际不触发金额变,保留)。
   */
  recalcAmounts: function () {
    const items = this.data.cartItems || [];
    const subtotal = items.reduce((sum, it) => sum + (it.price || 0) * (it.quantity || 0), 0);
    const shippingFee = SHIPPING_FEE_MAP[this.data.shippingMethod] ?? 0;
    const discount = calcDiscount(subtotal);
    const orderTotal = Math.max(0, subtotal + shippingFee - discount);
    this.setData({ subtotal, shippingFee, discount, orderTotal });
  },

  selectAddress: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const selectedAddress = this.data.selectedAddress || null;
    wx.navigateTo({
      url:
        '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=' +
        encodeURIComponent(selectedAddress ? JSON.stringify(selectedAddress) : ''),
    });
  },

  /**
   * 3.18 配送方式切换(setData 后实时算 4 金额项)。
   */
  onSelectShipping: function (e) {
    const method = e.currentTarget.dataset.method || 'FREE';
    this.setData({ shippingMethod: method, shippingFee: SHIPPING_FEE_MAP[method] ?? 0 });
    this.recalcAmounts();
  },

  /**
   * 3.18 备注输入(bindinput 触发,maxlength 50 已在 wxml 守)。
   * 不触发金额变,仅更新 remark。
   */
  onRemarkInput: function (e) {
    const value = e.detail.value || '';
    this.setData({ remark: value });
  },

  onSubmitOrder: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url:
          '/pages-sub/user/login/login?redirect=' +
          encodeURIComponent('/pages-sub/order/order-confirm/order-confirm'),
      });
      return;
    }
    if (!this.data.order || !(this.data.order.items || []).length) {
      wx.showToast({ title: '购物车为空', icon: 'none' });
      return;
    }
    if (!this.data.selectedAddress || !this.data.selectedAddress.id) {
      wx.showToast({ title: '请选择收货地址', icon: 'none' });
      return;
    }

    this.setData({ isCreating: true });
    wx.showLoading({ title: '正在创建订单...' });

    orderStore
      .placeOrder({
        addressId: this.data.selectedAddress.id,
        remark: this.data.remark || undefined,
      })
      .then((order) => {
        this.setData({ isCreating: false, order });
        this.initiatePayment(order);
      })
      .catch((err) => {
        wx.hideLoading();
        this.setData({ isCreating: false });
        wx.showToast({
          title: (err && err.message) || '创建订单失败',
          icon: 'none',
        });
      });
  },

  initiatePayment: function (order) {
    this.setData({ isPaying: true });
    wx.showLoading({ title: '正在发起支付...' });

    paymentModule
      .requestPayment(order.id, order.totalAmount)
      .then((result) => {
        wx.hideLoading();
        this.setData({ isPaying: false });

        if (result.isSuccess()) {
          wx.showToast({ title: '支付成功', icon: 'success' });
        } else if (result.isCancelled()) {
          wx.showToast({ title: '已取消支付', icon: 'none' });
        } else {
          wx.showToast({ title: result.errorMessage || '支付失败', icon: 'none' });
        }
        setTimeout(() => {
          wx.redirectTo({ url: '/pages-sub/order/order-list/order-list' });
        }, 1500);
      })
      .catch(() => {
        wx.hideLoading();
        this.setData({ isPaying: false });
        wx.showToast({ title: '支付失败', icon: 'none' });
        setTimeout(() => {
          wx.redirectTo({ url: '/pages-sub/order/order-list/order-list' });
        }, 1500);
      });
  },

  goBack: function () {
    wx.navigateBack();
  },
});
