/**
 * Cart page — wired to the new `features/cart` store + API per
 * OpenSpec §8.5. The store pulls from the server-side cart endpoints
 * (GET /api/cart, POST /api/cart/items, etc.) and exposes imperative
 * actions for add/remove/update/toggle/clear. The page binds the
 * store state to its `data` and forwards user actions to the store.
 *
 * mp-04 OD 原型对齐第二轮(openspec change mp-od-prototype-alignment,brief
 * `.superpowers/sdd/mp-od-4-cart-styling-brief.md`)。诊断阶段顺带发现并修复
 * 一个与视觉对齐无关的真 bug(design.md:诊断阶段发现导致页面不可用的 bug
 * 随该屏一并修,经协调者确认与 mp-01 onClearFilter / mp-02
 * activeCategoryId·data-category-id / mp-03 onIncrement/onDecrement 同类):
 *   商品行 checkbox + 全选栏死绑定 —— cart.wxml 一直引用
 *   `isItemSelected(item.id)`(WXML 表达式不支持任意函数调用,这个写法本身
 *   就不合法)、`bindtap="onSelectAllTap"`、`catchtap="onItemCheckTap"`,但本
 *   文件从未定义 onItemCheckTap/onSelectAllTap,点击 checkbox / 全选完全无
 *   响应,"已选 N 件"永远基于空数组(git blame:2026-06-16 v2-visual-redesign
 *   把 wxml 从原生 checkbox-group 换成自绘 checkbox 时引入,当时 cart.js 没
 *   同步更新,此后一直没人发现)。改成 wxml 直接绑 `item.selected`(由
 *   computeTotals() 统一派生写回,不再用非法函数调用表达式),补
 *   onItemCheckTap/onSelectAllTap 正确驱动 selectedItems + 联动
 *   computeTotals()。旧 onCheckboxChange/onSelectAll(原生 checkbox-group
 *   bindchange 事件形状,早已没有 wxml 引用)一并删除,不留死代码。
 *
 * 已知缺口(未做,超出本轮授权范围,见 report):
 *  - selectedAddressFromList 回传路径(用户手动从地址选择页选择后
 *    navigateBack)在本文件也从未被读取,是另一个独立死绑定,不属于这轮
 *    授权范围,未修 —— 不影响本轮修复(见下方 reconcileSelection 注释)。
 *  - CartAPI.toggleItem()(PATCH /cart/items/:id)和 CartAPI.updateItem()
 *    (PUT /cart/items/:id)在后端 CartController 里都没有对应路由(只有
 *    GET/POST items/DELETE items/DELETE),onToggleSelected 目前保留但不可用
 *    ——checkbox 交互改走纯前端 selectedItems 数组,不依赖这个端点。
 */
const { cartStore } = require('../../src/features/cart/store');
const cartUtil = require('../../utils/cart.js');
const { request } = require('../../utils/request.js');
const { formatYuan } = require('../../utils/money.js');

Page({
  data: {
    cartItems: [],
    totalPrice: '0.00',
    selectedPrice: '0.00',
    selectedItems: [],
    isAllSelected: false,
    selectedAddress: null,
    shippingFee: 0,
    isLoading: false,
    isError: false,
    errorMessage: '',
  },

  onShow: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart'),
      });
      return;
    }
    this.refreshCart();
    this.autoSelectDefaultAddress();
  },

  /**
   * mp-04 真 bug 修复(brief §2):此前 selectedAddress 初始值一直是 null,
   * 且全文件没有任何地方在页面加载时查地址列表自动选中默认地址 —— 唯一
   * 赋值路径只有用户手动跳转地址选择页(且该回传路径本身也未接线,见文件头
   * 已知缺口)。导致即使用户设置了默认收货地址,购物车页每次都显示"请选择
   * 收货地址"空态。这里在 onShow 时若 selectedAddress 仍是 null,复用
   * address-list.js 同款 self-scoped `/addresses` 端点(不重新发明 ——
   * src/features/user/api.ts 的 UserAPI.listAddresses() 指向
   * `/users/me/addresses`,后端 AddressController 实际路由是 `/addresses`,
   * 那份 API 封装是死代码,故未采用),找 isDefault === true 的一条自动
   * setData。用户没有任何地址 / 没有默认地址时保持 null,维持既有空态,不是
   * 错误。
   */
  autoSelectDefaultAddress: function () {
    if (this.data.selectedAddress) return;
    request({ url: '/addresses', needAuth: true })
      .then((addresses) => {
        if (this.data.selectedAddress) return; // 期间用户已手动选择,不覆盖
        const list = Array.isArray(addresses) ? addresses : [];
        const defaultAddress = list.find((a) => a.isDefault === true);
        if (defaultAddress) {
          this.setData({ selectedAddress: defaultAddress });
        }
      })
      .catch((err) => {
        console.error('查询默认地址失败:', err);
      });
  },

  refreshCart: function () {
    this.setData({ isLoading: true, isError: false });
    return cartStore
      .refresh()
      .then((cart) => this.renderCart(cart))
      .catch((err) => {
        // Fall back to local cache so the UI doesn't go blank when
        // the user is offline.
        const local = cartUtil.getCart();
        this.setData({
          cartItems: local,
          isError: true,
          errorMessage: err && err.message ? err.message : '加载购物车失败',
        });
        this.computeTotals(local);
        this.setData({ isLoading: false });
      });
  },

  renderCart: function (cart) {
    // Translate server-side cart items into the shape the WXML
    // expects: {id, name, price, imageUrl, quantity}. `selected`(供 wxml
    // 直接绑定)由 computeTotals() 统一从 selectedItems 派生写回,这里不再
    // 单独塞一份,避免两处真值打架。
    const items = (cart.items || []).map((it) => ({
      id: it.productId,
      productId: it.productId,
      name: it.productName || it.name || it.productId,
      price: it.unitPrice || it.price || 0,
      imageUrl: it.imageUrl || '',
      quantity: it.quantity,
    }));
    this.setData({ selectedItems: this.reconcileSelection(cart.items || [], items) });
    this.computeTotals(items);
    this.setData({
      isLoading: false,
      isError: false,
      errorMessage: '',
    });
  },

  /**
   * mp-04 checkbox 死绑定修复的一部分:决定"新出现的商品行默认是否勾选"。
   * 后端 Cart.addItem() 创建 CartItem 时 selected 硬编码 true(item
   * 3 核对结论,backend/.../domain/Cart.java:44/50),对齐 OD 图默认全选。
   * 但没有可用的后端持久化路径能把某一行标记回"未选"(toggle/update 端点
   * 都不存在,见文件头已知缺口),所以本地 selectedItems 数组是这轮唯一的
   * 选中态真值 —— 只在"第一次见到这个 productId"时用后端 selected 兜底,
   * 之后用户在这个页面实例生命周期内手动勾选/取消的结果保留,不被数量
   * +/- 触发的 re-render 悄悄覆盖回去。
   */
  reconcileSelection: function (rawItems, items) {
    const known = this._knownItemIds || (this._knownItemIds = new Set());
    const currentIds = new Set(items.map((item) => item.id));
    const next = (this.data.selectedItems || []).filter((id) => currentIds.has(id));
    const nextSet = new Set(next);
    rawItems.forEach((raw) => {
      const id = raw.productId;
      if (!known.has(id)) {
        known.add(id);
        if (raw.selected && !nextSet.has(id)) {
          next.push(id);
          nextSet.add(id);
        }
      }
    });
    return next;
  },

  computeTotals: function (items) {
    let total = 0;
    let selected = 0;
    const selectedIds = this.data.selectedItems || [];
    const selectedSet = new Set(selectedIds);
    items.forEach((item) => {
      total += item.price * item.quantity;
      if (selectedSet.has(item.id)) {
        selected += item.price * item.quantity;
      }
    });
    const shippingFee = selected >= 99 ? 0 : 10;
    const isAllSelected = items.length > 0 && items.every((item) => selectedSet.has(item.id));
    // wxml 商品行 checkbox 直接绑 item.selected(见文件头死绑定修复说明),
    // 这里统一派生写回,是 selected 展示态的唯一来源。
    const annotatedItems = items.map((item) => ({ ...item, selected: selectedSet.has(item.id) }));
    this.setData({
      cartItems: annotatedItems,
      totalPrice: formatYuan(selected + shippingFee),
      selectedPrice: formatYuan(selected),
      shippingFee: shippingFee,
      isAllSelected: isAllSelected,
    });
  },

  /**
   * 商品行 checkbox 点击(mp-04 死绑定修复)。wxml `catchtap="onItemCheckTap"
   * data-id="{{item.id}}"` 此前一直引用这个方法,但本文件从未定义,点击
   * 完全无响应。
   */
  onItemCheckTap: function (e) {
    const id = e.currentTarget.dataset.id;
    const selectedItems = this.data.selectedItems.includes(id)
      ? this.data.selectedItems.filter((itemId) => itemId !== id)
      : this.data.selectedItems.concat(id);
    this.setData({ selectedItems });
    this.computeTotals(this.data.cartItems);
  },

  /**
   * 全选栏点击(mp-04 死绑定修复)。wxml `bindtap="onSelectAllTap"` 此前一直
   * 引用这个方法,但本文件从未定义,"全选"完全无响应。
   */
  onSelectAllTap: function () {
    // 现算 allSelected(不直接读 data.isAllSelected)—— 那是 computeTotals()
    // 派生出来展示用的缓存字段,不保证跟这次点击前的 selectedItems 严格同步
    // (例如外部直接改了 selectedItems 还没触发过 computeTotals)。
    const selectedSet = new Set(this.data.selectedItems);
    const allSelected =
      this.data.cartItems.length > 0 && this.data.cartItems.every((item) => selectedSet.has(item.id));
    const selectedItems = allSelected ? [] : this.data.cartItems.map((item) => item.id);
    this.setData({ selectedItems });
    this.computeTotals(this.data.cartItems);
  },

  onQuantityChange: function (e) {
    const id = e.currentTarget.dataset.id;
    const quantity = Math.max(1, parseInt(e.detail.value) || 1);
    this.callStore('updateItem', id, quantity);
  },

  onMinus: function (e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find((i) => i.id === id);
    if (item && item.quantity > 1) this.callStore('updateItem', id, item.quantity - 1);
  },

  onPlus: function (e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.cartItems.find((i) => i.id === id);
    if (item) this.callStore('updateItem', id, item.quantity + 1);
  },

  onRemove: function (e) {
    const id = e.currentTarget.dataset.id;
    this.callStore('removeItem', id);
  },

  /**
   * 未接入 wxml(见文件头已知缺口:CartAPI.toggleItem() 对应的 PATCH
   * /cart/items/:id 在后端 CartController 没有路由,调用会 404)。保留是为了
   * 不丢失"选中态应该走后端持久化"这个已声明的 REST 契约意图,留给后续加
   * 后端端点的 round 接线;checkbox 点击这轮改走 onItemCheckTap 的纯前端
   * selectedItems 数组,不依赖这个方法。
   */
  onToggleSelected: function (e) {
    const id = e.currentTarget.dataset.id;
    this.callStore('toggleItem', id);
  },

  callStore: function (action, productId, quantity) {
    const fn = cartStore[action];
    if (typeof fn !== 'function') return;
    const promise = quantity === undefined ? fn.call(cartStore, productId) : fn.call(cartStore, productId, quantity);
    return promise
      .then((cart) => this.renderCart(cart))
      .catch((err) => {
        wx.showToast({ title: err && err.message ? err.message : '操作失败', icon: 'none' });
      });
  },

  onCheckout: function () {
    if (this.data.cartItems.length === 0) return;
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url: '/pages-sub/user/login/login?redirect=' + encodeURIComponent('/pages/cart/cart'),
      });
      return;
    }
    wx.navigateTo({ url: '/pages-sub/order/order-confirm/order-confirm' });
  },

  onSelectAddress: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const selectedAddress = this.data.selectedAddress || null;
    wx.navigateTo({
      url:
        '/pages-sub/user/address/address-list?selectMode=true&selectedAddress=' +
        encodeURIComponent(selectedAddress ? JSON.stringify(selectedAddress) : ''),
    });
  },
});
