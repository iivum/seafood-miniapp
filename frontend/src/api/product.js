/**
 * Product API (JavaScript Version)
 *
 * Provides methods for fetching and managing products with proper
 * error handling and type safety.
 */

const { request } = require('../../utils/request.js');

/**
 * Product API for interacting with the backend product service
 */
class ProductAPI {
  /**
   * Base API endpoint for products
   */
  static get BASE_ENDPOINT() {
    return '/products';
  }

  /**
   * Fetch products from the backend with pagination, filtering, and search
   */
  static async getProducts(params) {
    // Validate input parameters
    ProductAPI.validatePaginationParams(params);

    try {
      const response = await request({
        url: ProductAPI.BASE_ENDPOINT,
        method: 'GET',
        data: params,
      });

      // sprint-1-closure 8.x: backend 用 Spring Data Page<T> 格式
      // { content, totalElements, totalPages, number, size, first, last }
      // mp 端 UI 用自定义格式 { products, page, totalPages, totalProducts, hasNext, hasPrev }
      // 这里做转换,让 mp 端代码不感知后端分页协议
      console.log('[ProductAPI] raw response keys:', response ? Object.keys(response) : 'null');
      if (Array.isArray(response?.content)) {
        console.log('[ProductAPI] Spring Page detected, content len:', response.content.length);
        const totalPages = response.totalPages || 0;
        const number = response.number || 0;
        return {
          products: response.content,
          page: number,
          totalPages,
          totalProducts: response.totalElements || response.content.length,
          hasNext: response.last === false && number < totalPages - 1,
          hasPrev: response.first === false && number > 0,
        };
      }

      // 旧格式(如果后端迁回)直接返
      if (!ProductAPI.isValidPaginatedResponse(response)) {
        console.warn('Invalid response format from Product API');
        return {
          products: [],
          page: params.page,
          totalPages: 0,
          totalProducts: 0,
          hasNext: false,
          hasPrev: false,
        };
      }

      return response;
    } catch (error) {
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while fetching products');
        }
        throw new Error(`Failed to fetch products: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        throw new Error(`Failed to fetch products: ${error.message || 'Unknown error'}`);
      }

      throw new Error('Failed to fetch products: Unknown error occurred');
    }
  }

  /**
   * Validate pagination parameters
   */
  static validatePaginationParams(params) {
    if (!Number.isInteger(params.page) || params.page < 0) {
      throw new Error('Invalid pagination parameters: page must be a non-negative integer');
    }

    if (!Number.isInteger(params.pageSize) || params.pageSize <= 0) {
      throw new Error('Invalid pagination parameters: pageSize must be a positive integer');
    }
  }

  /**
   * Type guard to validate paginated response format
   */
  static isValidPaginatedResponse(response) {
    return (
      response &&
      Array.isArray(response.products) &&
      typeof response.page === 'number' &&
      typeof response.totalPages === 'number' &&
      typeof response.totalProducts === 'number' &&
      typeof response.hasNext === 'boolean' &&
      typeof response.hasPrev === 'boolean'
    );
  }
}

module.exports = { ProductAPI };
