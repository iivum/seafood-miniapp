import { Order, OrderStatus, OrderStatistics, CreateOrderRequest, PaymentRequest, RefundRequest } from '../types';
import { request } from '../../utils/request';

/**
 * Order API for interacting with the backend order service
 *
 * Provides methods for order management including creation,
 * payment, cancellation, and status tracking
 */
export class OrderAPI {
  /**
   * Base API endpoint for orders
   */
  private static readonly BASE_ENDPOINT = '/api/orders';

  /**
   * Create order from shopping cart
   *
   * @param userId - User ID
   * @param cartId - Cart ID
   * @returns Promise containing the created order
   */
  static async createOrder(userId: string, cartId: string): Promise<Order> {
    if (!userId || !cartId) {
      throw new Error('User ID and Cart ID are required');
    }

    try {
      const response = await request({
        url: OrderAPI.BASE_ENDPOINT,
        method: 'POST',
        data: { userId, cartId } as CreateOrderRequest,
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to create order');
    }
  }

  /**
   * Get order by ID
   *
   * @param orderId - Order ID
   * @returns Promise containing the order details
   */
  static async getOrderById(orderId: string): Promise<Order> {
    if (!orderId) {
      throw new Error('Order ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}`,
        method: 'GET',
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to get order');
    }
  }

  /**
   * Get orders by user ID and status
   *
   * @param userId - User ID
   * @param status - Optional order status filter
   * @returns Promise containing list of orders
   */
  static async getOrdersByUser(userId: string, status?: OrderStatus): Promise<Order[]> {
    if (!userId) {
      throw new Error('User ID is required');
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
        needAuth: true,
      });

      if (!Array.isArray(response)) {
        console.warn('Invalid response format from Orders API');
        return [];
      }

      return response.map((order: any) => OrderAPI.validateOrderResponse(order));
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to get orders');
    }
  }

  /**
   * Process payment for an order
   *
   * @param orderId - Order ID
   * @param paymentMethod - Payment method (e.g., 'WECHAT_PAY')
   * @param amount - Payment amount
   * @returns Promise containing the updated order
   */
  static async processPayment(orderId: string, paymentMethod: string, amount: number): Promise<Order> {
    if (!orderId) {
      throw new Error('Order ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/payment`,
        method: 'POST',
        data: { paymentMethod, amount } as PaymentRequest,
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to process payment');
    }
  }

  /**
   * Ship an order (admin action)
   *
   * @param orderId - Order ID
   * @param trackingNumber - Shipping tracking number
   * @returns Promise containing the updated order
   */
  static async shipOrder(orderId: string, trackingNumber: string): Promise<Order> {
    if (!orderId || !trackingNumber) {
      throw new Error('Order ID and tracking number are required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/ship`,
        method: 'PUT',
        data: { trackingNumber },
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to ship order');
    }
  }

  /**
   * Complete an order (mark as delivered)
   *
   * @param orderId - Order ID
   * @returns Promise containing the updated order
   */
  static async completeOrder(orderId: string): Promise<Order> {
    if (!orderId) {
      throw new Error('Order ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/complete`,
        method: 'PUT',
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to complete order');
    }
  }

  /**
   * Cancel an order
   *
   * @param orderId - Order ID
   * @param reason - Cancellation reason
   * @returns Promise containing the updated order
   */
  static async cancelOrder(orderId: string, reason: string): Promise<Order> {
    if (!orderId) {
      throw new Error('Order ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/cancel`,
        method: 'PUT',
        data: { reason },
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to cancel order');
    }
  }

  /**
   * Process refund for an order
   *
   * @param orderId - Order ID
   * @param reason - Refund reason
   * @param amount - Optional refund amount (full refund if not specified)
   * @returns Promise containing the updated order
   */
  static async processRefund(orderId: string, reason: string, amount?: number): Promise<Order> {
    if (!orderId) {
      throw new Error('Order ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/refund`,
        method: 'POST',
        data: { reason, amount } as RefundRequest,
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to process refund');
    }
  }

  /**
   * Get order statistics for a user
   *
   * @param userId - User ID
   * @param startDate - Start date (YYYY-MM-DD)
   * @param endDate - End date (YYYY-MM-DD)
   * @returns Promise containing order statistics
   */
  static async getOrderStatistics(
    userId: string,
    startDate: string,
    endDate: string
  ): Promise<OrderStatistics> {
    if (!userId || !startDate || !endDate) {
      throw new Error('User ID, start date, and end date are required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${userId}/statistics`,
        method: 'GET',
        data: { startDate, endDate },
        needAuth: true,
      });

      return OrderAPI.validateStatisticsResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to get order statistics');
    }
  }

  /**
   * Validate order response structure
   *
   * @param response - Response to validate
   * @returns Validated order object
   */
  private static validateOrderResponse(response: any): Order {
    if (!response || typeof response !== 'object') {
      throw new Error('Invalid order response');
    }

    // Map status string to OrderStatus enum if needed
    let status = response.status;
    if (typeof status === 'string') {
      status = OrderStatus[status as keyof typeof OrderStatus] || OrderStatus.PENDING_PAYMENT;
    }

    return {
      id: response.id,
      orderNumber: response.orderNumber,
      userId: response.userId,
      items: response.items || [],
      status,
      totalPrice: response.totalPrice,
      shippingFee: response.shippingFee || 0,
      itemsTotalPrice: response.itemsTotalPrice || response.totalPrice,
      address: response.address,
      transactionId: response.transactionId,
      trackingNumber: response.trackingNumber,
      cancelReason: response.cancelReason,
      refundTransactionId: response.refundTransactionId,
      refundReason: response.refundReason,
      createdAt: response.createdAt,
      updatedAt: response.updatedAt,
    };
  }

  /**
   * Validate statistics response structure
   *
   * @param response - Response to validate
   * @returns Validated statistics object
   */
  private static validateStatisticsResponse(response: any): OrderStatistics {
    if (!response || typeof response !== 'object') {
      throw new Error('Invalid statistics response');
    }

    return {
      totalOrders: response.totalOrders || 0,
      totalAmount: response.totalAmount || 0,
      averageOrderValue: response.averageOrderValue || 0,
      pendingCount: response.pendingCount || 0,
      paidCount: response.paidCount || 0,
      shippedCount: response.shippedCount || 0,
      deliveredCount: response.deliveredCount || 0,
      cancelledCount: response.cancelledCount || 0,
      refundedCount: response.refundedCount || 0,
    };
  }

  /**
   * Handle errors consistently
   *
   * @param error - Error to handle
   * @param defaultMessage - Default error message
   * @returns Never returns, always throws
   */
  private static handleError(error: unknown, defaultMessage: string): never {
    if (error instanceof Error) {
      if (error.message.includes('Network')) {
        throw new Error(`Network error occurred: ${defaultMessage}`);
      }
      throw new Error(`${defaultMessage}: ${error.message}`);
    }

    if (typeof error === 'object' && error !== null) {
      const apiError = error as { message?: string; statusCode?: number };
      throw new Error(`${defaultMessage}: ${apiError.message || 'Unknown error'}`);
    }

    throw new Error(`${defaultMessage}: Unknown error occurred`);
  }
}

/**
 * Map order status to display text (Chinese)
 */
export const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  [OrderStatus.PENDING_PAYMENT]: '待付款',
  [OrderStatus.PAID]: '已支付',
  [OrderStatus.SHIPPED]: '已发货',
  [OrderStatus.DELIVERED]: '已发货',
  [OrderStatus.COMPLETED]: '已完成',
  [OrderStatus.CANCELLED]: '已取消',
  [OrderStatus.REFUNDED]: '已退款',
};

/**
 * Map order status to CSS class suffix
 */
export const ORDER_STATUS_CLASS: Record<OrderStatus, string> = {
  [OrderStatus.PENDING_PAYMENT]: 'pending',
  [OrderStatus.PAID]: 'paid',
  [OrderStatus.SHIPPED]: 'shipped',
  [OrderStatus.DELIVERED]: 'delivered',
  [OrderStatus.COMPLETED]: 'completed',
  [OrderStatus.CANCELLED]: 'cancelled',
  [OrderStatus.REFUNDED]: 'refunded',
};
