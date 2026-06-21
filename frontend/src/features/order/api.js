/**
 * Runtime shim for features/order/api.ts.
 */
const { get, post } = require('../../shared/api/request');

const OrderAPI = {
  list() {
    // 后端 GET /api/orders 返 Spring Page<Order> { content[] },非裸数组。
    // 解包 content,否则 orderStore.orders 拿到 Page 对象 → order-list 渲空(C5 mp-08 实证)。
    return get('/orders', { needAuth: true }).then((res) =>
      Array.isArray(res) ? res : res && res.content ? res.content : res,
    );
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
