/**
 * Safe Area Utility - 适配iPhone刘海屏和安卓异形屏
 * @description 提供安全区域检测和适配功能
 */

/**
 * 获取安全区域信息
 * @returns {Object} 包含 top, bottom, left, right 的对象，单位为px
 */
function getSafeArea() {
  const systemInfo = wx.getSystemInfoSync();
  return {
    top: systemInfo.safeArea?.top || 0,
    bottom: systemInfo.safeArea?.bottom || 0,
    left: systemInfo.safeArea?.left || 0,
    right: systemInfo.safeArea?.right || 0
  };
}

/**
 * 获取状态栏高度
 * @returns {number} 状态栏高度，单位px
 */
function getStatusBarHeight() {
  const systemInfo = wx.getSystemInfoSync();
  return systemInfo.statusBarHeight || 20;
}

/**
 * 检测是否为刘海屏/异形屏设备
 * @returns {boolean} true 表示是异形屏设备
 */
function isNotchedDevice() {
  const systemInfo = wx.getSystemInfoSync();
  const safeArea = systemInfo.safeArea;

  if (!safeArea) return false;

  // 如果顶部安全区域大于状态栏高度，说明有刘海
  const statusBarHeight = systemInfo.statusBarHeight || 20;
  return safeArea.top > statusBarHeight + 5;
}

/**
 * 获取屏幕宽高
 * @returns {Object} 包含 width 和 height 的对象，单位px
 */
function getScreenSize() {
  const systemInfo = wx.getSystemInfoSync();
  return {
    width: systemInfo.windowWidth,
    height: systemInfo.windowHeight
  };
}

/**
 * 获取底部安全区域高度（用于fixed元素避免与home indicator重叠）
 * @returns {number} 底部安全区域高度，单位px
 */
function getBottomSafeAreaHeight() {
  const systemInfo = wx.getSystemInfoSync();
  const safeArea = systemInfo.safeArea;
  const screenHeight = systemInfo.windowHeight;

  if (!safeArea) return 0;

  // 底部安全区域 = 屏幕高度 - safeArea.bottom
  return Math.max(0, screenHeight - safeArea.bottom);
}

/**
 * 将px转换为rpx
 * @param {number} px 像素值
 * @returns {number} rpx值
 */
function px2rpx(px) {
  const systemInfo = wx.getSystemInfoSync();
  return px * (750 / systemInfo.windowWidth);
}

/**
 * 将rpx转换为px
 * @param {number} rpx rpx值
 * @returns {number} 像素值
 */
function rpx2px(rpx) {
  const systemInfo = wx.getSystemInfoSync();
  return rpx * (systemInfo.windowWidth / 750);
}

module.exports = {
  getSafeArea,
  getStatusBarHeight,
  isNotchedDevice,
  getScreenSize,
  getBottomSafeAreaHeight,
  px2rpx,
  rpx2px
};
