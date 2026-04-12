# SPEC.md - 功能规格文档

本文档记录海鲜商城小程序的功能规格、API 设计和数据结构。

---

## 📋 功能概览

### 已完成功能

#### 1. 购物车模块 ✅
- [x] 获取购物车 `GET /cart`
- [x] 添加商品到购物车 `POST /cart`
- [x] 更新购物车商品数量 `PUT /cart/items/:itemId`
- [x] 删除购物车商品 `DELETE /cart/items/:itemId`
- [x] 切换商品选中状态 `PATCH /cart/items/:itemId/toggle-selection`
- [x] 清空购物车 `DELETE /cart`
- [x] 输入验证和 XSS 防护
- [x] 完整的错误处理

#### 2. 商品列表模块 ✅
- [x] 获取商品列表 `GET /products`
- [x] 分页支持
- [x] 分类筛选
- [x] 关键词搜索
- [x] XSS 防护
- [x] 商品详情页
- [x] 下拉刷新
- [x] 上拉加载更多

---

## 🔌 API 详细规格

### 1. 商品 API

#### GET /products
获取商品列表（分页）

**请求参数**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码（默认 0）|
| pageSize | int | 否 | 每页数量（默认 10）|
| category | string | 否 | 分类筛选 |
| keyword | string | 否 | 搜索关键词 |

**响应示例**
```json
{
  "products": [
    {
      "id": "prod_001",
      "name": "新鲜三文鱼",
      "description": "挪威进口空运",
      "price": 128.00,
      "stock": 50,
      "category": "鱼类",
      "imageUrl": "https://example.com/salmon.jpg",
      "onSale": true
    }
  ],
  "page": 0,
  "totalPages": 5,
  "totalProducts": 48,
  "hasNext": true,
  "hasPrev": false
}
```

**错误响应**
```json
{
  "message": "Invalid pagination parameters: page must be a non-negative integer",
  "statusCode": 400,
  "timestamp": "2026-04-11T12:00:00Z"
}
```

---

### 2. 购物车 API

#### GET /cart
获取当前用户的购物车

**响应示例**
```json
{
  "id": "cart_123",
  "items": [
    {
      "id": "item_001",
      "productId": "prod_001",
      "name": "新鲜三文鱼",
      "price": 128.00,
      "quantity": 2,
      "imageUrl": "https://example.com/salmon.jpg"
    }
  ],
  "totalPrice": 256.00,
  "totalItems": 2,
  "selectedItems": ["item_001"]
}
```

#### POST /cart
添加商品到购物车

**请求体**
```json
{
  "productId": "prod_001",
  "quantity": 2
}
```

**验证规则**
| 字段 | 规则 |
|------|------|
| productId | 非空字符串 |
| quantity | 正整数 |

#### PUT /cart/items/:itemId
更新购物车商品数量

**请求体**
```json
{
  "quantity": 3
}
```

**验证规则**
| 字段 | 规则 |
|------|------|
| quantity | ≥ 0 的整数 |

#### DELETE /cart/items/:itemId
删除购物车商品

**路径参数**
| 参数 | 说明 |
|------|------|
| itemId | 购物车商品项 ID |

#### PATCH /cart/items/:itemId/toggle-selection
切换商品选中状态

#### DELETE /cart
清空购物车

---

## 💾 数据模型

### Product (商品)
```typescript
interface Product {
  id: string;           // 商品ID，格式: prod_xxx
  name: string;         // 商品名称
  description: string;  // 商品描述
  price: number;        // 价格（元）
  stock: number;        // 库存数量
  category: string;     // 分类: 鱼类/虾类/蟹类/贝类/藻类/其他
  imageUrl: string;     // 图片URL
  onSale: boolean;      // 是否促销
}
```

### CartItem (购物车项)
```typescript
interface CartItem {
  id: string;           // 购物车项ID
  productId: string;    // 关联商品ID
  name: string;         // 商品名称（冗余存储）
  price: number;        // 单价（冗余存储）
  quantity: number;     // 数量
  imageUrl: string;     // 图片URL（冗余存储）
}
```

### Cart (购物车)
```typescript
interface Cart {
  id: string;           // 购物车ID
  items: CartItem[];    // 商品列表
  totalPrice: number;   // 总价
  totalItems: number;   // 总数量
  selectedItems: string[]; // 选中项ID列表
}
```

---

## 🎨 页面结构

### 页面列表
| 页面 | 文件路径 | 状态 |
|------|----------|------|
| 首页/商品列表 | `pages/index/` | ✅ |
| 商品详情 | `pages/product-detail/` | ✅ |
| 购物车 | `pages/cart/` | ✅ |
| 订单列表 | `pages/order-list/` | 🚧 |
| 地址管理 | `pages/address/` | ✅ |
| 登录 | `pages/login/` | 🚧 |
| 个人中心 | `pages/profile/` | 🚧 |

### 页面跳转关系
```
首页 (index)
  ├── 商品详情 (product-detail)
  │     └── 加入购物车 → 购物车 (cart)
  ├── 购物车 (cart)
  │     └── 结算 → 订单确认
  └── 个人中心 (profile)
        ├── 登录 (login)
        └── 地址管理 (address)
```

---

## 🔄 业务流程

### 购物流程
```
浏览商品 → 查看详情 → 加入购物车 → 确认订单 → 支付 → 订单完成
    ↑                                                    ↓
    └──────────────── 取消/退货 ←─────────────────────────┘
```

### 购物车流程
```
查看购物车 → 修改数量 → 选择商品 → 结算 → 创建订单
```

---

## 🚨 错误处理

### HTTP 状态码
| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 前端错误处理
```typescript
try {
  const cart = await CartAPI.addToCart({ productId, quantity });
} catch (error) {
  if (error instanceof Error) {
    // 网络错误
    if (error.message.includes('Network')) {
      showToast('网络连接失败');
    } else {
      showToast(error.message);
    }
  }
}
```

---

## 📝 备注

- API 版本: v1
- 数据格式: JSON
- 字符编码: UTF-8
- 认证方式: JWT (待实现)

---

*本规格文档随代码更新同步维护。*
