import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from './store';
import { useLogin } from './queries';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

const loginSchema = z.object({
  username: z.string().min(1, '请输入用户名'),
  password: z.string().min(1, '请输入密码'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export function LoginPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const hydrated = useAuthStore((s) => s.hydrated);
  const { register, handleSubmit, formState, setError } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  });
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const login = useLogin();

  /**
   * Validate the `from` redirect target against an allowlist of safe
   * internal paths. Prevents open-redirect via a crafted ?from= URL
   * (e.g. `//evil.com`, `\\evil.com`, empty string, protocol-relative
   * URLs). Anything that doesn't match the pattern falls back to
   * /admin/dashboard.
   */
  const rawFrom =
    searchParams.get('from') ??
    (location.state as { from?: string } | null)?.from ??
    '/admin/dashboard';
  const FROM_PATTERN = /^\/[a-zA-Z0-9_\-/?.&=#%]*$/;
  const from =
    FROM_PATTERN.test(rawFrom) && !rawFrom.startsWith('//') && rawFrom.length > 1
      ? rawFrom
      : '/admin/dashboard';

  useEffect(() => {
    if (hydrated && isAuthenticated) {
      navigate(from, { replace: true });
    }
  }, [hydrated, isAuthenticated, navigate, from]);

  if (hydrated && isAuthenticated) {
    return <Navigate to="/admin/dashboard" replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login.mutateAsync(values);
      navigate(from, { replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : '登录失败';
      setError('root.serverError', { message });
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center bg-app-bg p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>海鲜商城管理后台</CardTitle>
          <CardDescription>请使用管理员账号登录</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="username">用户名</Label>
              <Input
                id="username"
                type="text"
                autoComplete="username"
                aria-invalid={Boolean(formState.errors.username)}
                {...register('username')}
              />
              {formState.errors.username ? (
                <p className="text-small text-feedback-error" role="alert">
                  {formState.errors.username.message}
                </p>
              ) : null}
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                aria-invalid={Boolean(formState.errors.password)}
                {...register('password')}
              />
              {formState.errors.password ? (
                <p className="text-small text-feedback-error" role="alert">
                  {formState.errors.password.message}
                </p>
              ) : null}
            </div>
            {formState.errors.root?.serverError ? (
              <p className="text-small text-feedback-error" role="alert">
                {formState.errors.root.serverError.message}
              </p>
            ) : null}
            <Button type="submit" className="w-full" disabled={login.isPending}>
              {login.isPending ? '登录中…' : '登录'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

export default LoginPage;
