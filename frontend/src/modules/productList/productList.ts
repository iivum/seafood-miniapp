/**
 * Product List Module
 *
 * Manages product list state, loading, filtering, and pagination
 * for the seafood mini-app frontend.
 */

import { Product, ProductQueryParams } from '../../types';
import { ProductAPI } from '../../api/product';

/**
 * Product list configuration options
 */
export interface ProductListOptions {
  /** Page size for pagination (default: 20) */
  pageSize?: number;
}

/**
 * Product list state interface
 */
export interface ProductListState {
  /** Currently loaded products */
  products: Product[];
  /** Loading state */
  isLoading: boolean;
  /** Error state */
  isError: boolean;
  /** Error object if any */
  error: Error | null;
  /** Pagination info */
  pagination: {
    currentPage: number;
    totalPages: number;
    totalProducts: number;
  };
  /** Applied filters */
  filters: {
    category?: string;
    keyword?: string;
  };
  /** Whether there is a next page */
  hasNext: boolean;
  /** Whether there is a previous page */
  hasPrev: boolean;
}

/**
 * Product List Module class for managing product list state and operations
 */
export class ProductListModule {
  /** Default page size */
  private static readonly DEFAULT_PAGE_SIZE = 20;

  /** Current module state */
  public state: ProductListState;

  /** Page size for pagination */
  public pageSize: number;

  constructor(options: ProductListOptions = {}) {
    this.pageSize = options.pageSize || ProductListModule.DEFAULT_PAGE_SIZE;

    // Initialize state with default values
    this.state = {
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
    };
  }

  /**
   * Load products from the API
   *
   * @param params - Query parameters for filtering and pagination
   */
  async loadProducts(params: Partial<ProductQueryParams> = {}): Promise<void> {
    const loadParams = {
      page: params.page !== undefined ? params.page : 0, // Start from first page if not specified
      pageSize: this.pageSize,
      category: params.category !== undefined ? params.category : this.state.filters.category,
      keyword: params.keyword !== undefined ? params.keyword : this.state.filters.keyword,
    };

    // If new filters are provided, update the state filters immediately
    if (params.category !== undefined) {
      this.state.filters.category = params.category;
    }
    if (params.keyword !== undefined) {
      this.state.filters.keyword = params.keyword;
    }

    this.state.isLoading = true;
    this.state.isError = false;
    this.state.error = null;

    try {
      const response = await ProductAPI.getProducts(loadParams);

      this.state.products = response.products;
      this.state.pagination = {
        currentPage: response.page,
        totalPages: response.totalPages,
        totalProducts: response.totalProducts,
      };
      this.state.hasNext = response.hasNext;
      this.state.hasPrev = response.hasPrev;
    } catch (error) {
      this.state.isError = true;
      this.state.error = error instanceof Error ? error : new Error('Unknown error occurred');
      this.state.products = [];
      this.state.hasNext = false;
      this.state.hasPrev = false;
    } finally {
      this.state.isLoading = false;
    }
  }

  /**
   * Load next page of products
   */
  async loadNextPage(): Promise<void> {
    if (!this.hasNext || this.isLoading) {
      return;
    }

    const nextPage = this.state.pagination.currentPage + 1;

    this.state.isLoading = true;

    try {
      const response = await ProductAPI.getProducts({
        page: nextPage,
        pageSize: this.pageSize,
        category: this.state.filters.category,
        keyword: this.state.filters.keyword,
      });

      this.state.products = [...this.state.products, ...response.products];
      this.state.pagination = {
        currentPage: response.page,
        totalPages: response.totalPages,
        totalProducts: response.totalProducts,
      };
      this.state.hasNext = response.hasNext;
      this.state.hasPrev = response.hasPrev;
    } catch (error) {
      this.state.isError = true;
      this.state.error = error instanceof Error ? error : new Error('Unknown error occurred');
    } finally {
      this.state.isLoading = false;
    }
  }

  /**
   * Load previous page of products
   */
  async loadPrevPage(): Promise<void> {
    if (!this.hasPrev || this.isLoading) {
      return;
    }

    const prevPage = this.state.pagination.currentPage - 1;

    this.state.isLoading = true;

    try {
      const response = await ProductAPI.getProducts({
        page: prevPage,
        pageSize: this.pageSize,
        category: this.state.filters.category,
        keyword: this.state.filters.keyword,
      });

      this.state.products = [...this.state.products, ...response.products];
      this.state.pagination = {
        currentPage: response.page,
        totalPages: response.totalPages,
        totalProducts: response.totalProducts,
      };
      this.state.hasNext = response.hasNext;
      this.state.hasPrev = response.hasPrev;
    } catch (error) {
      this.state.isError = true;
      this.state.error = error instanceof Error ? error : new Error('Unknown error occurred');
    } finally {
      this.state.isLoading = false;
    }
  }

  /**
   * Apply category filter and reload products
   *
   * @param category - Category to filter by
   */
  async applyCategoryFilter(category: string): Promise<void> {
    this.state.filters.category = category;
    await this.loadProducts({ category, page: 0 });
  }

  /**
   * Apply search keyword and reload products
   *
   * @param keyword - Search keyword
   */
  async applySearchKeyword(keyword: string): Promise<void> {
    this.state.filters.keyword = keyword;
    await this.loadProducts({ keyword, page: 0 });
  }

  /**
   * Clear all filters and reload products
   */
  async clearFilters(): Promise<void> {
    this.state.filters = {
      category: undefined,
      keyword: undefined,
    };
    await this.loadProducts({ page: 0 });
  }

  /**
   * Refresh product list (reload from first page)
   */
  async refreshProducts(): Promise<void> {
    // Clear existing products before refresh
    this.state.products = [];
    await this.loadProducts();
  }

  /**
   * Clear error state
   */
  clearError(): void {
    this.state.isError = false;
    this.state.error = null;
  }

  /**
   * Retry loading products after error
   */
  async retryLoadProducts(): Promise<void> {
    await this.loadProducts();
  }

  /**
   * Format product price for display
   *
   * @param product - Product to format price for
   * @returns Formatted price string
   */
  formatProductPrice(product: Product): string {
    return `￥${product.price.toFixed(2)}`;
  }

  /**
   * Check if product is in stock
   *
   * @param product - Product to check
   * @returns true if product has stock > 0
   */
  isInStock(product: Product): boolean {
    return product.stock > 0;
  }

  /**
   * Get product badge text (e.g., "促销" for on-sale items)
   *
   * @param product - Product to get badge for
   * @returns Badge text or null if no badge
   */
  getProductBadgeText(product: Product): string | null {
    return product.onSale ? '促销' : null;
  }

  /**
   * Get empty state message
   *
   * @returns Appropriate message for empty state
   */
  getEmptyStateMessage(): string {
    if (this.state.filters.keyword) {
      return '未找到相关商品';
    }
    if (this.state.filters.category) {
      return '该分类下暂无商品';
    }
    return '暂无商品';
  }

  /**
   * Get error message
   *
   * @returns Formatted error message or default message
   */
  getErrorMessage(): string {
    if (!this.state.error) {
      return '未知错误';
    }

    const message = this.state.error.message;
    if (message.includes('Network')) {
      return '网络连接失败，请检查网络设置';
    }
    if (message.includes('Internal server')) {
      return '服务器错误，请稍后重试';
    }
    return `加载失败: ${message}`;
  }

  // Computed properties (getters)

  /** Check if product list is empty */
  get isEmpty(): boolean {
    return this.state.products.length === 0;
  }

  /** Check if data is loading */
  get isLoading(): boolean {
    return this.state.isLoading;
  }

  /** Check if there is an error */
  get isError(): boolean {
    return this.state.isError;
  }

  /** Check if there is a next page */
  get hasNext(): boolean {
    return this.state.hasNext;
  }

  /** Check if there is a previous page */
  get hasPrev(): boolean {
    return this.state.pagination.currentPage > 0;
  }

  /** Check if currently loading more data (not first page) */
  get isLoadingMore(): boolean {
    return this.state.isLoading && this.state.pagination.currentPage > 0;
  }
}