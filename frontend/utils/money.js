/**
 * 共享金额格式化 util(mp-cross-screen-cleanup design.md D6)。
 *
 * 收敛 order-confirm.js 本地 roundYuan()(Math.round(amount*100)/100)与
 * cart.js 裸 .toFixed(2) 两处各写一份的格式化逻辑,统一成这一个共享实现。
 */

/** 四舍五入到 2 位小数,返回 number(供继续参与算式的场景用,如金额加总)。 */
function roundYuan(amount) {
  return Math.round(amount * 100) / 100;
}

/** 四舍五入到 2 位小数,返回字符串(供直接渲染展示的场景用)。 */
function formatYuan(amount) {
  return roundYuan(amount).toFixed(2);
}

module.exports = { roundYuan, formatYuan };
