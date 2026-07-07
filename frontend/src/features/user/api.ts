/**
 * User feature: API client.
 *
 * Authenticated (CUSTOMER) endpoints per backend contract:
 *   GET   /api/users/me              — current user info
 *   GET   /api/users/me/addresses    — list addresses
 *   POST  /api/users/me/addresses    — add address
 *   PUT   /api/users/me/addresses/{id} — update address
 *   DELETE /api/users/me/addresses/{id} — remove address
 *   PATCH  /api/users/me/phone       — bind/update phone via WeChat getPhoneNumber code
 */
import { del, get, patch, post, put } from '../../shared/api/request';

export interface Address {
  id?: string;
  receiverName: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  isDefault?: boolean;
}

export const UserAPI = {
  me(): Promise<{ id: string; nickname?: string; avatarUrl?: string; role: string }> {
    return get('/users/me', { needAuth: true });
  },
  listAddresses(): Promise<Address[]> {
    return get<Address[]>('/users/me/addresses', { needAuth: true });
  },
  addAddress(a: Address): Promise<Address> {
    return post<Address>('/users/me/addresses', a, { needAuth: true });
  },
  updateAddress(id: string, a: Address): Promise<Address> {
    return put<Address>(`/users/me/addresses/${encodeURIComponent(id)}`, a, { needAuth: true });
  },
  removeAddress(id: string): Promise<void> {
    return del<void>(`/users/me/addresses/${encodeURIComponent(id)}`, { needAuth: true });
  },
  bindPhone(code: string): Promise<{ id: string; nickname?: string; avatarUrl?: string; role: string; phone?: string }> {
    return patch('/users/me/phone', { code }, { needAuth: true });
  },
};
