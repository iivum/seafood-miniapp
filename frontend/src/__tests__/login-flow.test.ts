/**
 * P2 login.wxml 登录流改造测试。
 *
 * 根因:login.wxml 走 open-type="getPhoneNumber" button,需企业资质 +
 * 真机授权,开发者工具登录走不通,导致"登录空白"。
 *
 * 修法:
 *   - login.wxml:删 getPhoneNumber button,加 2 个 button(开发者登录 + 微信登录)
 *   - login.js:onGetPhoneNumber 删;加 onDevLogin(本地 + e2e,code 以 dev- 开头)
 *     和 onWxLogin(真机,wx.login 拿 code)
 *   - login.js 消费 onLoad(query.redirect):登录成功跳回原 redirect 目标
 *
 * 此测试锁住 6 个不变量防漏。现状:login.wxml 仍含 getPhoneNumber +
 * login.js 没有 onDevLogin/onWxLogin → 至少 4/6 FAIL。
 */
import * as fs from 'fs';
import * as path from 'path';

function readLoginWxml(): string {
  return fs.readFileSync(
    path.join(__dirname, '../../pages-sub/user/login/login.wxml'),
    'utf-8'
  );
}

function readLoginJs(): string {
  return fs.readFileSync(
    path.join(__dirname, '../../pages-sub/user/login/login.js'),
    'utf-8'
  );
}

describe('login.wxml 登录流改造(P2)', () => {
  it('login.wxml 不含 getPhoneNumber', () => {
    const src = readLoginWxml();
    expect(src).not.toMatch(/open-type=["']getPhoneNumber["']/);
  });

  it('login.wxml 含 bindtap=onDevLogin 按钮(开发者登录)', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/bindtap=["']onDevLogin["']/);
  });

  it('login.wxml 含 bindtap=onWxLogin 按钮(微信登录)', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/bindtap=["']onWxLogin["']/);
  });

  it('login.js 有 onDevLogin 函数体', () => {
    const src = readLoginJs();
    expect(src).toMatch(/onDevLogin\s*\([^)]*\)\s*\{/);
  });

  it('login.js 有 onWxLogin 函数体', () => {
    const src = readLoginJs();
    expect(src).toMatch(/onWxLogin\s*\([^)]*\)\s*\{/);
  });

  it('onDevLogin 调 wx.login 拿 code(以 dev- 开头走 dev-login)', () => {
    const src = readLoginJs();
    const match = src.match(/onDevLogin\s*\([^)]*\)\s*\{([\s\S]*?)\n\s{4}\}/);
    expect(match).not.toBeNull();
    const body = match![1];
    expect(/wx\.login\(/.test(body)).toBe(true);
  });
});
