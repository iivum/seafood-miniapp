/**
 * Simple in-memory cache utility for API responses
 */

const DEFAULT_TTL = 5 * 60 * 1000; // 5 minutes

class SimpleCache {
  constructor() {
    this.store = new Map();
  }

  /**
   * Get item from cache
   * @param {string} key - Cache key
   * @returns {any|null} Cached value or null if expired/not found
   */
  get(key) {
    const item = this.store.get(key);
    if (!item) return null;

    const now = Date.now();
    if (now > item.expiry) {
      this.store.delete(key);
      return null;
    }

    return item.value;
  }

  /**
   * Set item in cache
   * @param {string} key - Cache key
   * @param {any} value - Value to cache
   * @param {number} ttl - Time to live in milliseconds
   */
  set(key, value, ttl = DEFAULT_TTL) {
    const expiry = Date.now() + ttl;
    this.store.set(key, { value, expiry });
  }

  /**
   * Remove item from cache
   * @param {string} key - Cache key
   */
  remove(key) {
    this.store.delete(key);
  }

  /**
   * Clear all cache
   */
  clear() {
    this.store.clear();
  }

  /**
   * Generate cache key from params
   * @param {string} base - Base key
   * @param {object} params - Parameters
   * @returns {string} Cache key
   */
  static generateKey(base, params) {
    return `${base}:${JSON.stringify(params || {})}`;
  }
}

// Singleton instance
const cache = new SimpleCache();

module.exports = {
  SimpleCache,
  cache,
  DEFAULT_TTL
};
