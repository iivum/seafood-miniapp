import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/store';

/**
 * Route guard per spec §Route protection:
 *  - Anonymous → /admin/login?from=<original>
 *  - Already authed visiting /admin/login → /admin/dashboard
 *  - Hydration must complete before deciding (avoids redirect flicker)
 */
export function RequireAuth() {
  const location = useLocation();
  const hydrated = useAuthStore((s) => s.hydrated);
  const isAuthed = useAuthStore((s) => s.isAuthenticated());

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center text-app-muted">正在加载…</div>
    );
  }

  if (!isAuthed) {
    const from = `${location.pathname}${location.search}`;
    return <Navigate to={`/admin/login?from=${encodeURIComponent(from)}`} replace />;
  }

  return <Outlet />;
}

export function RequireAnonymous() {
  const hydrated = useAuthStore((s) => s.hydrated);
  const isAuthed = useAuthStore((s) => s.isAuthenticated());

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center text-app-muted">正在加载…</div>
    );
  }

  if (isAuthed) {
    return <Navigate to="/admin/dashboard" replace />;
  }

  return <Outlet />;
}
