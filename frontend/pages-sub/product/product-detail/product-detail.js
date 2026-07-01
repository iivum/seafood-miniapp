/**
 * Product detail page — wired to the new `features/product` API
 * per OpenSpec §8.5. Uses `ProductAPI.getById` for the detail and
 * `cartStore.addItem` for the "add to cart" action.
 *
 * mp-03 OD 原型对齐(openspec change mp-od-prototype-alignment,brief
 * `.superpowers/sdd/mp-od-3-product-detail-brief.md`)。诊断阶段顺带发现并
 * 修复两个与视觉对齐无关的真 bug(design.md:诊断阶段发现导致页面不可用的
 * bug 随该屏一并修):
 *  1. 数量 stepper 死绑定 —— wxml 一直引用 bindtap="onIncrement"/"onDecrement",
 *     但本文件从未定义这两个方法(也没有 behavior 注入),+/− 完全不响应。
 *     补上两个方法 + data.quantity 默认值 1,上限 = product.stock,与 wxml
 *     is-disabled 判断条件(`quantity <= 1` / `quantity >= product.stock`)对齐。
 *  2. onBuyNow 重复定义 —— 此前本文件定义了两次 onBuyNow,JS 对象字面量只保留
 *     最后一份(加购后 switchTab 购物车),前一份(加购后跳 mp-06 订单确认页,
 *     带 source=direct_buy)是从未执行的死代码。这违反了
 *     openspec/specs/mini-program/spec.md:91-107"Direct buy from product
 *     detail"需求 ——"立即购买"应跳订单确认页,不是购物车。删掉重复定义,
 *     保留跳 mp-06 的语义(回归修复,不是新功能)。
 *     已知缺口(未做,超出本次前端授权范围,见 report):该需求同时要求
 *     "mp-06 不触碰购物车 + items 卡片只显示这一个商品",但后端
 *     `POST /api/orders`(OrderController#create)没有 @RequestBody,只能无条件
 *     从用户服务端购物车建单,不支持传显式 items —— 真正合规需要后端加能力。
 *     这里维持"addItem 合并进现有购物车"语义,不做"先清空购物车"这种有破坏性
 *     副作用的隐式操作。
 */
const { ProductAPI } = require('../../../src/features/product/api');
const { cartStore } = require('../../../src/features/cart/store');
const { recommendationModule } = require('../../../src/modules/recommendation/recommendation.js');

Page({
  data: {
    product: null,
    recommendations: [],
    isLoading: true,
    isError: false,
    errorMessage: '',
    /** 收藏状态(本地,无后端)— 占位 */
    favorited: false,
    isAdding: false,
    /** mp-03 数量 stepper(死绑定修复):默认 1,上限 product.stock。 */
    quantity: 1,
  },

  onLoad: function (options) {
    if (options && options.id) {
      this.fetchProductDetail(options.id);
    }
  },

  fetchProductDetail: function (id) {
    this.setData({ isLoading: true, isError: false });
    ProductAPI.getById(id)
      .then((product) => {
        this.setData({ product, isLoading: false });
        this.fetchRecommendations(product);
      })
      .catch((err) => {
        this.setData({
          isLoading: false,
          isError: true,
          errorMessage: (err && err.message) || '加载商品失败',
        });
        if (!err || err.statusCode !== 401) {
          wx.showToast({ title: '加载商品失败', icon: 'none' });
        }
      });
  },

  fetchRecommendations: function (product) {
    recommendationModule
      .getProductRecommendations(product)
      .then((recommendations) => {
        const valid = (recommendations || []).filter((rec) => rec.products.length > 0);
        this.setData({ recommendations: valid });
      })
      .catch(() => {
        // best-effort
      });
  },

  /**
   * mp-03 数量 stepper "−"(死绑定修复)。下限 1,与 wxml
   * `quantity <= 1 ? 'is-disabled' : ''` 判断条件一致。
   */
  onDecrement: function () {
    if (this.data.quantity <= 1) return;
    this.setData({ quantity: this.data.quantity - 1 });
  },

  /**
   * mp-03 数量 stepper "+"(死绑定修复)。上限 product.stock(商品未加载时视为
   * 0,不允许增加),与 wxml `quantity >= product.stock ? 'is-disabled' : ''`
   * 判断条件一致。
   */
  onIncrement: function () {
    const product = this.data.product;
    const stock = product ? product.stock : 0;
    if (this.data.quantity >= stock) return;
    this.setData({ quantity: this.data.quantity + 1 });
  },

  onAddToCart: function () {
    const product = this.data.product;
    if (!product || product.stock === 0) return;
    if (this.data.isAdding) return;
    this.setData({ isAdding: true });
    cartStore
      .addItem(product.id, this.data.quantity || 1)
      .then(() => {
        wx.showToast({ title: '已加入购物车', icon: 'success' });
        recommendationModule.recordPurchase(product);
      })
      .catch((err) => {
        wx.showToast({
          title: (err && err.message) || '加入购物车失败',
          icon: 'none',
        });
      })
      .then(() => this.setData({ isAdding: false }));
  },

  onCustomerService: function () {
    wx.showModal({ title: '客服', content: '客服微信:seafood-cs(占位)', showCancel: false });
  },

  onToggleFavorite: function () {
    this.setData({ favorited: !this.data.favorited });
    wx.showToast({
      title: this.data.favorited ? '已收藏' : '已取消收藏',
      icon: 'none',
    });
  },

  /**
   * 立即购买(sprint-1-closure 5.4;mp-03 OD 对齐时修复回归 —— 本文件此前定义
   * 了两次 onBuyNow,后一份[switchTab 购物车]覆盖了这份[跳 mp-06],违反
   * openspec/specs/mini-program/spec.md:91-107"Direct buy from product
   * detail"(立即购买应跳订单确认页,不是购物车)。
   * 已知缺口见文件头注释:购物车合并语义保留,未做后端 items 隔离
   * (mp-06 仍会读用户完整购物车,不是"只有这一个商品"的孤立订单)。
   */
  onBuyNow: function () {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.navigateTo({ url: '/pages-sub/user/login/login' });
      return;
    }
    const product = this.data.product;
    if (!product) return;
    if (product.stock === 0) {
      wx.showToast({ title: '已售罄', icon: 'none' });
      return;
    }
    cartStore
      .addItem(product.id, this.data.quantity || 1)
      .then(() => {
        recommendationModule.recordPurchase(product);
        wx.navigateTo({
          url: '/pages-sub/order/order-confirm/order-confirm?source=direct_buy',
        });
      })
      .catch(() => {
        wx.showToast({ title: '请稍后重试', icon: 'none' });
      });
  },

  goToHome: function () {
    wx.switchTab({ url: '/pages/index/index' });
  },

  goToCart: function () {
    wx.switchTab({ url: '/pages/cart/cart' });
  },

  goToProductDetail: function (e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: '/pages-sub/product/product-detail/product-detail?id=' + id,
    });
  },

  /**
   * mp-03 悬浮顶栏返回(brief §1)。真实 wx.navigateBack(),不是装饰。
   */
  onBack: function () {
    wx.navigateBack();
  },

  /**
   * mp-03 悬浮顶栏收藏(brief §1)。纯装饰,参考 mp-01 onBellTap 模式 —— 与
   * 底部操作栏已有的 onToggleFavorite(本地可切换 ♥/♡ 状态)是两个独立入口,
   * 互不影响;这个不接后端(真实收藏能力不存在)。
   */
  onFavoriteTap: function () {
    wx.showToast({ title: '功能开发中', icon: 'none' });
  },

  /**
   * mp-03 悬浮顶栏分享(brief §1)。小程序原生页面生命周期方法,由
   * `<button open-type="share">` 触发原生分享面板调用 —— 标题/图片取当前
   * 商品真实字段,不是编造;path 带 id 使被分享方直达同一商品。
   */
  onShareAppMessage: function () {
    const product = this.data.product;
    if (!product) return {};
    return {
      title: product.name,
      imageUrl: product.imageUrl,
      path: '/pages-sub/product/product-detail/product-detail?id=' + product.id,
    };
  },
});
