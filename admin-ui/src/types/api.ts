/**
 * Types mirrored from backend Java records.
 *  - `ProductResponse` / `ProductRequest` / `ProductStatsResponse`  → product.api.dto
 *  - `OrderResponse`                                                → order.api.dto
 *  - `OrderDetailResponse`                                         → bff.admin.dto
 *  - `DashboardResponse` / `OrderStatsResponse` / `TopProductResponse` → bff.admin.dto
 *  - `UserResponse` / `TokenResponse` / `AdminLoginRequest`         → user.api.dto
 *
 * These are intentionally hand-maintained (not auto-generated) to keep
 * the build hermetic. The backend error contract is `ErrorResponse`:
 *   { code: string, message: string, fieldErrors?: Record<string,string> }
 */

export type ProductStatus = 'ACTIVE' | 'OUT_OF_STOCK' | 'DISCONTINUED';

export const PRODUCT_CATEGORIES = ['鱼类', '虾蟹', '贝类', '软体', '海藻'] as const;
export type ProductCategory = (typeof PRODUCT_CATEGORIES)[number];

export interface ProductResponse {
  id: string;
  name: string;
  description: string;
  price: string; // BigDecimal serialized as string
  stock: number;
  category: string;
  imageUrl: string;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  name: string;
  description: string;
  price: number;
  stock: number;
  category: string;
  imageUrl: string;
}

export interface ProductStatsResponse {
  total: number;
  onSale: number;
  outOfStock: number;
  byCategory: Record<string, number>;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page
  size: number;
}

export type OrderStatusCode = 'PENDING' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED';

export interface OrderItem {
  productId: string;
  productName: string;
  unitPrice: string;
  quantity: number;
}

export interface OrderResponse {
  id: string;
  userId: string;
  items: OrderItem[];
  totalAmount: string;
  status: OrderStatusCode;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OrderDetailItem {
  productId: string;
  productName: string;
  unitPrice: string;
  quantity: number;
  product: ProductResponse | null;
}

export interface OrderDetailResponse {
  order: OrderResponse;
  customer: UserResponse;
  items: OrderDetailItem[];
}

export interface OrderStatsResponse {
  today: number;
  week: number;
  month: number;
}

export interface TopProductResponse {
  product: ProductResponse | null;
  totalQuantitySold: number;
}

export interface DashboardResponse {
  orderStats: OrderStatsResponse;
  productStats: ProductStatsResponse;
  topProducts: TopProductResponse[];
}

export interface UserResponse {
  id: string;
  openId: string | null;
  nickname: string;
  avatarUrl: string;
  role: string;
  phone: string | null;
  addresses: unknown[];
  createdAt: string;
}

export interface AdminLoginRequest {
  username: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  role: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
