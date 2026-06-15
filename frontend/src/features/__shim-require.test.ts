/**
 * v2 视觉 5.18(2026-06-15)现场发现 failing test:
 *   mp WeChat DevTools 加载 .js shim 时,3 个 api.js 写错 relative path,
 *   require('../shared/api/request') 解析到 src/features/shared/api/request(不存在)
 *   实际应为 ../../shared/api/request。
 *   后果:cart / product / order tab 静默 crash,后续 navigateTo 全失败,
 *   weapp-dev-mcp reLaunch 返 ok 但 page 不切换(WeChat DevTools 自动化 API 静默)。
 *   v2 视觉 5.19(2026-06-15 现场发现)次轮:address-list / address-edit
 *   2 个 sub-page require('../../utils/request.js') 解析到 pages-sub/user/utils/
 *   (不存在),实际应为 ../../../../utils/request.js(4 级 ../)。
 *   后果:address 列表/编辑 sub-page 静默 fail,后续 navigateTo 全失败。
 *   测试契约:用 jest.isolateModules 隔离 module 副作用,验证 require 路径不抛 MODULE_NOT_FOUND。
 *   跑法:cd frontend && npm test -- --testPathPattern="__shim-require"
 */

describe('mp runtime shim require path(2026-06-15 现场修复)', () => {
  it('cart/api.js 不应抛 MODULE_NOT_FOUND(../../shared)', () => {
    let isolatedRequire: NodeJS.Require;
    jest.isolateModules(() => {
      isolatedRequire = require;
    });
    expect(() => isolatedRequire('./cart/api.js')).not.toThrow(/Cannot find module/);
  });

  it('order/api.js 不应抛 MODULE_NOT_FOUND(../../shared)', () => {
    let isolatedRequire: NodeJS.Require;
    jest.isolateModules(() => {
      isolatedRequire = require;
    });
    expect(() => isolatedRequire('./order/api.js')).not.toThrow(/Cannot find module/);
  });

  it('product/api.js 不应抛 MODULE_NOT_FOUND(../../shared)', () => {
    let isolatedRequire: NodeJS.Require;
    jest.isolateModules(() => {
      isolatedRequire = require;
    });
    expect(() => isolatedRequire('./product/api.js')).not.toThrow(/Cannot find module/);
  });

  it('auth/api.js(对照组,本来就是 ../../)正常 require', () => {
    let isolatedRequire: NodeJS.Require;
    jest.isolateModules(() => {
      isolatedRequire = require;
    });
    expect(() => isolatedRequire('./auth/api.js')).not.toThrow(/Cannot find module/);
  });

  it('pages-sub/user/address/address-list.js 不应抛 MODULE_NOT_FOUND(../../../../utils)', () => {
    let isolatedRequire: NodeJS.Require;
    jest.isolateModules(() => {
      isolatedRequire = require;
    });
    expect(() => isolatedRequire('../../pages-sub/user/address/address-list.js')).not.toThrow(/Cannot find module/);
  });

  it('pages-sub/user/address/address-edit.js 不应抛 MODULE_NOT_FOUND(../../../../utils)', () => {
    let isolatedRequire: NodeJS.Require;
    jest.isolateModules(() => {
      isolatedRequire = require;
    });
    expect(() => isolatedRequire('../../pages-sub/user/address/address-edit.js')).not.toThrow(/Cannot find module/);
  });
});

