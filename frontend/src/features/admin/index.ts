/**
 * Admin feature: a thin façade that re-exports the admin-ui URL.
 *
 * The mini-program does not own the admin surface — that's a separate
 * React app at `admin-ui/`. The "admin" feature here only knows the
 * entry URL so the merchant entry on the profile page can deep-link
 * to it.
 */

export const ADMIN_UI_URL = '/pages-sub/merchant/merchant/merchant';
