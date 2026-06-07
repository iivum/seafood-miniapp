/**
 * Shared API DTOs.
 *
 * Re-exports the canonical DTOs from the shared `types/` so that
 * features and shared code can import from a stable path. New
 * cross-cutting types (e.g. ErrorResponse) live here.
 */

/** Backend ErrorResponse shape — see `ErrorResponse.java`. */
export interface ApiErrorResponse {
  code:
    | 'NOT_FOUND'
    | 'VALIDATION'
    | 'DOMAIN'
    | 'TOKEN_EXPIRED'
    | 'TOKEN_INVALID'
    | 'TOKEN_REUSED';
  message: string;
  fieldErrors?: Record<string, string>;
}

/** Result of the central `request` helper. */
export interface ApiResult<T> {
  data: T;
  statusCode: number;
}
