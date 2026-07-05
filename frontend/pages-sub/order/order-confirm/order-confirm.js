/**
 * Order confirm / detail page — 路线图 3.14 / 3.17 / 3.18 OD v2 重构。
 *
 * 3.17:4 金额项联动实时算(商品总额 = Σ item.price × item.quantity;
 *       运费 = 配送方式映射;优惠 = 满 100 减 10 占位;实付 = 总额 + 运费 - 优惠)
 * 3.18:配送方式 3 选(免运费 / 顺丰 12 / 中通 8)+ 备注 max 50 字
 *
 * mp-06 OD 原型对齐(openspec change mp-od-prototype-alignment,brief
 * `.superpowers/sdd/mp-od-5-order-confirm-brief.md`):
 *  ① 金额浮点数精度真 bug(brief 优先级最高,当前生产环境已存在,不是这次
 *     诊断引入的):recalcAmounts 全程裸浮点数运算,没有在任何环节四舍五入,
 *     底部结算金额会显示"¥404.94000000000005"这种精度尾巴直接漏到用户界面
 *     (145.11×2 + 124.72 满 100 减 10 即复现,见 __tests__)。改成算完
 *     subtotal/discount/orderTotal 后统一用 roundYuan() 规整到 2 位小数再
 *     setData,不把裸浮点数塞进 data 让 wxml 插值时才暴露精度问题。
 *  ② 顶部标题栏(brief §1,新增)+ 默认地址自动选中(brief §2,同 mp-04
 *     cart.js autoSelectDefaultAddress 同类问题第二次出现,按 brief 要求
 *     各自维护一份,不跨文件抽公共函数)+ "共 N 件"底部真实件数统计(brief §3,
 *     itemCount = 商品种类数 = cartItems.length,不是数量总和)。
 *  ③ 预计送达卡片/"顺丰冷链可达"标签/商家分组"海港直营"/SKU chip 明确排除,
 *     不做(brief 范围边界:无后端数据支撑或概念不成立)。
 */
const { orderStore } = require('../../../src/features/order/store');
const { cartStore } = require('../../../src/features/cart/store');
const { ProductAPI } = require('../../../src/features/product/api');
const { paymentModule } = require('../../../src/modules/payment/payment.js');
const { request } = require('../../../utils/request.js');

// 3.17 配送方式 → 运费映射
const SHIPPING_FEE_MAP = {
  FREE: 0,
  SF: 12,
  ZTO: 8,
};

// 3.17 优惠规则(占位,Sprint 3 接真实优惠):满 100 减 10
function calcDiscount(subtotal) {
  return subtotal >= 100 ? 10 : 0;
}

// mp-06 金额精度修复:统一在写入 data 前 round 到 2 位小数(brief §"优先修")。
function roundYuan(amount) {
  return Math.round(amount * 100) / 100;
}

Page({
  data: {
    order: null,
    selectedAddress: null,
    cartItems: [],
    // mp-backend-contract-gaps D3b:mp-03 立即购买带来的原始 items({productId,
    // quantity}),非空时 onSubmitOrder 走 placeDirectBuyOrder 而非 placeOrder。
    directBuyItems: null,
    // 3.18 配送方式 3 选
    shippingMethod: 'FREE',
    shippingFee: 0,
    // 3.18 备注 max 50 字
    remark: '',
    // 3.17 4 金额项
    subtotal: 0,
    discount: 0,
    orderTotal: 0,
    // mp-06 brief §3:底部"共 N 件"真实商品种类数统计
    itemCount: 0,
    isCreating: false,
    isPaying: false,
    errorMessage: '',
  },

  onLoad: function (options) {
    if (options && options.id) {
      this.loadExistingOrder(options.id);
    } else if (options && options.source === 'direct_buy') {
      // mp-backend-contract-gaps D3b:mp-03"立即购买"带显式 items 跳转过来,
      // 渲染这些 items 而非用户购物车 —— 全程不调用 cartStore.refresh()/
      // cartStore 的任何方法,购物车不被触碰。默认地址自动选中与购物车无关,
      // 直接购买结算同样需要,照常调用。
      this.loadDirectBuyPreview(options.items);
      this.autoSelectDefaultAddress();
    } else {
      this.refreshCartPreview();
      // brief §2 真 bug:仅新下单(购物车结算)流程需要自动选默认地址;
      // "查看已下单订单"(options.id 存在)应展示订单实际收货地址,不应用
      // 任意默认地址覆盖,故不在那个分支调用。
      this.autoSelectDefaultAddress();
    }
  },

  /**
   * mp-06 真 bug 修复(brief §2,同 mp-04 cart.js autoSelectDefaultAddress
   * 同类问题第二次出现)。此前 selectedAddress 初始值一直是 null,且首次
   * 进入本页时没有任何地方自动查询并选中用户的默认地址 —— 唯一赋值路径只有
   * 用户手动跳转地址选择页(该回传路径见 onShow,已正常工作,未改动)。
   * 复用 address-list.js / cart.js 同款 self-scoped `/addresses` 端点,
   * 找 isDefault === true 的一条自动 setData。用户没有任何地址 / 没有默认
   * 地址时保持 null,维持既有空态,不是错误。按 brief 要求各自维护一份
   * 实现,不跨文件抽公共函数(YAGNI,两处触发时机/生命周期钩子不同)。
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

  onShow: function () {
    if (this.data.order) return;
    const pages = getCurrentPages();
    const currentPage = pages[pages.length - 1];
    if (currentPage && currentPage.selectedAddressFromList) {
      this.setData({ selectedAddress: currentPage.selectedAddressFromList });
      currentPage.selectedAddressFromList = null;
    }
  },

  loadExistingOrder: function (id) {
    orderStore
      .loadById(id)
      .then((order) => this.setData({ order }))
      .catch((err) => {
        this.setData({
          errorMessage: (err && err.message) || '加载订单失败',
        });
      });
  },

  refreshCartPreview: function () {
    // Use the new cart store to render the order preview.
    cartStore
      .refresh()
      .then((cart) => {
        const items = (cart.items || []).map((it) => ({
          id: it.productId,
          name: it.productName || it.productId,
          price: it.unitPrice || 0,
          quantity: it.quantity,
          imageUrl: it.imageUrl || '',
        }));
        this.setData({
          order: {
            id: null,
            totalAmount: 0,
            items: items.map((it) => ({
              productId: it.id,
              productName: it.name,
              unitPrice: it.price,
              quantity: it.quantity,
            })),
            status: 'PENDING',
          },
          cartItems: items,
        });
        this.recalcAmounts();
      })
      .catch(() => {
        // best-effort: leave order as null so the empty-state renders
      });
  },

  /**
   * mp-backend-contract-gaps D3b:mp-03"立即购买"预览 —— 与 refreshCartPreview
   * 平行的另一条数据源。URL 带来的 rawItems 只有 {productId, quantity},
   * 逐个 ProductAPI.getById() 补 name/price/imageUrl 后拼成与
   * refreshCartPreview 完全相同的 {id, name, price, quantity, imageUrl}
   * 形状,喂进同一套 order/cartItems/recalcAmounts 管线 —— 全程不调用
   * cartStore 的任何方法,购物车不被读也不被写。
   * 原始 items 另存 directBuyItems,供 onSubmitOrder 判断走哪条建单分支。
   */
  loadDirectBuyPreview: function (rawItems) {
    let items;
    try {
      items = JSON.parse(decodeURIComponent(rawItems));
    } catch (err) {
      items = [];
    }
    if (!Array.isArray(items) || items.length === 0) {
      return;
    }
    Promise.all(
      items.map((item) =>
        ProductAPI.getById(item.productId).then((product) => ({
          id: item.productId,
          name: (product && product.name) || item.productId,
          price: (product && product.price) || 0,
          quantity: item.quantity,
          imageUrl: (product && product.imageUrl) || '',
        })),
      ),
    )
      .then((cartItems) => {
        this.setData({
          order: {
            id: null,
            totalAmount: 0,
            items: cartItems.map((it) => ({
              productId: it.id,
              productName: it.name,
              unitPrice: it.price,
              quantity: it.quantity,
            })),
            status: 'PENDING',
          },
          cartItems,
          directBuyItems: items,
        });
        this.recalcAmounts();
      })
      .catch(() => {
        // best-effort: leave order as null so the empty-state renders(与 refreshCartPreview 一致)
      });
  },

  /**
   * 3.17 实时算 4 金额项(总额 / 运费 / 优惠 / 实付)+ mp-06 brief §3 商品件数。
   * 调用时机:refreshCartPreview + onSelectShipping + onRemarkInput(实际不触发金额变,保留)。
   *
   * mp-06 金额精度修复(brief 优先级最高的真 bug):subtotal/discount/orderTotal
   * 全程裸浮点数运算(如 145.11×2 + 124.72 = 414.94000000000005),此前直接
   * setData 进去,wxml 插值时把精度尾巴漏给用户("¥404.94000000000005")。
   * 现在算完每一项后立刻用 roundYuan() 规整到 2 位小数再写入 data。
   */
  recalcAmounts: function () {
    const items = this.data.cartItems || [];
    const subtotal = roundYuan(items.reduce((sum, it) => sum + (it.price || 0) * (it.quantity || 0), 0));
    const shippingFee = SHIPPING_FEE_MAP[this.data.shippingMethod] ?? 0;
    const discount = roundYuan(calcDiscount(subtotal));
    const orderTotal = roundYuan(Math.max(0, subtotal + shippingFee - discount));
    const itemCount = items.length;
    this.setData({ subtotal, shippingFee, discount, orderTotal, itemCount });
  },

  selectAddress: function () {
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

  /**
   * 3.18 配送方式切换(setData 后实时算 4 金额项)。
   */
  onSelectShipping: function (e) {
    const method = e.currentTarget.dataset.method || 'FREE';
    this.setData({ shippingMethod: method, shippingFee: SHIPPING_FEE_MAP[method] ?? 0 });
    this.recalcAmounts();
  },

  /**
   * 3.18 备注输入(bindinput 触发,maxlength 50 已在 wxml 守)。
   * 不触发金额变,仅更新 remark。
   */
  onRemarkInput: function (e) {
    const value = e.detail.value || '';
    this.setData({ remark: value });
  },

  onSubmitOrder: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({
        url:
          '/pages-sub/user/login/login?redirect=' +
          encodeURIComponent('/pages-sub/order/order-confirm/order-confirm'),
      });
      return;
    }
    if (!this.data.order || !(this.data.order.items || []).length) {
      wx.showToast({ title: '购物车为空', icon: 'none' });
      return;
    }
    if (!this.data.selectedAddress || !this.data.selectedAddress.id) {
      wx.showToast({ title: '请选择收货地址', icon: 'none' });
      return;
    }

    this.setData({ isCreating: true });
    wx.showLoading({ title: '正在创建订单...' });

    // mp-backend-contract-gaps D3b:direct-buy 走显式 items 建单(不清购物车,
    // 因为从未碰过购物车);其余(购物车结算)分支完全不变。
    const directBuyItems = this.data.directBuyItems;
    const hasDirectBuyItems = Array.isArray(directBuyItems) && directBuyItems.length > 0;
    const placeOrderPromise = hasDirectBuyItems
      ? orderStore.placeDirectBuyOrder({
          items: directBuyItems,
          addressId: this.data.selectedAddress.id,
          remark: this.data.remark || undefined,
        })
      : orderStore.placeOrder({
          addressId: this.data.selectedAddress.id,
          remark: this.data.remark || undefined,
        });

    placeOrderPromise
      .then((order) => {
        this.setData({ isCreating: false, order });
        this.initiatePayment(order);
      })
      .catch((err) => {
        wx.hideLoading();
        this.setData({ isCreating: false });
        wx.showToast({
          title: (err && err.message) || '创建订单失败',
          icon: 'none',
        });
      });
  },

  initiatePayment: function (order) {
    this.setData({ isPaying: true });
    wx.showLoading({ title: '正在发起支付...' });

    paymentModule
      .requestPayment(order.id, order.totalAmount)
      .then((result) => {
        wx.hideLoading();
        this.setData({ isPaying: false });

        if (result.isSuccess()) {
          wx.showToast({ title: '支付成功', icon: 'success' });
        } else if (result.isCancelled()) {
          wx.showToast({ title: '已取消支付', icon: 'none' });
        } else {
          wx.showToast({ title: result.errorMessage || '支付失败', icon: 'none' });
        }
        setTimeout(() => {
          wx.redirectTo({ url: '/pages-sub/order/order-list/order-list' });
        }, 1500);
      })
      .catch(() => {
        wx.hideLoading();
        this.setData({ isPaying: false });
        wx.showToast({ title: '支付失败', icon: 'none' });
        setTimeout(() => {
          wx.redirectTo({ url: '/pages-sub/order/order-list/order-list' });
        }, 1500);
      });
  },

  goBack: function () {
    wx.navigateBack();
  },
});
