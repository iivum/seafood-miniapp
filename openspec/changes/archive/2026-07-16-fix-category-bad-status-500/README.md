# fix-category-bad-status-500

P1:products 文档 status 为非法枚举值时 findByCategory 反序列化直接抛异常,单条坏数据放大为整个分类 500 不可用(2026-07-13 E2E 发现,INACTIVE 触发)
