/**
 * Runtime shim for features/user/api.ts.
 *
 * mp-od-10 login-userinfo:此前完全不存在——auth/store.js 需要 UserAPI.me()
 * 补拉登录后的真实用户信息(后端 wechat-login 响应体没有 user 字段)。只实现
 * me(),.ts 源码里的 listAddresses/addAddress/updateAddress/removeAddress 是
 * 死代码(cart.js 已确认走的是后端实际路由 /addresses,不是 /users/me/addresses),
 * 不重复造轮子。
 */
const { get } = require('../../shared/api/request');

const UserAPI = {
  me() {
    return get('/users/me', { needAuth: true });
  },
};

module.exports = { UserAPI };
