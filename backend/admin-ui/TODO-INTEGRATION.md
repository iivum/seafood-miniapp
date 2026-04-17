# Admin UI Integration Testing - TODO

**Test Date:** 2026-04-17 (Updated 09:35)
**Branch:** test/admin-ui-integration
**Admin UI URL:** http://localhost:8090/admin

---

## Environment Status

### Docker Services (Updated 2026-04-17 09:32)
| Service | Status | Port | Notes |
|---------|--------|------|-------|
| discovery-service | Healthy | 8761 | Running |
| mongodb | Healthy | 27017 | Running |
| redis | Healthy | 6379 | Running |
| gateway | Healthy | 8080 | Running |
| product-service | Healthy | 8081 | Running |
| order-service | Healthy | 8082 | Running (returns 500 on /orders) |
| user-service | Healthy | 8083 | Running |
| admin-ui | Healthy | 8090 | Running |

### Gateway Routes
| Path | Service | Status |
|------|---------|--------|
| /api/products | product-service | ✓ Working |
| /api/orders | order-service | ✗ Returns 500 |
| /api/users | user-service | ✓ Working |
| /admin/** | admin-ui | ✗ CSS/JS blocked by Security |

---

## Critical Issue #1: Login Page Cannot Render (CSS/JS Blocked)

**Severity:** CRITICAL

**Finding:** The login page at `http://localhost:8090/admin/login` returns HTTP 200 but the page appears blank/empty in the browser.

**Browser Console Errors:**
```
Refused to apply style from 'http://localhost:8090/admin/VAADIN/themes/lumo/styles.css' 
because its MIME type ('application/json') is not a supported stylesheets MIME type

Failed to load resource: the server responded with a status of 401 ()
```

**Root Cause:** Spring Security Configuration

In `SecurityConfig.java`:
```java
.requestMatchers("/admin/login", "/login").permitAll()   // ✓ login page permitted
.requestMatchers("/admin/**").authenticated()            // ✗ VAADIN resources blocked
```

When the browser requests `/admin/VAADIN/themes/lumo/styles.css`, Spring Security returns 401 Unauthorized because the VAADIN resources are not in the permitted list.

**Impact:**
- Login page cannot load CSS, appearing blank/empty
- Authentication cannot proceed
- Admin UI is completely unusable

**Fix Required:**
Update SecurityConfig to permit Vaadin static resources:
```java
.requestMatchers("/admin/login", "/login", "/admin/VAADIN/**", "/admin/frontend/**").permitAll()
```

**Reference File:**
- `backend/admin-ui/src/main/java/com/seafood/admin/config/SecurityConfig.java` (lines 23-26)

---

## Issue #2: Order Management - Internal Server Error

**Severity:** HIGH

**Error:**
```bash
$ curl -s --noproxy '*' http://localhost:8082/orders
{"message":"Internal server error"}
```

**Also via Gateway:**
```bash
$ curl -s --noproxy '*' http://localhost:8080/api/orders
{"message":"Internal server error"}
```

**Investigation:**
- order-service container shows as healthy
- MongoDB connection is established successfully
- The `/orders` endpoint returns 500 immediately
- No obvious exceptions in recent logs (30+ minutes)

**Possible Causes:**
- Database query exception
- Missing collection or data initialization issue
- Invalid order data in database

**Reference File:**
- `backend/order-service/src/main/java/.../OrderController.java`
- `backend/order-service/src/main/java/.../OrderRepository.java`

---

## Issue #3: Product API Response - Paginated vs Array

**Severity:** MEDIUM

**API Response:**
```json
{
  "hasPrev": false,
  "totalProducts": 0,
  "totalPages": 0,
  "hasNext": false,
  "page": 0,
  "products": []
}
```

**Issue:** API returns a paginated wrapper object, not a direct array.

**Current Status:** Admin UI may expect direct array. Cannot verify because login page is broken.

**Reference Files:**
- `backend/product-service/src/main/java/.../ProductController.java` (API response structure)
- `backend/admin-ui/src/main/java/.../ProductClient.java` (client expectation)

---

## Issue #4: Gateway Routes Not Working (404)

**Severity:** MEDIUM (Dev Environment Only)

**Finding:** Direct paths `/products`, `/orders`, `/users` return 404 through gateway.

**Correct Paths:**
- `/api/products` → product-service
- `/api/orders` → order-service
- `/api/users` → user-service

This is expected behavior based on gateway configuration (`application.yml`):
```yaml
predicates:
  - Path=/api/products/**
  - Path=/api/orders/**
  - Path=/api/users/**
filters:
  - StripPrefix=1
```

---

## Summary of Required Fixes

### Priority 1 (Critical):
1. **Fix Spring Security Config** - Add `/admin/VAADIN/**` and `/admin/frontend/**` to permitted patterns
   - File: `backend/admin-ui/src/main/java/com/seafood/admin/config/SecurityConfig.java`
   - Lines: 23-26

2. **Fix order-service 500 error** - Investigate why `/orders` endpoint returns internal server error
   - File: `backend/order-service/src/main/java/.../OrderController.java`
   - Check MongoDB repository queries and data initialization

### Priority 2 (High):
3. **Test Product API with non-empty data** - Verify deserialization works
4. **Test Dashboard after login fix** - Confirm statistics display properly
5. **Test User Management** - Verify user list displays

### Priority 3 (Medium):
6. **Verify all admin-ui pages work after fixes**:
   - /admin/dashboard
   - /admin/products
   - /admin/orders
   - /admin/users

---

## Test Commands Used

```bash
# Check Docker services
docker ps --filter "name=seafood-miniapp"

# Check specific service logs
docker logs seafood-miniapp-admin-ui-1 --tail 30
docker logs seafood-miniapp-order-service-1 --tail 30

# Test API endpoints via services directly
curl -s --noproxy '*' http://localhost:8081/products
curl -s --noproxy '*' http://localhost:8082/orders
curl -s --noproxy '*' http://localhost:8083/users

# Test API endpoints via gateway
curl -s --noproxy '*' http://localhost:8080/api/products
curl -s --noproxy '*' http://localhost:8080/api/orders
curl -s --noproxy '*' http://localhost:8080/api/users

# Check login page (will show 401 for CSS)
curl -s --noproxy '*' -I http://localhost:8090/admin/login
curl -s --noproxy '*' http://localhost:8090/admin/VAADIN/themes/lumo/styles.css

# Access Admin UI
open http://localhost:8090/admin/login
```

---

## Files to Review

### Security Issue:
- `backend/admin-ui/src/main/java/com/seafood/admin/config/SecurityConfig.java`

### Order Service Issue:
- `backend/order-service/src/main/java/.../OrderController.java`
- `backend/order-service/src/main/java/.../OrderRepository.java`

### API Response Structure:
- `backend/product-service/src/main/java/.../ProductController.java`
- `backend/admin-ui/src/main/java/.../ProductClient.java`

### Login View:
- `backend/admin-ui/src/main/java/com/seafood/admin/views/login/LoginView.java`
