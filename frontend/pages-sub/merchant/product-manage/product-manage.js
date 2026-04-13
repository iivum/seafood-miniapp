const app = getApp();
const request = require('../../../utils/request.js');

Page({
  data: {
    products: [],
    isLoading: false,
    isEmpty: false,
    showAddModal: false,
    editingProduct: null,
    formData: {
      name: '',
      description: '',
      price: '',
      stock: '',
      category: '',
      imageUrl: ''
    }
  },

  onLoad: function() {
    this.loadProducts();
  },

  onShow: function() {
    this.loadProducts();
  },

  loadProducts: function() {
    this.setData({ isLoading: true });

    request({
      url: '/products',
      method: 'GET',
      needAuth: true
    })
    .then(res => {
      this.setData({
        products: res.products || [],
        isLoading: false,
        isEmpty: (res.products || []).length === 0
      });
    })
    .catch(err => {
      console.error('Load products failed', err);
      this.setData({ isLoading: false, isEmpty: true });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onAddProduct: function() {
    this.setData({
      showAddModal: true,
      editingProduct: null,
      formData: {
        name: '',
        description: '',
        price: '',
        stock: '',
        category: '',
        imageUrl: ''
      }
    });
  },

  onEditProduct: function(e) {
    const product = e.currentTarget.dataset.product;
    this.setData({
      showAddModal: true,
      editingProduct: product,
      formData: {
        name: product.name,
        description: product.description,
        price: String(product.price),
        stock: String(product.stock),
        category: product.category,
        imageUrl: product.imageUrl || ''
      }
    });
  },

  onDeleteProduct: function(e) {
    const product = e.currentTarget.dataset.product;

    wx.showModal({
      title: '确认删除',
      content: `确定要删除商品"${product.name}"吗？`,
      success: (res) => {
        if (res.confirm) {
          this.deleteProduct(product.id);
        }
      }
    });
  },

  deleteProduct: function(productId) {
    request({
      url: `/products/${productId}`,
      method: 'DELETE',
      needAuth: true
    })
    .then(() => {
      wx.showToast({ title: '删除成功', icon: 'success' });
      this.loadProducts();
    })
    .catch(err => {
      console.error('Delete product failed', err);
      wx.showToast({ title: '删除失败', icon: 'none' });
    });
  },

  onModalCancel: function() {
    this.setData({ showAddModal: false });
  },

  onFormInput: function(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
    this.setData({
      [`formData.${field}`]: value
    });
  },

  onSubmit: function() {
    const { formData, editingProduct } = this.data;

    if (!formData.name || !formData.price || !formData.stock) {
      wx.showToast({ title: '请填写完整信息', icon: 'none' });
      return;
    }

    const productData = {
      name: formData.name,
      description: formData.description,
      price: parseFloat(formData.price),
      stock: parseInt(formData.stock),
      category: formData.category || '未分类',
      imageUrl: formData.imageUrl,
      onSale: true
    };

    const url = editingProduct ? `/products/${editingProduct.id}` : '/products';
    const method = editingProduct ? 'PUT' : 'POST';

    request({
      url,
      method,
      data: productData,
      needAuth: true
    })
    .then(() => {
      wx.showToast({
        title: editingProduct ? '修改成功' : '添加成功',
        icon: 'success'
      });
      this.setData({ showAddModal: false });
      this.loadProducts();
    })
    .catch(err => {
      console.error('Save product failed', err);
      wx.showToast({ title: '保存失败', icon: 'none' });
    });
  },

  goBack: function() {
    wx.navigateBack();
  }
})
