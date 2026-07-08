import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, '../../pages/index/index.wxml');
const JSON_PATH = path.resolve(__dirname, '../../pages/index/index.json');

describe('mp-01 首页 UI 微图标 emoji → van-icon(mp-icon-emoji-replacement)', () => {
  let wxml: string;

  beforeAll(() => {
    wxml = fs.readFileSync(WXML, 'utf8');
  });

  it('不再包含裸 emoji 图标字符(定位/铃铛/搜索/警告/无结果)', () => {
    expect(wxml).not.toMatch(/📍|🔔|🔍|⚠️|🦐/);
  });

  it('定位图标用 van-icon name="location-o"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="home-location__icon"\s+name="location-o"/);
  });

  it('通知铃铛图标用 van-icon name="bell"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="home-bell__icon"\s+name="bell"/);
  });

  it('搜索图标用 van-icon name="search"', () => {
    expect(wxml).toMatch(/<van-icon\s+class="home-search__icon"\s+name="search"/);
  });

  it('错误态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="warning-o",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const errorBlock = blocks.find((b) => b.includes('errorMessage'));
    expect(errorBlock).toBeDefined();
    expect(errorBlock).not.toMatch(/\bicon="/);
    expect(errorBlock).toMatch(/image=""/);
    expect(errorBlock).toMatch(/<van-icon\s+slot="image"\s+name="warning-o"/);
  });

  it('筛选后空态 shared-empty 不再传死 icon prop,改用具名 slot 塞 van-icon name="search",且显式 image=""', () => {
    const blocks = wxml.match(/<shared-empty[\s\S]*?<\/shared-empty>/g) ?? [];
    const emptyBlock = blocks.find((b) => b.includes('该分类暂无商品'));
    expect(emptyBlock).toBeDefined();
    expect(emptyBlock).not.toMatch(/\bicon="/);
    expect(emptyBlock).toMatch(/image=""/);
    expect(emptyBlock).toMatch(/<van-icon\s+slot="image"\s+name="search"/);
  });

  it('index.json 注册了 van-icon 组件', () => {
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
