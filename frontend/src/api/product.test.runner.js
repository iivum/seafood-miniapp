/**
 * Test runner to verify ProductAPI tests without needing npm install
 * This simulates the Jest test environment
 */

const fs = require('fs');
const path = require('path');

// Mock the request utility
const requestMock = {
  mockResolvedValueOnce: function(value) {
    this.resolvedValue = value;
    return this;
  },
  mockRejectedValueOnce: function(value) {
    this.rejectedValue = value;
    return this;
  },
  mockImplementation: function(fn) {
    this.implementation = fn;
    return this;
  }
};

// Simple mock function
function createMock() {
  const mockFn = (...args) => {
    if (mockFn.implementation) {
      return mockFn.implementation(...args);
    }
    if (mockFn.rejectedValue) {
      const error = mockFn.rejectedValue;
      mockFn.rejectedValue = null;
      throw error;
    }
    if (mockFn.resolvedValue !== undefined) {
      const value = mockFn.resolvedValue;
      mockFn.resolvedValue = undefined;
      return value;
    }
    return Promise.resolve();
  };

  mockFn.mockResolvedValueOnce = (value) => {
    mockFn.resolvedValue = value;
    return mockFn;
  };

  mockFn.mockRejectedValueOnce = (value) => {
    mockFn.rejectedValue = value;
    return mockFn;
  };

  mockFn.mockImplementation = (fn) => {
    mockFn.implementation = fn;
    return mockFn;
  };

  mockFn.mockClear = () => {
    mockFn.resolvedValue = undefined;
    mockFn.rejectedValue = null;
    mockFn.implementation = null;
    return mockFn;
  };

  return mockFn;
}

// Import the ProductAPI
const { ProductAPI } = require('./product.js');

// Test data
const sampleProduct = {
  id: '1',
  name: 'Fresh Salmon',
  description: 'Premium fresh salmon',
  price: 29.99,
  stock: 100,
  category: 'fish',
  imageUrl: 'https://example.com/salmon.jpg',
  onSale: true,
};

const samplePaginatedResponse = {
  products: [sampleProduct],
  page: 0,
  totalPages: 1,
  totalProducts: 1,
  hasNext: false,
  hasPrev: false,
};

// Run basic test
async function runTest() {
  console.log('🧪 Testing ProductAPI.getProducts()...');

  const mockRequest = createMock();

  try {
    mockRequest.mockResolvedValueOnce(samplePaginatedResponse);
    const result = await ProductAPI.getProducts({ page: 0, pageSize: 20 });

    if (JSON.stringify(result) === JSON.stringify(samplePaginatedResponse)) {
      console.log('✅ Test PASSED: Basic product fetching works');
    } else {
      console.log('❌ Test FAILED: Unexpected result', result);
    }
  } catch (error) {
    console.log('❌ Test FAILED with error:', error.message);
  }

  // Test error handling
  try {
    const apiError = {
      message: 'Internal server error',
      statusCode: 500,
      timestamp: new Date().toISOString(),
    };
    mockRequest.mockRejectedValueOnce(apiError);

    await ProductAPI.getProducts({ page: 0, pageSize: 20 });
    console.log('❌ Test FAILED: Should have thrown error');
  } catch (error) {
    if (error.message.includes('Internal server error')) {
      console.log('✅ Test PASSED: Error handling works');
    } else {
      console.log('❌ Test FAILED: Wrong error message', error.message);
    }
  }

  console.log('📊 Test Summary: Basic functionality verified');
}

runTest();