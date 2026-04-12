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

// ============================================================
// Order Types
// ============================================================

/**
 * Order status enum
 */
export enum OrderStatus {
  PENDING_PAYMENT = 'PENDING_PAYMENT',
  PAID = 'PAID',
  SHIPPED = 'SHIPPED',
  DELIVERED = 'DELIVERED',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  REFUNDED = 'REFUNDED',
}

/**
 * Order item interface
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
  /** Item image URL */
  imageUrl?: string;
}

/**
 * Shipping address interface
 */
export interface Address {
  /** Address ID */
  id?: string;
  /** Recipient name */
  receiverName: string;
  /** Contact phone */
  phone: string;
  /** Detailed address */
  address: string;
  /** City */
  city: string;
  /** District/County */
  district: string;
  /** Street/Building */
  street?: string;
}

/**
 * Order interface
 */
export interface Order {
  /** Order ID */
  id: string;
  /** Order number */
  orderNumber: string;
  /** User ID */
  userId: string;
  /** Order items */
  items: OrderItem[];
  /** Order status */
  status: OrderStatus;
  /** Total price including shipping */
  totalPrice: number;
  /** Shipping fee */
  shippingFee: number;
  /** Order items total price */
  itemsTotalPrice: number;
  /** Shipping address */
  address: Address;
  /** Transaction ID from payment */
  transactionId?: string;
  /** Tracking number */
  trackingNumber?: string;
  /** Cancellation reason */
  cancelReason?: string;
  /** Refund transaction ID */
  refundTransactionId?: string;
  /** Refund reason */
  refundReason?: string;
  /** Creation timestamp */
  createdAt: string;
  /** Last update timestamp */
  updatedAt: string;
}

/**
 * Order statistics
 */
export interface OrderStatistics {
  /** Total orders count */
  totalOrders: number;
  /** Total amount spent */
  totalAmount: number;
  /** Average order value */
  averageOrderValue: number;
  /** Pending payment count */
  pendingCount: number;
  /** Paid count */
  paidCount: number;
  /** Shipped count */
  shippedCount: number;
  /** Delivered count */
  deliveredCount: number;
  /** Cancelled count */
  cancelledCount: number;
  /** Refunded count */
  refundedCount: number;
}

/**
 * Create order request
 */
export interface CreateOrderRequest {
  /** User ID */
  userId: string;
  /** Cart ID */
  cartId: string;
  /** Shipping address (optional, uses default if not provided) */
  address?: Address;
}

/**
 * Payment request
 */
export interface PaymentRequest {
  /** Payment method */
  paymentMethod: string;
  /** Payment amount */
  amount: number;
}

/**
 * Refund request
 */
export interface RefundRequest {
  /** Refund reason */
  reason: string;
  /** Refund amount */
  amount?: number;
}