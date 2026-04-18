# WeChat Mini Program Native Component Optimization Design

**Date:** 2026-04-18
**Status:** Approved (Revised after senior review)
**Author:** Claude AI
**Reviewer:** Senior WeChat Mini Program Developer (8+ years)

---

## Overview

Replace all custom UI implementations with WeChat native components across 4 TabBar pages (index, category, cart, profile) and key sub-pages for better performance, accessibility, and native user experience.

---

## Pages in Scope

### TabBar Pages
1. `pages/index/index` - Homepage with search, banner, categories, product list
2. `pages/category/category` - Category browsing
3. `pages/cart/cart` - Shopping cart
4. `pages/profile/profile` - User profile

### Sub-Pages
5. `pages-sub/order/order-confirm/order-confirm` - Order confirmation
6. `pages-sub/user/login/login` - Login page

---

## Component Replacements

### 1. Form Inputs & Controls

| Page | Current | Replace With | Notes |
|------|---------|-------------|-------|
| cart | Custom `icon` checkboxes with `bindtap` | `checkbox-group` + `checkbox` | Native selection state |
| cart | Custom +/- text buttons | `input type="number"` | Native keyboard, validate in JS |
| cart | Address section | `navigator` to address-list | Navigate to saved addresses |
| order-confirm | Address selection | `navigator` to address-list | Select from saved addresses |
| profile | Text menu items | `navigator` with `hover-class="navigator-hover"` | Native tap feedback |

### 2. Navigation

| Page | Current | Replace With | Notes |
|------|---------|-------------|-------|
| TabBar pages | `wx.navigateTo` to TabBar | `navigator open-type="switchTab"` | Required for TabBar navigation |
| Sub-pages | `wx.navigateTo` | `navigator url=""` | Declarative, proper stack |
| All | Custom back arrows | System `navigateBack` | Proper page stack |
| index | `bindtap="goToDetail"` on product | `navigator url=""` | Declarative routing |
| category | `bindtap="goToDetail"` | `navigator url=""` | Declarative routing |
| cart | `bindtap="selectAddress"` | `navigator` to address-list | Navigate to address selection |

### 3. Feedback & States

| Page | Current | Replace With | Notes |
|------|---------|-------------|-------|
| cart | `icon type="clear"` for remove | `button size="mini" catchtap="onRemove"` | Proper interaction |
| index | Custom loading text | `button loading="{{isLoading}}"` | Native loading state |
| cart | Custom empty icon + text | `navigator open-type="switchTab"` to index | Native redirect |
| All | `wx.showToast` for success/error | Keep `wx.showToast` | Toast and buttons serve different purposes |

### 4. Buttons

| Page | Current | Replace With | Notes |
|------|---------|-------------|-------|
| cart | `bindtap="onCheckout"` | `button` with `loading="{{isCreating}}"` | Native loading |
| profile | `bindtap="onLogin"` | `button open-type="getUserInfo" bindgetuserinfo="onLogin"` | WeChat native |
| login | `loading="{{isLoading}}"` attribute | Native `button loading` | Already correct |
| all | Plain `view` with `bindtap` | `button size="mini"` | Proper touch target |

---

## File-by-File Changes

### pages/index/index.wxml

**Changes:**
1. Search `input` - Already has `type="search"` and `confirm-type="search"` ✅
2. Product cards - Use `navigator` for routing, but **NOT** wrapping add-to-cart button
3. "Add to cart" button - Keep as `view` with `catchtap` (avoid nested interactive elements)
4. Hot search items - Wrap with `navigator`

**After:**
```xml
<view class="product-item" wx:for="{{products}}" wx:key="id">
  <navigator url="/pages-sub/product/product-detail/product-detail?id={{item.id}}">
    <image src="{{item.imageUrl}}"/>
    <view class="product-info">
      <text>{{item.name}}</text>
      <text>￥{{item.price}}</text>
    </view>
  </navigator>
  <view class="add-cart-btn" catchtap="addToCart" data-product="{{item}}">加入购物车</view>
</view>
```

**Important:** Do NOT nest `button` inside `navigator`. Use `catchtap` on sibling elements instead.

### pages/cart/cart.wxml

**Changes:**
1. Item checkboxes - Use `checkbox-group` but **NOT** individual `checked` attribute
2. Quantity inputs - Use `input type="number"` without `min` attribute, validate in JS
3. Select all checkbox - Separate `checkbox` outside `checkbox-group`
4. Remove button - Use `button size="mini" catchtap="onRemove"`
5. Checkout button - Use `button` with `loading="{{isCreating}}"`
6. Empty state - Use `navigator open-type="switchTab"` for TabBar navigation
7. Address section - Use `navigator open-type="navigate"` to address-list

**After:**
```xml
<!-- Select All -->
<checkbox bindchange="onSelectAll" checked="{{selectedItems.length === cartItems.length && cartItems.length > 0}}"/>

<!-- Item List -->
<checkbox-group bindchange="onCheckboxChange">
  <label wx:for="{{cartItems}}" wx:key="id">
    <checkbox value="{{item.id}}"/>
    <image src="{{item.imageUrl}}"/>
    <view class="item-info">
      <text>{{item.name}}</text>
      <text>￥{{item.price}}</text>
      <input type="number" value="{{item.quantity}}" bindchange="onQuantityChange" data-id="{{item.id}}"/>
    </view>
  </label>
</checkbox-group>

<!-- Remove Button -->
<button size="mini" catchtap="onRemove" data-id="{{item.id}}">删除</button>

<!-- Checkout Button -->
<button loading="{{isCreating}}" disabled="{{selectedItems.length === 0}}">去结算</button>

<!-- Empty State Navigate -->
<navigator open-type="switchTab" url="/pages/index/index">去逛逛</navigator>

<!-- Address Navigate -->
<navigator open-type="navigate" url="/pages-sub/user/address/address-list">选择地址</navigator>
```

### pages/category/category.wxml

**Changes:**
1. Category items - Use `navigator open-type="reLaunch"` to refresh category view
2. Products - Use `navigator` for product cards with `catchtap` for add-to-cart
3. "Back to categories" - Use `view catchtap` to clear category selection (not navigator)

**After:**
```xml
<!-- Category Item -->
<navigator open-type="reLaunch" url="/pages/category/category?categoryId={{item.id}}">
  <text>{{item.icon}}</text>
  <text>{{item.name}}</text>
</navigator>

<!-- Product Card -->
<view class="product-item">
  <navigator url="/pages-sub/product/product-detail/product-detail?id={{item.id}}">
    <image src="{{item.imageUrl}}"/>
    <text>{{item.name}}</text>
    <text>￥{{item.price}}</text>
  </navigator>
  <view catchtap="addToCart">加入购物车</view>
</view>

<!-- Back to Categories -->
<view bindtap="onBackToCategories">全部分类</view>
```

### pages/profile/profile.wxml

**Changes:**
1. User header login area - Use `button open-type="getUserInfo" bindgetuserinfo="onLogin"`
2. Order nav items - Use `navigator` with `hover-class`
3. Menu items - Use `navigator` with `hover-class`

**After:**
```xml
<button open-type="getUserInfo" bindgetuserinfo="onLogin" class="user-header">
  <image src="{{userInfo.avatarUrl || '/images/default-avatar.png'}}"/>
  <text>{{userInfo.nickname || '点击登录'}}</text>
</button>

<navigator url="/pages-sub/order/order-list/order-list" class="menu-item" hover-class="navigator-hover">
  <text>全部订单</text>
  <text class="arrow">></text>
</navigator>
```

### pages-sub/order/order-confirm/order-confirm.wxml

**Changes:**
1. Address picker - Navigate to address list for selection
2. Submit button - Use `button` with `loading` state

### pages-sub/user/login/login.wxml

**Changes:**
1. Already uses `button` with `open-type` - verify correct usage ✅
2. Keep `loading="{{isLoading}}"` attribute

---

## JavaScript Handler Changes

### cart.js

**Changes:**
1. `onToggleSelect` → Remove (replaced by `checkbox-group`)
2. `onCheckboxChange` → Handle `e.detail.value` array from checkbox-group
3. `onQuantityChange` → Handle input with validation, get `id` from `dataset`
4. `onSelectAll` → Handle select all checkbox independently
5. Add `id` propagation via `data-id` attribute on input

**After:**
```javascript
// Handle checkbox group change
onCheckboxChange: function(e) {
  this.setData({ selectedItems: e.detail.value });
  this.refreshCart();
},

// Handle select all
onSelectAll: function(e) {
  const allSelected = e.detail.checked;
  if (allSelected) {
    this.setData({ selectedItems: this.data.cartItems.map(item => item.id) });
  } else {
    this.setData({ selectedItems: [] });
  }
  this.refreshCart();
},

// Handle quantity change - NOTE: input does NOT support min, validate in JS
onQuantityChange: function(e) {
  const id = e.currentTarget.dataset.id;
  let quantity = parseInt(e.detail.value) || 1;
  quantity = Math.max(1, quantity); // Enforce minimum of 1
  cartUtil.updateQuantity(id, quantity);
  this.refreshCart();
},

// Handle remove item
onRemove: function(e) {
  const id = e.currentTarget.dataset.id;
  cartUtil.removeFromCart(id);
  this.refreshCart();
},
```

### profile.js

**Changes:**
1. Login handler - Accept `e.detail` from `bindgetuserinfo`
2. Navigation handlers - Can be removed if using `navigator`

**After:**
```javascript
onLogin: function(e) {
  if (e.detail.errMsg.includes('ok')) {
    // User approved, get user info
    const userInfo = e.detail.userInfo;
    // Handle login...
  }
},
```

### index.js

**Changes:**
1. `goToDetail` - Keep for product card navigation (navigator doesn't work for all cases)
2. `addToCart` - Keep as `catchtap` on view element

---

## Critical Corrections (from Senior Review)

| # | Issue | Fix Applied |
|---|-------|-------------|
| 1 | `input type="number"` doesn't support `min` | Remove `min`, validate in JS with `Math.max(1, value)` |
| 2 | `open-type="delete"` doesn't exist | Use `button catchtap="onRemove"` |
| 3 | `checked` binding inside `checkbox-group` | Remove individual `checked`, let group manage state |
| 4 | `navigator` to TabBar without `open-type` | Add `open-type="switchTab"` |
| 5 | `picker mode="selector"` for addresses | Use `navigator` to address-list instead |
| 6 | `bindgetuserinfo` for phone login | Use `bindgetphonenumber` for phone, `bindgetuserinfo` for profile |
| 7 | Nested button in navigator | Restructure so interactive elements are siblings, not children |

---

## Testing Checklist

- [ ] Checkbox selection persists correctly via checkbox-group
- [ ] Quantity input validates min=1 in JavaScript handler
- [ ] Address picker navigates to address-list page
- [ ] Navigator hover states work on all menu items
- [ ] Button loading states display correctly
- [ ] Form submission disabled when cart is empty
- [ ] `navigator open-type="switchTab"` works for TabBar pages
- [ ] `navigator` without open-type works for sub-pages
- [ ] No nested interactive elements (button inside navigator)
- [ ] No console errors on any page

---

## Files to Modify

| File | Changes |
|------|---------|
| `pages/index/index.wxml` | Navigator for products, move add-to-cart outside navigator |
| `pages/cart/cart.wxml` | checkbox-group (no checked attr), input type=number, navigator with open-type |
| `pages/cart/cart.js` | onCheckboxChange, onSelectAll, onQuantityChange with Math.max validation |
| `pages/category/category.wxml` | Navigator with open-type for TabBar, restructure product cards |
| `pages/profile/profile.wxml` | Navigator with hover-class, button for login |
| `pages-sub/order/order-confirm/order-confirm.wxml` | navigator for address |
| `pages-sub/user/login/login.wxml` | Verify button usage |

---

## Out of Scope

- TabBar icon images (already using native tabBar)
- Admin UI (Vaadin, not WeChat)
- Backend API changes
- @vant/weapp component library usage (keeping native WeChat only)
- Replacing `wx.showToast` (toast serves different purpose than buttons)

---

## Success Criteria

1. All form inputs use WeChat native components
2. All navigation uses `navigator` with correct `open-type` (switchTab for TabBar)
3. All buttons use native `button` with proper `open-type` and `loading`
4. No custom checkbox/switch/toggle implementations
5. No nested interactive elements
6. All interactive elements have proper hover/tap feedback
7. All pages pass `npm test` with no new failures
