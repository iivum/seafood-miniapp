/**
 * admin-ui OrderListPage / OrderDetailPage 端点对齐
 *
 * v2.1 期间发现 admin-ui 调 /api/orders(mp 端 user-only,403 admin 访问),
 * 应改为 /api/admin/orders。本测试网静态层断言:读 .ts 源文件,提取
 * 每个 method 函数体内的 URL 路径,断言都含 /admin/orders。
 *
 * TDD:RED 阶段(改端点前)— 跑看 fail;GREEN 阶段改源。
 */

import { describe, it, expect } from 'vitest';
import * as fs from 'node:fs';
import * as path from 'node:path';

const ADMIN_ROOT = path.resolve(__dirname, '../..');

function readSource(relPath: string): string | null {
  const abs = path.resolve(ADMIN_ROOT, relPath);
  if (!fs.existsSync(abs)) return null;
  return fs.readFileSync(abs, 'utf-8');
}

/**
 * 在 src 文件里提取某个 method 内的所有 URL 路径
 * 方法定义:`fn: async (...) => { ... }` 或 `fn(...) => { ... }`
 */
function urlsForFn(src: string, fn: string): string[] {
  const lines = src.split('\n');
  const urls: string[] = [];
  let inFn = false;
  let braceDepth = 0;
  for (const line of lines) {
    if (!inFn) {
      const re = new RegExp(`^\\s*${fn}\\s*[:(]`, 'm');
      if (re.test(line)) {
        inFn = true;
        braceDepth = 0;
      }
    }
    if (inFn) {
      for (const ch of line) {
        if (ch === '{') braceDepth++;
        if (ch === '}') braceDepth--;
      }
      const urlMatches = line.match(/['"`]\/[^'"`]+['"`]/g);
      if (urlMatches) {
        for (const um of urlMatches) {
          const url = um.slice(1, -1);
          if (url.startsWith('/api/') || url.startsWith('/admin/')) {
            urls.push(url);
          }
        }
      }
      if (braceDepth <= 0 && inFn) break;
    }
  }
  return urls;
}

describe('OrderListPage 端点对齐(防 v2.1 /api/orders bug 回归)', () => {
  const FN_TO_TEST = ['list', 'ship', 'detail', 'batchShip', 'exportCsv'];

  it('list() 不应调 /orders 裸路径(mp 端 user-only,admin 必 403)', () => {
    const content = readSource('src/features/orders/api.ts');
    if (!content) throw new Error('source 缺失');
    const urls = urlsForFn(content, 'list');
    const hasBareOrders = urls.some(u => /^\/orders(\?|$)/.test(u));
    expect({ fn: 'list', urls, hasBareOrders })
      .toEqual({ fn: 'list', urls: expect.any(Array), hasBareOrders: false });
  });

  FN_TO_TEST.forEach(fn => {
    it(`${fn}() 端点应调 /admin/orders`, () => {
      const content = readSource('src/features/orders/api.ts');
      if (!content) throw new Error('source 缺失');
      const urls = urlsForFn(content, fn);
      const hasAdmin = urls.some(u => u.startsWith('/admin/orders'));
      expect({ fn, urls, hasAdmin })
        .toEqual({ fn, urls: expect.any(Array), hasAdmin: true });
    });
  });
});
