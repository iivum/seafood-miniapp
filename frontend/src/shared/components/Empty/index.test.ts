import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, 'index.wxml');
const JSON_PATH = path.resolve(__dirname, 'index.json');

describe('shared/components/Empty 通用空状态组件(mp-icon-emoji-replacement)', () => {
  it('index.wxml 不再包含裸邮筒 emoji,改用 van-icon name="search"', () => {
    const wxml = fs.readFileSync(WXML, 'utf8');
    expect(wxml).not.toMatch(/📭/);
    expect(wxml).toMatch(/<van-icon\s+class="empty__icon"\s+name="search"/);
  });

  it('index.json 存在且声明 component:true、注册了 van-icon', () => {
    expect(fs.existsSync(JSON_PATH)).toBe(true);
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.component).toBe(true);
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });
});
