import * as fs from 'fs';
import * as path from 'path';

/**
 * box-shadow 禁止裸写 hex 颜色(必须走 var(--shadow-*, rgba(...)) token)。
 *
 * 背景(2026-07-10 D5 e2e 实跑发现):多个页面 wxss 把阴影色裸写成
 * 全不透明 `#231814`(设计 token `--shadow-sm` 是 rgba(35,24,20,0.05)
 * 的 5% 透明度)—— 渲染成明显暗晕而非柔和投影,且违反 wxss 文件头
 * 注释自己声明的 token 约束。规则:剥掉 var(...) 回退串后,box-shadow
 * 声明里不得再残留任何 # 颜色字面量。
 */

const FRONTEND = path.resolve(__dirname, '../..');

function listWxss(dir: string): string[] {
  const out: string[] = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...listWxss(full));
    else if (entry.name.endsWith('.wxss')) out.push(full);
  }
  return out;
}

describe('pages/pages-sub 全部 wxss:box-shadow 无裸 hex', () => {
  const files = [
    ...listWxss(path.join(FRONTEND, 'pages')),
    ...listWxss(path.join(FRONTEND, 'pages-sub')),
  ];

  it.each(files.map((f) => [path.relative(FRONTEND, f), f]))(
    '%s',
    (_rel, full) => {
      const wxss = fs.readFileSync(full as string, 'utf8');
      const offenders: string[] = [];
      for (const [decl] of wxss.matchAll(/box-shadow:[^;]*;/g)) {
        const stripped = decl.replace(/var\([^)]*\)/g, '');
        if (/#[0-9a-fA-F]{3,8}/.test(stripped)) offenders.push(decl.trim());
      }
      expect(offenders).toEqual([]);
    },
  );
});
