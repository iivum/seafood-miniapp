/**
 * P2 + align-mp-login-with-od login.wxml 登录流改造测试。
 *
 * P2 根因:login.wxml 曾经把 open-type="getPhoneNumber" 当成*唯一*登录手段,
 * 需企业资质 + 真机授权,开发者工具登录走不通,导致"登录空白"。
 * 修法:login.wxml 加开发者登录 + 微信登录两个平级 button,getPhoneNumber 整体删除。
 *
 * align-mp-login-with-od:对齐 OD mp-10-login.html —— 微信登录前需勾选用户协议/
 * 隐私政策,登录成功后进入可跳过的 Step2(手机号绑定,重新引入 getPhoneNumber,
 * 但只用在这个*可跳过*的第二步,不再是登录本身的前置条件,P2 根因不会复现)。
 * 开发者登录保留,只是视觉收敛为不显眼入口。
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

describe('login.wxml/js 对齐 OD mp-10-login(align-mp-login-with-od)', () => {
  /** 从函数声明开始,按大括号配对提取完整函数体(不依赖固定缩进,避免嵌套块提前截断)。 */
  function extractFn(src: string, name: string): string {
    const startMatch = src.match(new RegExp(`${name}\\s*\\([^)]*\\)\\s*\\{`));
    expect(startMatch).not.toBeNull();
    const start = startMatch!.index! + startMatch![0].length;
    let depth = 1;
    let i = start;
    while (depth > 0 && i < src.length) {
      if (src[i] === '{') depth++;
      else if (src[i] === '}') depth--;
      i++;
    }
    return src.slice(start, i - 1);
  }

  it('login.wxml 含用户协议/隐私政策同意勾选(bindtap=onToggleAgree)', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/bindtap=["']onToggleAgree["']/);
  });

  it('login.wxml 含微信一键登录主按钮(bindtap=onWxLogin)', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/bindtap=["']onWxLogin["']/);
  });

  it('login.wxml Step1 含"暂不登录"跳过链接(bindtap=onSkipLogin)', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/bindtap=["']onSkipLogin["']/);
  });

  it('login.wxml Step2 含 getPhoneNumber 按钮(bindgetphonenumber=onGetPhoneNumber)', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/open-type=["']getPhoneNumber["']/);
    expect(src).toMatch(/bindgetphonenumber=["']onGetPhoneNumber["']/);
  });

  it('login.wxml Step2 含"暂不绑定"跳过链接(bindtap=onSkipPhoneBind)——getPhoneNumber 永远可跳过,不再是登录本身的前置条件', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/bindtap=["']onSkipPhoneBind["']/);
  });

  it('login.wxml Step2 含开发者测试手机号绑定入口(bindtap=onDevBindPhone)——devtools 无法触发真实 getPhoneNumber 授权时仍可覆盖 e2e', () => {
    const src = readLoginWxml();
    expect(src).toMatch(/bindtap=["']onDevBindPhone["']/);
  });

  it('login.js 有 onToggleAgree/onWxLogin/onSkipLogin/onGetPhoneNumber/onDevBindPhone/onSkipPhoneBind 函数体', () => {
    const src = readLoginJs();
    for (const name of [
      'onToggleAgree',
      'onWxLogin',
      'onSkipLogin',
      'onGetPhoneNumber',
      'onDevBindPhone',
      'onSkipPhoneBind',
    ]) {
      expect(src).toMatch(new RegExp(`${name}\\s*\\([^)]*\\)\\s*\\{`));
    }
  });

  it('onWxLogin 在调 wx.login 前先检查 agreed(协议未勾选时阻断登录)', () => {
    const src = readLoginJs();
    const body = extractFn(src, 'onWxLogin');
    const agreedCheckIdx = body.indexOf('agreed');
    const wxLoginIdx = body.indexOf('wx.login(');
    expect(agreedCheckIdx).toBeGreaterThan(-1);
    expect(wxLoginIdx).toBeGreaterThan(-1);
    expect(agreedCheckIdx).toBeLessThan(wxLoginIdx);
  });

  it('onDevBindPhone 合成 dev- 前缀 code 调 authStore.bindPhone(不依赖真实微信授权)', () => {
    const src = readLoginJs();
    const body = extractFn(src, 'onDevBindPhone');
    expect(/['"]dev-['"]/.test(body) || /`dev-/.test(body)).toBe(true);
    expect(/bindPhone\(/.test(body)).toBe(true);
  });

  it('onGetPhoneNumber 调 authStore.bindPhone 并处理拒绝授权的分支', () => {
    const src = readLoginJs();
    const body = extractFn(src, 'onGetPhoneNumber');
    expect(/bindPhone\(/.test(body)).toBe(true);
    expect(/errMsg/.test(body)).toBe(true);
  });
});
