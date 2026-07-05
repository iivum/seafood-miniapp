/**
 * frontend/utils/money.js —— 共享金额格式化 util(mp-cross-screen-cleanup design.md D6)。
 *
 * 背景:order-confirm.js 此前有一份本地 roundYuan()(Math.round(amount*100)/100),
 * cart.js 则直接裸调 .toFixed(2)——两处各写一份、互不共享,是本 change 要收敛的
 * 命名/风格不统一之一。roundYuan 返回 number(供 order-confirm 后续算式继续用),
 * formatYuan 返回 string(供 cart.js 直接渲染用),避免把 order-confirm 的
 * "算完再四舍五入再继续算"链路强行转成字符串再转回数字。
 */
const { roundYuan, formatYuan } = require('../money.js');

describe('utils/money.js', () => {
  describe('roundYuan', () => {
    it('四舍五入到 2 位小数,返回 number', () => {
      expect(roundYuan(414.94000000000005)).toBe(414.94);
      expect(roundYuan(1)).toBe(1);
      expect(roundYuan(0)).toBe(0);
    });

    it('复现 order-confirm.js 精度 bug 场景(145.11*2 + 124.72,满 100 减 10)', () => {
      const subtotal = roundYuan(145.11 * 2 + 124.72);
      expect(subtotal).toBe(414.94);
      const orderTotal = roundYuan(subtotal - 10);
      expect(orderTotal).toBe(404.94);
    });
  });

  describe('formatYuan', () => {
    it('返回四舍五入到 2 位小数的字符串', () => {
      expect(formatYuan(9.005)).toBe('9.01');
      expect(formatYuan(1)).toBe('1.00');
      expect(formatYuan(0)).toBe('0.00');
    });

    it('等价于 roundYuan(amount).toFixed(2)', () => {
      expect(formatYuan(414.94000000000005)).toBe(roundYuan(414.94000000000005).toFixed(2));
    });
  });
});
