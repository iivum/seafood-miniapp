/**
 * frontend/utils/order-actions.js —— 共享 order-action 分发(mp-cross-screen-cleanup
 * design.md D7)。
 *
 * 抽取自 order-list.js/order-detail.js 各自独立实现的约 120 行近乎相同的 action 分发
 * 逻辑(pay/cancelOrder/remindShip/reorder/deleteOrder/申请退款 + 409/403/404 错误
 * toast)。这份重复正是 mp-backend-contract-gaps 那次生产 bug 的根因——`err.status`
 * 改成 `err.statusCode || err.status` 时一份文件改了、另一份没同步,直到诊断时才发现
 * 两边早就分叉。以后任何一处分发逻辑的修复只改这一个文件。
 *
 * 临界修复(design 研究阶段发现,不是可选项):两个页面原来的"申请退款"实现都是
 * 错的,不只 order-list 的占位实现——后端 RefundRequest DTO
 * (backend/src/main/java/com/seafood/order/api/dto/RefundRequest.java)要求
 * amount(@NotNull @DecimalMin("0.01"))和 reason 都必填,但 order-detail.js 原
 * applyRefund() 的裸 request() 调用从未传 amount,对真实后端会 400——只是被
 * order-detail.test.js 里那个不校验 request body 内容的 mock(只判 url/method)掩盖
 * 了。这里统一改走 orderStore.requestRefund(id, amount, reason):该方法已存在、已在
 * store.test.ts 测试过,自带乐观更新(status=REFUNDING)+ 失败回滚,且正确把 amount
 * 转发给后端。amount 取 order.totalAmount(全额退款——两个页面都没有让用户自填退款
 * 金额的 UI,建这个 UI 不在本次任务范围内)。
 *
 * 调用方(order-list.js/order-detail.js)的 onActionTap 负责:
 *   - 调用前解析出完整的 order 对象(不只是 id)——退款分支需要 order.totalAmount,
 *     order-list.js 要多一行从 this.data.orders 里按 id 查找。
 *   - 传入 refresh callback:order-list 传 fetchOrders,order-detail 传
 *     refreshOrder。
 *   - viewTracking(order-list 跳转详情页 / order-detail 复制物流单号到剪贴板)
 *     在调用本模块前自行短路处理,不进入这里的 switch —— 这是两页合理的、非重复的
 *     差异(order-list 没有内联物流展示的 UI),不是需要统一的重复逻辑。
 */
const { OrderAPI } = require('../src/features/order/api');
const { orderStore } = require('../src/features/order/store');
const { cartStore } = require('../src/features/cart/store');

function confirmThenCancel(orderId) {
  return new Promise((resolve, reject) => {
    wx.showModal({
      title: '确认取消订单',
      content: '取消后无法恢复,确定要取消吗?',
      success: (res) => {
        if (res.confirm) {
          orderStore.cancel(orderId, '用户取消订单').then(resolve).catch(reject);
        } else {
          reject({ message: '用户取消', cancelled: true });
        }
      },
      fail: reject,
    });
  });
}

function confirmThenDelete(orderId) {
  return new Promise((resolve, reject) => {
    wx.showModal({
      title: '删除订单',
      content: '删除后无法恢复,确定删除吗?',
      success: (res) => {
        if (res.confirm) {
          // 删除 = 后端 cancel + 隐藏(本迭代没 delete 端点,走 cancel 替代,两页原来的惯例)
          orderStore.cancel(orderId, '用户删除订单').then(resolve).catch(reject);
        } else {
          reject({ message: '用户取消', cancelled: true });
        }
      },
      fail: reject,
    });
  });
}

/**
 * 申请退款/售后——两个 action id(requestRefund/afterSale)走同一行为。
 * 关键修复:调 orderStore.requestRefund,不是裸 request()/OrderAPI.requestRefund,
 * 且必须带 order.totalAmount(后端 amount 字段必填)。
 */
function confirmThenRefund(order) {
  return new Promise((resolve, reject) => {
    wx.showModal({
      title: '申请退款',
      content: '确定要申请退款?商家将在 24 小时内处理',
      success: (res) => {
        if (res.confirm) {
          orderStore
            .requestRefund(order.id, order.totalAmount, '用户主动申请')
            .then(resolve)
            .catch(reject);
        } else {
          reject({ message: '用户取消', cancelled: true });
        }
      },
      fail: reject,
    });
  });
}

function confirmThenReceive(orderId) {
  return new Promise((resolve, reject) => {
    wx.showModal({
      title: '确认收货',
      content: '请确认已收到商品,确认后无法再申请退款',
      success: (res) => {
        if (res.confirm) {
          OrderAPI.confirmReceive(orderId).then(resolve).catch(reject);
        } else {
          reject({ message: '用户取消', cancelled: true });
        }
      },
      fail: reject,
    });
  });
}

async function handleRebuy(orderId) {
  wx.hideLoading();
  wx.showLoading({ title: '加入购物车...', mask: true });
  try {
    const items = await OrderAPI.rebuy(orderId);
    // 把 rebuy 返回的 cart items 加到 cart store。
    // 抽取时发现:cartStore 真实只有 addItem(productId, quantity),没有 add ——
    // 这里此前(两份原始实现都)一直裸调不存在的 cartStore.add,会 TypeError,
    // 从改动前就一直是坏的,被测试用同样虚构的 { add: ... } mock 掩盖,同
    // requestRefund 缺 amount 是同一类"测试 mock 形状不真实"问题,顺带修。
    if (items && items.length) {
      for (const it of items) {
        await cartStore.addItem(it.productId, it.quantity);
      }
    }
    wx.hideLoading();
    wx.showToast({ title: `已加入 ${items.length} 件`, icon: 'success' });
    setTimeout(() => {
      wx.switchTab({ url: '/pages/cart/cart' });
    }, 800);
  } catch (err) {
    wx.hideLoading();
    wx.showToast({ title: (err && err.message) || '加入购物车失败', icon: 'none' });
  }
}

/**
 * 共享 action 分发入口。order-list.js/order-detail.js 的 onActionTap 都调这里
 * (viewTracking 除外,两页各自短路处理,见文件头注释)。
 *
 * @param {string} action - OrderActionRow 的 action id(如 'pay'/'cancelOrder')
 * @param {object} order - 完整订单对象(不只是 id——退款分支需要 order.totalAmount)
 * @param {() => Promise<any>} refresh - 成功后重新拉取数据的回调
 *   (order-list 传 this.fetchOrders.bind(this),order-detail 传
 *   this.refreshOrder.bind(this))
 */
async function dispatchOrderAction(action, order, refresh) {
  const orderId = order && order.id;
  wx.showLoading({ title: '处理中...', mask: true });
  try {
    switch (action) {
      case 'pay':
        await OrderAPI.pay(orderId);
        wx.showToast({ title: '已支付', icon: 'success' });
        break;
      case 'cancelOrder':
        await confirmThenCancel(orderId);
        break;
      case 'remindShip':
        await OrderAPI.remindShip(orderId);
        wx.showToast({ title: '已提醒商家发货', icon: 'success' });
        break;
      case 'reorder':
        await handleRebuy(orderId);
        return; // handleRebuy 自行处理 loading/toast/跳转,不刷新
      case 'confirmReceipt':
        await confirmThenReceive(orderId);
        wx.showToast({ title: '已确认收货', icon: 'success' });
        break;
      case 'requestRefund':
      case 'afterSale':
        await confirmThenRefund(order);
        wx.showToast({ title: '退款申请已提交', icon: 'success' });
        break;
      case 'withdrawRefund':
        wx.showToast({ title: '撤回功能开发中', icon: 'none' });
        break;
      case 'review':
        wx.showToast({ title: '评价功能开发中', icon: 'none' });
        break;
      case 'deleteOrder':
        await confirmThenDelete(orderId);
        break;
      default:
        wx.showToast({ title: '未知操作', icon: 'none' });
    }
    wx.hideLoading();
    await refresh();
  } catch (err) {
    wx.hideLoading();
    // OrderAPI(src/shared/api/request.js ApiError)真实只带 .statusCode,没有 .status——
    // 兼容读两者(同 mp-backend-contract-gaps 修复后两个页面已有的正确写法)。
    const status = err && (err.statusCode || err.status);
    if (status === 409) {
      wx.showToast({ title: '订单状态已变更', icon: 'none' });
      await refresh();
    } else if (status === 403 || status === 404) {
      wx.showToast({ title: '订单不存在或无权限', icon: 'none' });
    } else {
      const msg = (err && err.message) || '操作失败';
      wx.showToast({ title: msg, icon: 'none' });
    }
  }
}

module.exports = { dispatchOrderAction };
