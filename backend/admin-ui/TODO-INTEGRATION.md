# Admin UI Integration Testing - TODO

**Test Date:** 2026-04-17
**Branch:** test/admin-ui-integration
**Admin UI URL:** http://localhost:8090/admin

---

## Environment Status

### Docker Services
| Service | Status | Notes |
|---------|--------|-------|
| discovery-service | Healthy | Running |
| mongodb | Healthy | Running |
| redis | Healthy | Running |
| product-service | Healthy | Running |
| order-service | Healthy | Running |
| gateway | Unhealthy | Has MongoDB connection issues |
| admin-ui | Unhealthy | JAR build issue (using ui-design worktree JAR) |
| user-service | Restarting | NoClassDefFoundError - JAR is corrupt |

### Critical Issue: Admin UI JAR Cannot Be Built
- **Problem:** `backend/admin-ui/build/libs/admin-ui-1.0.0-SNAPSHOT.jar` is created as a **directory** instead of a file
- **Cause:** Gradle bootJar task fails with compilation errors:
  ```
  OrderDetailDialog.java:149: cannot find symbol: class OrderItemResponse
  OrderDetailDialog.java:195: cannot find symbol: class OrderHistoryResponse
  ```
- **Root Cause:** `OrderResponse` class uses package-private inner classes `OrderItemResponse` and `OrderHistoryResponse`, but `OrderDetailDialog` references them as `OrderResponse.OrderItemResponse` and `OrderResponse.OrderHistoryResponse`
- **Workaround Used:** Copied JAR from `.worktrees/ui-design/` to proceed with testing

---

## Issue #1: Login Functionality - BYPASSED (Security Issue)

**Severity:** CRITICAL

**Finding:** No login screen is displayed. The admin UI dashboard is accessible directly at `http://localhost:8090/admin/` without any authentication. The page shows "欢迎回来，管理员 👋" (Welcome back, Admin) without requiring credentials.

**Expected Behavior:** User should be redirected to a login page and must authenticate with `admin/admin123` before accessing any functionality.

**Impact:** 
- No authentication protection on admin panel
- Any unauthorized user can access all admin functions
- Security vulnerability - all operations are exposed

---

## Issue #2: Product Management - API Deserialization Error

**Severity:** HIGH

**Error:**
```
com.fasterxml.jackson.databind.exc.MismatchedInputException: 
Cannot deserialize value of type `java.util.ArrayList<ProductResponse>` 
from Object value (token `JsonToken.START_OBJECT`)
```

**URL:** http://localhost:8090/admin/products

**Root Cause Analysis:**
- Product API returns a paginated response: `{"hasPrev":false,"totalProducts":0,"totalPages":0,"hasNext":false,"page":0,"products":[]}`
- Admin UI `ProductClient` expects `List<ProductResponse>` directly
- The client expects an array but receives an object with pagination wrapper

**API Response (product-service):**
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

**Admin UI Client Expectation:**
```java
@GetMapping("/products")
List<ProductResponse> getAllProducts();
```

**Fix Required:** 
- Option 1: Update Admin UI client to expect a paginated wrapper response
- Option 2: Create a separate endpoint in product-service that returns a simple array
- Option 3: Update the ProductResponse to match the API structure

---

## Issue #3: Order Management - Internal Server Error

**Severity:** HIGH

**Error:**
```
feign.FeignException$InternalServerError: [500] during [GET] to 
[http://order-service/orders] [OrderClient#getAllOrders()]: 
[{"message":"Internal server error"}]
```

**URL:** http://localhost:8090/admin/orders

**Investigation:**
- Direct curl to `http://localhost:8082/orders` returns `{"message":"Internal server error"}`
- order-service is showing as healthy in Docker
- The 500 error indicates a server-side issue in order-service

**Possible Causes:**
- Database connection issue
- Missing data/initialization problem
- Code exception in the order controller

---

## Issue #4: User Management - Service Unavailable

**Severity:** CRITICAL (Service Down)

**Error:**
```
feign.FeignException$ServiceUnavailable: [503] during [GET] to 
[http://user-service/users] [UserClient#getAllUsers()]: 
[Load balancer does not contain an instance for the service user-service]
```

**URL:** http://localhost:8090/admin/users

**Root Cause:** user-service container is in a restart loop with the following error:
```
java.lang.NoClassDefFoundError: org/springframework/boot/SpringApplication
Caused by: java.lang.ClassNotFoundException: org.springframework.boot.SpringApplication
```

**Impact:** user-service is completely non-functional

---

## Issue #5: Dashboard - API Deserialization Error

**Severity:** HIGH

**Error:** Same deserialization error as Product Management
```
com.fasterxml.jackson.databind.exc.MismatchedInputException: 
Cannot deserialize value of type `java.util.ArrayList<ProductResponse>` 
from Object value (token `JsonToken.START_OBJECT`)
```

**URL:** http://localhost:8090/admin/dashboard

**Note:** Dashboard likely fetches product data to display statistics, causing the same deserialization issue.

---

## Issue #6: UI Design/Layout Observations

**Severity:** LOW (Cosmetic/Design)

### Observations:
1. **Navigation Sidebar:** Toggle button exists but sidebar appears always expanded on desktop
2. **Date Display:** Shows "2026年04月16日 Thu" - date may be stale or incorrect timezone
3. **"Online" Indicator:** Small text in bottom-right shows "Online" status, but this is misleading since the backend services have errors
4. **Quick Entry Buttons:** Redundant with sidebar navigation - consider consolidating
5. **Feature Module Cards:** Description text is duplicated from quick entry tooltips

---

## Summary of Required Fixes

### Priority 1 (Critical):
1. **Fix Admin UI JAR build** - Compilation errors in OrderDetailDialog.java
2. **Fix user-service** - NoClassDefFoundError, service won't start
3. **Implement Authentication** - No login protection currently

### Priority 2 (High):
4. **Fix Product API response mismatch** - Client expects List, API returns paginated object
5. **Fix order-service 500 error** - Investigate and resolve internal server error
6. **Fix Dashboard** - Same deserialization issue as products

### Priority 3 (Medium/Low):
7. **Improve error handling UI** - Current error messages show Java stack traces
8. **Add loading states** - No visual feedback during API calls
9. **Add connection status indicator** - "Online" text is misleading

---

## Files to Review for Fixes

### Compilation Error:
- `backend/admin-ui/src/main/java/com/seafood/admin/views/order/OrderDetailDialog.java` (lines 149, 195)
- `backend/admin-ui/src/main/java/com/seafood/admin/client/OrderResponse.java` (inner classes visibility)

### API Mismatch:
- `backend/admin-ui/src/main/java/com/seafood/admin/client/ProductClient.java`
- `backend/admin-ui/src/main/java/com/seafood/admin/client/ProductResponse.java`
- `backend/product-service/src/main/java/.../ProductController.java` (API response structure)

### User Service:
- `backend/user-service/build/libs/` - JAR may be corrupt

### Authentication:
- Check if there's a security configuration that needs to be enabled
- `backend/admin-ui/src/main/java/com/seafood/admin/` - security/auth related files

---

## Test Commands Used

```bash
# Check Docker services
docker ps --filter "name=seafood-miniapp"

# Check specific service logs
docker logs seafood-miniapp-admin-ui-1 --tail 20
docker logs seafood-miniapp-user-service-1 --tail 20
docker logs seafood-miniapp-product-service-1 --tail 20
docker logs seafood-miniapp-order-service-1 --tail 20

# Test API endpoints
curl -s --noproxy '*' http://localhost:8081/products
curl -s --noproxy '*' http://localhost:8082/orders

# Access Admin UI
open http://localhost:8090/admin
```
