/**
 * Product detail page — wired to the new `features/product` API
 * per OpenSpec §8.5. Uses `ProductAPI.getById` for the detail and
 * `cartStore.addItem` for the "add to cart" action.
 */
const { ProductAPI } = require('../../../src/features/product/api');
const { cartStore } = require('../../../src/features/cart/store');
const { recommendationModule } = require('../../../src/modules/recommendation/recommendation.js');

Page({
  data: {
    product: null,
    recommendations: [],
    isLoading: true,
    isError: false,
    errorMessage: '',
    /** 收藏状态(本地,无后端)— 占位 */
    favorited: false,
    isAdding: false,
  },

  onLoad: function (options) {
    if (options && options.id) {
      this.fetchProductDetail(options.id);
    }
  },

  fetchProductDetail: function (id) {
    this.setData({ isLoading: true, isError: false });
    ProductAPI.getById(id)
      .then((product) => {
        this.setData({ product, isLoading: false });
        this.fetchRecommendations(product);
      })
      .catch((err) => {
        this.setData({
          isLoading: false,
          isError: true,
          errorMessage: (err && err.message) || '加载商品失败',
        });
        if (!err || err.statusCode !== 401) {
          wx.showToast({ title: '加载商品失败', icon: 'none' });
        }
      });
  },

  fetchRecommendations: function (product) {
    recommendationModule
      .getProductRecommendations(product)
      .then((recommendations) => {
        const valid = (recommendations || []).filter((rec) => rec.products.length > 0);
        this.setData({ recommendations: valid });
      })
      .catch(() => {
        // best-effort
      });
  },

  onAddToCart: function () {
    const product = this.data.product;
    if (!product || product.stock === 0) return;
    if (this.data.isAdding) return;
    this.setData({ isAdding: true });
    cartStore
      .addItem(product.id, this.data.quantity || 1)
      .then(() => {
        wx.showToast({ title: '已加入购物车', icon: 'success' });
        recommendationModule.recordPurchase(product);
      })
      .catch((err) => {
        wx.showToast({
          title: (err && err.message) || '加入购物车失败',
          icon: 'none',
        });
      })
      .then(() => this.setData({ isAdding: false }));
  },

  /** sprint-1-closure 5.4 — 立即购买,带 direct_buy 标记跳订单确认页 */
  onBuyNow: function () {
    const product = this.data.product;
    if (!product) return;
    if (product.stock === 0) {
      wx.showToast({ title: '已售罄', icon: 'none' });
      return;
    }
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({ url: '/pages-sub/user/login/login' });
      return;
    }
    // 先加入购物车再跳订单确认(后端 placeOrder 从 cart 读)
    cartStore
      .addItem(product.id, this.data.quantity || 1)
      .then(() => {
        recommendationModule.recordPurchase(product);
        wx.navigateTo({
          url: '/pages-sub/order/order-confirm/order-confirm?source=direct_buy',
        });
      })
      .catch(() => {
        wx.showToast({ title: '请稍后重试', icon: 'none' });
      });
  },

  onCustomerService: function () {
    wx.showModal({ title: '客服', content: '客服微信:seafood-cs(占位)', showCancel: false });
  },

  onToggleFavorite: function () {
    this.setData({ favorited: !this.data.favorited });
    wx.showToast({
      title: this.data.favorited ? '已收藏' : '已取消收藏',
      icon: 'none',
    });
  },

  onBuyNow: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({ url: '/pages-sub/user/login/login' });
      return;
    }
    const product = this.data.product;
    if (!product) return;
    cartStore
      .addItem(product.id, 1)
      .then(() => {
        recommendationModule.recordPurchase(product);
        wx.switchTab({ url: '/pages/cart/cart' });
      })
      .catch(() => {
        wx.showToast({ title: '请稍后重试', icon: 'none' });
      });
  },

  goToHome: function () {
    wx.switchTab({ url: '/pages/index/index' });
  },

  goToCart: function () {
    wx.switchTab({ url: '/pages/cart/cart' });
  },

  goToProductDetail: function (e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: '/pages-sub/product/product-detail/product-detail?id=' + id,
    });
  },
});
