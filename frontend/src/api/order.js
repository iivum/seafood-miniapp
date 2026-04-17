/**
 * Order API (JavaScript Version - CommonJS)
 *
 * Provides methods for order management including creation,
 * payment, cancellation, and status tracking
 */

const { request } = require('../../utils/request.js');

// Order status enum
const OrderStatus = {
  PENDING_PAYMENT: 'PENDING_PAYMENT',
  PAID: 'PAID',
  SHIPPED: 'SHIPPED',
  DELIVERED: 'DELIVERED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  REFUNDED: 'REFUNDED',
};

/**
 * Order API for interacting with the backend order service
 */
class OrderAPI {
  /**
   * Base API endpoint for orders
   */
  static get BASE_ENDPOINT() {
    return '/orders';
  }

  /**
   * Create order from shopping cart
   */
  static async createOrder(userId, cartId) {
    if (!userId || !cartId) {
      throw new Error('User ID and Cart ID are required');
    }

    try {
      const response = await request({
        url: OrderAPI.BASE_ENDPOINT,
        method: 'POST',
        data: { userId, cartId },
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to create order');
    }
  }

  /**
   * Get order by ID
   */
  static async getOrderById(orderId) {
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
   */
  static async getOrdersByUser(userId, status) {
    if (!userId) {
      throw new Error('User ID is required');
    }

    try {
      const params = { userId };
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

      return response.map((order) => OrderAPI.validateOrderResponse(order));
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to get orders');
    }
  }

  /**
   * Process payment for an order
   */
  static async processPayment(orderId, paymentMethod, amount) {
    if (!orderId) {
      throw new Error('Order ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/payment`,
        method: 'POST',
        data: { paymentMethod, amount },
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to process payment');
    }
  }

  /**
   * Ship an order (admin action)
   */
  static async shipOrder(orderId, trackingNumber) {
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
   */
  static async completeOrder(orderId) {
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
   */
  static async cancelOrder(orderId, reason) {
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
   */
  static async processRefund(orderId, reason, amount) {
    if (!orderId) {
      throw new Error('Order ID is required');
    }

    try {
      const response = await request({
        url: `${OrderAPI.BASE_ENDPOINT}/${orderId}/refund`,
        method: 'POST',
        data: { reason, amount },
        needAuth: true,
      });

      return OrderAPI.validateOrderResponse(response);
    } catch (error) {
      return OrderAPI.handleError(error, 'Failed to process refund');
    }
  }

  /**
   * Get order statistics for a user
   */
  static async getOrderStatistics(userId, startDate, endDate) {
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
   */
  static validateOrderResponse(response) {
    if (!response || typeof response !== 'object') {
      throw new Error('Invalid order response');
    }

    const r = response;
    let status = r.status;
    if (typeof status === 'string') {
      status = OrderStatus[status] || OrderStatus.PENDING_PAYMENT;
    }

    return {
      id: r.id,
      orderNumber: r.orderNumber,
      userId: r.userId,
      items: r.items || [],
      status: status,
      totalPrice: r.totalPrice,
      shippingFee: r.shippingFee || 0,
      itemsTotalPrice: r.itemsTotalPrice || r.totalPrice,
      address: r.address,
      transactionId: r.transactionId,
      trackingNumber: r.trackingNumber,
      cancelReason: r.cancelReason,
      refundTransactionId: r.refundTransactionId,
      refundReason: r.refundReason,
      createdAt: r.createdAt,
      updatedAt: r.updatedAt,
    };
  }

  /**
   * Validate statistics response structure
   */
  static validateStatisticsResponse(response) {
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
   */
  static handleError(error, defaultMessage) {
    if (error instanceof Error) {
      if (error.message.includes('Network')) {
        throw new Error(`Network error occurred: ${defaultMessage}`);
      }
      throw new Error(`${defaultMessage}: ${error.message}`);
    }

    if (typeof error === 'object' && error !== null) {
      throw new Error(`${defaultMessage}: ${error.message || 'Unknown error'}`);
    }

    throw new Error(`${defaultMessage}: Unknown error occurred`);
  }
}

/**
 * Map order status to display text (Chinese)
 */
const ORDER_STATUS_TEXT = {
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
const ORDER_STATUS_CLASS = {
  [OrderStatus.PENDING_PAYMENT]: 'pending',
  [OrderStatus.PAID]: 'paid',
  [OrderStatus.SHIPPED]: 'shipped',
  [OrderStatus.DELIVERED]: 'delivered',
  [OrderStatus.COMPLETED]: 'completed',
  [OrderStatus.CANCELLED]: 'cancelled',
  [OrderStatus.REFUNDED]: 'refunded',
};

module.exports = {
  OrderAPI,
  OrderStatus,
  ORDER_STATUS_TEXT,
  ORDER_STATUS_CLASS,
};
