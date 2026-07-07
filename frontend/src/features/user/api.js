/**
 * Runtime shim for features/user/api.ts.
 *
 * mp-od-10 login-userinfo:此前完全不存在——auth/store.js 需要 UserAPI.me()
 * 补拉登录后的真实用户信息(后端 wechat-login 响应体没有 user 字段)。.ts 源码里的
 * listAddresses/addAddress/updateAddress/removeAddress 是死代码(cart.js 已确认
 * 走的是后端实际路由 /addresses,不是 /users/me/addresses),不重复造轮子;
 * bindPhone() 是 align-mp-login-with-od 新增,auth/store.js 的 Step2 手机号
 * 绑定会真实调用它,不是死代码。
 */
const { get, patch } = require('../../shared/api/request');

const UserAPI = {
  me() {
    return get('/users/me', { needAuth: true });
  },
  bindPhone(code) {
    return patch('/users/me/phone', { code }, { needAuth: true });
  },
};

module.exports = { UserAPI };
