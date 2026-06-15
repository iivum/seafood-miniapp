/**
 * 路线图 4.10 — RefundSheet 校验函数单测。
 * 与后端 RefundRequest 注解(@DecimalMin / @Size)对齐;两端校验防输入绕过。
 */
import { validateRefundInput } from './index';

describe('RefundSheet.validateRefundInput', () => {
  it('accepts valid amount + reason', () => {
    expect(validateRefundInput('50', '海鲜有问题', 100)).toBe('');
  });

  it('rejects empty reason', () => {
    expect(validateRefundInput('50', '', 100)).toContain('退款原因');
    expect(validateRefundInput('50', '   ', 100)).toContain('退款原因');
  });

  it('rejects reason over 200 chars', () => {
    const long = 'x'.repeat(201);
    expect(validateRefundInput('50', long, 100)).toContain('200 字符');
  });

  it('accepts reason exactly 200 chars (boundary)', () => {
    const exact = 'x'.repeat(200);
    expect(validateRefundInput('50', exact, 100)).toBe('');
  });

  it('rejects empty amount', () => {
    expect(validateRefundInput('', '理由', 100)).toContain('退款金额');
  });

  it('rejects zero or negative amount', () => {
    expect(validateRefundInput('0', '理由', 100)).toContain('大于 0');
    expect(validateRefundInput('-5', '理由', 100)).toContain('大于 0');
  });

  it('rejects amount exceeding order total', () => {
    expect(validateRefundInput('150', '理由', 100)).toContain('订单总额');
  });

  it('accepts amount equal to order total (boundary)', () => {
    expect(validateRefundInput('100', '理由', 100)).toBe('');
  });

  it('rejects non-numeric amount', () => {
    expect(validateRefundInput('abc', '理由', 100)).toContain('大于 0');
  });
});
