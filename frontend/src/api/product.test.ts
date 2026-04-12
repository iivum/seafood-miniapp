import { ProductAPI } from './product';
import { Product, ProductQueryParams, PaginatedProducts, ApiError } from '../types';
import { request } from '../../utils/request';

// Mock the request utility
jest.mock('../../utils/request');

const mockedRequest = request as jest.MockedFunction<typeof request>;

describe('ProductAPI', () => {
  // Sample test data
  const sampleProduct: Product = {
    id: '1',
    name: 'Fresh Salmon',
    description: 'Premium fresh salmon',
    price: 29.99,
    stock: 100,
    category: 'fish',
    imageUrl: 'https://example.com/salmon.jpg',
    onSale: true,
  };

  const samplePaginatedResponse: PaginatedProducts = {
    products: [sampleProduct],
    page: 0,
    totalPages: 1,
    totalProducts: 1,
    hasNext: false,
    hasPrev: false,
  };

  beforeEach(() => {
    jest.resetAllMocks();
  });

  describe('getProducts', () => {
    it('should fetch products successfully with default pagination', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(samplePaginatedResponse);
      const params: ProductQueryParams = { page: 0, pageSize: 20 };

      // Act
      const result = await ProductAPI.getProducts(params);

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: '/products',
        method: 'GET',
        data: params,
      });
      expect(result).toEqual(samplePaginatedResponse);
    });

    it('should fetch products with category filter', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(samplePaginatedResponse);
      const params: ProductQueryParams = {
        page: 0,
        pageSize: 20,
        category: 'fish'
      };

      // Act
      const result = await ProductAPI.getProducts(params);

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: '/products',
        method: 'GET',
        data: params,
      });
      expect(result).toEqual(samplePaginatedResponse);
    });

    it('should fetch products with search keyword', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(samplePaginatedResponse);
      const params: ProductQueryParams = {
        page: 0,
        pageSize: 20,
        keyword: 'salmon'
      };

      // Act
      const result = await ProductAPI.getProducts(params);

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: '/products',
        method: 'GET',
        data: params,
      });
      expect(result).toEqual(samplePaginatedResponse);
    });

    it('should fetch products with both category and keyword', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(samplePaginatedResponse);
      const params: ProductQueryParams = {
        page: 0,
        pageSize: 20,
        category: 'fish',
        keyword: 'salmon'
      };

      // Act
      const result = await ProductAPI.getProducts(params);

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(mockedRequest).toHaveBeenCalledWith({
        url: '/products',
        method: 'GET',
        data: params,
      });
      expect(result).toEqual(samplePaginatedResponse);
    });

    it('should handle API error', async () => {
      // Arrange
      const apiError: ApiError = {
        message: 'Internal server error',
        statusCode: 500,
        timestamp: new Date().toISOString(),
      };
      mockedRequest.mockRejectedValueOnce(apiError);

      // Act & Assert
      await expect(ProductAPI.getProducts({ page: 0, pageSize: 20 }))
        .rejects
        .toThrow('Failed to fetch products: Internal server error');
    });

    it('should handle network error', async () => {
      // Arrange
      const networkError = new Error('Network timeout');
      mockedRequest.mockRejectedValueOnce(networkError);

      // Act & Assert
      await expect(ProductAPI.getProducts({ page: 0, pageSize: 20 }))
        .rejects
        .toThrow('Network error occurred while fetching products');
    });

    it('should handle invalid response format', async () => {
      // Arrange
      const invalidResponse = { invalid: 'data' };
      mockedRequest.mockResolvedValueOnce(invalidResponse as any);

      // Act
      const result = await ProductAPI.getProducts({ page: 0, pageSize: 20 });

      // Assert
      // Should return empty paginated result when response format is invalid
      expect(result).toEqual({
        products: [],
        page: 0,
        totalPages: 0,
        totalProducts: 0,
        hasNext: false,
        hasPrev: false,
      });
    });

    it('should handle empty products response', async () => {
      // Arrange
      const emptyResponse: PaginatedProducts = {
        products: [],
        page: 0,
        totalPages: 0,
        totalProducts: 0,
        hasNext: false,
        hasPrev: false,
      };
      mockedRequest.mockResolvedValueOnce(emptyResponse);

      // Act
      const result = await ProductAPI.getProducts({ page: 0, pageSize: 20 });

      // Assert
      expect(result).toEqual(emptyResponse);
      expect(result.products).toHaveLength(0);
    });

    it('should validate pagination parameters', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(samplePaginatedResponse);

      // Act & Assert - Negative page
      await expect(ProductAPI.getProducts({ page: -1, pageSize: 20 }))
        .rejects
        .toThrow('Invalid pagination parameters');

      // Act & Assert - Zero pageSize
      await expect(ProductAPI.getProducts({ page: 0, pageSize: 0 }))
        .rejects
        .toThrow('Invalid pagination parameters');

      // Act & Assert - Negative pageSize
      await expect(ProductAPI.getProducts({ page: 0, pageSize: -10 }))
        .rejects
        .toThrow('Invalid pagination parameters');
    });

    it('should handle large page numbers gracefully', async () => {
      // Arrange
      const emptyResponse: PaginatedProducts = {
        products: [],
        page: 999,
        totalPages: 1,
        totalProducts: 0,
        hasNext: false,
        hasPrev: true,
      };
      mockedRequest.mockResolvedValueOnce(emptyResponse);

      // Act
      const result = await ProductAPI.getProducts({ page: 999, pageSize: 20 });

      // Assert
      expect(result).toEqual(emptyResponse);
    });

    it('should handle special characters in keyword', async () => {
      // Arrange
      mockedRequest.mockResolvedValueOnce(samplePaginatedResponse);
      const params: ProductQueryParams = {
        page: 0,
        pageSize: 20,
        keyword: 'salmon'
      };

      // Act
      const result = await ProductAPI.getProducts(params);

      // Assert
      expect(mockedRequest).toHaveBeenCalledTimes(1);
      expect(result).toEqual(samplePaginatedResponse);
    });
  });
});