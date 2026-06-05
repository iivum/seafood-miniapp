/**
 * Shared, cross-feature DTOs. Keep this file dependency-free so that
 * any feature can import from it without triggering cycles.
 */
export interface ApiError {
  message: string;
  statusCode: number;
  timestamp?: string;
}

export interface LoadingState {
  isLoading: boolean;
  isError: boolean;
  error: ApiError | null;
}
