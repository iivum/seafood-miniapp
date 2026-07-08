/**
 * 浏览足迹列表页(收藏 + 浏览足迹)。纯浏览记录,无操作按钮(design.md:
 * 只看,不可编辑)——列表布局,后端已按 viewedAt 降序返回,不再本地重排。
 */
const { ProductViewAPI } = require('../../../src/features/productView/api');

Page({
  data: {
    items: [],
    isLoading: false,
    isEmpty: false,
  },

  onLoad: function () {
    return this.loadFootprints();
  },

  onPullDownRefresh: function () {
    return this.loadFootprints().finally(() => wx.stopPullDownRefresh());
  },

  loadFootprints: function () {
    this.setData({ isLoading: true });
    return ProductViewAPI.list()
      .then((items) => {
        this.setData({ items: items || [], isEmpty: !items || items.length === 0 });
      })
      .catch(() => {
        wx.showToast({ title: '加载足迹失败', icon: 'none' });
      })
      .finally(() => {
        this.setData({ isLoading: false });
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
