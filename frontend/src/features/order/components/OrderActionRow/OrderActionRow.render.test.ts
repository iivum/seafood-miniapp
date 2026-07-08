/**
 * OrderActionRow 渲染层结构断言（mp-08 order-list task，openspec change
 * mp-od-prototype-alignment，brief `.superpowers/sdd/mp-od-7-order-list-brief.md`
 * "如果发现问题"）。
 *
 * 根因：此前该组件只有 `index.ts`（纯逻辑 `getActionsFor()`，7 状态已在
 * `OrderActionRow.test.ts` 100% 覆盖）+ 测试文件，完全没有 `.wxml`/`.wxss`/`.json`
 * （对比同目录 `OrderItemRow`/`OrderTrackingTimeline`/`RefundSheet` 都有全套文件），
 * 也没有任何页面在 `usingComponents` 里接线 —— `<order-action-row>` 在小程序
 * 运行时渲染成 0 内容的空标签，不报错、不崩溃，只是静默不显示任何按钮
 * （已用 weapp-dev MCP 在真实 WeChat DevTools 里核实：outerWxml 显示为空元素，
 * 高度仅 6px，console 无任何错误/警告）。
 *
 * 这份测试**不**重新验证 `getActionsFor()` 的状态→按钮映射逻辑本身（那部分
 * 已经在 `OrderActionRow.test.ts` 里 7 状态全分支覆盖，本次未改动该逻辑），
 * 只锁住"渲染层真的存在且能把 actions 渲染成按钮"这个不变量，防止同样的
 * 静默失败再次发生。
 */
import * as fs from 'fs';
import * as path from 'path';

const COMPONENT_DIR = path.resolve(__dirname);
const ORDER_LIST_JSON = path.resolve(
  __dirname,
  '../../../../../pages-sub/order/order-list/order-list.json'
);

function read(file: string): string {
  return fs.readFileSync(path.join(COMPONENT_DIR, file), 'utf8');
}

describe('OrderActionRow 渲染层（wxml/wxss/json 存在 + 真的渲染按钮）', () => {
  it('index.json 存在且声明为自定义组件', () => {
    const json = JSON.parse(read('index.json'));
    expect(json.component).toBe(true);
  });

  it('index.js（mp 运行时,非 .ts）存在且定义了真正的 Component()', () => {
    const js = read('index.js');
    expect(js).toMatch(/Component\(\{/);
    // properties.status 是父组件(order-list.wxml)传入的绑定,渲染层必须消费它
    expect(js).toMatch(/properties:\s*\{[\s\S]*?status:/);
  });

  it('index.wxml 遍历 actions 数组渲染出按钮(而不是空标签)', () => {
    const wxml = read('index.wxml');
    expect(wxml).toMatch(/wx:for="\{\{\s*actions\s*\}\}"/);
    // 按钮文案绑定 item.label,不是硬编码占位
    expect(wxml).toMatch(/\{\{\s*item\.label\s*\}\}/);
    // 点击要能把 action id 传给宿主页(order-list.js onActionTap 依赖 e.detail.id)
    expect(wxml).toMatch(/bindtap="onTap"/);
    expect(wxml).toMatch(/data-id="\{\{\s*item\.id\s*\}\}"/);
  });

  it('index.wxml 按钮 class 绑定 variant,4 种视觉态(primary/secondary/danger/disabled)有区分', () => {
    const wxml = read('index.wxml');
    expect(wxml).toMatch(/order-action-row__btn--\{\{\s*item\.variant\s*\}\}/);
  });

  it('index.wxss 4 个 variant 类全部存在且只用 token(不裸写 hex)', () => {
    const wxss = read('index.wxss');
    for (const variant of ['primary', 'secondary', 'danger', 'disabled']) {
      expect(wxss).toMatch(new RegExp(`\\.order-action-row__btn--${variant}\\s*\\{`));
    }
    // 不裸写 hex:每个出现的 # 颜色都必须在 var(--xxx, #fallback) 形式内
    const bareHex = wxss.match(/(?<!var\([^)]*)#[0-9a-fA-F]{3,6}\b/g) ?? [];
    // fallback 值本身合法(在 var(--x, #hex) 内),这里只挑不在 var(...) 括号内的裸写
    const rawHexOutsideVar = wxss
      .split('\n')
      .filter((line) => line.includes('#') && !line.includes('var('));
    expect(rawHexOutsideVar).toEqual([]);
    expect(bareHex.length === 0 || rawHexOutsideVar.length === 0).toBe(true);
  });

  it('order-list.json usingComponents 接线 order-action-row', () => {
    const pageJson = JSON.parse(fs.readFileSync(ORDER_LIST_JSON, 'utf8'));
    expect(pageJson.usingComponents['order-action-row']).toBeDefined();
    // 路径必须真实解析到本组件目录(而不是拼错路径又一次静默失败)
    const resolved = path.resolve(
      path.dirname(ORDER_LIST_JSON),
      pageJson.usingComponents['order-action-row'] + '.json'
    );
    expect(fs.existsSync(resolved)).toBe(true);
  });
});
