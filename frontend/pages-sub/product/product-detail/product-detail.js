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
    if (!product) return;
    cartStore
      .addItem(product.id, 1)
      .then(() => {
        wx.showToast({ title: '已加入购物车', icon: 'success' });
        recommendationModule.recordPurchase(product);
      })
      .catch((err) => {
        wx.showToast({
          title: (err && err.message) || '加入购物车失败',
          icon: 'none',
        });
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
