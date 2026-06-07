import { ProductAPI } from './api';
import { setBaseUrl } from '../../shared/api/request';

function setWxResponse(data: unknown, statusCode = 200) {
  (wx.request as jest.Mock).mockImplementation((opts: {
    success: (res: unknown) => void;
    fail: (err: unknown) => void;
  }) => {
    opts.success({ statusCode, data });
  });
}

describe('features/product/api', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    setBaseUrl('http://test.local/api');
  });

  it('list(): calls GET /api/products with serialized query', async () => {
    setWxResponse({ products: [], page: 0, totalPages: 0, totalProducts: 0, hasNext: false, hasPrev: false });
    await ProductAPI.list({ page: 0, pageSize: 20, category: 'fish', keyword: 'salmon' });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/products?page=0&pageSize=20&category=fish&keyword=salmon');
    expect(call.method).toBe('GET');
  });

  it('list(): omits empty filters from query', async () => {
    setWxResponse({ products: [], page: 0, totalPages: 0, totalProducts: 0, hasNext: false, hasPrev: false });
    await ProductAPI.list({ page: 1, pageSize: 10 });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/products?page=1&pageSize=10');
  });

  it('getById(): URL-encodes the id', async () => {
    setWxResponse({ id: 'a/b', name: 'X' });
    await ProductAPI.getById('a/b');
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.url).toBe('http://test.local/api/products/a%2Fb');
  });
});
