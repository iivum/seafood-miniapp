import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages-sub/order/order-list/order-list.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages-sub/order/order-list/order-list.json');

describe('mp-08 订单列表 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸搜索/店铺符号/emoji 字符', () => {
    expect(wxml).not.toMatch(/⌕|⌂|⚠️|📦/);
  });

  it('顶部搜索图标用 van-icon name="search"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="order-list__search-icon"\s+name="search"/);
  });

  it('订单卡片商家行图标用 van-icon name="shop-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="order-card__shop-icon"\s+name="shop-o"/);
  });

  it('错误态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="warning-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/\bicon="/);
    expect(errorBlock).toMatch(/image=""/);
    expect(errorBlock).toMatch(/<van-icon\s+slot="image"\s+name="warning-o"/);
  });

  it('空订单列表 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="orders-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('还没有相关订单哦'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/\bicon="/);
    expect(emptyBlock).toMatch(/image=""/);
    expect(emptyBlock).toMatch(/<van-icon\s+slot="image"\s+name="orders-o"/);
  });

  it('order-list.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
