import { CartAPI } from './api';
import { setBaseUrl } from '../../shared/api/request';
import { tokenStorage } from '../../shared/api/storage';
import { ProductAPI } from '../product/api';

/**
 * 路线图 2.5 E2E — 「首页 → 加购 → 购物车」全链路契约测试。
 *
 * <p>覆盖 5 段契约:
 * <ol>
 *   <li>首页 GET /api/products?page=0&size=20 返回商品列表(ACTIVE 过滤)</li>
 *   <li>点加购 POST /api/cart/items {productId, quantity:1}</li>
 *   <li>购物车 GET /api/cart 返回含该商品的 cart(后端 upsert 行为)</li>
 *   <li>点 stepper + PUT /api/cart/items/{productId} {quantity: 2} 增数</li>
 *   <li>点删除 DELETE /api/cart/items/{productId}</li>
 * </ol>
 * <p>本测试覆盖"业务契约",不实际渲染 mp 端 WXML — 视觉差异(2.23 验收)由
 * design owner 拍图对比 OD HTML 测得,本测试不替代。
 */

function setWxResponse(data: unknown, statusCode = 200) {
  (wx.request as jest.Mock).mockImplementation((opts: {
    success: (res: unknown) => void;
    fail: (err: unknown) => void;
  }) => {
    opts.success({ statusCode, data });
  });
}

function setWxResponseSequence(responses: unknown[]) {
  let i = 0;
  (wx.request as jest.Mock).mockImplementation((opts: {
    success: (res: unknown) => void;
    fail: (err: unknown) => void;
  }) => {
    const r = responses[i++];
    if (r && typeof r === 'object' && 'statusCode' in (r as Record<string, unknown>)) {
      opts.success(r);
    } else {
      opts.fail(r);
    }
  });
}

const sampleProducts = {
  products: [
    { id: 'p1', name: '三文鱼', description: '新鲜', price: 99, stock: 10,
      category: '鱼类', imageUrl: 'http://img/1.jpg', status: 'ACTIVE', createdAt: '', updatedAt: '' },
    { id: 'p2', name: '金枪鱼', description: '', price: 199, stock: 5,
      category: '鱼类', imageUrl: 'http://img/2.jpg', status: 'ACTIVE', createdAt: '', updatedAt: '' },
  ],
  page: 0, totalPages: 1, totalProducts: 2, hasNext: false, hasPrev: false,
};

describe('2.5 E2E: 首页 → 加购 → 购物车', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    setBaseUrl('http://test.local/api');
    tokenStorage.setTokens('a', 'r');
  });

  it('2.5.1:首页 GET /api/products?page=0&pageSize=20 → 拉商品列表', async () => {
    setWxResponse(sampleProducts);
    const products = await ProductAPI.list({ page: 0, pageSize: 20 });
    expect(products.products).toHaveLength(2);
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/products?page=0&pageSize=20');
    expect(call.method).toBe('GET');
  });

  it('2.5.2:点加购 POST /api/cart/items {productId, quantity:1} → 后端 upsert', async () => {
    setWxResponse({ userId: 'u1', items: [], totalQuantity: 0, totalSelectedQuantity: 0, selectedAmount: 0 });
    await CartAPI.addItem({ productId: 'p1', quantity: 1 });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items');
    expect(call.method).toBe('POST');
    expect(call.data).toEqual({ productId: 'p1', quantity: 1 });
  });

  it('2.5.3:购物车 GET /api/cart → 看到已加购商品', async () => {
    setWxResponse({
      id: 'u1', userId: 'u1', updatedAt: '2026-06-13T00:00:00Z',
      totalQuantity: 1, totalSelectedQuantity: 1, selectedAmount: 99,
      items: [
        { productId: 'p1', quantity: 1, selected: true, addedAt: '2026-06-13T00:00:00Z' },
      ],
    });
    const cart = await CartAPI.get();
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0].productId).toBe('p1');
  });

  it('2.5.4:stepper + → PUT /api/cart/items/{productId} {productId, quantity: 2}', async () => {
    setWxResponse({
      id: 'u1', userId: 'u1', updatedAt: '2026-06-13T00:00:00Z',
      totalQuantity: 2, totalSelectedQuantity: 2, selectedAmount: 198,
      items: [
        { productId: 'p1', quantity: 2, selected: true, addedAt: '2026-06-13T00:00:00Z' },
      ],
    });
    await CartAPI.updateItem('p1', { productId: 'p1', quantity: 2 });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items/p1');
    expect(call.method).toBe('PUT');
    expect(call.data).toEqual({ productId: 'p1', quantity: 2 });
  });

  it('2.5.5:删除 → DELETE /api/cart/items/{productId}', async () => {
    setWxResponse({ id: 'u1', userId: 'u1', items: [], updatedAt: '2026-06-13T00:00:00Z',
      totalQuantity: 0, totalSelectedQuantity: 0, selectedAmount: 0 });
    await CartAPI.removeItem('p1');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/cart/items/p1');
    expect(call.method).toBe('DELETE');
  });

  it('2.5.6:全链路 — 首页列表 → 加购 p1 → 购物车看到 p1(同一次会话)', async () => {
    setWxResponseSequence([
      // 1) 首页列表
      { statusCode: 200, data: sampleProducts },
      // 2) 加购 p1
      { statusCode: 200, data: {
        id: 'u1', userId: 'u1', updatedAt: '2026-06-13T00:00:00Z',
        totalQuantity: 1, totalSelectedQuantity: 1, selectedAmount: 99,
        items: [{ productId: 'p1', quantity: 1, selected: true, addedAt: '2026-06-13T00:00:00Z' }],
      } },
      // 3) 购物车查询
      { statusCode: 200, data: {
        id: 'u1', userId: 'u1', updatedAt: '2026-06-13T00:00:00Z',
        totalQuantity: 1, totalSelectedQuantity: 1, selectedAmount: 99,
        items: [{ productId: 'p1', quantity: 1, selected: true, addedAt: '2026-06-13T00:00:00Z' }],
      } },
    ]);
    // 模拟 mp 端完整流程
    const products = await ProductAPI.list({ page: 0, pageSize: 20 });
    expect(products.products).toHaveLength(2);
    await CartAPI.addItem({ productId: products.products[0].id, quantity: 1 });
    const cart = await CartAPI.get();
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0].productId).toBe('p1');
  });

  it('2.5.7:OD v2 视觉契约 — 4 屏 wxml 关键 token 引用断言(token 单一源)', async () => {
    // 本测试不直接渲染 WXML(那需要 jsdom + weapp-mp runtime 复杂 mock),
    // 改为通过文件存在性 + token 引用断言:
    //   - 4 屏 wxml 必须存在
    //   - wxml 必须引用 shared 组件(shared-button / shared-loading / shared-empty)
    //   - wxml 必须用 class 引用 OD v2 关键视觉类(home-banner / cat-sidebar /
    //     detail-info / cart-footer 等)
    // 真实视觉差异 < 5% 由 design owner 拍图对比(2.23 验收)
    const fs = require('fs');
    const path = require('path');
    const pages = [
      'pages/index/index.wxml',
      'pages/category/category.wxml',
      'pages-sub/product/product-detail/product-detail.wxml',
      'pages/cart/cart.wxml',
    ];
    const root = path.resolve(__dirname, '../../..');
    for (const rel of pages) {
      const abs = path.join(root, rel);
      expect(fs.existsSync(abs)).toBe(true);
      const content = fs.readFileSync(abs, 'utf-8');
      // 必须引用 shared 组件
      expect(content).toMatch(/shared-(button|loading|empty)/);
    }
    // 4 屏 wxml 关键视觉类必须存在(不直接比色值,比类名 — 色值走 var(--token, fallback))
    const indexWxml = fs.readFileSync(path.join(root, 'pages/index/index.wxml'), 'utf-8');
    expect(indexWxml).toMatch(/home-banner/);
    expect(indexWxml).toMatch(/home-chip/);
    expect(indexWxml).toMatch(/home-grid/);
    const catWxml = fs.readFileSync(path.join(root, 'pages/category/category.wxml'), 'utf-8');
    expect(catWxml).toMatch(/cat-sidebar/);
    expect(catWxml).toMatch(/cat-grid/);
    const detailWxml = fs.readFileSync(path.join(root, 'pages-sub/product/product-detail/product-detail.wxml'), 'utf-8');
    expect(detailWxml).toMatch(/detail-info/);
    expect(detailWxml).toMatch(/detail-stepper/);
    expect(detailWxml).toMatch(/detail-footer/);
    const cartWxml = fs.readFileSync(path.join(root, 'pages/cart/cart.wxml'), 'utf-8');
    expect(cartWxml).toMatch(/cart-checkbox/);
    expect(cartWxml).toMatch(/cart-stepper/);
    expect(cartWxml).toMatch(/cart-footer/);
  });
});

