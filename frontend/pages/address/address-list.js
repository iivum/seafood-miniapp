const app = getApp();
const request = require('../../utils/request.js');

Page({
  data: {
    addresses: [],
    selectMode: false, // 是否选择模式（从订单结算页跳转）
    selectedAddress: null
  },

  onLoad: function(options) {
    // 检查是否从订单结算页跳转
    if (options.selectMode === 'true') {
      this.setData({
        selectMode: true,
        selectedAddress: options.selectedAddress ? JSON.parse(options.selectedAddress) : null
      });
    }
    
    this.loadAddresses();
  },

  onShow: function() {
    this.loadAddresses();
  },

  // 加载地址列表
  loadAddresses: function() {
    const userInfo = app.globalData.userInfo;
    if (!userInfo) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      return;
    }

    request({
      url: `/addresses/user/${userInfo.id}`,
      needAuth: true
    })
    .then(res => {
      this.setData({
        addresses: res
      });
    })
    .catch(err => {
      console.error('加载地址列表失败:', err);
      wx.showToast({
        title: '加载地址失败',
        icon: 'none'
      });
    });
  },

  // 添加新地址
  addNewAddress: function() {
    wx.navigateTo({
      url: '/pages/address/address-edit'
    });
  },

  // 编辑地址
  editAddress: function(e) {
    const address = e.currentTarget.dataset.address;
    wx.navigateTo({
      url: `/pages/address/address-edit?id=${address.id}`
    });
  },

  // 删除地址
  deleteAddress: function(e) {
    const address = e.currentTarget.dataset.address;
    
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个收货地址吗？',
      success: (res) => {
        if (res.confirm) {
          request({
            url: `/addresses/${address.id}`,
            method: 'DELETE',
            needAuth: true
          })
          .then(() => {
            wx.showToast({
              title: '删除成功',
              icon: 'success'
            });
            this.loadAddresses();
          })
          .catch(err => {
            console.error('删除地址失败:', err);
            wx.showToast({
              title: '删除失败',
              icon: 'none'
            });
          });
        }
      }
    });
  },

  // 选择地址
  selectAddress: function(e) {
    if (!this.data.selectMode) {
      return;
    }

    const address = e.currentTarget.dataset.address;
    
    // 使用页面栈传递选择结果
    const pages = getCurrentPages();
    const cartPage = pages[pages.length - 2]; // 上一个页面是购物车
    
    if (cartPage && cartPage.route === 'pages/cart/cart') {
      // 将选择结果传递给购物车页面
      cartPage.setData({
        selectedAddress: address
      });
    }
    
    wx.navigateBack();
  },

  // 设置默认地址
  setDefaultAddress: function(e) {
    const address = e.currentTarget.dataset.address;
    
    request({
      url: `/addresses/${address.id}/default`,
      method: 'PUT',
      needAuth: true
    })
    .then(() => {
      wx.showToast({
        title: '设置默认地址成功',
        icon: 'success'
      });
      this.loadAddresses();
    })
    .catch(err => {
      console.error('设置默认地址失败:', err);
      wx.showToast({
        title: '设置失败',
        icon: 'none'
      });
    });
  }
})