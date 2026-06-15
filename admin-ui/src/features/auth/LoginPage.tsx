import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from './store';
import { useLogin } from './queries';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { AxiosError } from 'axios';
import type { ApiError } from '@/types/api';

const loginSchema = z.object({
  username: z.string().min(1, '请输入用户名'),
  password: z.string().min(1, '请输入密码'),
  remember: z.boolean().default(false),
});

type LoginFormValues = z.infer<typeof loginSchema>;

/** 本地"记住我"持久化 key。存用户名(非密码!)— 重新打开浏览器时自动填回。 */
const REMEMBER_KEY = 'seafood-admin-ui:remember-username';

/** sprint-1-closure 8.3 — 锁定状态 sessionStorage 持久化 key(会话内 refresh 不丢倒计时) */
const LOCKOUT_KEY = 'seafood-admin-ui:login-lockout';

interface LockoutState {
  /** 锁定到期时间戳(ms)。null = 未锁。 */
  until: number | null;
  /** 锁定来源:IP(429) 或 ACCOUNT(423) */
  scope: 'IP' | 'ACCOUNT' | 'NONE';
}

export function LoginPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const hydrated = useAuthStore((s) => s.hydrated);
  const [searchParams] = useSearchParams();
  const { register, handleSubmit, formState, setError, setValue, watch } =
    useForm<LoginFormValues>({
      resolver: zodResolver(loginSchema),
      defaultValues: {
        username: readRememberedUsername() || '',
        password: '',
        remember: Boolean(readRememberedUsername()),
      },
    });
  const navigate = useNavigate();
  const location = useLocation();
  const login = useLogin();
  const remember = watch('remember');

  // sprint-1-closure 8.2 / 8.3 — 锁定状态 + 倒计时
  const [lockout, setLockout] = useState<LockoutState>(readLockout());
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (lockout.until == null) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [lockout.until]);
  useEffect(() => {
    // 倒计时归零时自动清除锁定 + 持久化
    if (lockout.until != null && now >= lockout.until) {
      setLockout({ until: null, scope: 'NONE' });
      clearLockout();
    }
  }, [now, lockout.until]);
  const secondsLeft = lockout.until == null ? 0 : Math.max(0, Math.ceil((lockout.until - now) / 1000));
  const isLocked = secondsLeft > 0;

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
      // 2.14:记住我(仅持久化 username,非密码 — 安全考虑)
      if (values.remember) {
        writeRememberedUsername(values.username);
      } else {
        clearRememberedUsername();
      }
      await login.mutateAsync({ username: values.username, password: values.password });
      // 成功登录 → 清锁定状态
      setLockout({ until: null, scope: 'NONE' });
      clearLockout();
      navigate(from, { replace: true });
    } catch (err) {
      // sprint-1-closure 8.3 — 423 / 429 → 锁定 + 倒计时
      if (err instanceof AxiosError) {
        const status = err.response?.status;
        const body = err.response?.data as ApiError | undefined;
        const code = body?.code;
        if (status === 429 || code === 'AUTH_LOCKED') {
          // IP 锁:15 分钟倒计时
          const until = Date.now() + 15 * 60 * 1000;
          const next: LockoutState = { until, scope: 'IP' };
          setLockout(next);
          writeLockout(next);
          setError('root.serverError', { message: '登录尝试次数过多,请 15 分钟后再试' });
          return;
        }
        if (status === 423 || code === 'ACCOUNT_LOCKED') {
          const until = Date.now() + 15 * 60 * 1000;
          const next: LockoutState = { until, scope: 'ACCOUNT' };
          setLockout(next);
          writeLockout(next);
          setError('root.serverError', { message: '账户已被锁定,请 15 分钟后再试' });
          return;
        }
      }
      const message = err instanceof Error ? err.message : '登录失败';
      setError('root.serverError', { message });
    }
  });

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg p-4">
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
                disabled={isLocked}
                aria-invalid={Boolean(formState.errors.username)}
                {...register('username')}
              />
              {formState.errors.username ? (
                <p className="text-sm text-error" role="alert">
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
                disabled={isLocked}
                aria-invalid={Boolean(formState.errors.password)}
                {...register('password')}
              />
              {formState.errors.password ? (
                <p className="text-sm text-error" role="alert">
                  {formState.errors.password.message}
                </p>
              ) : null}
            </div>
            {/* 2.14:记住我(checkbox 状态受控 watch,勾选变更触发 username 持久化) */}
            <div className="flex items-center gap-2">
              <Checkbox
                id="remember"
                checked={Boolean(remember)}
                onCheckedChange={(v) => setValue('remember', Boolean(v))}
                disabled={isLocked}
              />
              <Label
                htmlFor="remember"
                className="cursor-pointer text-sm text-muted"
              >
                记住我(下次自动填用户名)
              </Label>
            </div>
            {formState.errors.root?.serverError ? (
              <p className="text-sm text-error" role="alert">
                {formState.errors.root.serverError.message}
              </p>
            ) : null}
            {isLocked ? (
              <p className="text-sm text-error" role="status" aria-live="polite">
                {lockout.scope === 'IP' ? '当前 IP 已被锁定,' : '当前账户已被锁定,'}
                剩余 {Math.floor(secondsLeft / 60)} 分 {secondsLeft % 60} 秒
              </p>
            ) : null}
            <Button
              type="submit"
              className="w-full"
              disabled={login.isPending || isLocked}
            >
              {login.isPending ? '登录中…' : isLocked ? `锁定中 (${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, '0')})` : '登录'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function readRememberedUsername(): string | undefined {
  try {
    return localStorage.getItem(REMEMBER_KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

function writeRememberedUsername(username: string) {
  try {
    localStorage.setItem(REMEMBER_KEY, username);
  } catch {
    /* localStorage 不可用时 silently skip */
  }
}

function clearRememberedUsername() {
  try {
    localStorage.removeItem(REMEMBER_KEY);
  } catch {
    /* silently skip */
  }
}

// sprint-1-closure 8.3 — 锁定状态 sessionStorage helpers(会话内 refresh 保留)

function readLockout(): LockoutState {
  try {
    const raw = sessionStorage.getItem(LOCKOUT_KEY);
    if (!raw) return { until: null, scope: 'NONE' };
    const parsed = JSON.parse(raw) as LockoutState;
    if (parsed.until != null && parsed.until > Date.now()) {
      return parsed;
    }
    return { until: null, scope: 'NONE' };
  } catch {
    return { until: null, scope: 'NONE' };
  }
}

function writeLockout(s: LockoutState) {
  try {
    sessionStorage.setItem(LOCKOUT_KEY, JSON.stringify(s));
  } catch {
    /* silently skip */
  }
}

function clearLockout() {
  try {
    sessionStorage.removeItem(LOCKOUT_KEY);
  } catch {
    /* silently skip */
  }
}

export default LoginPage;
