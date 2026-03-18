const cartUtil = require('../../utils/cart.js');

Page({
  data: {
    cartItems: [],
    totalPrice: 0
  },

  onShow: function() {
    this.refreshCart();
  },

  refreshCart: function() {
    const items = cartUtil.getCart();
    let total = 0;
    items.forEach(item => {
      total += item.price * item.quantity;
    });
    this.setData({
      cartItems: items,
      totalPrice: total.toFixed(2)
    });
  },

  onPlus: function(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find(i => i.id === id);
    cartUtil.updateQuantity(id, item.quantity + 1);
    this.refreshCart();
  },

  onMinus: function(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find(i => i.id === id);
    if (item.quantity > 1) {
      cartUtil.updateQuantity(id, item.quantity - 1);
      this.refreshCart();
    }
  },

  onRemove: function(e) {
    const id = e.currentTarget.dataset.id;
    cartUtil.removeFromCart(id);
    this.refreshCart();
  },

  onCheckout: function() {
    if (this.data.cartItems.length === 0) return;

    const app = getApp();
    if (!app.globalData.userInfo) {
      // 未登录，跳转到登录页面
      wx.navigateTo({
        url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/cart/cart')
      });
      return;
    }

    // 检查是否选择了收货地址
    if (!this.data.selectedAddress) {
      wx.showToast({
        title: '请选择收货地址',
        icon: 'none'
      });
      return;
    }

    wx.showLoading({ title: '正在提交订单...' });
    
    const orderData = {
      userId: app.globalData.userInfo.id,
      items: this.data.cartItems.map(item => ({
        productId: item.id,
        productName: item.name,
        price: item.price.toString(), // 转换为字符串格式
        quantity: item.quantity,
        imageUrl: item.imageUrl || ''
      })),
      totalAmount: this.data.totalPrice.toString(), // 添加总金额字段
      shippingAddress: this.data.selectedAddress.getFullAddress(), // 使用选择的收货地址
      addressId: this.data.selectedAddress.id, // 地址ID
      remark: '' // 可选字段
    };

    request({
      url: '/orders',
      method: 'POST',
      data: orderData,
      needAuth: true  // 需要认证
    })
    .then(res => {
      wx.hideLoading();
      wx.showToast({ title: '下单成功', icon: 'success' });
      cartUtil.clearCart();
      this.setData({ selectedAddress: null }); // 清空选择的地址
      setTimeout(() => {
        wx.navigateTo({
          url: '/pages/order-list/order-list'
        });
      }, 1500);
    })
    .catch(err => {
      wx.hideLoading();
      console.error('Checkout failed', err);
      if (err.statusCode !== 401) { // 401错误已在request.js中处理
        wx.showToast({
          title: '下单失败，请重试',
          icon: 'none'
        });
      }
    });
  },

  // 选择收货地址
  selectAddress: function() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      return;
    }

    // 跳转到地址列表页面，选择模式
    const selectedAddress = this.data.selectedAddress || null;
    wx.navigateTo({
      url: '/pages/address/address-list?selectMode=true&selectedAddress=' + 
           encodeURIComponent(selectedAddress ? JSON.stringify(selectedAddress) : '')
    });
  },

  // 页面显示时刷新数据
  onShow: function() {
    this.refreshCart();
    
    // 从地址列表页面返回后，检查是否有选中的地址
    const pages = getCurrentPages();
    const currentPage = pages[pages.length - 1];
    
    // 如果当前页面有选中的地址，使用它
    if (currentPage.selectedAddressFromList) {
      this.setData({
        selectedAddress: currentPage.selectedAddressFromList
      });
      currentPage.selectedAddressFromList = null; // 清空选择
    }
  }

  goToIndex: function() {
    wx.switchTab({ url: '/pages/index/index' });
  }
})
