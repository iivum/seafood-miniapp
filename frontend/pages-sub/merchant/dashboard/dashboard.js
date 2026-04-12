const app = getApp();
const request = require('../../../utils/request.js');

Page({
  data: {
    // Stats
    todayRevenue: 0,
    todayOrders: 0,
    totalProducts: 0,
    totalUsers: 0,
    // Trends (mock)
    revenueChange: 0,
    ordersChange: 0,
    // Recent orders
    recentOrders: [],
    // Loading
    isLoading: false
  },

  onLoad: function() {
    this.loadDashboard();
  },

  onShow: function() {
    this.loadDashboard();
  },

  loadDashboard: function() {
    this.setData({ isLoading: true });

    // Fetch multiple data sources in parallel
    Promise.all([
      this.fetchOrderStats(),
      this.fetchProductStats(),
      this.fetchUserStats()
    ])
    .then(() => {
      this.setData({ isLoading: false });
    })
    .catch(err => {
      console.error('Load dashboard failed', err);
      this.setData({ isLoading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  fetchOrderStats: function() {
    return new Promise((resolve, reject) => {
      // Mock data - in production, this would call the stats API
      setTimeout(() => {
        this.setData({
          todayRevenue: 12345.67,
          todayOrders: 89,
          revenueChange: 12.5,
          ordersChange: 8.3
        });
        resolve();
      }, 300);
    });
  },

  fetchProductStats: function() {
    return new Promise((resolve, reject) => {
      request({
        url: '/products',
        method: 'GET',
        needAuth: true
      })
      .then(res => {
        this.setData({
          totalProducts: (res.products || []).length
        });
        resolve();
      })
      .catch(() => {
        this.setData({ totalProducts: 0 });
        resolve(); // Don't fail the whole dashboard
      });
    });
  },

  fetchUserStats: function() {
    return new Promise((resolve, reject) => {
      request({
        url: '/api/users',
        method: 'GET',
        needAuth: true
      })
      .then(res => {
        this.setData({
          totalUsers: Array.isArray(res) ? res.length : 0
        });
        resolve();
      })
      .catch(() => {
        this.setData({ totalUsers: 0 });
        resolve();
      });
    });
  },

  formatMoney: function(amount) {
    if (amount >= 10000) {
      return (amount / 10000).toFixed(1) + '万';
    }
    return amount.toFixed(2);
  },

  goBack: function() {
    wx.navigateBack();
  }
})
