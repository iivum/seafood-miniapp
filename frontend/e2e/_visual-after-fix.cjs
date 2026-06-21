/**
 * Visual after fix — screenshot mp pages
 */
const automator = require('miniprogram-automator');
const path = require('node:path');

const WS = 'ws://127.0.0.1:9420';
const OUT = '/Users/linbinghui/agent-work/seafood-miniapp/frontend/e2e/screenshots';

const PAGES = [
  { name: 'mp-01-home', tab: '/pages/index/index' },
  { name: 'mp-02-category', tab: '/pages/category/category' },
  { name: 'mp-04-cart', tab: '/pages/cart/cart',
    storage: { userInfo: { openId: 'dev-mock', nickName: 'shoot' }, token: 'dev-mock-jwt' } },
  { name: 'mp-08-order-list', url: '/pages-sub/order/order-list/order-list',
    storage: { userInfo: { openId: 'dev-mock', nickName: 'shoot' }, token: 'dev-mock-jwt' } },
  { name: 'mp-07-address-list', url: '/pages-sub/user/address/address-list',
    storage: { userInfo: { id: 'dev-user-001', openId: 'dev-mock', nickName: 'shoot' }, token: 'dev-mock-jwt' } },
  { name: 'mp-03-product-detail', url: '/pages-sub/product/product-detail/product-detail?id=6a2f097fcb28035db83d88b3' },
  { name: 'mp-06-order-confirm', url: '/pages-sub/order/order-confirm/order-confirm',
    storage: { userInfo: { openId: 'dev-mock', nickName: 'shoot' }, token: 'dev-mock-jwt' } },
];

(async () => {
  for (const p of PAGES) {
    let mp;
    try {
      mp = await automator.connect({ wsEndpoint: WS });
      if (p.storage) {
        await mp.evaluate(s => {
          for (const [k, v] of Object.entries(s)) wx.setStorageSync(k, v);
        }, p.storage);
      }
      if (p.tab) await mp.switchTab(p.tab);
      else await mp.navigateTo(p.url);
      await new Promise(r => setTimeout(r, 4500));
      const file = path.join(OUT, `${p.name}-v2.1.png`);
      await mp.screenshot({ path: file });
      console.log(`✓ ${p.name} → ${file}`);
    } catch (e) {
      console.log(`✗ ${p.name}: ${e.message.slice(0, 100)}`);
    } finally {
      if (mp) { try { await mp.close(); } catch {} }
    }
  }
})();
