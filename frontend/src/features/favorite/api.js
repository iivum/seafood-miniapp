/**
 * Runtime shim for features/favorite/api.ts.
 *
 * 收藏 + 浏览足迹:self-scoped 门面,同 /api/addresses、/api/cart 既有惯例。
 */
const { get, post, del } = require('../../shared/api/request');

const FavoriteAPI = {
  add(productId) {
    return post(`/favorites/${encodeURIComponent(productId)}`, undefined, { needAuth: true });
  },
  remove(productId) {
    return del(`/favorites/${encodeURIComponent(productId)}`, { needAuth: true });
  },
  list() {
    return get('/favorites', { needAuth: true });
  },
};

module.exports = { FavoriteAPI };
