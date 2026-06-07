/**
 * Order list page — wired to the new `features/order` store per
 * OpenSpec §8.5. The store pulls from `GET /api/orders`.
 */
const { orderStore } = require('../../../src/features/order/store');

Page({
  data: {
    orders: [],
    isLoading: false,
    isError: false,
    errorMessage: '',
  },

  onShow: function () {
    this.fetchOrders();
  },

  fetchOrders: function () {
    this.setData({ isLoading: true, isError: false });
    orderStore
      .refresh()
      .then((orders) => this.setData({ orders, isLoading: false }))
      .catch((err) => {
        const message = err && err.message ? err.message : '加载订单失败';
        this.setData({ isLoading: false, isError: true, errorMessage: message });
        wx.showToast({ title: message, icon: 'none' });
      });
  },

  onCancel: function (e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认取消订单',
      content: '确定要取消这个订单吗？',
      success: (res) => {
        if (res.confirm) {
          orderStore
            .cancel(id, '用户取消订单')
            .then(() => {
              wx.showToast({ title: '已取消', icon: 'success' });
              this.fetchOrders();
            })
            .catch((err) => {
              wx.showToast({
                title: (err && err.message) || '取消失败',
                icon: 'none',
              });
            });
        }
      },
    });
  },

  onOrderTap: function (e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: '/pages-sub/order/order-confirm/order-confirm?id=' + encodeURIComponent(id),
    });
  },
});
