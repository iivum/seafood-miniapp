const app = getApp();
const request = require('../../../utils/request.js');

Page({
  data: {
    users: [],
    isLoading: false,
    isEmpty: false,
    selectedRole: 'ALL',
    roleOptions: [
      { value: 'ALL', label: '全部' },
      { value: 'USER', label: '普通用户' },
      { value: 'ADMIN', label: '管理员' }
    ]
  },

  onLoad: function() {
    this.loadUsers();
  },

  onShow: function() {
    this.loadUsers();
  },

  onRoleChange: function(e) {
    const role = e.currentTarget.dataset.role;
    this.setData({ selectedRole: role });
    this.loadUsers();
  },

  loadUsers: function() {
    this.setData({ isLoading: true });

    request({
      url: '/api/users',
      method: 'GET',
      needAuth: true
    })
    .then(res => {
      let users = Array.isArray(res) ? res : [];
      if (this.data.selectedRole !== 'ALL') {
        users = users.filter(u => u.role === this.data.selectedRole);
      }
      this.setData({
        users: users,
        isLoading: false,
        isEmpty: users.length === 0
      });
    })
    .catch(err => {
      console.error('Load users failed', err);
      this.setData({ isLoading: false, isEmpty: true });
      wx.showToast({ title: '加载失败', icon: 'none' });
    });
  },

  onViewUser: function(e) {
    const user = e.currentTarget.dataset.user;
    wx.showModal({
      title: '用户详情',
      content: `ID: ${user.id}\n昵称: ${user.nickname || '-'}\n手机: ${user.phone || '-'}\n角色: ${user.role}\n注册时间: ${user.createdAt || '-'}`,
      showCancel: false,
      confirmText: '确定'
    });
  },

  onToggleRole: function(e) {
    const user = e.currentTarget.dataset.user;
    const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';

    wx.showModal({
      title: '确认修改',
      content: `将用户 "${user.nickname || user.id}" 的角色修改为 ${newRole === 'ADMIN' ? '管理员' : '普通用户'}？`,
      success: (res) => {
        if (res.confirm) {
          this.updateUserRole(user.id, newRole);
        }
      }
    });
  },

  updateUserRole: function(userId, newRole) {
    request({
      url: `/api/users/${userId}/role`,
      method: 'PUT',
      data: { role: newRole },
      needAuth: true
    })
    .then(() => {
      wx.showToast({ title: '修改成功', icon: 'success' });
      this.loadUsers();
    })
    .catch(err => {
      console.error('Update role failed', err);
      wx.showToast({ title: '修改失败', icon: 'none' });
    });
  },

  onDisableUser: function(e) {
    const user = e.currentTarget.dataset.user;

    wx.showModal({
      title: '确认禁用',
      content: `确定要禁用用户 "${user.nickname || user.id}" 吗？`,
      success: (res) => {
        if (res.confirm) {
          this.updateUserStatus(user.id, 'DISABLED');
        }
      }
    });
  },

  onEnableUser: function(e) {
    const user = e.currentTarget.dataset.user;

    wx.showModal({
      title: '确认启用',
      content: `确定要启用用户 "${user.nickname || user.id}" 吗？`,
      success: (res) => {
        if (res.confirm) {
          this.updateUserStatus(user.id, 'ACTIVE');
        }
      }
    });
  },

  updateUserStatus: function(userId, status) {
    request({
      url: `/api/users/${userId}/status`,
      method: 'PUT',
      data: { status: status },
      needAuth: true
    })
    .then(() => {
      wx.showToast({ title: '操作成功', icon: 'success' });
      this.loadUsers();
    })
    .catch(err => {
      console.error('Update status failed', err);
      wx.showToast({ title: '操作失败', icon: 'none' });
    });
  },

  getRoleText: function(role) {
    const map = { 'USER': '普通用户', 'ADMIN': '管理员', 'DISABLED': '已禁用' };
    return map[role] || role;
  },

  getRoleClass: function(role) {
    const map = {
      'USER': 'role-user',
      'ADMIN': 'role-admin',
      'DISABLED': 'role-disabled'
    };
    return map[role] || '';
  },

  goBack: function() {
    wx.navigateBack();
  }
})
