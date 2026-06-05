/**
 * Shared hooks.
 *
 * In a WeChat mini-program there is no React-style hook runtime, but
 * page-level lifecycle methods benefit from a small "use"-style
 * pattern. This module exports helper functions the page `.js` files
 * call from `onLoad` / `onShow`. The implementations are pure
 * TypeScript so they can be unit-tested.
 */

import { authStore, type AuthStore } from '../../features/auth/store';

/**
 * Ensure the user is logged in. If not, runs the WeChat login flow.
 * Returns the current auth state after the operation completes.
 *
 * Used from page `onLoad`:
 *   onLoad() { useAuthGuard(this).ensureLoggedIn(); }
 */
export function useAuthGuard(_page: unknown): { ensureLoggedIn: () => Promise<AuthStore['getState'] extends () => infer R ? R : never> } {
  return {
    async ensureLoggedIn() {
      if (authStore.getState().isAuthenticated) return authStore.getState();
      try {
        await authStore.login();
      } catch {
        /* error is on the store; let the page render the login prompt */
      }
      return authStore.getState();
    },
  };
}
