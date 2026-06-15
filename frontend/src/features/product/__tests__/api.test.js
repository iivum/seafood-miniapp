/**
 * ProductAPI runtime shim tests.
 */

// Mock path must resolve to src/shared/api/request from this test file location
jest.mock('../../../shared/api/request', () => ({
  get: jest.fn().mockResolvedValue({ data: [] }),
}));

const { ProductAPI } = require('../api');
const { get } = require('../../../shared/api/request');

describe('ProductAPI', () => {
  beforeEach(() => jest.clearAllMocks());

  it('list builds query string with page and pageSize', async () => {
    await ProductAPI.list({ page: 0, pageSize: 20 });
    expect(get).toHaveBeenCalledWith('/products?page=0&pageSize=20');
  });

  it('list includes category when provided', async () => {
    await ProductAPI.list({ page: 0, pageSize: 10, category: '鱼类' });
    const calledWith = get.mock.calls[0][0];
    expect(calledWith).toContain('category=');
  });

  it('list includes keyword when provided', async () => {
    await ProductAPI.list({ page: 0, pageSize: 10, keyword: '虾' });
    const calledWith = get.mock.calls[0][0];
    expect(calledWith).toContain('keyword=');
  });

  it('getById encodes the id', async () => {
    await ProductAPI.getById('abc/123');
    expect(get).toHaveBeenCalledWith('/products/abc%2F123');
  });
});
