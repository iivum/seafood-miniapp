import { CartAPI } from './cart';
import { Cart, CartItem, AddToCartParams, UpdateCartItemParams, ApiError } from '../types';
import { request } from '../../utils/request';

// Mock the request utility
jest.mock('../../utils/request');

const mockedRequest = request as jest.MockedFunction<typeof request>;

describe('CartAPI', () => {
  // Sample test data
  const sampleCartItem: CartItem = {
    id: '1',
    productId: 'product-1',
    name: 'Fresh Salmon',
    price: 29.99,
    quantity: 2,
    imageUrl: 'https://example.com/salmon.jpg',
  };

  const sampleCart: Cart = {
    id: 'cart-1',
    items: [sampleCartItem],
    totalPrice: 59.98,
    totalItems: 2,
    selectedItems: ['1'],
  };

  beforeEach(() => {
    mockedRequest.mockReset();
  });

  afterEach(() => {
    mockedRequest.mockReset();
  });

  describe('addToCart', () => {
    it('should add product to cart successfully', async () => {
      // Arrange
      const addParams: AddToCartParams = {
        productId: 'product-1',
        quantity: 2,
      };
      mockedRequest.mockResolvedValueOnce({
        success: true,
        data: sampleCart,
      });

      // Act
      const result = await CartAPI.addToCart(addParams);

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: '/cart',
        method: 'POST',
        data: addParams,
      });
      expect(result).toEqual(sampleCart);
    });

    it('should handle add to cart with default quantity', async () => {
      // Arrange
      const addParams: AddToCartParams = {
        productId: 'product-1',
        quantity: 1,
      };
      mockedRequest.mockResolvedValueOnce({
        success: true,
        data: sampleCart,
      });

      // Act
      const result = await CartAPI.addToCart(addParams);

      // Assert
      expect(result).toEqual(sampleCart);
    });

    it('should handle API error when adding to cart', async () => {
      // Arrange
      const apiError: ApiError = {
        message: 'Product not found',
        statusCode: 404,
        timestamp: new Date().toISOString(),
      };
      mockedRequest.mockRejectedValueOnce(apiError);

      // Act & Assert
      await expect(CartAPI.addToCart({ productId: 'invalid', quantity: 1 }))
        .rejects
        .toThrow('Failed to add item to cart: Product not found');
    });

    it('should handle network error when adding to cart', async () => {
      // Arrange
      const networkError = new Error('Network timeout');
      mockedRequest.mockRejectedValueOnce(networkError);

      // Act & Assert
      await expect(CartAPI.addToCart({ productId: 'product-1', quantity: 1 }))
        .rejects
        .toThrow('Network error occurred while adding to cart');
    });

    it('should validate add to cart parameters', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce({
        success: true,
        data: sampleCart,
      });

      // Act & Assert - Empty productId
      await expect(CartAPI.addToCart({ productId: '', quantity: 1 }))
        .rejects
        .toThrow('Invalid product ID');

      // Act & Assert - Zero quantity
      await expect(CartAPI.addToCart({ productId: 'product-1', quantity: 0 }))
        .rejects
        .toThrow('Invalid quantity');

      // Act & Assert - Negative quantity
      await expect(CartAPI.addToCart({ productId: 'product-1', quantity: -1 }))
        .rejects
        .toThrow('Invalid quantity');
    });

    it('should handle plain Error without Network keyword', async () => {
      // Arrange
      const plainError = new Error('Server error');
      mockedRequest.mockRejectedValueOnce(plainError);

      // Act & Assert
      await expect(CartAPI.addToCart({ productId: 'product-1', quantity: 1 }))
        .rejects
        .toThrow('Failed to add item to cart: Server error');
    });

    it('should handle non-object non-Error value', async () => {
      // Arrange
      mockedRequest.mockRejectedValueOnce('Something went wrong');

      // Act & Assert
      await expect(CartAPI.addToCart({ productId: 'product-1', quantity: 1 }))
        .rejects
        .toThrow('Failed to add item to cart: Unknown error occurred');
    });
  });

  describe('getCart', () => {
    it('should fetch cart successfully', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(sampleCart);

      // Act
      const result = await CartAPI.getCart();

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: '/cart',
        method: 'GET',
      });
      expect(result).toEqual(sampleCart);
    });

    it('should handle empty cart', async () => {
      // Arrange
      const emptyCart: Cart = {
        id: 'cart-1',
        items: [],
        totalPrice: 0,
        totalItems: 0,
        selectedItems: [],
      };
      mockedRequest.mockResolvedValueOnce({
        data: emptyCart,
      });

      // Act
      const result = await CartAPI.getCart();

      // Assert
      expect(result).toEqual(emptyCart);
      expect(result.items).toHaveLength(0);
    });

    it('should handle network error when fetching cart', async () => {
      // Arrange
      const networkError = new Error('Network timeout');
      mockedRequest.mockRejectedValueOnce(networkError);

      // Act & Assert
      await expect(CartAPI.getCart())
        .rejects
        .toThrow('Network error occurred while fetching cart');
    });

    it('should handle plain Error without Network keyword', async () => {
      // Arrange
      const plainError = new Error('Internal server error');
      mockedRequest.mockRejectedValueOnce(plainError);

      // Act & Assert
      await expect(CartAPI.getCart())
        .rejects
        .toThrow('Failed to fetch cart: Internal server error');
    });

    it('should handle object error without message property', async () => {
      // Arrange - plain object without message property hits typeof object branch
      mockedRequest.mockRejectedValueOnce({ code: 500 });

      // Act & Assert
      await expect(CartAPI.getCart())
        .rejects
        .toThrow('Failed to fetch cart: Unknown error');
    });

    it('should handle non-object non-Error value', async () => {
      // Arrange
      mockedRequest.mockRejectedValueOnce(null);

      // Act & Assert
      await expect(CartAPI.getCart())
        .rejects
        .toThrow('Failed to fetch cart: Unknown error occurred');
    });
  });

  describe('updateCartItem', () => {
    it('should update cart item quantity successfully', async () => {
      // Arrange
      const updateParams: UpdateCartItemParams = {
        itemId: '1',
        quantity: 5,
      };
      const updatedCart: Cart = {
        ...sampleCart,
        items: [{
          ...sampleCartItem,
          quantity: 5,
        }],
        totalPrice: 149.95,
        totalItems: 5,
      };
      mockedRequest.mockResolvedValueOnce(updatedCart);

      // Act
      const result = await CartAPI.updateCartItem(updateParams);

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: `/cart/items/${updateParams.itemId}`,
        method: 'PUT',
        data: { quantity: updateParams.quantity },
      });
      expect(result).toEqual(updatedCart);
    });

    it('should handle update with zero quantity (remove item)', async () => {
      // Arrange
      const updateParams: UpdateCartItemParams = {
        itemId: '1',
        quantity: 0,
      };
      const emptyCart: Cart = {
        id: 'cart-1',
        items: [],
        totalPrice: 0,
        totalItems: 0,
        selectedItems: [],
      };
      mockedRequest.mockResolvedValueOnce({
        data: emptyCart,
      });

      // Act
      const result = await CartAPI.updateCartItem(updateParams);

      // Assert
      expect(result).toEqual(emptyCart);
    });

    it('should validate update parameters', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(sampleCart);

      // Act & Assert - Empty itemId
      await expect(CartAPI.updateCartItem({ itemId: '', quantity: 1 }))
        .rejects
        .toThrow('Invalid item ID');

      // Act & Assert - Negative quantity
      await expect(CartAPI.updateCartItem({ itemId: '1', quantity: -1 }))
        .rejects
        .toThrow('Invalid quantity');
    });

    it('should handle plain Error without Network keyword', async () => {
      // Arrange
      const plainError = new Error('Forbidden');
      mockedRequest.mockRejectedValueOnce(plainError);

      // Act & Assert
      await expect(CartAPI.updateCartItem({ itemId: '1', quantity: 2 }))
        .rejects
        .toThrow('Failed to update cart item: Forbidden');
    });

    it('should handle object error without message property', async () => {
      // Arrange
      mockedRequest.mockRejectedValueOnce({ status: 403 });

      // Act & Assert
      await expect(CartAPI.updateCartItem({ itemId: '1', quantity: 2 }))
        .rejects
        .toThrow('Failed to update cart item: Unknown error');
    });

    it('should handle non-object non-Error value', async () => {
      // Arrange
      mockedRequest.mockRejectedValueOnce(500);

      // Act & Assert
      await expect(CartAPI.updateCartItem({ itemId: '1', quantity: 2 }))
        .rejects
        .toThrow('Failed to update cart item: Unknown error occurred');
    });
  });

  describe('removeCartItem', () => {
    it('should remove cart item successfully', async () => {
      // Arrange
      const emptyCart: Cart = {
        id: 'cart-1',
        items: [],
        totalPrice: 0,
        totalItems: 0,
        selectedItems: [],
      };
      mockedRequest.mockResolvedValueOnce({
        success: true,
        data: emptyCart,
      });

      // Act
      const result = await CartAPI.removeCartItem('1');

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: `/cart/items/1`,
        method: 'DELETE',
      });
      expect(result).toEqual(emptyCart);
    });

    it('should validate itemId parameter', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce({
        success: true,
        data: sampleCart,
      });

      // Act & Assert - Empty itemId
      await expect(CartAPI.removeCartItem(''))
        .rejects
        .toThrow('Invalid item ID');
    });

    it('should handle plain Error without Network keyword', async () => {
      // Arrange
      const plainError = new Error('Item not found');
      mockedRequest.mockRejectedValueOnce(plainError);

      // Act & Assert
      await expect(CartAPI.removeCartItem('1'))
        .rejects
        .toThrow('Failed to remove cart item: Item not found');
    });

    it('should handle non-object non-Error value', async () => {
      // Arrange
      mockedRequest.mockRejectedValueOnce(undefined);

      // Act & Assert
      await expect(CartAPI.removeCartItem('1'))
        .rejects
        .toThrow('Failed to remove cart item: Unknown error occurred');
    });
  });

  describe('toggleItemSelection', () => {
    it('should toggle item selection successfully', async () => {
      // Arrange
      const updatedCart: Cart = {
        ...sampleCart,
        selectedItems: [],
      };
      mockedRequest.mockResolvedValueOnce(updatedCart);

      // Act
      const result = await CartAPI.toggleItemSelection('1');

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: `/cart/items/1/toggle-selection`,
        method: 'PATCH',
      });
      expect(result).toEqual(updatedCart);
    });

    it('should validate itemId parameter', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(sampleCart);

      // Act & Assert - Empty itemId
      await expect(CartAPI.toggleItemSelection(''))
        .rejects
        .toThrow('Invalid item ID');
    });

    it('should handle plain Error without Network keyword', async () => {
      // Arrange
      const plainError = new Error('Conflict');
      mockedRequest.mockRejectedValueOnce(plainError);

      // Act & Assert
      await expect(CartAPI.toggleItemSelection('1'))
        .rejects
        .toThrow('Failed to toggle item selection: Conflict');
    });

    it('should handle non-object non-Error value', async () => {
      // Arrange
      mockedRequest.mockRejectedValueOnce(true);

      // Act & Assert
      await expect(CartAPI.toggleItemSelection('1'))
        .rejects
        .toThrow('Failed to toggle item selection: Unknown error occurred');
    });
  });

  describe('clearCart', () => {
    it('should clear cart successfully', async () => {
      // Arrange
      const emptyCart: Cart = {
        id: 'cart-1',
        items: [],
        totalPrice: 0,
        totalItems: 0,
        selectedItems: [],
      };
      mockedRequest.mockResolvedValueOnce({
        success: true,
        data: emptyCart,
      });

      // Act
      const result = await CartAPI.clearCart();

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: '/cart',
        method: 'DELETE',
      });
      expect(result).toEqual(emptyCart);
    });

    it('should handle plain Error without Network keyword', async () => {
      // Arrange
      const plainError = new Error('Service unavailable');
      mockedRequest.mockRejectedValueOnce(plainError);

      // Act & Assert
      await expect(CartAPI.clearCart())
        .rejects
        .toThrow('Failed to clear cart: Service unavailable');
    });

    it('should handle non-object non-Error value', async () => {
      // Arrange
      mockedRequest.mockRejectedValueOnce({ error: 'Unknown error' });

      // Act & Assert
      await expect(CartAPI.clearCart())
        .rejects
        .toThrow('Failed to clear cart: Unknown error');
    });
  });
});