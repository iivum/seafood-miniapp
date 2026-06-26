/**
 * P0-1 启动崩防御测试。
 *
 * 根因:utils/request.js 导出对象 { request, authRequest } 非函数。
 * app.js 4 处写 `const request = require('./utils/request.js')` 拿到对象
 * 后调 request({...}) 抛 TypeError: request is not a function,
 * 有 token 时 onLaunch 同步抛错 → 启动中断,setBaseUrl/refreshFlags 不执行。
 *
 * 修法:必须用解构形式 `const { request } = require('./utils/request.js')`。
 *
 * 此测试锁住"app.js 内 require utils/request.js 必须是解构形式",防止重犯。
 * 当前现状:3 处非解构形式(L158 / L219 / L244)→ 第一断言失败
 *         改完后:3 处解构形式 → 全部断言通过
 */
import * as fs from 'fs';
import * as path from 'path';

function readAppJs(): string {
  return fs.readFileSync(
    path.join(__dirname, '../../app.js'),
    'utf-8'
  );
}

describe('app.js require 解构(防 P0-1 重犯)', () => {
  it('所有 require utils/request.js 必须是解构形式(无 const request = require(...))', () => {
    const src = readAppJs();
    // 匹配:const request = require('...utils/request.js')  ← 错
    const badPattern = /const\s+request\s*=\s*require\([^)]*utils\/request\.js[^)]*\)/g;
    const matches = src.match(badPattern) ?? [];
    expect(matches).toEqual([]);
  });

  it('所有 require utils/request.js 必须是 const { request } = require(...) 形式', () => {
    const src = readAppJs();
    const goodPattern = /const\s+\{\s*request\s*\}\s*=\s*require\([^)]*utils\/request\.js[^)]*\)/g;
    const matches = src.match(goodPattern) ?? [];
    // 3 处:validateToken / fetchWeChatToken / logout
    expect(matches.length).toBeGreaterThanOrEqual(3);
  });
});
