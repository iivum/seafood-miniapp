/**
 * 收藏网格列表页(收藏 + 浏览足迹)。OD 原型仪表盘只显数字,这里是"点数字进来"
 * 的完整列表——网格布局对齐 pages/index 首页商品 2 列 grid 惯例。
 */
const { FavoriteAPI } = require('../../../src/features/favorite/api');

Page({
  data: {
    items: [],
    isLoading: false,
    isEmpty: false,
  },

  onLoad: function () {
    return this.loadFavorites();
  },

  onPullDownRefresh: function () {
    return this.loadFavorites().finally(() => wx.stopPullDownRefresh());
  },

  loadFavorites: function () {
    this.setData({ isLoading: true });
    return FavoriteAPI.list()
      .then((items) => {
        this.setData({ items: items || [], isEmpty: !items || items.length === 0 });
      })
      .catch(() => {
        wx.showToast({ title: '加载收藏失败', icon: 'none' });
      })
      .finally(() => {
        this.setData({ isLoading: false });
      });
  },

  onRemoveFavorite: function (e) {
    const productId = e.currentTarget.dataset.id;
    FavoriteAPI.remove(productId)
      .then(() => this.loadFavorites())
      .catch(() => {
        wx.showToast({ title: '取消收藏失败', icon: 'none' });
      });
  },

  onItemTap: function (e) {
    const { id, available } = e.currentTarget.dataset;
    if (!available) {
      wx.showToast({ title: '该商品已下架', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: `/pages-sub/product/product-detail/product-detail?id=${id}` });
  },

  onBack: function () {
    wx.navigateBack();
  },
});
