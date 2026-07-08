/**
 * mp-08 订单列表卡片 "共 N 件商品" 文案（openspec change mp-od-prototype-alignment,
 * brief `.superpowers/sdd/mp-od-7-order-list-brief.md` §1）。
 *
 * OD golden(`frontend/e2e/od-golden/mp-08-order-list.png`)每张订单卡片
 * "订单号" 那一行下方有一行 "共 N 件商品"，当前实现完全没有这个文案。
 * N = 商品种类数（`item.items.length`），同 mp-06 order-confirm.js
 * `itemCount = cartItems.length`（种类数，非数量总和）的判断逻辑 ——
 * 本页同一 wxml 里已有 "等 {{item.items.length}} 件商品"（第 108 行）
 * 用同一字段表达"种类数"，故直接复用 `item.items.length`，不新增
 * page-level 计算字段（YAGNI，避免和已有折叠行的口径产生二次真相源）。
 *
 * 此测试锁住两个不变量防漏：现状文案缺失 → RED。
 */
import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/order/order-list/order-list.wxml');

describe('mp-08 订单卡片 "共 N 件商品" 文案（真实数据，item.items.length）', () => {
  let wxml: string;
  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('order-card__summary 区块内有 "共 {{item.items.length}} 件商品" 绑定', () => {
    const summaryStart = wxml.indexOf('class="order-card__summary"');
    const actionsStart = wxml.indexOf('class="order-card__actions"');
    expect(summaryStart).toBeGreaterThan(-1);
    expect(actionsStart).toBeGreaterThan(summaryStart);
    const summaryBlock = wxml.slice(summaryStart, actionsStart);
    expect(summaryBlock).toMatch(/共\s*\{\{\s*item\.items\.length\s*\}\}\s*件商品/);
  });

  it('"共 N 件商品" 用真实种类数字段，不是硬编码数字', () => {
    const match = wxml.match(/共\s*\{\{\s*(item\.items\.length)\s*\}\}\s*件商品/);
    expect(match).not.toBeNull();
    expect(match![1]).toBe('item.items.length');
  });
});
