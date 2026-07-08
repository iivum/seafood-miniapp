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
  // mp-08 状态机 5 操作端点(路线图 2.9)—— .ts 源早已实现,.js shim 此前漏同步,
  // 导致 OrderActionRow 展示层修好后点击 pay/remindShip/confirmReceive/reorder
  // 全部 TypeError(mp-od-prototype-alignment mp-08 诊断发现)。
  pay(id, paymentMethod = 'wechat') {
    return post(`/orders/${encodeURIComponent(id)}/pay`, { paymentMethod }, { needAuth: true });
  },
  remindShip(id) {
    return post(`/orders/${encodeURIComponent(id)}/remind-ship`, undefined, { needAuth: true });
  },
  confirmReceive(id) {
    return post(`/orders/${encodeURIComponent(id)}/confirm-receive`, undefined, { needAuth: true });
  },
  rebuy(id) {
    return post(`/orders/${encodeURIComponent(id)}/rebuy`, undefined, { needAuth: true });
  },
  requestRefund(id, body) {
    return post(`/orders/${encodeURIComponent(id)}/refund`, body, { needAuth: true });
  },
};

module.exports = { OrderAPI };
