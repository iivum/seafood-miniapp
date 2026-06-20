import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LayoutDashboard, Package, ShoppingCart, LogOut, Fish, Undo2, Images } from 'lucide-react';
import { useAuthStore } from '@/features/auth/store';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

const NAV_ITEMS = [
  { to: '/admin/dashboard', label: '仪表盘', icon: LayoutDashboard },
  { to: '/admin/products', label: '商品', icon: Package },
  { to: '/admin/orders', label: '订单', icon: ShoppingCart },
  { to: '/admin/refunds', label: '退款审核', icon: Undo2 },
  { to: '/admin/banners', label: 'Banner', icon: Images },
] as const;

export function AppShell() {
  const username = useAuthStore((s) => s.username);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/admin/login', { replace: true });
  };

  return (
    <div className="flex min-h-screen bg-bg">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-border bg-surface text-fg md:flex">
        <div className="flex h-16 items-center gap-2 border-b border-border px-4">
          <Fish className="h-6 w-6 text-accent" />
          <span className="font-display text-lg font-semibold">海鲜后台</span>
        </div>
        <nav className="flex-1 space-y-1 p-3" aria-label="主导航">
          {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-base transition-colors',
                  isActive
                    ? 'bg-accent-soft text-fg'
                    : 'text-muted hover:bg-soft hover:text-fg',
                )
              }
            >
              <Icon className="h-4 w-4" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-border p-3 text-sm text-muted">
          <div className="mb-2 px-3">{username ?? '未登录'}</div>
          <Button
            variant="ghost"
            className="w-full justify-start text-muted hover:bg-soft hover:text-fg"
            onClick={handleLogout}
          >
            <LogOut className="mr-2 h-4 w-4" /> 退出登录
          </Button>
        </div>
      </aside>
      <main className="flex-1 overflow-x-hidden p-6">
        <Outlet />
      </main>
    </div>
  );
}

export default AppShell;
