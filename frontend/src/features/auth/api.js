/**
 * Runtime shim for features/auth/api.ts.
 */
const { post } = require('../../shared/api/request');

const AuthAPI = {
  wechatLogin(payload) {
    return post('/auth/wechat-login', payload);
  },
};

module.exports = { AuthAPI };
