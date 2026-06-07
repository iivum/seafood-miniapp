import { useMutation } from '@tanstack/react-query';
import { useAuthStore } from './store';
import type { AdminLoginRequest, ApiError } from '@/types/api';
import { AxiosError } from 'axios';

interface LoginError extends Error {
  code?: string;
  fieldErrors?: Record<string, string>;
}

function toLoginError(err: unknown): LoginError {
  if (err instanceof AxiosError) {
    const apiError = err.response?.data as ApiError | undefined;
    const wrapped: LoginError = new Error(apiError?.message ?? '登录失败,请稍后重试');
    wrapped.code = apiError?.code;
    wrapped.fieldErrors = apiError?.fieldErrors;
    return wrapped;
  }
  if (err instanceof Error) {
    return err;
  }
  return new Error('登录失败,请稍后重试');
}

/**
 * React Query mutation wrapper around the auth store login.
 * Errors are normalized so the Login view can show `error.message` and
 * (when present) field-level errors from the backend's `VALIDATION` code.
 */
export function useLogin() {
  const login = useAuthStore((s) => s.login);
  return useMutation<void, Error, AdminLoginRequest>({
    mutationFn: async (req) => {
      try {
        await login(req);
      } catch (err) {
        throw toLoginError(err);
      }
    },
  });
}
