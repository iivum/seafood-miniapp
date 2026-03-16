const request = require('../../utils/request.js');

Page({
  data: {
    categories: [
      { name: '鱼类', icon: '/images/icons/fish.png' },
      { name: '虾蟹', icon: '/images/icons/shrimp.png' },
      { name: '贝类', icon: '/images/icons/shell.png' },
      { name: '活鲜', icon: '/images/icons/live.png' }
    ],
    products: []
  },

  onLoad: function() {
    this.fetchProducts();
  },

  fetchProducts: function() {
    request({ url: '/products' })
      .then(res => {
        this.setData({ products: res });
      })
      .catch(err => {
        console.error('Fetch products failed', err);
      });
  },

  goToDetail: function(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/product-detail/product-detail?id=${id}`
    });
  }
})
