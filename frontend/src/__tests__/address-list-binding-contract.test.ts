import * as fs from 'fs';
import * as path from 'path';

/**
 * wxml↔JS 死绑定契约(mp-02/03/04 同族防线)。
 *
 * 背景(2026-07-10 D5 e2e 实跑发现):address-list.wxml 选中勾绑定
 * `selectedId`,但 address-list.js 从未定义该字段 —— 真实用户流选中勾
 * 永远渲染不出来,且任何运行时日志都不会报错(wxml 未定义标识符静默为
 * undefined)。本测试静态解析 wxml 全部 {{...}} 表达式的根标识符,断言
 * 每一个都能在页面 JS 的 data 块或 setData 调用里找到,防止再次出现
 * "绑定了一个不存在的状态"的死绑定。
 */

const FRONTEND = path.resolve(__dirname, '../..');
const PAGE = 'pages-sub/user/address/address-list';

/** wx:for 默认循环变量与 wxml 字面量,不算页面状态 */
const NON_STATE = new Set(['item', 'index', 'true', 'false', 'null', 'undefined']);

function wxmlRootIdentifiers(wxml: string): Set<string> {
  const roots = new Set<string>();
  for (const [, expr] of wxml.matchAll(/\{\{([^}]*)\}\}/g)) {
    // 去掉字符串字面量后,只取成员链的根标识符
    // (selectedAddress.id → selectedAddress;负向后行排除 .city 这类成员名)
    const cleaned = expr.replace(/'[^']*'|"[^"]*"/g, '');
    for (const [root] of cleaned.matchAll(/(?<![\w$.])[A-Za-z_$][\w$]*/g)) {
      if (!NON_STATE.has(root)) roots.add(root);
    }
  }
  return roots;
}

function pageStateKeys(js: string): Set<string> {
  const keys = new Set<string>();
  // data: { key: ..., } 块里的顶层键
  const dataBlock = js.match(/data:\s*\{([\s\S]*?)\n\s*\}/);
  if (dataBlock) {
    for (const [, key] of dataBlock[1].matchAll(/^\s*([A-Za-z_$][\w$]*)\s*:/gm)) keys.add(key);
  }
  // this.setData({ key: ... }) 里的顶层键(含 'a.b' 路径形式的根)
  for (const [, literal] of js.matchAll(/setData\(\s*\{([\s\S]*?)\}\s*[,)]/g)) {
    for (const [, key] of literal.matchAll(/['"]?([A-Za-z_$][\w$]*)(?:\.[\w$.[\]]+)?['"]?\s*:/g)) keys.add(key);
  }
  return keys;
}

describe('address-list wxml↔JS 绑定契约', () => {
  const wxml = fs.readFileSync(path.join(FRONTEND, `${PAGE}.wxml`), 'utf8');
  const js = fs.readFileSync(path.join(FRONTEND, `${PAGE}.js`), 'utf8');

  it('wxml 引用的每个状态标识符都必须在 JS 的 data/setData 中定义', () => {
    const bound = wxmlRootIdentifiers(wxml);
    const defined = pageStateKeys(js);
    const dead = [...bound].filter((id) => !defined.has(id)).sort();
    expect(dead).toEqual([]);
  });

  it('选中勾绑定使用 selectedAddress(单一事实源),不引入派生的 selectedId', () => {
    expect(wxml).not.toMatch(/selectedId/);
    expect(wxml).toMatch(/selectedAddress\.id\s*===\s*item\.id/);
  });
});
