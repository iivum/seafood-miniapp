import * as fs from 'fs';
import * as path from 'path';

const FRONTEND = path.resolve(__dirname, '../..');

const FILES = [
  'pages/index/index.wxml',
  'pages/category/category.wxml',
  'pages-sub/product/product-detail/product-detail.wxml',
  'pages/cart/cart.wxml',
  'pages-sub/order/order-list/order-list.wxml',
] as const;

function normalize(s: string): string {
  return s.replace(/\r\n/g, '\n').replace(/[ \t]+\n/g, '\n').trim();
}

describe('S-2 hover-class 全量合约快照（防止后续误删）', () => {
  for (const rel of FILES) {
    it(`${rel} 快照`, () => {
      const content = normalize(fs.readFileSync(path.join(FRONTEND, rel), 'utf8'));
      expect(content).toMatchSnapshot();
    });
  }
});
