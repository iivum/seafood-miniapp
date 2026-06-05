import { tokenStorage } from './storage';

describe('shared/api/tokenStorage', () => {
  it('returns null when wx is unavailable', () => {
    const savedWx = (global as { wx?: unknown }).wx;
    (global as { wx?: unknown }).wx = undefined;
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
    expect(tokenStorage.getUser()).toBeNull();
    tokenStorage.setTokens('a', 'b'); // should noop
    tokenStorage.clear();
    (global as { wx?: unknown }).wx = savedWx;
  });

  it('round-trips tokens through wx storage', () => {
    tokenStorage.setTokens('access-1', 'refresh-1');
    expect(tokenStorage.getAccessToken()).toBe('access-1');
    expect(tokenStorage.getRefreshToken()).toBe('refresh-1');
  });

  it('round-trips user info', () => {
    tokenStorage.setUser({ id: 'u1', role: 'CUSTOMER' });
    expect(tokenStorage.getUser()).toEqual({ id: 'u1', role: 'CUSTOMER' });
    tokenStorage.setUser(null);
    expect(tokenStorage.getUser()).toBeNull();
  });

  it('clears all storage on clear()', () => {
    tokenStorage.setTokens('a', 'b');
    tokenStorage.setUser({ id: 'x' });
    tokenStorage.clear();
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
    expect(tokenStorage.getUser()).toBeNull();
  });
});
