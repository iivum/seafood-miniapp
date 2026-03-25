import { Product, ProductQueryParams, PaginatedProducts, ApiError } from '../types';
import { request } from '../../utils/request';

/**
 * Product API for interacting with the backend product service
 *
 * Provides methods for fetching and managing products with proper
 * error handling, input validation, and type safety
 */
export class ProductAPI {
  /**
   * Base API endpoint for products
   */
  private static readonly BASE_ENDPOINT = '/products';

  /**
   * Fetch products from the backend with pagination, filtering, and search
   *
   * @param params - Query parameters for pagination and filtering
   * @returns Promise containing paginated products
   * @throws Error if the request fails or validation fails
   */
  static async getProducts(params: ProductQueryParams): Promise<PaginatedProducts> {
    // Validate input parameters
    ProductAPI.validatePaginationParams(params);

    try {
      const response = await request({
        url: ProductAPI.BASE_ENDPOINT,
        method: 'GET',
        data: params,
      });

      // Type guard to ensure response has expected structure
      if (!ProductAPI.isValidPaginatedResponse(response)) {
        // If response is not in expected format, return empty result
        // This handles cases where API returns different format
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
      // Handle different types of errors
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while fetching products');
        }
        throw new Error(`Failed to fetch products: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        const apiError = error as Partial<ApiError>;
        throw new Error(`Failed to fetch products: ${apiError.message || 'Unknown error'}`);
      }

      throw new Error('Failed to fetch products: Unknown error occurred');
    }
  }

  /**
   * Validate pagination parameters
   *
   * @param params - Parameters to validate
   * @throws Error if validation fails
   */
  private static validatePaginationParams(params: ProductQueryParams): void {
    if (!Number.isInteger(params.page) || params.page < 0) {
      throw new Error('Invalid pagination parameters: page must be a non-negative integer');
    }

    if (!Number.isInteger(params.pageSize) || params.pageSize <= 0) {
      throw new Error('Invalid pagination parameters: pageSize must be a positive integer');
    }

    // Sanitize keyword to prevent potential XSS
    if (params.keyword) {
      params.keyword = ProductAPI.sanitizeInput(params.keyword);
    }

    // Sanitize category
    if (params.category) {
      params.category = ProductAPI.sanitizeInput(params.category);
    }
  }

  /**
   * Type guard to validate paginated response format
   *
   * @param response - Response to validate
   * @returns true if response has valid paginated format
   */
  private static isValidPaginatedResponse(response: any): response is PaginatedProducts {
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

  /**
   * Sanitize user input to prevent XSS attacks
   *
   * @param input - Input string to sanitize
   * @returns Sanitized string
   */
  private static sanitizeInput(input: string): string {
    const div = typeof document !== 'undefined' ? document.createElement('div') : null;
    if (div) {
      div.textContent = input;
      return div.innerHTML;
    }

    // Server-side fallback - basic HTML escaping
    return input
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#x27;');
  }
}