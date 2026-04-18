# WeChat Mini Program Native Component Optimization Design

**Date:** 2026-04-18
**Status:** Approved
**Author:** Claude AI

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
| cart | Custom +/- text buttons | `input type="number"` with `min="1"` | Native keyboard, validation |
| cart | Address section | `picker` mode="selector" | Native address picker |
| order-confirm | Address selection | `picker` | Native picker UI |
| profile | Text menu items | `navigator` with `hover-class="navigator-hover"` | Native tap feedback |

### 2. Navigation

| Page | Current | Replace With | Notes |
|------|---------|-------------|-------|
| All | `wx.navigateTo({ url })` in JS | `navigator url=""` in WXML | Declarative, proper stack |
| All | Custom back arrows | System `navigateBack` | Proper page stack |
| index | `bindtap="goToDetail"` on product | `navigator url=""` | Declarative routing |
| category | `bindtap="goToDetail"` | `navigator url=""` | Declarative routing |
| cart | `bindtap="selectAddress"` | `navigator url=""` | Native address picker |

### 3. Feedback & States

| Page | Current | Replace With | Notes |
|------|---------|-------------|-------|
| cart | `icon type="clear"` for remove | `button` with `open-type="delete"` | Proper interaction |
| index | Custom loading text | `button loading="{{isLoading}}"` | Native loading state |
| All | `wx.showToast` for success | Native `button` with `formType="submit"` | Integrated feedback |
| cart | Custom empty icon + text | `navigator` to index | Native redirect |

### 4. Buttons

| Page | Current | Replace With | Notes |
|------|---------|-------------|-------|
| cart | `bindtap="onCheckout"` | `button` with `formType="submit"` + loading | Native loading |
| profile | `bindtap="onLogin"` | `button` with `open-type="getPhoneNumber"` | WeChat native |
| login | `loading="{{isLoading}}"` attribute | Native `button loading` | Already correct |
| all | Plain `view` with `bindtap` | `button size="mini"` | Proper touch target |

---

## File-by-File Changes

### pages/index/index.wxml

**Changes:**
1. Search `input` - Add `type="search"` and native `confirm-type="search"`
2. Product cards - Wrap with `navigator url="/pages-sub/product/product-detail/..."`
3. "Add to cart" button - Change from `view` to `button` with `size="mini"`
4. Hot search items - Wrap with `navigator`

**After:**
```xml
<navigator url="/pages-sub/product/product-detail/product-detail?id={{item.id}}" class="product-item">
  <image src="{{item.imageUrl}}"/>
  <view class="product-info">
    <text>{{item.name}}</text>
    <text>￥{{item.price}}</text>
    <button size="mini" catchtap="addToCart">加入购物车</button>
  </view>
</navigator>
```

### pages/cart/cart.wxml

**Changes:**
1. Item checkboxes - Replace custom icon with `checkbox-group`
2. Quantity inputs - Replace +/- buttons with `input type="number"`
3. Select all - Use `checkbox` in `checkbox-group`
4. Remove button - Use `button` with `size="mini"` and icon
5. Checkout button - Use `button` with `loading="{{isCreating}}"`
6. Empty state - Use `navigator` for "去逛逛"

**After:**
```xml
<checkbox-group bindchange="onCheckboxChange">
  <label wx:for="{{cartItems}}" wx:key="id">
    <checkbox value="{{item.id}}" checked="{{selectedItems.includes(item.id)}}"/>
    <image src="{{item.imageUrl}}"/>
    <view class="item-info">
      <text>{{item.name}}</text>
      <text>￥{{item.price}}</text>
      <input type="number" min="1" value="{{item.quantity}}" bindchange="onQuantityChange"/>
    </view>
  </label>
</checkbox-group>
```

### pages/category/category.wxml

**Changes:**
1. Category items - Use `navigator`
2. Products - Use `navigator` for product cards
3. "Back to categories" - Use `navigator` with delta back

**After:**
```xml
<navigator class="category-item" url="/pages/category/category?categoryId={{item.id}}">
  <text>{{item.icon}}</text>
  <text>{{item.name}}</text>
</navigator>
```

### pages/profile/profile.wxml

**Changes:**
1. User header login area - Use `button` with `open-type="getUserInfo"`
2. Order nav items - Use `navigator`
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
1. Address picker - Use `picker` mode="selector" or `navigator` to address-list
2. Submit button - Use `button` with proper `loading` state

### pages-sub/user/login/login.wxml

**Changes:**
1. Already uses `button` with `open-type` - verify correct usage
2. Add `hover-class` for tap feedback

---

## JavaScript Handler Changes

### cart.js

**Changes:**
1. `onToggleSelect` → `onCheckboxChange` (receives `detail.value` array)
2. `onPlus`/`onMinus` → `onQuantityChange` (receives new value)
3. `onToggleSelectAll` → part of `checkbox-group` change event
4. Remove manual state management for checkboxes

**Before:**
```javascript
onToggleSelect: function(e) {
  const id = e.currentTarget.dataset.id;
  const selectedItems = [...this.data.selectedItems];
  const index = selectedItems.indexOf(id);
  if (index > -1) {
    selectedItems.splice(index, 1);
  } else {
    selectedItems.push(id);
  }
  this.setData({ selectedItems: selectedItems });
}
```

**After:**
```javascript
onCheckboxChange: function(e) {
  this.setData({ selectedItems: e.detail.value });
  this.refreshCart();
},

onQuantityChange: function(e) {
  const id = e.currentTarget.dataset.id;
  const quantity = parseInt(e.detail.value) || 1;
  cartUtil.updateQuantity(id, quantity);
  this.refreshCart();
}
```

### profile.js

**Changes:**
1. Login handler - Accept `bindgetuserinfo` event parameter
2. Navigation handlers - Can be removed if using `navigator`

### index.js

**Changes:**
1. `goToDetail` - May be removable if using `navigator`
2. `addToCart` - Keep as `catchtap` on button element

---

## Testing Checklist

- [ ] Checkbox selection persists correctly
- [ ] Quantity input respects min=1
- [ ] Address picker opens native selector or navigates to address list
- [ ] Navigator hover states work on all menu items
- [ ] Button loading states display correctly
- [ ] Form submission disabled when cart is empty
- [ ] Page navigation works with `navigator` component
- [ ] No console errors on any page

---

## Files to Modify

| File | Changes |
|------|---------|
| `pages/index/index.wxml` | Navigator for products, button for add-to-cart |
| `pages/index/index.js` | Remove goToDetail if using navigator |
| `pages/cart/cart.wxml` | checkbox-group, input type=number, picker |
| `pages/cart/cart.js` | onCheckboxChange, onQuantityChange handlers |
| `pages/category/category.wxml` | Navigator for categories and products |
| `pages/profile/profile.wxml` | Navigator for menus, button for login |
| `pages/profile/profile.js` | Accept getuserinfo event |
| `pages-sub/order/order-confirm/order-confirm.wxml` | Navigator for address |
| `pages-sub/user/login/login.wxml` | Verify native button usage |

---

## Out of Scope

- TabBar icon images (already using native tabBar)
- Admin UI (Vaadin, not WeChat)
- Backend API changes
- @vant/weapp component library usage (keeping native WeChat only)

---

## Success Criteria

1. All form inputs use WeChat native components
2. All navigation uses `navigator` component where applicable
3. All buttons use native `button` with proper `open-type` and `loading`
4. No custom checkbox/switch/toggle implementations
5. All interactive elements have proper hover/tap feedback
6. All pages pass `npm test` with no new failures
