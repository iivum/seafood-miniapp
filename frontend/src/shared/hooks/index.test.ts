import { useAuthGuard } from './index';
import { authStore } from '../../features/auth/store';
import { tokenStorage } from '../api/storage';

function setWxLoginCode(code: string | null) {
  (wx.login as jest.Mock).mockImplementation((opts: {
    success: (res: { code?: string }) => void;
    fail: (err: unknown) => void;
  }) => {
    if (code) opts.success({ code });
    else opts.fail({ errMsg: 'fail' });
  });
}

describe('shared/hooks/useAuthGuard', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    tokenStorage.clear();
    authStore.resetForTest();
  });

  it('ensureLoggedIn: returns existing auth state without calling login', async () => {
    tokenStorage.setTokens('a', 'r');
    authStore['_setState' as keyof typeof authStore]; // satisfy noUnused
    (authStore as unknown as { state: object }).state = {
      user: { id: 'u1' },
      isAuthenticated: true,
      isLoggingIn: false,
      lastError: null,
    };
    const guard = useAuthGuard({});
    const state = await guard.ensureLoggedIn();
    expect(state.isAuthenticated).toBe(true);
  });

  it('ensureLoggedIn: triggers login when not authenticated', async () => {
    setWxLoginCode('code');
    (wx.request as jest.Mock).mockImplementation((opts: {
      success: (res: unknown) => void;
    }) => {
      opts.success({ statusCode: 200, data: { accessToken: 'a', refreshToken: 'r', user: { id: 'u1' } } });
    });
    const guard = useAuthGuard({});
    const state = await guard.ensureLoggedIn();
    expect(state.isAuthenticated).toBe(true);
  });
});
