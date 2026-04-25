#!/usr/bin/env python3
"""
海鲜商城测试数据初始化脚本
Seafood Mall Test Data Initialization Script

此脚本用于向 MongoDB 中写入测试数据，包括：
- 测试用户数据（用户名、密码、收货地址）
- 测试商品数据（海鲜商品，分类：鱼类/虾蟹/贝类/藻类）
- 测试订单数据（已支付/待发货/已完成等状态）

使用方法:
    python3 init_test_data.py [--clear] [--host HOST] [--port PORT]

参数:
    --clear: 清空现有数据后重新初始化
    --host: MongoDB 主机地址 (默认: localhost)
    --port: MongoDB 端口 (默认: 27017)
"""

import argparse
import sys
from datetime import datetime, timedelta
from typing import List, Dict, Any
from uuid import uuid4

from pymongo import MongoClient


# MongoDB 配置
DEFAULT_HOST = "localhost"
DEFAULT_PORT = 27017

# 数据库名称
DB_PRODUCT = "seafood_product"
DB_ORDER = "seafood_order"
DB_USER = "seafood_user"


# ===========================================
# 测试用户数据
# ===========================================
def get_test_users() -> List[Dict[str, Any]]:
    """生成测试用户数据"""
    return [
        {
            "_id": "user_001",
            "openId": f"o_test_{uuid4().hex[:8]}",
            "nickname": "海鲜爱好者",
            "avatarUrl": "https://picsum.photos/200",
            "email": "test1@example.com",
            "phone": "13800001001",
            "password": "$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqQBrkHx7WhqEDFP/4aYdCJlPk5dK",  # password123
            "address": "北京市朝阳区建国路88号",
            "role": "USER",
            "createdAt": datetime.utcnow() - timedelta(days=30),
            "updatedAt": datetime.utcnow() - timedelta(days=5),
        },
        {
            "_id": "user_002",
            "openId": f"o_test_{uuid4().hex[:8]}",
            "nickname": "海味珍馐",
            "avatarUrl": "https://picsum.photos/200",
            "email": "test2@example.com",
            "phone": "13800001002",
            "password": "$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqQBrkHx7WhqEDFP/4aYdCJlPk5dK",
            "address": "上海市浦东新区世纪大道1000号",
            "role": "USER",
            "createdAt": datetime.utcnow() - timedelta(days=25),
            "updatedAt": datetime.utcnow() - timedelta(days=3),
        },
        {
            "_id": "user_003",
            "openId": f"o_test_{uuid4().hex[:8]}",
            "nickname": "渔夫老王",
            "avatarUrl": "https://picsum.photos/200",
            "email": "merchant1@example.com",
            "phone": "13800001003",
            "password": "$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqQBrkHx7WhqEDFP/4aYdCJlPk5dK",
            "address": "广州市天河区珠江新城花城大道",
            "role": "MERCHANT",
            "createdAt": datetime.utcnow() - timedelta(days=60),
            "updatedAt": datetime.utcnow() - timedelta(days=1),
        },
        {
            "_id": "user_004",
            "openId": f"o_test_{uuid4().hex[:8]}",
            "nickname": "管理员小明",
            "avatarUrl": "https://picsum.photos/200",
            "email": "admin@example.com",
            "phone": "13800001004",
            "password": "$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqQBrkHx7WhqEDFP/4aYdCJlPk5dK",
            "address": "深圳市南山区科技园南区",
            "role": "ADMIN",
            "createdAt": datetime.utcnow() - timedelta(days=90),
            "updatedAt": datetime.utcnow(),
        },
    ]


# ===========================================
# 测试商品数据
# ===========================================
def get_test_products() -> List[Dict[str, Any]]:
    """生成测试商品数据"""
    return [
        # 鱼类
        {
            "_id": "prod_001",
            "name": "新鲜三文鱼刺身",
            "description": "挪威进口冰鲜三文鱼，肉质鲜嫩，适合刺身和寿司",
            "price": 158.00,
            "stock": 50,
            "category": "鱼类",
            "imageUrl": "https://picsum.photos/seed/salmon/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=30),
            "updatedAt": datetime.utcnow() - timedelta(days=1),
        },
        {
            "_id": "prod_002",
            "name": "东海带鱼段",
            "description": "东海野生带鱼，鱼身完整，银光闪闪，肉质细腻",
            "price": 45.00,
            "stock": 100,
            "category": "鱼类",
            "imageUrl": "https://picsum.photos/seed/hairtail/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=28),
            "updatedAt": datetime.utcnow() - timedelta(days=2),
        },
        {
            "_id": "prod_003",
            "name": "鲜活石斑鱼",
            "description": "南海野生石斑鱼，清蒸红烧皆宜，味道鲜美",
            "price": 128.00,
            "stock": 30,
            "category": "鱼类",
            "imageUrl": "https://picsum.photos/seed/grouper/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=25),
            "updatedAt": datetime.utcnow() - timedelta(days=3),
        },
        {
            "_id": "prod_004",
            "name": "冰鲜金枪鱼",
            "description": "日本料理店专用金枪鱼，肉质紧实，适合做刺身",
            "price": 268.00,
            "stock": 20,
            "category": "鱼类",
            "imageUrl": "https://picsum.photos/seed/tuna/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=20),
            "updatedAt": datetime.utcnow() - timedelta(days=1),
        },
        # 虾蟹类
        {
            "_id": "prod_005",
            "name": "鲜活大闸蟹（公4两/母3两）",
            "description": "阳澄湖大闸蟹，蟹黄饱满，蟹肉鲜甜",
            "price": 168.00,
            "stock": 80,
            "category": "虾蟹",
            "imageUrl": "https://picsum.photos/seed/crab/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=22),
            "updatedAt": datetime.utcnow() - timedelta(days=1),
        },
        {
            "_id": "prod_006",
            "name": "阿根廷红虾",
            "description": "进口阿根廷红虾，个大肉多，适合白灼或蒜蓉蒸",
            "price": 88.00,
            "stock": 120,
            "category": "虾蟹",
            "imageUrl": "https://picsum.photos/seed/redshrimp/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=26),
            "updatedAt": datetime.utcnow() - timedelta(days=2),
        },
        {
            "_id": "prod_007",
            "name": "鲜活小龙虾",
            "description": "湖北潜江小龙虾，麻辣蒜蓉两相宜",
            "price": 58.00,
            "stock": 200,
            "category": "虾蟹",
            "imageUrl": "https://picsum.photos/seed/crayfish/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=18),
            "updatedAt": datetime.utcnow() - timedelta(days=1),
        },
        {
            "_id": "prod_008",
            "name": "帝王蟹腿",
            "description": "阿拉斯加帝王蟹腿，肉质饱满，鲜甜可口",
            "price": 328.00,
            "stock": 40,
            "category": "虾蟹",
            "imageUrl": "https://picsum.photos/seed/kingcrab/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=15),
            "updatedAt": datetime.utcnow() - timedelta(days=3),
        },
        # 贝类
        {
            "_id": "prod_009",
            "name": "鲜活鲍鱼（10头）",
            "description": "大连皱纹鲍鱼，养殖于深海，品质优良",
            "price": 128.00,
            "stock": 60,
            "category": "贝类",
            "imageUrl": "https://picsum.photos/seed/abalone/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=24),
            "updatedAt": datetime.utcnow() - timedelta(days=2),
        },
        {
            "_id": "prod_010",
            "name": "鲜活扇贝",
            "description": "威海扇贝，蒜蓉粉丝蒸，味道鲜美",
            "price": 68.00,
            "stock": 150,
            "category": "贝类",
            "imageUrl": "https://picsum.photos/seed/scallop/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=21),
            "updatedAt": datetime.utcnow() - timedelta(days=1),
        },
        {
            "_id": "prod_011",
            "name": "生蚝（半打）",
            "description": "乳山生蚝，鲜嫩多汁，可刺身可碳烤",
            "price": 98.00,
            "stock": 80,
            "category": "贝类",
            "imageUrl": "https://picsum.photos/seed/oyster/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=19),
            "updatedAt": datetime.utcnow() - timedelta(days=2),
        },
        {
            "_id": "prod_012",
            "name": "花蛤（1斤）",
            "description": "青岛花蛤，吐沙干净，适合爆炒或做汤",
            "price": 25.00,
            "stock": 300,
            "category": "贝类",
            "imageUrl": "https://picsum.photos/seed/clam/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=16),
            "updatedAt": datetime.utcnow() - timedelta(days=1),
        },
        # 藻类
        {
            "_id": "prod_013",
            "name": "海带结（干货）",
            "description": "烟台海带结，自然晾晒，炖汤凉拌皆宜",
            "price": 18.00,
            "stock": 500,
            "category": "藻类",
            "imageUrl": "https://picsum.photos/seed/kelp/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=30),
            "updatedAt": datetime.utcnow() - timedelta(days=5),
        },
        {
            "_id": "prod_014",
            "name": "紫菜（寿司专用）",
            "description": "福建霞浦紫菜，烤制金黄，适合寿司和包饭",
            "price": 35.00,
            "stock": 400,
            "category": "藻类",
            "imageUrl": "https://picsum.photos/seed/nori/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=28),
            "updatedAt": datetime.utcnow() - timedelta(days=4),
        },
        {
            "_id": "prod_015",
            "name": "海蜇丝（即食）",
            "description": "舟山即食海蜇丝，开袋即食，口感爽脆",
            "price": 28.00,
            "stock": 250,
            "category": "藻类",
            "imageUrl": "https://picsum.photos/seed/jellyfish/400/400",
            "onSale": True,
            "createdAt": datetime.utcnow() - timedelta(days=17),
            "updatedAt": datetime.utcnow() - timedelta(days=2),
        },
    ]


# ===========================================
# 测试订单数据
# ===========================================
def get_test_orders() -> List[Dict[str, Any]]:
    """生成测试订单数据"""
    now = datetime.utcnow()

    return [
        # 订单 1: 已完成订单
        {
            "_id": "order_001",
            "userId": "user_001",
            "orderNumber": f"SF{now.year}{now.month:02d}0001",
            "items": [
                {"productId": "prod_001", "name": "新鲜三文鱼刺身", "price": 158.00, "quantity": 2},
                {"productId": "prod_010", "name": "鲜活扇贝", "price": 68.00, "quantity": 1},
            ],
            "totalPrice": 384.00,
            "finalPrice": 384.00,
            "discountAmount": 0.00,
            "shippingFee": 0.00,
            "status": "DELIVERED",
            "shippingAddress": {
                "id": "addr_001",
                "contactName": "张三",
                "phone": "13800001001",
                "province": "北京市",
                "city": "北京市",
                "district": "朝阳区",
                "detailAddress": "建国路88号",
                "postalCode": "100022",
            },
            "transactionId": f"txn_{uuid4().hex[:16]}",
            "trackingNumber": "SF1234567890",
            "note": "请尽快送达",
            "createdAt": now - timedelta(days=10),
            "paidAt": now - timedelta(days=10),
            "shippedAt": now - timedelta(days=9),
            "deliveredAt": now - timedelta(days=7),
            "orderHistory": [
                {"description": "Order created", "status": "PENDING_PAYMENT", "timestamp": now - timedelta(days=10)},
                {"description": "Order paid", "status": "PAID", "timestamp": now - timedelta(days=10)},
                {"description": "Order shipped", "status": "SHIPPED", "timestamp": now - timedelta(days=9)},
                {"description": "Order delivered", "status": "DELIVERED", "timestamp": now - timedelta(days=7)},
            ],
        },
        # 订单 2: 待发货订单
        {
            "_id": "order_002",
            "userId": "user_001",
            "orderNumber": f"SF{now.year}{now.month:02d}0002",
            "items": [
                {"productId": "prod_005", "name": "鲜活大闸蟹（公4两/母3两）", "price": 168.00, "quantity": 2},
            ],
            "totalPrice": 336.00,
            "finalPrice": 336.00,
            "discountAmount": 0.00,
            "shippingFee": 0.00,
            "status": "PAID",
            "shippingAddress": {
                "id": "addr_001",
                "contactName": "张三",
                "phone": "13800001001",
                "province": "北京市",
                "city": "北京市",
                "district": "朝阳区",
                "detailAddress": "建国路88号",
                "postalCode": "100022",
            },
            "transactionId": f"txn_{uuid4().hex[:16]}",
            "note": "",
            "createdAt": now - timedelta(days=3),
            "paidAt": now - timedelta(days=3),
            "shippedAt": None,
            "deliveredAt": None,
            "orderHistory": [
                {"description": "Order created", "status": "PENDING_PAYMENT", "timestamp": now - timedelta(days=3)},
                {"description": "Order paid", "status": "PAID", "timestamp": now - timedelta(days=3)},
            ],
        },
        # 订单 3: 待付款订单
        {
            "_id": "order_003",
            "userId": "user_002",
            "orderNumber": f"SF{now.year}{now.month:02d}0003",
            "items": [
                {"productId": "prod_003", "name": "鲜活石斑鱼", "price": 128.00, "quantity": 1},
                {"productId": "prod_009", "name": "鲜活鲍鱼（10头）", "price": 128.00, "quantity": 2},
                {"productId": "prod_013", "name": "海带结（干货）", "price": 18.00, "quantity": 2},
            ],
            "totalPrice": 420.00,
            "finalPrice": 420.00,
            "discountAmount": 0.00,
            "shippingFee": 0.00,
            "status": "PENDING_PAYMENT",
            "shippingAddress": {
                "id": "addr_002",
                "contactName": "李四",
                "phone": "13800001002",
                "province": "上海市",
                "city": "上海市",
                "district": "浦东新区",
                "detailAddress": "世纪大道1000号",
                "postalCode": "200120",
            },
            "transactionId": None,
            "note": "请用保温袋包装",
            "createdAt": now - timedelta(days=1),
            "paidAt": None,
            "shippedAt": None,
            "deliveredAt": None,
            "orderHistory": [
                {"description": "Order created", "status": "PENDING_PAYMENT", "timestamp": now - timedelta(days=1)},
            ],
        },
        # 订单 4: 已发货订单
        {
            "_id": "order_004",
            "userId": "user_002",
            "orderNumber": f"SF{now.year}{now.month:02d}0004",
            "items": [
                {"productId": "prod_008", "name": "帝王蟹腿", "price": 328.00, "quantity": 1},
                {"productId": "prod_011", "name": "生蚝（半打）", "price": 98.00, "quantity": 1},
            ],
            "totalPrice": 426.00,
            "finalPrice": 426.00,
            "discountAmount": 0.00,
            "shippingFee": 0.00,
            "status": "SHIPPED",
            "shippingAddress": {
                "id": "addr_002",
                "contactName": "李四",
                "phone": "13800001002",
                "province": "上海市",
                "city": "上海市",
                "district": "浦东新区",
                "detailAddress": "世纪大道1000号",
                "postalCode": "200120",
            },
            "transactionId": f"txn_{uuid4().hex[:16]}",
            "trackingNumber": "YTO1234567890",
            "note": "",
            "createdAt": now - timedelta(days=5),
            "paidAt": now - timedelta(days=5),
            "shippedAt": now - timedelta(days=4),
            "deliveredAt": None,
            "orderHistory": [
                {"description": "Order created", "status": "PENDING_PAYMENT", "timestamp": now - timedelta(days=5)},
                {"description": "Order paid", "status": "PAID", "timestamp": now - timedelta(days=5)},
                {"description": "Order shipped", "status": "SHIPPED", "timestamp": now - timedelta(days=4)},
            ],
        },
        # 订单 5: 已取消订单
        {
            "_id": "order_005",
            "userId": "user_001",
            "orderNumber": f"SF{now.year}{now.month:02d}0005",
            "items": [
                {"productId": "prod_006", "name": "阿根廷红虾", "price": 88.00, "quantity": 3},
            ],
            "totalPrice": 264.00,
            "finalPrice": 264.00,
            "discountAmount": 0.00,
            "shippingFee": 10.00,
            "status": "CANCELLED",
            "shippingAddress": {
                "id": "addr_001",
                "contactName": "张三",
                "phone": "13800001001",
                "province": "北京市",
                "city": "北京市",
                "district": "朝阳区",
                "detailAddress": "建国路88号",
                "postalCode": "100022",
            },
            "transactionId": None,
            "cancellationReason": "商品缺货",
            "note": "",
            "createdAt": now - timedelta(days=8),
            "paidAt": None,
            "shippedAt": None,
            "deliveredAt": None,
            "cancelledAt": now - timedelta(days=8),
            "orderHistory": [
                {"description": "Order created", "status": "PENDING_PAYMENT", "timestamp": now - timedelta(days=8)},
                {"description": "Order cancelled: 商品缺货", "status": "CANCELLED", "timestamp": now - timedelta(days=8)},
            ],
        },
        # 订单 6: 已退款订单
        {
            "_id": "order_006",
            "userId": "user_003",
            "orderNumber": f"SF{now.year}{now.month:02d}0006",
            "items": [
                {"productId": "prod_004", "name": "冰鲜金枪鱼", "price": 268.00, "quantity": 1},
                {"productId": "prod_002", "name": "东海带鱼段", "price": 45.00, "quantity": 2},
            ],
            "totalPrice": 358.00,
            "finalPrice": 358.00,
            "discountAmount": 0.00,
            "shippingFee": 0.00,
            "status": "REFUNDED",
            "shippingAddress": {
                "id": "addr_003",
                "contactName": "王五",
                "phone": "13800001003",
                "province": "广州市",
                "city": "广州市",
                "district": "天河区",
                "detailAddress": "珠江新城花城大道",
                "postalCode": "510623",
            },
            "transactionId": f"txn_{uuid4().hex[:16]}",
            "refundTransactionId": f"ref_{uuid4().hex[:16]}",
            "refundReason": "商品变质",
            "note": "",
            "createdAt": now - timedelta(days=15),
            "paidAt": now - timedelta(days=15),
            "shippedAt": now - timedelta(days=14),
            "deliveredAt": now - timedelta(days=12),
            "refundedAt": now - timedelta(days=10),
            "orderHistory": [
                {"description": "Order created", "status": "PENDING_PAYMENT", "timestamp": now - timedelta(days=15)},
                {"description": "Order paid", "status": "PAID", "timestamp": now - timedelta(days=15)},
                {"description": "Order shipped", "status": "SHIPPED", "timestamp": now - timedelta(days=14)},
                {"description": "Order delivered", "status": "DELIVERED", "timestamp": now - timedelta(days=12)},
                {"description": "Order refunded: 商品变质", "status": "REFUNDED", "timestamp": now - timedelta(days=10)},
            ],
        },
    ]


# ===========================================
# 主函数
# ===========================================
def init_test_data(host: str, port: int, clear: bool = False) -> bool:
    """
    初始化测试数据

    Args:
        host: MongoDB 主机地址
        port: MongoDB 端口
        clear: 是否清空现有数据

    Returns:
        bool: 操作是否成功
    """
    print(f"=" * 60)
    print(f"海鲜商城测试数据初始化")
    print(f"=" * 60)
    print(f"MongoDB: {host}:{port}")
    print(f"操作: {'清空并重新初始化' if clear else '追加数据'}")
    print(f"=" * 60)

    try:
        # 连接 MongoDB
        client = MongoClient(host, port, serverSelectionTimeoutMS=5000)
        # 验证连接
        client.admin.command("ping")
        print(f"✓ MongoDB 连接成功")

        # 获取数据库和集合
        db_product = client[DB_PRODUCT]
        db_order = client[DB_ORDER]
        db_user = client[DB_USER]

        if clear:
            print(f"\n正在清空现有数据...")
            db_product.products.drop()
            db_order.orders.drop()
            db_user.users.drop()
            print(f"✓ 数据已清空")

        # 初始化用户数据
        print(f"\n正在初始化用户数据...")
        users = get_test_users()
        db_user.users.insert_many(users)
        print(f"✓ 已插入 {len(users)} 条用户数据")

        # 初始化商品数据
        print(f"\n正在初始化商品数据...")
        products = get_test_products()
        db_product.products.insert_many(products)
        print(f"✓ 已插入 {len(products)} 条商品数据")

        # 初始化订单数据
        print(f"\n正在初始化订单数据...")
        orders = get_test_orders()
        db_order.orders.insert_many(orders)
        print(f"✓ 已插入 {len(orders)} 条订单数据")

        # 统计
        print(f"\n" + "=" * 60)
        print(f"数据统计:")
        print(f"  用户: {db_user.users.count_documents({})} 条")
        print(f"  商品: {db_product.products.count_documents({})} 条")
        print(f"  订单: {db_order.orders.count_documents({})} 条")
        print(f"=" * 60)

        client.close()
        return True

    except Exception as e:
        print(f"\n✗ 错误: {e}")
        return False


def generate_markdown_docs() -> str:
    """生成 Markdown 格式的测试数据文档"""
    users = get_test_users()
    products = get_test_products()
    orders = get_test_orders()

    md = """# 测试数据文档

本文档记录海鲜商城小程序的测试数据，包括用户、商品和订单数据。

---

## 测试用户

| 用户ID | 昵称 | 手机号 | 角色 | 地址 |
|--------|------|--------|------|------|
"""

    for u in users:
        md += f"| {u['_id']} | {u['nickname']} | {u['phone']} | {u['role']} | {u['address']} |\n"

    md += """
**测试账号说明:**
- 所有账号密码均为: `password123`
- USER: 普通用户
- MERCHANT: 商户
- ADMIN: 管理员

---

## 测试商品

### 鱼类

| 商品ID | 名称 | 价格(元) | 库存 | 状态 |
|--------|------|----------|------|------|
"""

    fish_products = [p for p in products if p["category"] == "鱼类"]
    for p in fish_products:
        md += f"| {p['_id']} | {p['name']} | {p['price']:.2f} | {p['stock']} | {'在售' if p['onSale'] else '下架'} |\n"

    md += """
### 虾蟹类

| 商品ID | 名称 | 价格(元) | 库存 | 状态 |
|--------|------|----------|------|------|
"""

    shrimp_products = [p for p in products if p["category"] == "虾蟹"]
    for p in shrimp_products:
        md += f"| {p['_id']} | {p['name']} | {p['price']:.2f} | {p['stock']} | {'在售' if p['onSale'] else '下架'} |\n"

    md += """
### 贝类

| 商品ID | 名称 | 价格(元) | 库存 | 状态 |
|--------|------|----------|------|------|
"""

    shellfish_products = [p for p in products if p["category"] == "贝类"]
    for p in shellfish_products:
        md += f"| {p['_id']} | {p['name']} | {p['price']:.2f} | {p['stock']} | {'在售' if p['onSale'] else '下架'} |\n"

    md += """
### 藻类

| 商品ID | 名称 | 价格(元) | 库存 | 状态 |
|--------|------|----------|------|------|
"""

    algae_products = [p for p in products if p["category"] == "藻类"]
    for p in algae_products:
        md += f"| {p['_id']} | {p['name']} | {p['price']:.2f} | {p['stock']} | {'在售' if p['onSale'] else '下架'} |\n"

    md += """
---

## 测试订单

| 订单ID | 订单号 | 用户ID | 状态 | 总价(元) | 创建时间 |
|--------|--------|--------|------|----------|----------|
"""

    status_map = {
        "PENDING_PAYMENT": "待付款",
        "PAID": "已支付",
        "SHIPPED": "已发货",
        "DELIVERED": "已完成",
        "CANCELLED": "已取消",
        "REFUNDED": "已退款",
    }

    for o in orders:
        created_at = o["createdAt"].strftime("%Y-%m-%d %H:%M")
        status = status_map.get(o["status"], o["status"])
        md += f"| {o['_id']} | {o['orderNumber']} | {o['userId']} | {status} | {o['totalPrice']:.2f} | {created_at} |\n"

    md += """
### 订单状态说明

| 状态 | 说明 |
|------|------|
| PENDING_PAYMENT | 待付款 - 订单已创建，等待用户支付 |
| PAID | 已支付 - 用户已完成支付，等待发货 |
| SHIPPED | 已发货 - 商品已发出，等待收货 |
| DELIVERED | 已完成 - 用户已确认收货 |
| CANCELLED | 已取消 - 订单已取消 |
| REFUNDED | 已退款 - 已支付订单已退款 |

---

## 使用说明

### 运行数据初始化

```bash
# 初始化测试数据（追加模式）
python3 init_test_data.py

# 清空并重新初始化
python3 init_test_data.py --clear

# 指定 MongoDB 地址
python3 init_test_data.py --host 192.168.1.100 --port 27017
```

### 注意事项

1. 确保 MongoDB 服务正在运行
2. 默认连接 `localhost:27017`
3. 测试数据中的密码已加密存储，登录时请使用明文密码 `password123`
4. 订单状态流转: 待付款 → 已支付 → 已发货 → 已完成
5. 已支付订单可取消（变为已取消），已发货后可退款（变为已退款）

---

*本文档由 `init_test_data.py` 脚本自动生成
"""

    return md


def main():
    parser = argparse.ArgumentParser(description="海鲜商城测试数据初始化")
    parser.add_argument("--clear", action="store_true", help="清空现有数据后重新初始化")
    parser.add_argument("--host", default=DEFAULT_HOST, help=f"MongoDB 主机地址 (默认: {DEFAULT_HOST})")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"MongoDB 端口 (默认: {DEFAULT_PORT})")
    parser.add_argument("--generate-docs", action="store_true", help="生成 Markdown 测试数据文档")

    args = parser.parse_args()

    if args.generate_docs:
        # 生成文档
        docs_dir = "/Users/linbinghui/.openclaw/workspace/seafood-miniapp/docs"
        import os
        os.makedirs(docs_dir, exist_ok=True)

        md = generate_markdown_docs()
        docs_path = os.path.join(docs_dir, "test-data.md")
        with open(docs_path, "w", encoding="utf-8") as f:
            f.write(md)
        print(f"✓ 测试数据文档已生成: {docs_path}")
        return

    # 初始化数据
    success = init_test_data(args.host, args.port, args.clear)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
