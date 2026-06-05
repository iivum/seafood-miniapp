/**
 * Order confirm / detail page — wired to the new `features/order`
 * store per OpenSpec §8.5.
 *
 * - If a query `id` is present, the page loads the order detail via
 *   `orderStore.loadById(id)`.
 * - Otherwise (user is checking out from the cart) the page calls
 *   `orderStore.placeOrder({addressId})` to create the order, which
 *   also clears the cart as a side effect.
 */
const { orderStore } = require('../../../src/features/order/store');
const { cartStore } = require('../../../src/features/cart/store');
const { paymentModule } = require('../../../src/modules/payment/payment.js');

Page({
  data: {
    order: null,
    selectedAddress: null,
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
        const total = (cart.items || []).reduce(
          (sum, it) => sum + (it.unitPrice || 0) * it.quantity,
          0,
        );
        this.setData({
          order: {
            id: null,
            totalAmount: total,
            items: (cart.items || []).map((it) => ({
              productId: it.productId,
              productName: it.productName || it.productId,
              unitPrice: it.unitPrice || 0,
              quantity: it.quantity,
            })),
            status: 'PENDING',
          },
        });
      })
      .catch(() => {
        // best-effort: leave order as null so the empty-state renders
      });
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
      .placeOrder({ addressId: this.data.selectedAddress.id })
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
