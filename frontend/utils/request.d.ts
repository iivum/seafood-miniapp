/**
 * Type declarations for WeChat Mini-Program global wx object
 * These augment the global scope provided by miniprogram-api-typings
 */

/**
 * Request configuration options
 */
export interface RequestOptions {
  /** API endpoint URL */
  url: string;
  /** HTTP method */
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  /** Request data (for POST, PUT, PATCH) */
  data?: unknown;
  /** Custom headers */
  header?: Record<string, string>;
  /** Whether authentication is required */
  needAuth?: boolean;
  /** Request timeout in milliseconds */
  timeout?: number;
}

/**
 * Request interceptor function type
 */
export type RequestInterceptor = (options: RequestOptions) => RequestOptions | Promise<RequestOptions>;

/**
 * Response interceptor function type
 */
export type ResponseInterceptor = (response: unknown) => unknown | Promise<unknown>;

/**
 * Request error interface
 */
export interface RequestError extends Error {
  /** HTTP status code if available */
  statusCode?: number;
  /** Original error data */
  data?: unknown;
}

/**
 * Request function signature
 */
export function request(options: RequestOptions): Promise<any>;
export function get(url: string, params?: Record<string, unknown>, options?: Partial<RequestOptions>): Promise<any>;
export function post(url: string, data?: unknown, options?: Partial<RequestOptions>): Promise<any>;
export function put(url: string, data?: unknown, options?: Partial<RequestOptions>): Promise<any>;
export function del(url: string, options?: Partial<RequestOptions>): Promise<any>;
export function patch(url: string, data?: unknown, options?: Partial<RequestOptions>): Promise<any>;
export function authRequest(options: RequestOptions): Promise<any>;
export function authGet(url: string, params?: Record<string, unknown>, options?: Partial<RequestOptions>): Promise<any>;
export function authPost(url: string, data?: unknown, options?: Partial<RequestOptions>): Promise<any>;
