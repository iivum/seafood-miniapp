const app = getApp();
const request = require('../../utils/request.js');

Page({
  data: {
    address: {},
    regionList: ['请选择', '请选择', '请选择'],
    regionIndex: [0, 0, 0],
    isSaving: false,
    errorMsg: '',
    isEdit: false
  },

  onLoad: function(options) {
    if (options.id) {
      this.setData({
        isEdit: true,
        address: {
          id: options.id
        }
      });
      this.loadAddressDetail(options.id);
    }
  },

  // 加载地址详情
  loadAddressDetail: function(id) {
    request({
      url: `/addresses/${id}`,
      needAuth: true
    })
    .then(res => {
      this.setData({
        address: res
      });
      this.updateRegionDisplay(res);
    })
    .catch(err => {
      console.error('加载地址详情失败:', err);
      wx.showToast({
        title: '加载地址失败',
        icon: 'none'
      });
    });
  },

  // 更新地区显示
  updateRegionDisplay: function(address) {
    if (address.province && address.city && address.district) {
      // 这里简化处理，实际应该加载完整的省市区列表
      this.setData({
        regionIndex: [1, 1, 1], // 模拟已选择
        regionList: [address.province, address.city, address.district]
      });
    }
  },

  // 地区选择变化
  onRegionChange: function(e) {
    const region = e.detail.value;
    const address = this.data.address;
    
    address.province = region[0];
    address.city = region[1];
    address.district = region[2];
    
    this.setData({
      address: address,
      regionIndex: region,
      errorMsg: ''
    });
  },

  // 表单验证
  validateForm: function(formData) {
    const errors = [];

    // 验证姓名
    if (!formData.name || formData.name.trim().length < 2) {
      errors.push('请输入正确的收件人姓名');
    }

    // 验证手机号
    if (!formData.phone || !/^1[3-9]\d{9}$/.test(formData.phone)) {
      errors.push('请输入正确的手机号');
    }

    // 验证地区
    if (!formData.province || formData.province === '请选择') {
      errors.push('请选择所在地区');
    }

    // 验证详细地址
    if (!formData.detailAddress || formData.detailAddress.trim().length < 5) {
      errors.push('请输入详细的收货地址');
    }

    return errors;
  },

  // 保存地址
  saveAddress: function(e) {
    const formData = e.detail.value;
    const userInfo = app.globalData.userInfo;
    
    // 验证表单
    const errors = this.validateForm(formData);
    if (errors.length > 0) {
      this.setData({
        errorMsg: errors[0]
      });
      return;
    }

    this.setData({
      isSaving: true,
      errorMsg: ''
    });

    // 构建地址数据
    const addressData = {
      id: this.data.address.id || null,
      userId: userInfo.id,
      name: formData.name.trim(),
      phone: formData.phone,
      province: formData.region[0],
      city: formData.region[1],
      district: formData.region[2],
      detailAddress: formData.detailAddress.trim(),
      isDefault: formData.isDefault || false
    };

    // 保存地址
    const url = this.data.isEdit ? `/addresses/${addressData.id}` : '/addresses';
    const method = this.data.isEdit ? 'PUT' : 'POST';

    request({
      url: url,
      method: method,
      data: addressData,
      needAuth: true
    })
    .then(res => {
      this.setData({
        isSaving: false
      });
      
      wx.showToast({
        title: this.data.isEdit ? '修改成功' : '添加成功',
        icon: 'success'
      });
      
      setTimeout(() => {
        wx.navigateBack();
      }, 1500);
    })
    .catch(err => {
      this.setData({
        isSaving: false
      });
      
      console.error('保存地址失败:', err);
      wx.showToast({
        title: this.data.isEdit ? '修改失败' : '添加失败',
        icon: 'none'
      });
    });
  },

  // 删除地址
  deleteAddress: function() {
    if (!this.data.isEdit) {
      return;
    }

    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个收货地址吗？',
      success: (res) => {
        if (res.confirm) {
          request({
            url: `/addresses/${this.data.address.id}`,
            method: 'DELETE',
            needAuth: true
          })
          .then(() => {
            wx.showToast({
              title: '删除成功',
              icon: 'success'
            });
            
            setTimeout(() => {
              wx.navigateBack();
            }, 1500);
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
  }
})