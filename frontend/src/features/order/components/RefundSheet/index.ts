/**
 * 路线图 4.10 — RefundSheet 组件 props / 校验逻辑类型。
 *
 * <p>parent 通过 {@code order} / {@code visible} 传上下文,通过 {@code onSubmit}
 * 回调(父组件调 {@code orderStore.requestRefund})。校验在前端 zod-schema-style
 * 集中于 {@link validate} 函数,与后端 RefundRequest 注解(@DecimalMin / @Size)
 * 一致 — 双重校验防输入绕过。
 */
import type { Order } from '../../types';

export interface RefundSheetProps {
  /** 订单上下文(总额用于金额上限校验)。 */
  order: Order;
  /** 是否显示。 */
  visible: boolean;
  /** 提交中(显示 loading,防双击)。 */
  submitting?: boolean;
  /** 父组件提供:收到合法 amount + reason 时调 {@code orderStore.requestRefund}。 */
  onSubmit?: (amount: number, reason: string) => void | Promise<void>;
  /** 关闭 sheet。 */
  onClose?: () => void;
}

/**
 * 前端校验:与后端 RefundRequest 注解同步。
 * @returns errorMessage(非空表示失败)/ ""(成功)
 */
export function validateRefundInput(
  amount: string,
  reason: string,
  orderTotal: number
): string {
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
    return `退款金额不能超过订单总额 ¥${orderTotal}`;
  }
  return '';
}
