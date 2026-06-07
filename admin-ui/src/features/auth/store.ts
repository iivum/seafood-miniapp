import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { api } from '@/lib/api';
import type { AdminLoginRequest, TokenResponse, UserResponse } from '@/types/api';

interface AuthState {
  /** In-memory only. HttpOnly cookie carries the real authority. */
  accessToken: string | null;
  username: string | null;
  role: string | null;
  isHydrated: boolean;
  hydrated: boolean;
  setHydrated: () => void;
  setSession: (tokens: TokenResponse, username?: string) => void;
  clear: () => void;
  isAuthenticated: () => boolean;
  hasRole: (role: string) => boolean;
  login: (req: AdminLoginRequest) => Promise<void>;
  refresh: () => Promise<boolean>;
  logout: () => Promise<void>;
  loadSession: () => Promise<{ username: string; role: string } | null>;
}

/**
 * Auth state lives in Zustand (per design §7.2 stores/).
 *
 * Security model:
 *  - Refresh token: stored ONLY as an HttpOnly+Secure cookie set by
 *    the backend. JS never sees it (XSS cannot exfiltrate).
 *  - Access token: short-lived (15 min), kept in memory only; the
 *    request interceptor attaches it as `Authorization: Bearer ...`.
 *  - Username + role: persisted to localStorage as a non-sensitive
 *    "session hint" so the router can render before the server
 *    confirms. `loadSession()` always validates against the server
 *    (via /admin/auth/refresh) before granting real access.
 *  - On logout: backend revokes the cookie, then we clear local
 *    state. Subsequent page loads see no session hint and
 *    RequireAuth redirects to /admin/login.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      username: null,
      role: null,
      isHydrated: false,
      hydrated: false,
      setHydrated: () => set({ isHydrated: true, hydrated: true }),

      setSession: (tokens, username) => {
        set({
          accessToken: tokens.accessToken,
          username: username ?? get().username,
          role: tokens.role,
        });
      },

      clear: () => {
        set({ accessToken: null, username: null, role: null });
      },

      isAuthenticated: () => {
        const { role, username } = get();
        return Boolean(role && username);
      },

      hasRole: (role) => get().role === role,

      login: async (req) => {
        const res = await api.post<TokenResponse>('/admin/auth/login', req);
        get().setSession(res.data, req.username);
      },

      refresh: async () => {
        try {
          // Backend reads HttpOnly refresh cookie. No body.
          const res = await api.post<TokenResponse>('/admin/auth/refresh', {});
          get().setSession(res.data);
          return true;
        } catch {
          get().clear();
          return false;
        }
      },

      logout: async () => {
        // Server revokes the HttpOnly cookie. We then clear local
        // state. If the network call fails, we still clear locally —
        // the next protected request will 401 and the router will
        // redirect to /admin/login.
        try {
          await api.post('/admin/auth/logout', {});
        } catch {
          /* best-effort */
        } finally {
          get().clear();
        }
      },

      loadSession: async () => {
        // Server-side confirmation. The persisted role/username is a
        // hint only; we never grant access until /admin/auth/refresh
        // succeeds (the HttpOnly cookie is the source of truth).
        const ok = await get().refresh();
        if (!ok) {
          // Refresh failed → no valid session. Clear the persisted
          // hint so the next load doesn't briefly render as authed.
          get().clear();
          return null;
        }
        const s = get();
        if (!s.username || !s.role) {
          return null;
        }
        // Optionally fetch user details to verify session
        try {
          await api.get<UserResponse>(`/users/${s.username}`).catch(() => undefined);
        } catch {
          /* ignore */
        }
        return { username: s.username, role: s.role };
      },
    }),
    {
      name: 'seafood-admin-auth',
      storage: createJSONStorage(() => localStorage),
      // Persist only the non-sensitive session hint. The access token
      // and the refresh credential are NOT persisted — refresh lives
      // in an HttpOnly cookie; access lives in memory and is
      // re-issued by /admin/auth/refresh on the next session load.
      partialize: (state) => ({ username: state.username, role: state.role }),
      onRehydrateStorage: () => (state) => {
        state?.setHydrated();
      },
    },
  ),
);
