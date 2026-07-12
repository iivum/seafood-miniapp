import * as fs from 'fs';
import * as path from 'path';

const WXML = path.resolve(__dirname, 'index.wxml');
const JSON_PATH = path.resolve(__dirname, 'index.json');

describe('shared/components/Empty 通用空状态组件(mp-icon-emoji-replacement)', () => {
  it('index.wxml 不再包含裸邮筒 emoji,改用 van-icon name="{{icon}}"(默认 "search")', () => {
    const wxml = fs.readFileSync(WXML, 'utf8');
    expect(wxml).not.toMatch(/📭/);
    expect(wxml).toMatch(/<van-icon\s+class="empty__icon"\s+name="\{\{icon\}\}"/);
  });

  it('index.json 存在且声明 component:true、注册了 van-icon', () => {
    expect(fs.existsSync(JSON_PATH)).toBe(true);
    const parsed = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
    expect(parsed.component).toBe(true);
    expect(parsed.usingComponents['van-icon']).toBe('@vant/weapp/icon/index');
  });

  it('index.js 新增 icon(默认 "search")和 retryText(默认 "重试")两个 properties', () => {
    const js = fs.readFileSync(path.resolve(__dirname, 'index.js'), 'utf8');
    expect(js).toMatch(/icon:\s*\{\s*type:\s*String,\s*value:\s*'search',?\s*\}/);
    expect(js).toMatch(/retryText:\s*\{\s*type:\s*String,\s*value:\s*'重试',?\s*\}/);
  });

  it('index.wxml 用 {{icon}}/{{retryText}} 绑定渲染,不再硬编码图标名/按钮文案', () => {
    const wxml = fs.readFileSync(WXML, 'utf8');
    expect(wxml).toMatch(/<van-icon\s+class="empty__icon"\s+name="\{\{icon\}\}"/);
    expect(wxml).toMatch(/>\{\{retryText\}\}<\/button>/);
  });
});
