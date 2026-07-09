import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/category/category.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages/category/category.json');

describe('mp-02 分类页 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸 emoji 图标字符(搜索/警告/无结果)', () => {
    expect(wxml).not.toMatch(/🔍|⚠️|🦐/);
  });

  it('顶部搜索图标用 van-icon name="search"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="cat-topbar__search-icon"\s+name="search"/);
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

  it('空状态 shared-empty 用 icon="search" prop', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('该分类暂无商品'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/image=""/);
    expect(emptyBlock).not.toMatch(/slot="image"/);
    expect(emptyBlock).toMatch(/icon="search"/);
    expect(emptyBlock).not.toMatch(/retry-text|bind:retry|retryable/);
  });

  it('category.json 的 shared-empty 指向本地组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['shared-empty']).toBe('/src/shared/components/Empty/index');
  });

  it('category.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
