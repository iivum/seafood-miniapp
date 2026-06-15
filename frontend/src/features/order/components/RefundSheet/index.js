/**
 * 路线图 4.10 — mp-08 申请退款底部 sheet(Wechat Mini Program Component)。
 *
 * <p>行为:打开时 amount 默认填订单总额(用户可改,<= 总额),reason 留空(强制填)。
 * 点击「提交」:前端 validate 校验 → 通过调 props.onSubmit(amount, reason);
 * 失败在 sheet 内红条提示,不关闭。
 * 点击「取消」/ 蒙层:调 props.onClose(),外部决定是否清空表单。
 *
 * <p>校验规则与后端 RefundRequest 同步(后端 @DecimalMin / @Size):amount > 0 且
 * ≤ 订单总额,reason 非空且 ≤ 200 字符。两端校验防输入绕过。
 */
Component({
  properties: {
    order: { type: Object, value: null },
    visible: { type: Boolean, value: false },
    submitting: { type: Boolean, value: false },
  },

  data: {
    amount: '',
    reason: '',
    errorMessage: '',
  },

  observers: {
    'visible, order': function (visible, order) {
      if (visible && order) {
        // 打开时 amount 默认填订单总额,清空 reason 与 error
        this.setData({
          amount: String(order.totalAmount),
          reason: '',
          errorMessage: '',
        });
      }
    },
  },

  methods: {
    noop() {},

    onMaskTap() {
      if (this.data.submitting) return;
      this.triggerEvent('close');
    },

    onReasonInput(e) {
      this.setData({ reason: e.detail.value, errorMessage: '' });
    },

    onAmountInput(e) {
      this.setData({ amount: e.detail.value, errorMessage: '' });
    },

    onCancel() {
      if (this.data.submitting) return;
      this.triggerEvent('close');
    },

    onSubmit() {
      const { order, amount, reason } = this.data;
      if (!order) return;
      const err = validateRefundInput(amount, reason, order.totalAmount);
      if (err) {
        this.setData({ errorMessage: err });
        return;
      }
      this.triggerEvent('submit', {
        amount: Number(amount),
        reason: reason.trim(),
      });
    },
  },
});

/**
 * 校验函数(与 .ts 端同名,小程序运行时无 TS 编译,这里写 JS 版同步)。
 * 规则与后端 RefundRequest 注解一致;reason 1..200,amount > 0 且 ≤ 订单总额。
 */
function validateRefundInput(amount, reason, orderTotal) {
  if (reason == null || reason.trim().length === 0) {
    return '请填写退款原因';
  }
  if (reason.length > 200) {
    return '退款原因超过 200 字符上限';
  }
  if (amount == null || amount.trim().length === 0) {
    return '请填写退款金额';
  }
  const parsed = Number(amount);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return '退款金额必须大于 0';
  }
  if (parsed > orderTotal) {
    return '退款金额不能超过订单总额 ¥' + orderTotal;
  }
  return '';
}
