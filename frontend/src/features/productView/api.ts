/**
 * ProductView feature: API client.
 *
 * 收藏 + 浏览足迹(design.md D6):record() 是 best-effort 调用方(product-detail.js
 * onLoad)负责吞掉异常,这里不做额外重试/降级。
 *
 *   POST /api/product-views/{productId} — 记一条足迹(静默,upsert)
 *   GET  /api/product-views             — 足迹列表(富化,按 viewedAt 降序)
 */
import { get, post } from '../../shared/api/request';

export interface ProductViewItem {
  productId: string;
  productName: string;
  price: number;
  imageUrl: string;
  available: boolean;
  viewedAt: string;
}

export const ProductViewAPI = {
  record(productId: string): Promise<void> {
    return post<void>(`/product-views/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  list(): Promise<ProductViewItem[]> {
    return get<ProductViewItem[]>('/product-views', { needAuth: true });
  },
};
