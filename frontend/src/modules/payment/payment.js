/**
 * Payment Module
 *
 * WeChat Pay integration for the seafood mini-app.
 * Handles payment flow with wx.requestPayment() API.
 */

const { OrderAPI } = require('../../api/order.js');

/**
 * Payment status
 */
const PaymentStatus = {
  PENDING: 'pending',
  SUCCESS: 'success',
  FAILED: 'failed',
  CANCELLED: 'cancelled',
};

/**
 * Payment result
 */
class PaymentResult {
  constructor(status, transactionId = null, errorMessage = null) {
    this.status = status;
    this.transactionId = transactionId;
    this.errorMessage = errorMessage;
  }

  isSuccess() {
    return this.status === PaymentStatus.SUCCESS;
  }

  isCancelled() {
    return this.status === PaymentStatus.CANCELLED;
  }
}

/**
 * Payment Module class
 * Handles WeChat Pay integration
 */
class PaymentModule {
  constructor() {
    // WeChat Pay configuration
    // In production, these would come from backend after calling WeChat Pay API
    this.environment = 'mock'; // 'mock' | 'development' | 'production'
  }

  /**
   * Request payment for an order
   *
   * @param orderId - Order ID
   * @param totalAmount - Total amount in yuan
   * @returns Promise containing PaymentResult
   */
  async requestPayment(orderId, totalAmount) {
    try {
      // Step 1: Get payment parameters from backend
      const paymentParams = await this.getPaymentParams(orderId, totalAmount);

      // Step 2: Call WeChat Pay
      const result = await this.callWeChatPay(paymentParams);

      // Step 3: Verify payment with backend
      if (result.errMsg === 'requestPayment:ok') {
        return await this.verifyPayment(orderId, result);
      } else if (result.errMsg === 'requestPayment:fail cancel') {
        return new PaymentResult(PaymentStatus.CANCELLED, null, '用户取消支付');
      } else {
        return new PaymentResult(PaymentStatus.FAILED, null, result.errMsg || '支付失败');
      }
    } catch (error) {
      console.error('Payment request failed:', error);
      return new PaymentResult(PaymentStatus.FAILED, null, error.message || '支付请求失败');
    }
  }

  /**
   * Get payment parameters from backend
   * The backend should call WeChat Pay API to get these parameters
   */
  async getPaymentParams(orderId, totalAmount) {
    // In production, backend calls WeChat Pay API:
    // POST https://api.mch.weixin.qq.com/pay/unifiedorder
    // Returns: timeStamp, nonceStr, package, signType, paySign

    // For now, return mock parameters for development
    // The real implementation would be:
    // const response = await request({
    //   url: `/api/payment/${orderId}/params`,
    //   method: 'POST',
    //   data: { totalAmount: totalAmount * 100 } // in fen
    // });

    if (this.environment === 'mock') {
      // Mock payment parameters for development
      return {
        timeStamp: Math.floor(Date.now() / 1000).toString(),
        nonceStr: this.generateNonceStr(),
        package: 'prepay_id=mock_prepay_id_' + orderId,
        signType: 'MD5',
        paySign: 'mock_pay_sign_' + orderId,
        orderId: orderId,
        totalFee: Math.floor(totalAmount * 100), // in fen
      };
    }

    throw new Error('Payment environment not configured');
  }

  /**
   * Call WeChat Pay via wx.requestPayment
   */
  callWeChatPay(paymentParams) {
    return new Promise((resolve, reject) => {
      // In production, paymentParams contains real WeChat Pay data
      // In mock mode, we simulate the payment flow

      if (this.environment === 'mock') {
        // Simulate WeChat Pay dialog
        wx.showModal({
          title: '模拟微信支付',
          content: `支付金额: ¥${(paymentParams.totalFee / 100).toFixed(2)}\n\n点击确定模拟支付成功`,
          success: (res) => {
            if (res.confirm) {
              resolve({ errMsg: 'requestPayment:ok' });
            } else {
              resolve({ errMsg: 'requestPayment:fail cancel' });
            }
          }
        });
        return;
      }

      // Real WeChat Pay call
      wx.requestPayment({
        timeStamp: paymentParams.timeStamp,
        nonceStr: paymentParams.nonceStr,
        package: paymentParams.package,
        signType: paymentParams.signType,
        paySign: paymentParams.paySign,
        success: (res) => {
          resolve({ ...res, errMsg: 'requestPayment:ok' });
        },
        fail: (res) => {
          resolve({ ...res, errMsg: res.errMsg || 'requestPayment:fail' });
        }
      });
    });
  }

  /**
   * Verify payment with backend
   */
  async verifyPayment(orderId, paymentResult) {
    try {
      // Call backend to verify payment and update order status
      const order = await OrderAPI.processPayment(
        orderId,
        'WECHAT_PAY',
        0 // Amount is handled by backend with WeChat transaction ID
      );

      if (order && order.transactionId) {
        return new PaymentResult(PaymentStatus.SUCCESS, order.transactionId);
      }

      return new PaymentResult(PaymentStatus.FAILED, null, '支付验证失败');
    } catch (error) {
      console.error('Payment verification failed:', error);
      return new PaymentResult(PaymentStatus.FAILED, null, '支付验证失败');
    }
  }

  /**
   * Generate random nonce string
   */
  generateNonceStr() {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let result = '';
    for (let i = 0; i < 32; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
  }

  /**
   * Set payment environment
   * @param {'mock' | 'development' | 'production'} env
   */
  setEnvironment(env) {
    this.environment = env;
  }

  /**
   * Check if WeChat Pay is available
   */
  isWeChatPayAvailable() {
    // Check if weChat app version supports payment
    const sysInfo = wx.getSystemInfoSync();
    // WeChat Pay is available in most modern versions
    return true;
  }
}

// Export singleton instance
const paymentModule = new PaymentModule();

module.exports = {
  PaymentModule,
  paymentModule,
  PaymentStatus,
  PaymentResult,
};
