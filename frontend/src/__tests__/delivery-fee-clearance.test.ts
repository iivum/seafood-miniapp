import * as fs from 'fs';
import * as path from 'path';

/**
 * order-confirm 配送方式选中态:勾选徽章不得遮挡费用文本。
 *
 * 背景(2026-07-10 D5 e2e 实跑发现):`.delivery-option__check` 为
 * absolute 定位(right:16rpx、宽 36rpx),与右对齐的
 * `.delivery-option__fee` 共用右侧槽位 —— 选中任何一档,该档费用
 * 文本都被圆形徽章盖住(「¥ 0」只露「¥」)。修复约定:选中态给
 * fee 让出 ≥52rpx(16rpx 偏移 + 36rpx 徽章宽)的右侧空间。
 */

const WXSS = path.resolve(
  __dirname,
  '../../pages-sub/order/order-confirm/order-confirm.wxss',
);

describe('order-confirm 配送费用与勾选徽章互斥占位', () => {
  it('.delivery-option.is-selected .delivery-option__fee 必须有 ≥52rpx 的右侧让位', () => {
    const wxss = fs.readFileSync(WXSS, 'utf8');
    const block = wxss.match(
      /\.delivery-option\.is-selected\s+\.delivery-option__fee\s*\{([^}]*)\}/,
    );
    expect(block).not.toBeNull();
    const clearance = block![1].match(/margin-right:\s*(\d+)rpx/);
    expect(clearance).not.toBeNull();
    expect(Number(clearance![1])).toBeGreaterThanOrEqual(52);
  });
});
