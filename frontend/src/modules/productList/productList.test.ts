/**
 * Product List Module Tests
 *
 * Tests for product list component including:
 * - Product card rendering
 * - Loading indicator
 * - Empty state handling
 * - Filter and search functionality
 * - Pagination behavior
 */

import { ProductListModule } from './productList';
import { Product, PaginatedProducts } from '../../types';
import { ProductAPI } from '../../api/product';

// Mock the ProductAPI
jest.mock('../../api/product');

const mockedProductAPI = ProductAPI as jest.Mocked<typeof ProductAPI>;

describe('ProductListModule', () => {
  // Sample test data
  const sampleProducts: Product[] = [
    {
      id: '1',
      name: 'Fresh Salmon',
      description: 'Premium fresh salmon',
      price: 29.99,
      stock: 100,
      category: 'fish',
      imageUrl: 'https://example.com/salmon.jpg',
      onSale: true,
    },
    {
      id: '2',
      name: 'Atlantic Cod',
      description: 'Fresh Atlantic cod',
      price: 24.99,
      stock: 50,
      category: 'fish',
      imageUrl: 'https://example.com/cod.jpg',
      onSale: false,
    },
    {
      id: '3',
      name: 'King Prawns',
      description: 'Large king prawns',
      price: 39.99,
      stock: 75,
      category: 'shellfish',
      imageUrl: 'https://example.com/prawns.jpg',
      onSale: true,
    },
  ];

  const samplePaginatedResponse: PaginatedProducts = {
    products: sampleProducts,
    page: 0,
    totalPages: 2,
    totalProducts: 15,
    hasNext: true,
    hasPrev: false,
  };

  beforeEach(() => {
    mockedProductAPI.getProducts.mockReset();
    mockedProductAPI.getProducts.mockResolvedValue(samplePaginatedResponse);
  });

  describe('Initialization', () => {
    it('should initialize with default state', () => {
      const module = new ProductListModule();

      expect(module.state).toEqual({
        products: [],
        isLoading: false,
        isError: false,
        error: null,
        pagination: {
          currentPage: 0,
          totalPages: 0,
          totalProducts: 0,
        },
        filters: {
          category: undefined,
          keyword: undefined,
        },
        hasNext: false,
        hasPrev: false,
      });
    });

    it('should initialize with custom page size', () => {
      const module = new ProductListModule({ pageSize: 10 });

      expect(module.pageSize).toBe(10);
    });

    it('should initialize with default page size of 20', () => {
      const module = new ProductListModule();

      expect(module.pageSize).toBe(20);
    });
  });

  describe('Product Loading', () => {
    it('should load products successfully on first load', async () => {
      // Arrange
      mockedProductAPI.getProducts.mockResolvedValueOnce(samplePaginatedResponse);

      const module = new ProductListModule();

      // Act
      await module.loadProducts();

      // Assert
      expect(mockedProductAPI.getProducts).toHaveBeenCalledTimes(1);
      expect(mockedProductAPI.getProducts).toHaveBeenCalledWith({
        page: 0,
        pageSize: 20,
        category: undefined,
        keyword: undefined,
      });

      expect(module.state.products).toEqual(sampleProducts);
      expect(module.state.pagination.currentPage).toBe(0);
      expect(module.state.pagination.totalPages).toBe(2);
      expect(module.state.pagination.totalProducts).toBe(15);
      expect(module.state.isLoading).toBe(false);
      expect(module.state.isError).toBe(false);
      expect(module.state.error).toBeNull();
    });

    it('should set loading state correctly during load', async () => {
      // Arrange
      const promise = new Promise<PaginatedProducts>((resolve) => {
        setTimeout(() => resolve(samplePaginatedResponse), 100);
      });
      mockedProductAPI.getProducts.mockReturnValueOnce(promise);

      const module = new ProductListModule();

      // Act
      const loadPromise = module.loadProducts();

      // Assert - loading should be true
      expect(module.state.isLoading).toBe(true);

      await loadPromise;

      // Assert - loading should be false after completion
      expect(module.state.isLoading).toBe(false);
    });

    it('should handle API error gracefully', async () => {
      // Arrange
      const apiError = new Error('Failed to fetch products: Network error');
      mockedProductAPI.getProducts.mockRejectedValueOnce(apiError);

      const module = new ProductListModule();

      // Act
      await module.loadProducts();

      // Assert
      expect(module.state.isLoading).toBe(false);
      expect(module.isError).toBe(true);
      expect(module.state.error).not.toBeNull();
      expect(module.state.products).toEqual([]);
    });

    it('should load products with category filter', async () => {
      // Arrange
      mockedProductAPI.getProducts.mockResolvedValueOnce({
        ...samplePaginatedResponse,
        products: [sampleProducts[0]],
      });

      const module = new ProductListModule();

      // Act
      await module.loadProducts({ category: 'fish' });

      // Assert
      expect(mockedProductAPI.getProducts).toHaveBeenCalledWith({
        page: 0,
        pageSize: 20,
        category: 'fish',
        keyword: undefined,
      });

      expect(module.state.filters.category).toBe('fish');
    });

    it('should load products with search keyword', async () => {
      // Arrange
      mockedProductAPI.getProducts.mockResolvedValueOnce({
        ...samplePaginatedResponse,
        products: [sampleProducts[0]],
      });

      const module = new ProductListModule();

      // Act
      await module.loadProducts({ keyword: 'salmon' });

      // Assert
      expect(mockedProductAPI.getProducts).toHaveBeenCalledWith({
        page: 0,
        pageSize: 20,
        category: undefined,
        keyword: 'salmon',
      });

      expect(module.state.filters.keyword).toBe('salmon');
    });

    it('should load next page correctly', async () => {
      // Arrange
      const page1Response = {
        ...samplePaginatedResponse,
        products: sampleProducts.slice(0, 2),
        hasNext: true,
        hasPrev: false,
      };

      const page2Response = {
        ...samplePaginatedResponse,
        products: sampleProducts.slice(2),
        page: 1,
        hasNext: false,
        hasPrev: true,
      };

      mockedProductAPI.getProducts
        .mockResolvedValueOnce(page1Response)
        .mockResolvedValueOnce(page2Response);

      const module = new ProductListModule();

      // Act - load first page
      await module.loadProducts();

      // Assert first page
      expect(module.state.pagination.currentPage).toBe(0);
      expect(module.state.hasNext).toBe(true);

      // Act - load next page
      await module.loadNextPage();

      // Assert second page
      expect(module.state.pagination.currentPage).toBe(1);
      expect(module.state.hasNext).toBe(false);
      expect(module.state.hasPrev).toBe(true);
    });

    it('should load previous page correctly', async () => {
      // Arrange
      const page1Response = {
        ...samplePaginatedResponse,
        products: sampleProducts.slice(0, 2),
        hasNext: true,
        hasPrev: false,
      };

      const page2Response = {
        ...samplePaginatedResponse,
        products: sampleProducts.slice(2),
        page: 1,
        hasNext: false,
        hasPrev: true,
      };

      mockedProductAPI.getProducts
        .mockResolvedValueOnce(page1Response)
        .mockResolvedValueOnce(page2Response)
        .mockResolvedValueOnce(page1Response);

      const module = new ProductListModule();

      // Act - load first and second page
      await module.loadProducts();
      await module.loadNextPage();

      // Act - load previous page
      await module.loadPrevPage();

      // Assert back to first page
      expect(module.state.pagination.currentPage).toBe(0);
      expect(module.state.hasNext).toBe(true);
      expect(module.state.hasPrev).toBe(false);
    });
  });

  describe('Product Card Rendering', () => {
    it('should format product price correctly', () => {
      const module = new ProductListModule();

      const product: Product = {
        id: '1',
        name: 'Test Product',
        description: 'Test',
        price: 29.99,
        stock: 100,
        category: 'fish',
        imageUrl: 'https://example.com/test.jpg',
        onSale: false,
      };

      const formattedPrice = module.formatProductPrice(product);

      expect(formattedPrice).toBe('￥29.99');
    });

    it('should format product price with zero value', () => {
      const module = new ProductListModule();

      const product: Product = {
        id: '1',
        name: 'Free Product',
        description: 'Test',
        price: 0,
        stock: 100,
        category: 'fish',
        imageUrl: 'https://example.com/test.jpg',
        onSale: false,
      };

      const formattedPrice = module.formatProductPrice(product);

      expect(formattedPrice).toBe('￥0.00');
    });

    it('should format product price with large value', () => {
      const module = new ProductListModule();

      const product: Product = {
        id: '1',
        name: 'Expensive Product',
        description: 'Test',
        price: 9999.99,
        stock: 100,
        category: 'fish',
        imageUrl: 'https://example.com/test.jpg',
        onSale: false,
      };

      const formattedPrice = module.formatProductPrice(product);

      expect(formattedPrice).toBe('￥9999.99');
    });

    it('should check if product is in stock', () => {
      const module = new ProductListModule();

      const inStockProduct: Product = {
        id: '1',
        name: 'In Stock',
        description: 'Test',
        price: 10,
        stock: 10,
        category: 'fish',
        imageUrl: 'https://example.com/test.jpg',
        onSale: false,
      };

      const outOfStockProduct: Product = {
        id: '2',
        name: 'Out of Stock',
        description: 'Test',
        price: 10,
        stock: 0,
        category: 'fish',
        imageUrl: 'https://example.com/test.jpg',
        onSale: false,
      };

      expect(module.isInStock(inStockProduct)).toBe(true);
      expect(module.isInStock(outOfStockProduct)).toBe(false);
    });

    it('should get product badge text for on-sale items', () => {
      const module = new ProductListModule();

      const onSaleProduct: Product = {
        id: '1',
        name: 'On Sale',
        description: 'Test',
        price: 10,
        stock: 100,
        category: 'fish',
        imageUrl: 'https://example.com/test.jpg',
        onSale: true,
      };

      const regularProduct: Product = {
        id: '2',
        name: 'Regular',
        description: 'Test',
        price: 10,
        stock: 100,
        category: 'fish',
        imageUrl: 'https://example.com/test.jpg',
        onSale: false,
      };

      expect(module.getProductBadgeText(onSaleProduct)).toBe('促销');
      expect(module.getProductBadgeText(regularProduct)).toBeNull();
    });
  });

  describe('Empty State Handling', () => {
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

      mockedProductAPI.getProducts.mockResolvedValueOnce(emptyResponse);

      const module = new ProductListModule();

      // Act
      await module.loadProducts();

      // Assert
      expect(module.state.products).toEqual([]);
      expect(module.state.pagination.totalProducts).toBe(0);
      expect(module.isEmpty).toBe(true);
    });

    it('should check if product list is empty', () => {
      const module = new ProductListModule();

      // Initially empty
      expect(module.isEmpty).toBe(true);

      // After loading products
      module.state = {
        ...module.state,
        products: sampleProducts,
      };

      expect(module.isEmpty).toBe(false);
    });

    it('should get empty state message', () => {
      const module = new ProductListModule();

      // Default empty message
      expect(module.getEmptyStateMessage()).toBe('暂无商品');

      // With search keyword
      module.state = {
        ...module.state,
        filters: { keyword: 'nonexistent' },
      };

      expect(module.getEmptyStateMessage()).toBe('未找到相关商品');

      // With category filter
      module.state = {
        ...module.state,
        filters: { category: 'nonexistent' },
      };

      expect(module.getEmptyStateMessage()).toBe('该分类下暂无商品');
    });
  });

  describe('Loading Indicator', () => {
    it('should show loading indicator during data fetch', async () => {
      // Arrange
      const promise = new Promise<PaginatedProducts>((resolve) => {
        setTimeout(() => resolve(samplePaginatedResponse), 100);
      });
      mockedProductAPI.getProducts.mockReturnValueOnce(promise);

      const module = new ProductListModule();

      // Act
      const loadPromise = module.loadProducts();

      // Assert loading is true
      expect(module.isLoading).toBe(true);

      await loadPromise;

      // Assert loading is false after completion
      expect(module.isLoading).toBe(false);
    });

    it('should not show loading indicator when not loading', () => {
      const module = new ProductListModule();

      expect(module.isLoading).toBe(false);
    });

    it('should check if loading more data', () => {
      const module = new ProductListModule();

      // Initially not loading more
      expect(module.isLoadingMore).toBe(false);

      // Simulate loading more
      module.state = {
        ...module.state,
        isLoading: true,
        pagination: {
          ...module.state.pagination,
          currentPage: 1,
        },
      };

      expect(module.isLoadingMore).toBe(true);
    });
  });

  describe('Error Handling', () => {
    it('should handle network timeout error', async () => {
      // Arrange
      const networkError = new Error('Network timeout');
      networkError.name = 'NetworkError';
      mockedProductAPI.getProducts.mockRejectedValueOnce(networkError);

      const module = new ProductListModule();

      // Act
      await module.loadProducts();

      // Assert
      expect(module.isError).toBe(true);
      expect(module.state.error).not.toBeNull();
      expect(module.getErrorMessage()).toContain('网络');
    });

    it('should handle API server error', async () => {
      // Arrange
      const apiError = new Error('Failed to fetch products: Internal server error');
      mockedProductAPI.getProducts.mockRejectedValueOnce(apiError);

      const module = new ProductListModule();

      // Act
      await module.loadProducts();

      // Assert
      expect(module.isError).toBe(true);
      expect(module.state.error).not.toBeNull();
      expect(module.getErrorMessage()).toContain('服务器错误');
    });

    it('should retry loading products after error', async () => {
      // Arrange
      const apiError = new Error('Network error');
      mockedProductAPI.getProducts
        .mockRejectedValueOnce(apiError)
        .mockResolvedValueOnce(samplePaginatedResponse);

      const module = new ProductListModule();

      // Act - first attempt fails
      await module.loadProducts();

      expect(module.isError).toBe(true);

      // Act - retry succeeds
      await module.retryLoadProducts();

      expect(module.isError).toBe(false);
      expect(module.state.products).toEqual(sampleProducts);
    });

    it('should clear error state', async () => {
      // Arrange
      const apiError = new Error('Network error');
      mockedProductAPI.getProducts
        .mockRejectedValueOnce(apiError)
        .mockResolvedValueOnce(samplePaginatedResponse);

      const module = new ProductListModule();

      // Act - cause error
      await module.loadProducts();

      expect(module.isError).toBe(true);

      // Act - clear error
      module.clearError();

      expect(module.isError).toBe(false);
      expect(module.state.error).toBeNull();
    });
  });

  describe('Filter and Search', () => {
    it('should apply category filter', async () => {
      // Arrange
      const filteredResponse = {
        products: [sampleProducts[0]],
        page: 0,
        totalPages: 1,
        totalProducts: 1,
        hasNext: false,
        hasPrev: false,
      };
      mockedProductAPI.getProducts.mockResolvedValueOnce(filteredResponse);

      const module = new ProductListModule();

      // Act
      await module.applyCategoryFilter('fish');

      // Assert
      expect(module.state.filters.category).toBe('fish');
      expect(module.state.products).toHaveLength(1);
      expect(module.state.pagination.currentPage).toBe(0);
    });

    it('should apply search keyword', async () => {
      // Arrange
      const searchResponse = {
        products: [sampleProducts[0]],
        page: 0,
        totalPages: 1,
        totalProducts: 1,
        hasNext: false,
        hasPrev: false,
      };
      mockedProductAPI.getProducts.mockResolvedValueOnce(searchResponse);

      const module = new ProductListModule();

      // Act
      await module.applySearchKeyword('salmon');

      // Assert
      expect(module.state.filters.keyword).toBe('salmon');
      expect(module.state.products).toHaveLength(1);
      expect(module.state.pagination.totalProducts).toBe(1);
    });

    it('should clear all filters', async () => {
      // Arrange
      mockedProductAPI.getProducts.mockResolvedValueOnce(samplePaginatedResponse);

      const module = new ProductListModule();

      // Act - apply filters first
      await module.applyCategoryFilter('fish');
      await module.applySearchKeyword('salmon');

      expect(module.state.filters.category).toBe('fish');
      expect(module.state.filters.keyword).toBe('salmon');

      // Act - clear filters
      await module.clearFilters();

      // Assert
      expect(module.state.filters.category).toBeUndefined();
      expect(module.state.filters.keyword).toBeUndefined();
      expect(module.state.pagination.currentPage).toBe(0);
    });

    it('should handle combined category and keyword filter', async () => {
      // Arrange
      const combinedResponse = {
        ...samplePaginatedResponse,
        products: [sampleProducts[0]],
        totalProducts: 1,
      };
      mockedProductAPI.getProducts.mockResolvedValueOnce(combinedResponse);

      const module = new ProductListModule();

      // Act
      await module.loadProducts({ category: 'fish', keyword: 'salmon' });

      // Assert
      expect(module.state.filters.category).toBe('fish');
      expect(module.state.filters.keyword).toBe('salmon');
      expect(mockedProductAPI.getProducts).toHaveBeenCalledWith({
        page: 0,
        pageSize: 20,
        category: 'fish',
        keyword: 'salmon',
      });
    });
  });

  describe('Pagination', () => {
    it('should check if there is next page', () => {
      const module = new ProductListModule();

      // No next page initially
      expect(module.hasNext).toBe(false);

      // With next page data
      module.state = {
        ...module.state,
        pagination: {
          currentPage: 0,
          totalPages: 2,
          totalProducts: 15,
        },
        hasNext: true,
        hasPrev: false,
      };

      expect(module.hasNext).toBe(true);
    });

    it('should check if there is previous page', () => {
      const module = new ProductListModule();

      // No previous page initially
      expect(module.hasPrev).toBe(false);

      // With previous page data
      module.state = {
        ...module.state,
        pagination: {
          currentPage: 1,
          totalPages: 2,
          totalProducts: 15,
        },
      };

      expect(module.hasPrev).toBe(true);
    });

    it('should reset to first page when applying filters', async () => {
      // Arrange
      mockedProductAPI.getProducts
        .mockResolvedValueOnce({
          ...samplePaginatedResponse,
          products: sampleProducts.slice(0, 2),
          hasNext: true,
          hasPrev: false,
        })
        .mockResolvedValueOnce({
          ...samplePaginatedResponse,
          products: sampleProducts.slice(2),
          page: 1,
          hasNext: false,
          hasPrev: true,
        })
        .mockResolvedValueOnce({
          ...samplePaginatedResponse,
          products: [sampleProducts[0]],
          hasNext: false,
          hasPrev: false,
        });

      const module = new ProductListModule();

      // Act - load page 1
      await module.loadProducts();
      await module.loadNextPage();

      expect(module.state.pagination.currentPage).toBe(1);

      // Act - apply filter should reset to page 0
      await module.applyCategoryFilter('fish');

      expect(module.state.pagination.currentPage).toBe(0);
    });
  });

  describe('Data Refresh', () => {
    it('should refresh product list', async () => {
      // Arrange
      mockedProductAPI.getProducts
        .mockResolvedValueOnce(samplePaginatedResponse)
        .mockResolvedValueOnce({
          ...samplePaginatedResponse,
          products: [...sampleProducts, {
            id: '4',
            name: 'New Product',
            description: 'New',
            price: 19.99,
            stock: 50,
            category: 'fish',
            imageUrl: 'https://example.com/new.jpg',
            onSale: false,
          }],
        });

      const module = new ProductListModule();

      // Act - initial load
      await module.loadProducts();

      expect(module.state.products).toHaveLength(3);

      // Act - refresh
      await module.refreshProducts();

      expect(module.state.products).toHaveLength(4);
      expect(module.state.products[3].name).toBe('New Product');
    });

    it('should clear products on refresh', async () => {
      // Arrange
      mockedProductAPI.getProducts
        .mockResolvedValueOnce(samplePaginatedResponse)
        .mockResolvedValueOnce(samplePaginatedResponse);

      const module = new ProductListModule();

      // Act - initial load
      await module.loadProducts();

      expect(module.state.products).toHaveLength(3);

      // Act - refresh
      await module.refreshProducts();

      // Should have products after refresh
      expect(module.state.products).toHaveLength(3);
    });
  });
});