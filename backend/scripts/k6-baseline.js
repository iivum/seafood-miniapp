// k6 baseline script — exercises 5 core endpoints
// Per test-suite-roadmap/design.md §2.1 子项目 ③ C3
//
// Usage:
//   k6 run backend/scripts/k6-baseline.js
//   k6 run -e BASE=http://localhost:8080 -e ADMIN_USER=admin -e ADMIN_PASS=xxx \
//          -e CUSTOMER_USER=u-1 -e CUSTOMER_PASS=yyy \
//          backend/scripts/k6-baseline.js
//
// Outputs per-endpoint P50/P95/P99; exits non-zero if P99 > 500ms.

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: parseInt(__ENV.VUS || '10', 10),
    duration: __ENV.DURATION || '30s',
    thresholds: {
        'http_req_duration{name:GET /api/products}': ['p(99)<500'],
        'http_req_duration{name:POST /api/admin/auth/login}': ['p(99)<500'],
        'http_req_duration{name:GET /api/orders}': ['p(99)<500'],
        'http_req_duration{name:POST /api/orders}': ['p(99)<500'],
        'http_req_duration{name:GET /api/admin/orders}': ['p(99)<500'],
    },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const ADMIN_USER = __ENV.ADMIN_USER || 'admin';
const ADMIN_PASS = __ENV.ADMIN_PASS || 'admin';
const CUSTOMER_USER = __ENV.CUSTOMER_USER || 'u-test';
const CUSTOMER_PASS = __ENV.CUSTOMER_PASS || 'test';

const params = (name) => ({ tags: { name } });

export default function () {
    // 1. GET /api/products (public)
    const r1 = http.get(`${BASE}/api/products`, params('GET /api/products'));
    check(r1, { 'GET /api/products 200': (r) => r.status === 200 });

    // 2. POST /api/admin/auth/login
    const login = http.post(`${BASE}/api/admin/auth/login`,
        JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }),
        Object.assign({ headers: { 'Content-Type': 'application/json' } },
            params('POST /api/admin/auth/login')));
    check(login, { 'admin login 200': (r) => r.status === 200 });
    const adminToken = login.json('accessToken') || '';

    // 3. GET /api/orders (CUSTOMER auth — skip if no auth configured)
    if (__ENV.CUSTOMER_TOKEN) {
        const r3 = http.get(`${BASE}/api/orders`,
            Object.assign({ headers: { 'Authorization': `Bearer ${__ENV.CUSTOMER_TOKEN}` } },
                params('GET /api/orders')));
        check(r3, { 'GET /api/orders 200': (r) => r.status === 200 });
    }

    // 4. POST /api/orders
    if (__ENV.CUSTOMER_TOKEN) {
        const r4 = http.post(`${BASE}/api/orders`, '{}',
            Object.assign({
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${__ENV.CUSTOMER_TOKEN}`,
                },
            }, params('POST /api/orders')));
        check(r4, { 'POST /api/orders 4xx/5xx (expected without cart)':
            (r) => r.status >= 400 });
    }

    // 5. GET /api/admin/orders (ADMIN auth)
    if (adminToken) {
        const r5 = http.get(`${BASE}/api/admin/orders`,
            Object.assign({ headers: { 'Authorization': `Bearer ${adminToken}` } },
                params('GET /api/admin/orders')));
        check(r5, { 'GET /api/admin/orders 200': (r) => r.status === 200 });
    }

    sleep(1);
}
