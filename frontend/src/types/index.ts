// Core type definitions for the seafood mini-app

/**
 * Product interface representing a seafood product
 * Maps to the backend Product.java model
 */
export interface Product {
  /** Product ID */
  id: string;
  /** Product name */
  name: string;
  /** Product description */
  description: string;
  /** Product price */
  price: number;
  /** Available stock quantity */
  stock: number;
  /** Product category */
  category: string;
  /** Product image URL */
  imageUrl: string;
  /** Whether the product is on sale */
  onSale: boolean;
}

/**
 * Pagination parameters for product queries
 */
export interface PaginationParams {
  /** Page number (0-based) */
  page: number;
  /** Number of items per page */
  pageSize: number;
}

/**
 * Product query parameters
 */
export interface ProductQueryParams extends PaginationParams {
  /** Optional category filter */
  category?: string;
  /** Optional search keyword */
  keyword?: string;
}

/**
 * Paginated product response
 */
export interface PaginatedProducts {
  /** Array of products for the current page */
  products: Product[];
  /** Current page number */
  page: number;
  /** Total number of pages */
  totalPages: number;
  /** Total number of products */
  totalProducts: number;
  /** Whether there is a next page */
  hasNext: boolean;
  /** Whether there is a previous page */
  hasPrev: boolean;
}

/**
 * Error response from API
 */
export interface ApiError {
  /** Error message */
  message: string;
  /** HTTP status code */
  statusCode: number;
  /** Error timestamp */
  timestamp: string;
}

/**
 * Loading state for async operations
 */
export interface LoadingState {
  /** Whether data is being loaded */
  isLoading: boolean;
  /** Whether an error occurred */
  isError: boolean;
  /** Error object if any */
  error: ApiError | null;
}

/**
 * Product state interface for frontend state management
 */
export interface ProductState extends LoadingState {
  /** Currently loaded products */
  products: Product[];
  /** Current pagination info */
  pagination: {
    currentPage: number;
    totalPages: number;
    totalProducts: number;
  };
  /** Applied filters */
  filters: {
    category?: string;
    keyword?: string;
  };
}

/**
 * Cart item interface representing an item in the shopping cart
 */
export interface CartItem {
  /** Cart item ID */
  id: string;
  /** Product ID */
  productId: string;
  /** Product name */
  name: string;
  /** Product price */
  price: number;
  /** Quantity in cart */
  quantity: number;
  /** Product image URL */
  imageUrl: string;
}

/**
 * Shopping cart interface
 */
export interface Cart {
  /** Cart ID */
  id: string;
  /** Array of cart items */
  items: CartItem[];
  /** Total price of all items */
  totalPrice: number;
  /** Total number of items */
  totalItems: number;
  /** Array of selected item IDs */
  selectedItems: string[];
}

/**
 * Parameters for adding item to cart
 */
export interface AddToCartParams {
  /** Product ID to add */
  productId: string;
  /** Quantity to add */
  quantity: number;
}

/**
 * Parameters for updating cart item
 */
export interface UpdateCartItemParams {
  /** Item ID to update */
  itemId: string;
  /** New quantity */
  quantity: number;
}

/**
 * Cart state interface for frontend state management
 */
export interface CartState extends LoadingState {
  /** Current cart data */
  cart: Cart;
  /** Whether cart is being updated */
  isUpdating: boolean;
}

// ============ Order Types ============

/**
 * Order status enum
 */
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

/**
 * Order item interface representing a single item in an order
 */
export interface OrderItem {
  /** Item ID */
  id: string;
  /** Product ID */
  productId: string;
  /** Product name */
  name: string;
  /** Product price */
  price: number;
  /** Quantity ordered */
  quantity: number;
  /** Total price for this item (price * quantity) */
  totalPrice: number;
  /** Product image URL */
  imageUrl: string;
}

/**
 * Shipping address interface
 */
export interface ShippingAddress {
  /** Address ID */
  id: string;
  /** Recipient name */
  receiverName: string;
  /** Phone number */
  phone: string;
  /** Province */
  province: string;
  /** City */
  city: string;
  /** District/County */
  district: string;
  /** Detailed address */
  detail: string;
  /** Postal code */
  postalCode?: string;
  /** Is default address */
  isDefault: boolean;
}

/**
 * Order history entry
 */
export interface OrderHistory {
  /** Description of the status change */
  description: string;
  /** Status at the time of this entry */
  status: OrderStatus;
  /** Timestamp of this history entry */
  timestamp: string;
}

/**
 * Order interface
 */
export interface Order {
  /** Order ID */
  id: string;
  /** User ID */
  userId: string;
  /** Order number (human readable) */
  orderNumber: string;
  /** Order items */
  items: OrderItem[];
  /** Total price before discounts and shipping */
  totalPrice: number;
  /** Final price after discounts and shipping */
  finalPrice: number;
  /** Discount amount applied */
  discountAmount: number;
  /** Shipping fee */
  shippingFee: number;
  /** Current order status */
  status: OrderStatus;
  /** Shipping address */
  shippingAddress: ShippingAddress;
  /** Transaction ID from payment */
  transactionId?: string;
  /** Tracking number */
  trackingNumber?: string;
  /** Order note */
  note?: string;
  /** Cancellation reason */
  cancellationReason?: string;
  /** Refund transaction ID */
  refundTransactionId?: string;
  /** Refund reason */
  refundReason?: string;
  /** Order creation timestamp */
  createdAt: string;
  /** Payment timestamp */
  paidAt?: string;
  /** Shipping timestamp */
  shippedAt?: string;
  /** Delivery timestamp */
  deliveredAt?: string;
  /** Cancellation timestamp */
  cancelledAt?: string;
  /** Refund timestamp */
  refundedAt?: string;
  /** Order history */
  orderHistory: OrderHistory[];
}

/**
 * Parameters for creating an order
 */
export interface CreateOrderParams {
  /** User ID */
  userId: string;
  /** Cart ID */
  cartId: string;
  /** Address ID for shipping */
  addressId: string;
  /** Selected item IDs from cart (optional) */
  selectedItemIds?: string[];
}

/**
 * Order statistics
 */
export interface OrderStatistics {
  /** Total number of orders */
  totalOrders: number;
  /** Total amount spent */
  totalAmount: number;
  /** Number of pending payment orders */
  pendingPaymentCount: number;
  /** Number of paid orders */
  paidCount: number;
  /** Number of shipped orders */
  shippedCount: number;
  /** Number of delivered orders */
  deliveredCount: number;
}

/**
 * Order state interface for frontend state management
 */
export interface OrderState extends LoadingState {
  /** Current order (for single order views) */
  order: Order | null;
  /** Orders list */
  orders: Order[];
  /** Order statistics */
  statistics: OrderStatistics | null;
}