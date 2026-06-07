/**
 * Runtime shim for features/order/api.ts.
 */
const { get, post } = require('../shared/api/request');

const OrderAPI = {
  list() {
    return get('/orders', { needAuth: true });
  },
  getById(id) {
    return get(`/orders/${encodeURIComponent(id)}`, { needAuth: true });
  },
  create(body) {
    return post('/orders', body, { needAuth: true });
  },
  cancel(id, reason) {
    return post(`/orders/${encodeURIComponent(id)}/cancel`, { reason }, { needAuth: true });
  },
  ship(id) {
    return post(`/orders/${encodeURIComponent(id)}/ship`, undefined, { needAuth: true });
  },
};

module.exports = { OrderAPI };
