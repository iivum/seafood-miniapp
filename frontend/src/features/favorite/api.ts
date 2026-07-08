/**
 * Favorite feature: API client.
 *
 * 收藏 + 浏览足迹(design.md):self-scoped 门面,同 /api/addresses、/api/cart
 * 既有惯例——身份取自后端 JWT principal,不在 URL 里带 userId。
 *
 *   POST   /api/favorites/{productId}  — 收藏(幂等)
 *   DELETE /api/favorites/{productId}  — 取消收藏(幂等)
 *   GET    /api/favorites              — 收藏列表(富化)
 */
import { del, get, post } from '../../shared/api/request';

export interface FavoriteItem {
  productId: string;
  productName: string;
  price: number;
  imageUrl: string;
  available: boolean;
}

export const FavoriteAPI = {
  add(productId: string): Promise<string[]> {
    return post<string[]>(`/favorites/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  remove(productId: string): Promise<string[]> {
    return del<string[]>(`/favorites/${encodeURIComponent(productId)}`, { needAuth: true });
  },
  list(): Promise<FavoriteItem[]> {
    return get<FavoriteItem[]>('/favorites', { needAuth: true });
  },
};
