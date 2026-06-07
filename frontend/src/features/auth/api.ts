/**
 * Auth feature: API client.
 *
 * Thin wrappers over the central `request` helper. Public endpoints
 * (no JWT required): `POST /api/auth/wechat-login`,
 * `POST /api/auth/refresh`.
 */
import { post, type WechatLoginResponse } from '../../shared/api/request';
import type { WechatLoginPayload } from './types';

export const AuthAPI = {
  /** Exchange a WeChat `code` for an access+refresh token pair. */
  wechatLogin(payload: WechatLoginPayload): Promise<WechatLoginResponse> {
    return post<WechatLoginResponse>('/auth/wechat-login', payload);
  },
};
