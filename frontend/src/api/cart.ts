import { Cart, AddToCartParams, UpdateCartItemParams, ApiError } from '../types';
import { request } from '../../utils/request';

/**
 * Cart API for interacting with the backend cart service
 *
 * Provides methods for managing shopping cart with proper
 * error handling, input validation, and type safety
 */
export class CartAPI {
  /**
   * Base API endpoint for cart
   */
  private static readonly BASE_ENDPOINT = '/cart';

  /**
   * Add a product to the cart
   *
   * @param params - Parameters containing productId and quantity
   * @returns Promise containing updated cart
   * @throws Error if the request fails or validation fails
   */
  static async addToCart(params: AddToCartParams): Promise<Cart> {
    // Validate input parameters
    CartAPI.validateAddToCartParams(params);

    try {
      const response = await request({
        url: CartAPI.BASE_ENDPOINT,
        method: 'POST',
        data: params,
      });

      // Return the cart data directly or response itself if it's already in correct format
      return response.data || response;
    } catch (error) {
      // Handle different types of errors
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while adding to cart');
        }
        throw new Error(`Failed to add item to cart: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        const apiError = error as Partial<ApiError>;
        throw new Error(`Failed to add item to cart: ${apiError.message || 'Unknown error'}`);
      }

      throw new Error('Failed to add item to cart: Unknown error occurred');
    }
  }

  /**
   * Get the current user's cart
   *
   * @returns Promise containing the cart
   * @throws Error if the request fails
   */
  static async getCart(): Promise<Cart> {
    try {
      const response = await request({
        url: CartAPI.BASE_ENDPOINT,
        method: 'GET',
      });

      // Return the cart data directly or response itself if it's already in correct format
      return response.data || response;
    } catch (error) {
      // Handle different types of errors
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while fetching cart');
        }
        throw new Error(`Failed to fetch cart: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        const apiError = error as Partial<ApiError>;
        throw new Error(`Failed to fetch cart: ${apiError.message || 'Unknown error'}`);
      }

      throw new Error('Failed to fetch cart: Unknown error occurred');
    }
  }

  /**
   * Update cart item quantity
   *
   * @param params - Parameters containing itemId and new quantity
   * @returns Promise containing updated cart
   * @throws Error if the request fails or validation fails
   */
  static async updateCartItem(params: UpdateCartItemParams): Promise<Cart> {
    // Validate input parameters
    CartAPI.validateUpdateCartItemParams(params);

    try {
      const response = await request({
        url: `${CartAPI.BASE_ENDPOINT}/items/${params.itemId}`,
        method: 'PUT',
        data: { quantity: params.quantity },
      });

      // Return the cart data directly or response itself if it's already in correct format
      return response.data || response;
    } catch (error) {
      // Handle different types of errors
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while updating cart item');
        }
        throw new Error(`Failed to update cart item: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        const apiError = error as Partial<ApiError>;
        throw new Error(`Failed to update cart item: ${apiError.message || 'Unknown error'}`);
      }

      throw new Error('Failed to update cart item: Unknown error occurred');
    }
  }

  /**
   * Remove an item from the cart
   *
   * @param itemId - ID of the item to remove
   * @returns Promise containing updated cart
   * @throws Error if the request fails or validation fails
   */
  static async removeCartItem(itemId: string): Promise<Cart> {
    // Validate itemId
    if (!itemId || typeof itemId !== 'string' || itemId.trim() === '') {
      throw new Error('Invalid item ID');
    }

    try {
      const response = await request({
        url: `${CartAPI.BASE_ENDPOINT}/items/${itemId}`,
        method: 'DELETE',
      });

      // Return the cart data, falling back to response if .data is undefined
      return response.data || response;
    } catch (error) {
      // Handle different types of errors
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while removing cart item');
        }
        throw new Error(`Failed to remove cart item: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        const apiError = error as Partial<ApiError>;
        throw new Error(`Failed to remove cart item: ${apiError.message || 'Unknown error'}`);
      }

      throw new Error('Failed to remove cart item: Unknown error occurred');
    }
  }

  /**
   * Toggle item selection in cart
   *
   * @param itemId - ID of the item to toggle selection
   * @returns Promise containing updated cart
   * @throws Error if the request fails or validation fails
   */
  static async toggleItemSelection(itemId: string): Promise<Cart> {
    // Validate itemId
    if (!itemId || typeof itemId !== 'string' || itemId.trim() === '') {
      throw new Error('Invalid item ID');
    }

    try {
      const response = await request({
        url: `${CartAPI.BASE_ENDPOINT}/items/${itemId}/toggle-selection`,
        method: 'PATCH',
      });

      // Return the cart data, falling back to response if .data is undefined
      return response.data || response;
    } catch (error) {
      // Handle different types of errors
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while toggling item selection');
        }
        throw new Error(`Failed to toggle item selection: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        const apiError = error as Partial<ApiError>;
        throw new Error(`Failed to toggle item selection: ${apiError.message || 'Unknown error'}`);
      }

      throw new Error('Failed to toggle item selection: Unknown error occurred');
    }
  }

  /**
   * Clear all items from the cart
   *
   * @returns Promise containing empty cart
   * @throws Error if the request fails
   */
  static async clearCart(): Promise<Cart> {
    try {
      const response = await request({
        url: CartAPI.BASE_ENDPOINT,
        method: 'DELETE',
      });

      // Return the cart data, falling back to response if .data is undefined
      return response.data || response;
    } catch (error) {
      // Handle different types of errors
      if (error instanceof Error) {
        if (error.message.includes('Network')) {
          throw new Error('Network error occurred while clearing cart');
        }
        throw new Error(`Failed to clear cart: ${error.message}`);
      }

      if (typeof error === 'object' && error !== null) {
        const apiError = error as Partial<ApiError>;
        throw new Error(`Failed to clear cart: ${apiError.message || 'Unknown error'}`);
      }

      throw new Error('Failed to clear cart: Unknown error occurred');
    }
  }

  /**
   * Validate add to cart parameters
   *
   * @param params - Parameters to validate
   * @throws Error if validation fails
   */
  private static validateAddToCartParams(params: AddToCartParams): void {
    if (!params.productId || typeof params.productId !== 'string' || params.productId.trim() === '') {
      throw new Error('Invalid product ID');
    }

    if (typeof params.quantity !== 'number' || params.quantity <= 0) {
      throw new Error('Invalid quantity');
    }
  }

  /**
   * Validate update cart item parameters
   *
   * @param params - Parameters to validate
   * @throws Error if validation fails
   */
  private static validateUpdateCartItemParams(params: UpdateCartItemParams): void {
    if (!params.itemId || typeof params.itemId !== 'string' || params.itemId.trim() === '') {
      throw new Error('Invalid item ID');
    }

    if (typeof params.quantity !== 'number' || params.quantity < 0) {
      throw new Error('Invalid quantity');
    }
  }
}