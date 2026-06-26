/**
 * P1 裂图资源补图回归测试
 *
 * 根因:login.wxml 引用 /images/logo.png,profile.wxml 引用 /images/default-avatar.png,
 * 两文件曾缺失 → 登录页 logo 裂图 + 个人页头像裂图,登录页"空白"的一部分。
 *
 * 验收:
 *   1. images/logo.png 存在(>1KB,有内容)
 *   2. images/default-avatar.png 存在(>200B,有人形)
 *
 * 失败模式:任一文件缺失/为空 → CI 红,防后人手贱删。
 */
import * as fs from 'fs';
import * as path from 'path';

const IMAGES_DIR = path.resolve(__dirname, '../../images');

describe('mp 静态资源 — 补裂图(P1)', () => {
  describe('images/logo.png(登录页品牌图)', () => {
    const p = path.join(IMAGES_DIR, 'logo.png');

    it('存在', () => {
      expect(fs.existsSync(p)).toBe(true);
    });

    it('非空(>1KB,登录页可识别)', () => {
      const size = fs.statSync(p).size;
      expect(size).toBeGreaterThan(1024);
      // eslint-disable-next-line no-console
      console.log(`[logo-budget] ${size}B = ${(size / 1024).toFixed(1)}KB`);
    });
  });

  describe('images/default-avatar.png(个人页占位头像)', () => {
    const p = path.join(IMAGES_DIR, 'default-avatar.png');

    it('存在', () => {
      expect(fs.existsSync(p)).toBe(true);
    });

    it('非空(>200B,占位可见)', () => {
      const size = fs.statSync(p).size;
      expect(size).toBeGreaterThan(200);
      // eslint-disable-next-line no-console
      console.log(`[avatar-budget] ${size}B = ${(size / 1024).toFixed(1)}KB`);
    });
  });
});
