/**
 * order/api.js(mp 运行时真实执行的 shim) ↔ order/api.ts(类型检查用源码,mp 运行时从不执行)
 * 方法集合契约测试。
 *
 * mp-od-prototype-alignment mp-08 诊断发现:api.test.ts 一直 `import { OrderAPI } from './api'`
 * (走 .ts),10 个用例全绿,但真实 .js shim 当时只有 5/10 方法(缺 pay/remindShip/
 * confirmReceive/rebuy/requestRefund)——OrderActionRow 展示层修好后点付款/提醒发货/
 * 确认收货/再次购买全部会抛 TypeError,.test.ts 测不出来因为它压根没在测 mp 运行时
 * 真正加载的文件。这个测试直接 require .js shim,锁住方法集合不再和 .ts 漂移。
 */
const { OrderAPI: jsShim } = require('./api.js');

describe('order/api.js shim ↔ order/api.ts 方法集合契约', () => {
  it('js shim 导出的方法名集合应和 ts 源码文档记录的一致', () => {
    // 与 api.ts 顶部注释里列出的 "mp-08 状态机 5 操作端点" + 基础 5 个 CRUD 端点对齐,
    // 手工维护这份期望列表(不 import .ts 本身,否则测试又会绕回"测 ts 不测 js"的老路)。
    const expectedMethods = [
      'list', 'getById', 'create', 'cancel', 'ship',
      'pay', 'remindShip', 'confirmReceive', 'rebuy', 'requestRefund',
    ].sort();
    expect(Object.keys(jsShim).sort()).toEqual(expectedMethods);
  });

  it('每个方法都应是函数(不是漏写成 undefined/非函数)', () => {
    for (const key of Object.keys(jsShim)) {
      expect(typeof jsShim[key]).toBe('function');
    }
  });
});
