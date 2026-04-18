# WeChat Native Components Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace custom UI implementations with WeChat native components across 4 TabBar pages and key sub-pages.

**Architecture:** Full native component replacement using WeChat's built-in components (checkbox-group, input type=number, navigator with proper open-type, button with loading). No external component libraries.

**Tech Stack:** WeChat Mini Program (native components only), JavaScript

---

## File Structure

| File | Responsibility |
|------|----------------|
| `pages/cart/cart.wxml` | Cart UI with checkbox-group, input type=number |
| `pages/cart/cart.js` | Cart handlers: onCheckboxChange, onSelectAll, onQuantityChange |
| `pages/index/index.wxml` | Index with navigator for products |
| `pages/category/category.wxml` | Category with navigator and reLaunch |
| `pages/profile/profile.wxml` | Profile with navigator menus and button login |
| `pages-sub/order/order-confirm/order-confirm.wxml` | Order confirm with navigator for address |
| `pages-sub/user/login/login.wxml` | Login page (verify native button usage) |

---

## Task 1: Cart Page - WXML Native Components

**Files:**
- Modify: `frontend/pages/cart/cart.wxml`

- [ ] **Step 1: Read current cart.wxml**

Review current implementation at lines 1-82 to understand structure.

- [ ] **Step 2: Replace icon checkboxes with checkbox-group**

Change from:
```xml
<view class="item-checkbox" bindtap="onToggleSelect" data-id="{{item.id}}">
  <icon type="{{selectedItems.includes(item.id) ? 'success' : 'circle'}}" size="20" color="{{selectedItems.includes(item.id) ? '#07c160' : '#999'}}"></icon>
</view>
```

To:
```xml
<checkbox value="{{item.id}}" class="item-checkbox"/>
```

- [ ] **Step 3: Replace select-all icon with checkbox**

Change from:
```xml
<view class="select-all" bindtap="onToggleSelectAll">
  <icon type="{{selectedItems.length === cartItems.length ? 'success' : 'circle'}}" size="20" color="{{selectedItems.length === cartItems.length ? '#07c160' : '#999'}}"></icon>
  <text>全选</text>
</view>
```

To:
```xml
<checkbox bindchange="onSelectAll" checked="{{selectedItems.length === cartItems.length && cartItems.length > 0}}"/>
<text>全选</text>
```

- [ ] **Step 4: Replace +/- buttons with input type="number"**

Change from:
```xml
<view class="quantity-control">
  <text class="minus" bindtap="onMinus" data-id="{{item.id}}">-</text>
  <text class="num">{{item.quantity}}</text>
  <text class="plus" bindtap="onPlus" data-id="{{item.id}}">+</text>
</view>
```

To:
```xml
<view class="quantity-control">
  <input type="number" value="{{item.quantity}}" bindchange="onQuantityChange" data-id="{{item.id}}" class="quantity-input"/>
</view>
```

- [ ] **Step 5: Replace remove icon with button**

Change from:
```xml
<icon class="remove-btn" type="clear" size="20" bindtap="onRemove" data-id="{{item.id}}"></icon>
```

To:
```xml
<button size="mini" class="remove-btn" catchtap="onRemove" data-id="{{item.id}}">删除</button>
```

- [ ] **Step 6: Update address section to navigator**

Change from:
```xml
<view class="address-section card" bindtap="selectAddress">
```

To:
```xml
<navigator class="address-section card" open-type="navigate" url="/pages-sub/user/address/address-list?selectMode=true">
```

- [ ] **Step 7: Update empty state to navigator with switchTab**

Change from:
```xml
<button class="btn" bindtap="goToIndex">去逛逛</button>
```

To:
```xml
<navigator open-type="switchTab" url="/pages/index/index" class="btn">去逛逛</navigator>
```

- [ ] **Step 8: Add checkbox-group wrapper**

Wrap the cart item list with:
```xml
<checkbox-group bindchange="onCheckboxChange">
  <!-- cart items here -->
</checkbox-group>
```

---

## Task 2: Cart Page - JavaScript Handlers

**Files:**
- Modify: `frontend/pages/cart/cart.js`

- [ ] **Step 1: Read current cart.js**

Review lines 1-182 to understand current handlers.

- [ ] **Step 2: Replace onToggleSelect with onCheckboxChange**

Change from:
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
  this.refreshCart();
},
```

To:
```javascript
onCheckboxChange: function(e) {
  this.setData({ selectedItems: e.detail.value });
  this.refreshCart();
},
```

- [ ] **Step 3: Replace onToggleSelectAll with onSelectAll**

Change from:
```javascript
onToggleSelectAll: function() {
  if (this.data.selectedItems.length === this.data.cartItems.length) {
    this.setData({ selectedItems: [] });
  } else {
    const allIds = this.data.cartItems.map(item => item.id);
    this.setData({ selectedItems: allIds });
  }
  this.refreshCart();
},
```

To:
```javascript
onSelectAll: function(e) {
  const allSelected = e.detail.checked;
  if (allSelected) {
    this.setData({ selectedItems: this.data.cartItems.map(item => item.id) });
  } else {
    this.setData({ selectedItems: [] });
  }
  this.refreshCart();
},
```

- [ ] **Step 4: Replace onPlus/onMinus with onQuantityChange**

Remove existing onPlus and onMinus, add:
```javascript
onQuantityChange: function(e) {
  const id = e.currentTarget.dataset.id;
  let quantity = parseInt(e.detail.value) || 1;
  quantity = Math.max(1, quantity); // Enforce minimum of 1
  cartUtil.updateQuantity(id, quantity);
  this.refreshCart();
},
```

- [ ] **Step 5: Update onRemove to use dataset**

Change from:
```javascript
onRemove: function(e) {
  const app = getApp();
  if (!app.globalData.userInfo) {
    wx.showToast({ title: '请先登录', icon: 'none' });
    return;
  }
  const id = e.currentTarget.dataset.id;
  cartUtil.removeFromCart(id);
  this.refreshCart();
},
```

Keep as-is (already uses dataset).

- [ ] **Step 6: Remove goToIndex function**

The navigator with switchTab now handles this. Remove:
```javascript
goToIndex: function() {
  wx.switchTab({ url: '/pages/index/index' });
}
```

---

## Task 3: Index Page - WXML Navigator

**Files:**
- Modify: `frontend/pages/index/index.wxml`

- [ ] **Step 1: Read current index.wxml**

Review lines 1-116 to understand structure.

- [ ] **Step 2: Restructure product cards to avoid nested interactive elements**

Current structure has bindtap on image and view. Change to use navigator for image/name/price, but keep add-to-cart as separate catchtap:

Change from:
```xml
<view class="product-item" wx:for="{{products}}" wx:key="id">
  <image class="product-img" src="{{item.imageUrl}}" mode="aspectFill" lazy-load="true" bindtap="goToDetail" data-id="{{item.id}}"></image>
  <view class="product-info">
    <view class="product-name" bindtap="goToDetail" data-id="{{item.id}}">
      <text wx:if="{{!item.nameHighlighted}}">{{item.name}}</text>
      <view wx:else class="highlight-wrapper">
        <text>{{item.nameBefore}}</text><text class="highlight">{{item.nameMatch}}</text><text>{{item.nameAfter}}</text>
      </view>
    </view>
    <text class="product-price">￥{{item.price}}</text>
    <view class="add-cart-btn" catchtap="addToCart" data-product="{{item}}">加入购物车</view>
  </view>
</view>
```

To:
```xml
<view class="product-item" wx:for="{{products}}" wx:key="id">
  <navigator url="/pages-sub/product/product-detail/product-detail?id={{item.id}}" class="product-navigator">
    <image class="product-img" src="{{item.imageUrl}}" mode="aspectFill" lazy-load="true"></image>
    <view class="product-info">
      <view class="product-name">
        <text wx:if="{{!item.nameHighlighted}}">{{item.name}}</text>
        <view wx:else class="highlight-wrapper">
          <text>{{item.nameBefore}}</text><text class="highlight">{{item.nameMatch}}</text><text>{{item.nameAfter}}</text>
        </view>
      </view>
      <text class="product-price">￥{{item.price}}</text>
    </view>
  </navigator>
  <view class="add-cart-btn" catchtap="addToCart" data-product="{{item}}">加入购物车</view>
</view>
```

- [ ] **Step 3: Add CSS for product-navigator if needed**

Add to `pages/index/index.wxss`:
```css
.product-navigator {
  flex: 1;
}
```

---

## Task 4: Category Page - WXML Navigator

**Files:**
- Modify: `frontend/pages/category/category.wxml`

- [ ] **Step 1: Read current category.wxml**

Review lines 1-72 to understand structure.

- [ ] **Step 2: Add open-type="switchTab" to category navigation**

Change from:
```xml
<view class="category-nav safe-area-bottom-fixed">
  <view class="nav-item" wx:for="{{categories}}" wx:key="id" bindtap="onCategoryTap" data-category="{{item.id}}">
```

To:
```xml
<view class="category-nav safe-area-bottom-fixed">
  <navigator wx:for="{{categories}}" wx:key="id" open-type="switchTab" url="/pages/category/category?categoryId={{item.id}}" class="nav-item">
    <view class="category-icon-wrapper">
      <text class="category-icon">{{item.icon}}</text>
    </view>
    <text>{{item.name}}</text>
  </navigator>
```

- [ ] **Step 3: Update category header back navigation**

Change from:
```xml
<view class="category-header-left" bindtap="onBackToCategories">
```

Keep as-is (view with bindtap is acceptable for internal page navigation).

---

## Task 5: Profile Page - WXML Navigator and Button

**Files:**
- Modify: `frontend/pages/profile/profile.wxml`

- [ ] **Step 1: Read current profile.wxml**

Review lines 1-54 to understand structure.

- [ ] **Step 2: Replace user-header bindtap with button**

Change from:
```xml
<view class="user-header" bindtap="onLogin">
  <image class="avatar" src="{{userInfo ? userInfo.avatarUrl : '/images/default-avatar.png'}}"></image>
  <view class="user-info">
    <text class="nickname">{{userInfo ? userInfo.nickname : '点击登录'}}</text>
    <view class="role_tag" wx:if="{{userInfo && userInfo.role === 'MERCHANT'}}">商家</view>
  </view>
</view>
```

To:
```xml
<button open-type="getUserInfo" bindgetuserinfo="onLogin" class="user-header">
  <image class="avatar" src="{{userInfo ? userInfo.avatarUrl : '/images/default-avatar.png'}}"></image>
  <view class="user-info">
    <text class="nickname">{{userInfo ? userInfo.nickname : '点击登录'}}</text>
    <view class="role_tag" wx:if="{{userInfo && userInfo.role === 'MERCHANT'}}">商家</view>
  </view>
</button>
```

- [ ] **Step 3: Replace order-nav items with navigator**

Change from:
```xml
<view class="order-nav">
  <view class="nav-item">
    <icon type="waiting" size="24"></icon>
    <text>待付款</text>
  </view>
  <view class="nav-item">
    <icon type="success" size="24"></icon>
    <text>待发货</text>
  </view>
  <view class="nav-item">
    <icon type="download" size="24"></icon>
    <text>待收货</text>
  </view>
</view>
```

To:
```xml
<view class="order-nav">
  <navigator url="/pages-sub/order/order-list/order-list?status=PENDING_PAYMENT" class="nav-item" hover-class="navigator-hover">
    <icon type="waiting" size="24"></icon>
    <text>待付款</text>
  </navigator>
  <navigator url="/pages-sub/order/order-list/order-list?status=PAID" class="nav-item" hover-class="navigator-hover">
    <icon type="success" size="24"></icon>
    <text>待发货</text>
  </navigator>
  <navigator url="/pages-sub/order/order-list/order-list?status=SHIPPED" class="nav-item" hover-class="navigator-hover">
    <icon type="download" size="24"></icon>
    <text>待收货</text>
  </navigator>
</view>
```

- [ ] **Step 4: Replace menu items with navigator**

Change from:
```xml
<view class="menu-item" bindtap="goToOrderList">
  <text>全部订单</text>
  <text class="arrow">></text>
</view>
<view class="menu-item">
  <text>收货地址</text>
  <text class="arrow">></text>
</view>
<view class="menu-item">
  <text>联系客服</text>
  <text class="arrow">></text>
</view>
```

To:
```xml
<navigator url="/pages-sub/order/order-list/order-list" class="menu-item" hover-class="navigator-hover">
  <text>全部订单</text>
  <text class="arrow">></text>
</navigator>
<navigator url="/pages-sub/user/address/address-list" class="menu-item" hover-class="navigator-hover">
  <text>收货地址</text>
  <text class="arrow">></text>
</navigator>
<navigator url="tel:400-123-4567" class="menu-item" hover-class="navigator-hover">
  <text>联系客服</text>
  <text class="arrow">></text>
</navigator>
```

- [ ] **Step 5: Add CSS for navigator-hover**

Add to `pages/profile/profile.wxss`:
```css
.navigator-hover {
  opacity: 0.7;
  background-color: #f5f5f5;
}
```

---

## Task 6: Order Confirm Page - WXML Navigator

**Files:**
- Modify: `frontend/pages-sub/order/order-confirm/order-confirm.wxml`

- [ ] **Step 1: Read current order-confirm.wxml**

Review lines 1-87 to understand structure.

- [ ] **Step 2: Replace address section bindtap with navigator**

Change from:
```xml
<view class="section address-section" bindtap="selectAddress">
```

To:
```xml
<navigator class="section address-section" open-type="navigate" url="/pages-sub/user/address/address-list?selectMode=true">
```

---

## Task 7: Login Page - Verify Native Button

**Files:**
- Modify: `frontend/pages-sub/user/login/login.wxml`

- [ ] **Step 1: Read current login.wxml**

Review lines 1-36 to verify implementation.

- [ ] **Step 2: Verify button usage is correct**

Current implementation:
```xml
<button class="wechat-login-btn" bindtap="wechatLogin" loading="{{isLoading}}">
```

This is correct. No changes needed.

- [ ] **Step 3: Add hover-class for tap feedback**

Change to:
```xml
<button class="wechat-login-btn" bindtap="wechatLogin" loading="{{isLoading}}" hover-class="button-hover">
```

- [ ] **Step 4: Add CSS for button-hover**

Add to `pages-sub/user/login/login.wxss`:
```css
.button-hover {
  opacity: 0.8;
}
```

---

## Task 8: Testing and Verification

**Files:**
- Test: All modified pages

- [ ] **Step 1: Run npm test**

Run: `cd frontend && npm test`
Expected: All tests pass

- [ ] **Step 2: Verify checkbox behavior**

Test checklist:
- [ ] Cart items can be selected/deselected via checkbox
- [ ] Select all checkbox selects/deselects all items
- [ ] Selected items persist after page refresh

- [ ] **Step 3: Verify quantity input**

Test checklist:
- [ ] Quantity input accepts numeric values only
- [ ] Quantity minimum is enforced to 1
- [ ] Quantity changes update cart correctly

- [ ] **Step 4: Verify navigation**

Test checklist:
- [ ] Product cards navigate to detail page
- [ ] Category navigation works
- [ ] Profile menu navigation works
- [ ] Address selection navigates correctly

- [ ] **Step 5: Verify button states**

Test checklist:
- [ ] Login button shows loading state
- [ ] Checkout button disabled when cart empty
- [ ] Remove button triggers removal

---

## Task 9: CSS Adjustments

**Files:**
- Modify: `frontend/pages/cart/cart.wxss`
- Modify: `frontend/pages/index/index.wxss`
- Modify: `frontend/pages/profile/profile.wxss`

- [ ] **Step 1: Add cart checkbox styling**

Add to `pages/cart/cart.wxss`:
```css
.item-checkbox {
  margin-right: 10px;
}

checkbox {
  transform: scale(0.9);
}
```

- [ ] **Step 2: Add cart quantity input styling**

Add to `pages/cart/cart.wxss`:
```css
.quantity-input {
  width: 60px;
  height: 30px;
  border: 1px solid #ddd;
  border-radius: 4px;
  text-align: center;
  font-size: 14px;
}
```

- [ ] **Step 3: Add index product navigator styling**

Add to `pages/index/index.wxss`:
```css
.product-navigator {
  flex: 1;
}

.product-item {
  display: flex;
  align-items: center;
}
```

---

## Self-Review Checklist

- [ ] All checkbox-group usage removes individual `checked` attribute
- [ ] All `input type="number"` does not have `min` attribute (validation in JS)
- [ ] All TabBar navigation uses `open-type="switchTab"`
- [ ] All sub-page navigation uses `navigator` without open-type (or with open-type="navigate")
- [ ] No nested interactive elements (button inside navigator)
- [ ] Profile login uses `button open-type="getUserInfo" bindgetuserinfo`
- [ ] All handlers receive data from `e.detail` or `e.currentTarget.dataset`
- [ ] Quantity validation uses `Math.max(1, value)`

---

**Plan complete.** Saved to `docs/superpowers/plans/2026-04-18-wechat-native-components-plan.md`

---

## Two Execution Options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
