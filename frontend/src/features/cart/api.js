/**
 * Runtime shim for features/cart/api.ts — see request.js for the
 * pattern. The WeChat mini-program runtime loads `.js` files.
 */
const { get, post, put, del, patch } = require('../../shared/api/request');

const CartAPI = {
  get() {
    return get('/cart', { needAuth: true });
  },
  addItem(body) {
    return post('/cart/items', body, { needAuth: true });
  },
  updateItem(productId, body) {
    return put(`/cart/items/${encodeURIComponent(productId)}`, body, { needAuth: true });
  },
  removeItem(productId) {
    return del(`/cart/items/${encodeURIComponent(productId)}`, { needAuth: true });
  },
  toggleItem(productId) {
    return patch(`/cart/items/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  clear() {
    return del('/cart', { needAuth: true });
  },
};

module.exports = { CartAPI };
