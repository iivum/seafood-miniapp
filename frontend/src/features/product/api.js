/**
 * Runtime shim for features/product/api.ts.
 */
const { get } = require('../shared/api/request');

const ProductAPI = {
  list(params) {
    const qs = new URLSearchParams();
    qs.set('page', String(params.page));
    qs.set('pageSize', String(params.pageSize));
    if (params.category) qs.set('category', params.category);
    if (params.keyword) qs.set('keyword', params.keyword);
    return get(`/products?${qs.toString()}`);
  },
  getById(id) {
    return get(`/products/${encodeURIComponent(id)}`);
  },
};

module.exports = { ProductAPI };
