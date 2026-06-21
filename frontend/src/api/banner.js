/**
 * Banner API(后端驱动 home hero 轮播)。
 *
 * GET /api/banners 返回 List<BannerResponse>(已按 sortOrder 升序、只含 ACTIVE)。
 * 字段:{ id, tone, emoji, title, subtitle, targetProductId, sortOrder, status, ... }
 * 与 pages/index/index.wxml 的 swiper(item.tone/emoji/title/subtitle + data-banner-id)对齐。
 */

const { request } = require('../../utils/request.js');

class BannerAPI {
  static get BASE_ENDPOINT() {
    return '/banners';
  }

  /** 拉取启用 banner 列表;异常或非数组返回 [](swiper wx:for 兜底不渲染)。 */
  static async getBanners() {
    try {
      const response = await request({
        url: BannerAPI.BASE_ENDPOINT,
        method: 'GET',
      });
      return Array.isArray(response) ? response : [];
    } catch (error) {
      console.warn('[BannerAPI] 拉取 banner 失败,降级空列表:', error && error.message);
      return [];
    }
  }
}

module.exports = { BannerAPI };
