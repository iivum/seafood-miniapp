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
export const PRODUCT_STATUSES = ['ACTIVE', 'OUT_OF_STOCK', 'DISCONTINUED'] as const;

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

// ----- Banner(banner.api.dto)-----
export type BannerTone = 'ACCENT' | 'SOFT';
export const BANNER_TONES = ['ACCENT', 'SOFT'] as const;
export type BannerStatus = 'ACTIVE' | 'INACTIVE';

export interface BannerResponse {
  id: string;
  tone: BannerTone;
  emoji: string;
  title: string;
  subtitle: string;
  targetProductId: string | null;
  sortOrder: number;
  status: BannerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface BannerRequest {
  tone: BannerTone;
  emoji: string;
  title: string;
  subtitle: string;
  targetProductId: string | null;
  sortOrder: number;
  active: boolean;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page
  size: number;
}

export type OrderStatusCode =
  | 'PENDING'
  | 'PAID'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDING'
  | 'REFUNDED';

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
  // 4.1 物流信息(SHIPPED 之后才有值;老订单无字段,前端按 undefined 处理)
  tracking?: {
    carrier: string;
    trackingNumber: string;
    events: Array<{ at: string; status: string; location: string; description: string }>;
  } | null;
}

// ===== 路线图 4.13 / 4.15:批量发货 + CSV 导出 =====

export interface BatchShipRequest {
  orderIds: string[];
  carrier?: string;
  trackingNumber?: string;
}

export interface BatchShipFailedItem {
  orderId: string;
  reason: string;
}

export interface BatchShipResponse {
  successIds: string[];
  failed: BatchShipFailedItem[];
  total: number;
  successCount: number;
  failedCount: number;
}

// ===== 路线图 4.8 + 4.11:退款审核 =====

export type RefundStatusCode = 'REQUESTED' | 'APPROVED' | 'REJECTED';

export interface RefundResponse {
  id: string;
  orderId: string;
  userId: string;
  amount: string;
  reason: string;
  status: RefundStatusCode;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
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

export interface TrendPointResponse {
  date: string; // ISO local date (yyyy-MM-dd),UTC+8
  count: number;
}

export interface DashboardResponse {
  orderStats: OrderStatsResponse;
  productStats: ProductStatsResponse;
  topProducts: TopProductResponse[];
  // 路线图 2.17 / 2.18 / 2.21:
  trend7d: TrendPointResponse[];
  lowStock: ProductResponse[];
  recentOrders: OrderResponse[];
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
