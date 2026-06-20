import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { RequireAnonymous, RequireAuth } from './components/layout/RequireAuth';
import LoginPage from './features/auth/LoginPage';
import DashboardPage from './features/dashboard/DashboardPage';
import ProductListPage from './features/products/ProductListPage';
import OrderListPage from './features/orders/OrderListPage';
import OrderDetailPage from './features/orders/OrderDetailPage';
import RefundReviewPage from './features/refunds/RefundReviewPage';
import BannerListPage from './features/banners/BannerListPage';
import Sprint0TokenShowcase from './features/spike/Sprint0TokenShowcase';
import { Toaster } from './components/ui/toaster';

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/admin/dashboard" replace /> },
  {
    path: '/admin',
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/admin/dashboard" replace /> },
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'products', element: <ProductListPage /> },
          { path: 'orders', element: <OrderListPage /> },
          { path: 'orders/:id', element: <OrderDetailPage /> },
          { path: 'refunds', element: <RefundReviewPage /> },
          { path: 'banners', element: <BannerListPage /> },
          // 1.24 Sprint 0 末 spike — token 切流验证页(不进生产,Sprint 1 起删)
          { path: '_spike', element: <Sprint0TokenShowcase /> },
        ],
      },
    ],
  },
  {
    path: '/admin/login',
    element: <RequireAnonymous />,
    children: [{ index: true, element: <LoginPage /> }],
  },
  { path: '*', element: <Navigate to="/admin/dashboard" replace /> },
]);

export function AppRouter() {
  return (
    <>
      <RouterProvider router={router} />
      <Toaster />
    </>
  );
}
