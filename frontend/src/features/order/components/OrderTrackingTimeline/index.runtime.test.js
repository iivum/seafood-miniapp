/**
 * OrderTrackingTimeline mp 运行时(index.js,非 .ts)回归锁。
 *
 * mp-od-prototype-alignment mp-09 接线时发现:index.js 此前
 * `require('./index.ts-helpers')` 指向一个从未存在过的文件——mp 运行时无 TS
 * 编译,`computeStages`/`shouldShow` 只在 index.ts 里实现(index.test.ts 已覆盖),
 * 从未被抽出成独立 .js。组件此前没有任何页面接线,这条 require 从未被真实执行到;
 * 一旦接进页面,mp 运行时加载 index.js 会直接因 require 不存在的模块而崩溃整个组件
 * (同 OrderActionRow"渲染层修好后才暴露"的休眠 bug 模式,第 3 次出现)。
 *
 * index.test.ts(ts-jest,moduleFileExtensions 优先 .ts)完全测不到这个 bug ——
 * 它 import 的是 index.ts,不会触发 index.js 里的坏 require。这份测试直接
 * require('./index.js')(mp 运行时实际加载的文件,同 order-list.test.js 顶层
 * require 页面文件的惯例),用 mock 的全局 Component() 捕获配置;如果 require
 * 本身重新抛错(比如坏路径重新出现),整个测试文件会直接 fail to run,信号更强。
 */
let componentConfig;
global.Component = (config) => {
  componentConfig = config;
};
require('./index.js');

describe('OrderTrackingTimeline/index.js(mp 运行时)', () => {
  it('require 成功注册 Component,observers.order 存在', () => {
    expect(componentConfig).toBeDefined();
    expect(componentConfig.observers.order).toBeInstanceOf(Function);
  });

  describe('observers.order(mp 运行时行为,逻辑同 index.ts computeStages/shouldShow)', () => {
    function run(order) {
      const ctx = { setData: jest.fn() };
      componentConfig.observers.order.call(ctx, order);
      return ctx.setData.mock.calls[0][0];
    }

    it('PENDING 订单:不显示时间线', () => {
      const patch = run({ id: 'o1', status: 'PENDING', createdAt: '2026-06-01T10:00:00Z' });
      expect(patch.visible).toBe(false);
    });

    it('SHIPPED 订单 + 2 个 tracking events:已发货 + 运输中 done', () => {
      const order = {
        id: 'o1',
        status: 'SHIPPED',
        createdAt: '2026-06-01T10:00:00Z',
        tracking: {
          carrier: '顺丰',
          trackingNumber: 'SF123',
          events: [
            { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
            { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
          ],
        },
      };
      const patch = run(order);
      expect(patch.visible).toBe(true);
      expect(patch.stages.shippedClass).toContain('--done');
      expect(patch.stages.inTransitClass).toContain('--done');
      expect(patch.stages.deliveredAt).toBeNull();
      expect(patch.trackingNumber).toBe('SF123');
      expect(patch.carrier).toBe('顺丰');
    });

    it('COMPLETED 订单 + 3 个 events:全 4 节点 done', () => {
      const order = {
        id: 'o1',
        status: 'COMPLETED',
        createdAt: '2026-06-01T10:00:00Z',
        tracking: {
          carrier: '顺丰',
          trackingNumber: 'SF123',
          events: [
            { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
            { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
            { at: '2026-06-03T10:00:00Z', status: 'DELIVERED', location: '北京', description: '已签收' },
          ],
        },
      };
      const patch = run(order);
      expect(patch.visible).toBe(true);
      expect(patch.stages.shippedClass).toContain('--done');
      expect(patch.stages.inTransitClass).toContain('--done');
      expect(patch.stages.deliveredClass).toContain('--done');
      expect(patch.stages.deliveredAt).not.toBeNull();
    });

    it('order 为 null:visible false,不抛错', () => {
      expect(() => run(null)).not.toThrow();
      expect(run(null).visible).toBe(false);
    });
  });
});
