/**
 * Product feature: types.
 *
 * Canonical Product shape matching the backend `Product.java` model.
 * Re-exports the legacy type from `src/types/index.ts` so existing
 * callers (and the JS page modules) keep working.
 */
export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  stock: number;
  /** Category id (matches backend sealed interface). */
  category: string;
  imageUrl: string;
  status: 'ACTIVE' | 'OUT_OF_STOCK' | 'DISCONTINUED';
  createdAt: string;
  updatedAt: string;
}

export interface ProductQueryParams {
  page: number;
  pageSize: number;
  category?: string;
  keyword?: string;
}

export interface PaginatedProducts {
  products: Product[];
  page: number;
  totalPages: number;
  totalProducts: number;
  hasNext: boolean;
  hasPrev: boolean;
}
