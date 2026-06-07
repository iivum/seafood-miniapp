/**
 * Product feature: API client.
 *
 * Public endpoints (no auth required) per backend contract:
 *   GET /api/products          — paginated, with optional category/keyword
 *   GET /api/products/{id}     — single product detail
 */
import { get } from '../../shared/api/request';
import type { Product, PaginatedProducts, ProductQueryParams } from './types';

export const ProductAPI = {
  list(params: ProductQueryParams): Promise<PaginatedProducts> {
    // Backend takes query string for GET — the shared `get` helper
    // appends it.
    const qs = new URLSearchParams();
    qs.set('page', String(params.page));
    qs.set('pageSize', String(params.pageSize));
    if (params.category) qs.set('category', params.category);
    if (params.keyword) qs.set('keyword', params.keyword);
    return get<PaginatedProducts>(`/products?${qs.toString()}`);
  },

  getById(id: string): Promise<Product> {
    return get<Product>(`/products/${encodeURIComponent(id)}`);
  },
};
