/**
 * OrderActionRow — mp 运行时组件(小程序运行时无 TS 编译,只认 .js)。
 *
 * 根因(openspec change mp-od-prototype-alignment,brief
 * `.superpowers/sdd/mp-od-7-order-list-brief.md` "如果发现问题"):这个组件此前
 * 只有 `index.ts`(纯逻辑 `getActionsFor()` + 7 状态映射,已在
 * `OrderActionRow.test.ts` 100% 覆盖测试)+ `index.wxml`/`.wxss`/`.json` 全部缺失,
 * 也没有任何页面在 usingComponents 里接线 —— `<order-action-row>` 在
 * order-list.wxml 第 123-127 行渲染成 0 内容的空标签,不报错、不崩溃,只是
 * 静默不显示任何按钮(WeChat DevTools 对未注册自定义标签的默认行为)。
 * 影响面:每一个订单卡片,所有状态的取消/付款/确认收货/退款等核心操作全部
 * 不可点击 —— spec `mini-program/spec.md:262`
 * "Order list and detail (mp-08) customer action row" 要求 MUST 渲染,
 * 此前 0% 满足。
 *
 * 状态 → 按钮矩阵与 `.ts` 端同名同步(同 RefundSheet/index.js validateRefundInput
 * 的既有惯例:小程序运行时无 TS 编译,这里写 JS 版同步,不改变已测试通过的
 * 状态→按钮语义/顺序/variant 归类)。
 */
const MAP = {
  PENDING: [
    { id: 'cancelOrder', label: '取消订单', variant: 'secondary' },
    { id: 'pay', label: '立即付款', variant: 'primary' },
  ],
  PAID: [
    { id: 'remindShip', label: '提醒发货', variant: 'secondary' },
    { id: 'requestRefund', label: '申请退款', variant: 'secondary' },
  ],
  SHIPPED: [
    { id: 'viewTracking', label: '查看物流', variant: 'secondary' },
    { id: 'confirmReceipt', label: '确认收货', variant: 'primary' },
  ],
  COMPLETED: [
    { id: 'review', label: '评价', variant: 'secondary' },
    { id: 'reorder', label: '再次购买', variant: 'primary' },
    { id: 'afterSale', label: '申请售后', variant: 'secondary' },
  ],
  CANCELLED: [
    { id: 'deleteOrder', label: '删除', variant: 'danger' },
    { id: 'reorder', label: '再次购买', variant: 'primary' },
  ],
  REFUNDING: [
    { id: 'refundPending', label: '退款处理中', variant: 'disabled' },
  ],
  REFUNDED: [
    { id: 'deleteOrder', label: '删除', variant: 'danger' },
    { id: 'reorder', label: '再次购买', variant: 'primary' },
  ],
};

function getActionsFor(status) {
  return MAP[status] || [];
}

Component({
  options: {
    addGlobalClass: true,
  },
  properties: {
    status: {
      type: String,
      value: 'PENDING',
    },
  },
  data: {
    actions: MAP.PENDING,
  },
  observers: {
    status(status) {
      this.setData({ actions: getActionsFor(status) });
    },
  },
  methods: {
    onTap(e) {
      const id = e.currentTarget.dataset.id;
      // disabled 按钮(REFUNDING 唯一态"退款处理中")不触发 action 事件,
      // 防止父页面 handleAction switch 落到 default 弹「未知操作」误导用户。
      const action = this.data.actions.find((a) => a.id === id);
      if (!action || action.variant === 'disabled') return;
      this.triggerEvent('action', { id });
    },
  },
});
