/**
 * Runtime shim for features/productView/api.ts.
 */
const { get, post } = require('../../shared/api/request');

const ProductViewAPI = {
  record(productId) {
    return post(`/product-views/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  list() {
    return get('/product-views', { needAuth: true });
  },
};

module.exports = { ProductViewAPI };
