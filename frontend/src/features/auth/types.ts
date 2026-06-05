/**
 * Auth feature: types.
 *
 * Re-exports the canonical user shape from `shared/api/storage` so
 * callers can `import { User } from 'features/auth/types'`.
 */
export type { StoredUser as User } from '../../shared/api/storage';

export interface WechatLoginPayload {
  /** Returned by `wx.login({success})` */
  code: string;
}
