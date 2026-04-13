/**
 * Type declarations for cache utility module
 */

declare class SimpleCache {
  constructor();
  get(key: string): unknown | null;
  set(key: string, value: unknown, ttl?: number): void;
  remove(key: string): void;
  clear(): void;
  static generateKey(base: string, params: Record<string, unknown>): string;
}

export { SimpleCache };
export const cache: SimpleCache;
export const DEFAULT_TTL: number;
