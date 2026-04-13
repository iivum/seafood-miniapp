import {
  Order,
  OrderStatus,
  CreateOrderParams,
  OrderStatistics,
  ApiError,
} from '../types';
import { request } from '../../utils/request';

/**
 * Order API for interacting with the backend order service
 *
 * Provides methods for order management including creation,
 * status updates, and payment processing
 */
export class OrderAPI {
  /**
   * Base API endpoint for orders
   */
  private static readonly BASE_ENDPOINT = '/orders';

  /**
   * Create a new order from the shopping cart
   *
   * @param params - Order creation parameters
   * @returns Promise containing the created order
   * @throws Error if the request fails or validation fails
   */
  static async createOrder(params: CreateOrderParams): Promise<Order> {
    OrderAPI.validateCreateOrderParams(params);

    try {
      const response = await request({
        url: OrderAPI.BASE_ENDPOINT,
        method: 'POST',
        data: params,
      });

      if (!OrderAPI.isValidOrderResponse(response)) {
        throw new Error('Invalid response format from Order API');
      }

      return response;
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to create order');
    }
  }

  /**
   * Get order by ID
   *
   * @param orderId - The order ID
   * @returns Promise containing the order details
   * @throws Error if the request fails
   */
  static async getOrderById(orderId: string): Promise<Order> {
    if (!orderId || typeof orderId !== 'string') {
      throw new Error('Invalid order ID');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}`,
        method: 'GET',
      });

      if (!OrderAPI.isValidOrderResponse(response)) {
        throw new Error('Invalid response format from Order API');
      }

      return response;
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to fetch order');
    }
  }

  /**
   * Get orders by user ID and optional status filter
   *
   * @param userId - The user ID
   * @param status - Optional status filter
   * @returns Promise containing list of orders
   * @throws Error if the request fails
   */
  static async getOrders(userId: string, status?: OrderStatus): Promise<Order[]> {
    if (!userId || typeof userId !== 'string') {
      throw new Error('Invalid user ID');
    }

    try {
      const params: Record<string, string> = { userId };
      if (status) {
        params.status = status;
      }

      const response = await request({
        url: OrderAPI.BASE_ENDPOINT,
        method: 'GET',
        data: params,
      });

      if (!Array.isArray(response)) {
        throw new Error('Invalid response format: expected array');
      }

      return response;
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to fetch orders');
    }
  }

  /**
   * Process payment for an order
   *
   * @param orderId - The order ID
   * @param paymentData - Payment details (e.g., transaction ID)
   * @returns Promise containing the updated order
   * @throws Error if the request fails
   */
  static async processPayment(
    orderId: string,
    paymentData: { transactionId: string }
  ): Promise<Order> {
    if (!orderId || typeof orderId !== 'string') {
      throw new Error('Invalid order ID');
    }

    if (!paymentData?.transactionId) {
      throw new Error('Transaction ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/payment`,
        method: 'POST',
        data: paymentData,
      });

      if (!OrderAPI.isValidOrderResponse(response)) {
        throw new Error('Invalid response format from Order API');
      }

      return response;
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to process payment');
    }
  }

  /**
   * Cancel an order
   *
   * @param orderId - The order ID
   * @param reason - Cancellation reason
   * @returns Promise containing the updated order
   * @throws Error if the request fails
   */
  static async cancelOrder(orderId: string, reason: string): Promise<Order> {
    if (!orderId || typeof orderId !== 'string') {
      throw new Error('Invalid order ID');
    }

    if (!reason || typeof reason !== 'string') {
      throw new Error('Cancellation reason is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/cancel`,
        method: 'PUT',
        data: { reason },
      });

      if (!OrderAPI.isValidOrderResponse(response)) {
        throw new Error('Invalid response format from Order API');
      }

      return response;
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to cancel order');
    }
  }

  /**
   * Get order statistics for a user
   *
   * @param userId - The user ID
   * @param startDate - Start date for statistics period
   * @param endDate - End date for statistics period
   * @returns Promise containing order statistics
   * @throws Error if the request fails
   */
  static async getOrderStatistics(
    userId: string,
    startDate: string,
    endDate: string
  ): Promise<OrderStatistics> {
    if (!userId || typeof userId !== 'string') {
      throw new Error('Invalid user ID');
    }

    if (!startDate || !endDate) {
      throw new Error('Start date and end date are required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${userId}/statistics`,
        method: 'GET',
        data: { startDate, endDate },
      });

      if (!OrderAPI.isValidStatisticsResponse(response)) {
        throw new Error('Invalid response format from Statistics API');
      }

      return response;
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to fetch order statistics');
    }
  }

  /**
   * Validate create order parameters
   *
   * @param params - Parameters to validate
   * @throws Error if validation fails
   */
  private static validateCreateOrderParams(params: CreateOrderParams): void {
    if (!params.userId || typeof params.userId !== 'string') {
      throw new Error('Invalid user ID');
    }

    if (!params.cartId || typeof params.cartId !== 'string') {
      throw new Error('Invalid cart ID');
    }

    if (!params.addressId || typeof params.addressId !== 'string') {
      throw new Error('Invalid address ID');
    }

    if (params.selectedItemIds && !Array.isArray(params.selectedItemIds)) {
      throw new Error('Selected item IDs must be an array');
    }
  }

  /**
   * Type guard to validate order response format
   *
   * @param response - Response to validate
   * @returns true if response has valid order format
   */
  private static isValidOrderResponse(response: any): response is Order {
    return (
      response &&
      typeof response.id === 'string' &&
      typeof response.userId === 'string' &&
      typeof response.orderNumber === 'string' &&
      Array.isArray(response.items) &&
      typeof response.totalPrice === 'number' &&
      typeof response.finalPrice === 'number' &&
      typeof response.status === 'string' &&
      response.shippingAddress !== null
    );
  }

  /**
   * Type guard to validate statistics response format
   *
   * @param response - Response to validate
   * @returns true if response has valid statistics format
   */
  private static isValidStatisticsResponse(
    response: any
  ): response is OrderStatistics {
    return (
      response &&
      typeof response.totalOrders === 'number' &&
      typeof response.totalAmount === 'number'
    );
  }

  /**
   * Handle errors from API calls
   *
   * @param error - The error that occurred
   * @param defaultMessage - Default error message
   * @returns Never returns, always throws
   * @throws Error with appropriate message
   */
  private static handleError(error: unknown, defaultMessage: string): never {
    if (error instanceof Error) {
      if (error.message.includes('Network')) {
        throw new Error(`Network error occurred while ${defaultMessage.toLowerCase()}`);
      }
      throw error;
    }

    if (typeof error === 'object' && error !== null) {
      const apiError = error as Partial<ApiError>;
      throw new Error(`${defaultMessage}: ${apiError.message || 'Unknown error'}`);
    }

    throw new Error(`${defaultMessage}: Unknown error occurred`);
  }
}

/**
 * Helper function to get human-readable status text
 *
 * @param status - Order status
 * @returns Human-readable status text in Chinese
 */
export function getOrderStatusText(status: OrderStatus): string {
  const statusMap: Record<OrderStatus, string> = {
    PENDING_PAYMENT: '待支付',
    PAID: '已支付',
    SHIPPED: '已发货',
    DELIVERED: '已送达',
    CANCELLED: '已取消',
    REFUNDED: '已退款',
  };

  return statusMap[status] || status;
}

/**
 * Helper function to get status color class
 *
 * @param status - Order status
 * @returns CSS class name for status badge
 */
export function getOrderStatusClass(status: OrderStatus): string {
  const classMap: Record<OrderStatus, string> = {
    PENDING_PAYMENT: 'status-pending',
    PAID: 'status-paid',
    SHIPPED: 'status-shipped',
    DELIVERED: 'status-delivered',
    CANCELLED: 'status-cancelled',
    REFUNDED: 'status-refunded',
  };

  return classMap[status] || 'status-default';
}