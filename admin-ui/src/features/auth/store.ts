import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { api, readCookie, writeCookie } from '@/lib/api';
import type { AdminLoginRequest, TokenResponse, UserResponse } from '@/types/api';

interface AuthState {
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
 * Tokens are kept in httpOnly cookies on the server side; the client keeps a
 * non-sensitive shadow `admin_refresh_token` cookie to drive the refresh
 * interceptor. The session "logged in" signal is just role+username.
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
        const expiresIn = Math.max(
          1,
          Math.floor((new Date(tokens.refreshTokenExpiresAt).getTime() - Date.now()) / 1000),
        );
        writeCookie('admin_refresh_token', tokens.refreshToken, expiresIn);
        set({
          accessToken: tokens.accessToken,
          username: username ?? get().username,
          role: tokens.role,
        });
      },

      clear: () => {
        writeCookie('admin_refresh_token', '', 0);
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
          const shadow = readCookie('admin_refresh_token');
          if (!shadow) {
            get().clear();
            return false;
          }
          const res = await api.post<TokenResponse>('/admin/auth/refresh', { refreshToken: shadow });
          get().setSession(res.data);
          return true;
        } catch {
          get().clear();
          return false;
        }
      },

      logout: async () => {
        try {
          await api.post('/admin/auth/logout', {}).catch(() => undefined);
        } finally {
          get().clear();
        }
      },

      loadSession: async () => {
        const { isAuthenticated } = get();
        if (!isAuthenticated()) {
          // Try to refresh on first load
          const ok = await get().refresh();
          if (!ok) {
            return null;
          }
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
      partialize: (state) => ({ username: state.username, role: state.role }),
      onRehydrateStorage: () => (state) => {
        state?.setHydrated();
      },
    },
  ),
);
