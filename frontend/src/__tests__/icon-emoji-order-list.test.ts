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

  it('错误态 shared-empty 用 icon="warning-o" prop(本地组件真正支持,不再需要 vant 的 image=""/slot 绕过写法)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/image=""/);
    expect(errorBlock).not.toMatch(/slot="image"/);
    expect(errorBlock).toMatch(/icon="warning-o"/);
  });

  it('错误态 shared-empty 带 retryable="{{true}}"(否则本地组件的重试按钮 wx:if="{{retryable}}" 不渲染)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).toMatch(/retryable="\{\{true\}\}"/);
  });

  it('空订单列表 shared-empty 用 icon="orders-o" prop', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('还没有相关订单哦'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/image=""/);
    expect(emptyBlock).not.toMatch(/slot="image"/);
    expect(emptyBlock).toMatch(/icon="orders-o"/);
  });

  it('空订单列表 shared-empty 带 retryable="{{true}}"(否则本地组件的重试按钮 wx:if="{{retryable}}" 不渲染)', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('还没有相关订单哦'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).toMatch(/retryable="\{\{true\}\}"/);
  });

  it('order-list.json 的 shared-empty 指向本地组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['shared-empty']).toBe('/src/shared/components/Empty/index');
  });

  it('order-list.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
