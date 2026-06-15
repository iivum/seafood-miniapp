# Spec: product-sku

## Purpose

[TBD — see change `v2-visual-redesign` for context. Defines the SKU value object on the Product aggregate, the spec selection bottom sheet on mp-03, and the inventory deduction rules.]

## Requirements

### Requirement: Product aggregate supports SKUs
The system SHALL extend the `Product` aggregate to include a nullable `skus: List<SKU>` collection. Each `SKU` value object MUST have fields: `id` (UUID, server-generated), `spec` (string, 1-50 chars, e.g. "500g/份"), `unitPrice` (BigDecimal, must be > 0), `stock` (int, must be >= 0), and `position` (int, for stable ordering). When `skus` is null or empty, the system MUST treat `Product.price` and `Product.stock` as the default single-SKU representation. When `skus` is non-empty, the system MUST ignore `Product.price` and `Product.stock` for display and inventory decisions, and use the SKU values exclusively.

#### Scenario: Product without SKUs uses price and stock
- **WHEN** a product has `skus` null or empty
- **AND** a customer views the product detail page
- **THEN** the page displays `Product.price` as the unit price
- **AND** `Product.stock` is the available inventory

#### Scenario: Product with SKUs uses SKU values
- **WHEN** a product has `skus` with 2 entries: `{spec: "500g/份", unitPrice: 68, stock: 50}` and `{spec: "1kg/份", unitPrice: 128, stock: 20}`
- **AND** a customer views the product detail page
- **THEN** the page displays a "选择规格" prompt
- **AND** the customer must select a SKU before adding to cart
- **AND** `Product.price` and `Product.stock` are NOT displayed

#### Scenario: SKU position determines ordering
- **WHEN** a product's SKUs are listed in the admin UI
- **THEN** they appear in ascending `position` order
- **AND** the same ordering is used on the mp-03 spec selection sheet

---

### Requirement: SKU repository and queries
The system SHALL provide a `ProductRepository.findBySkuId(UUID skuId)` method that returns the parent `Product` aggregate containing a SKU with the given id (or empty if not found). The system MUST validate that no two SKUs within the same product share the same `spec` string, returning HTTP 409 with `code: "DUPLICATE_SKU_SPEC"` on conflict during product create/update.

#### Scenario: Find parent product by SKU id
- **WHEN** the system needs to act on a specific SKU (e.g. inventory deduction during order create)
- **THEN** it calls `ProductRepository.findBySkuId(skuId)` to locate the parent product
- **AND** the returned product includes the matching SKU in its `skus` collection

#### Scenario: Duplicate SKU spec rejected
- **WHEN** an admin operator submits a product with two SKUs having `spec: "500g/份"`
- **THEN** the system returns HTTP 409 with `code: "DUPLICATE_SKU_SPEC"`
- **AND** the response message names the duplicate spec

---

### Requirement: SKU validation rules
The system SHALL enforce the following validation rules on every `SKU`:
- `unitPrice` MUST be > 0 (rejected: 0, negative)
- `stock` MUST be >= 0 (rejected: negative)
- `spec` MUST be 1-50 chars after trim
- `position` MUST be unique within the product's `skus` collection

#### Scenario: Zero or negative unit price rejected
- **WHEN** an admin operator submits a SKU with `unitPrice: 0`
- **THEN** the system returns HTTP 400 with `code: "VALIDATION"`
- **AND** `fieldErrors["skus[0].unitPrice"]` contains "单价必须大于 0"

#### Scenario: Negative stock rejected
- **WHEN** an admin operator submits a SKU with `stock: -5`
- **THEN** the system returns HTTP 400 with `code: "VALIDATION"`
- **AND** `fieldErrors["skus[1].stock"]` contains "库存不能为负数"

---

### Requirement: SKU selection UI on mp-03
The system SHALL render a SKU selection bottom sheet on the product detail page (mp-03) when the product has non-empty `skus`. The sheet MUST show each SKU's `spec`, `unitPrice` (formatted as ¥XX), and `stock` (with "缺货" badge if `stock = 0`). The customer MUST select a SKU before tapping "加购" or "立即购买". The selected SKU's `unitPrice` and `stock` MUST be submitted with the cart-add or order-create request.

#### Scenario: Open spec sheet
- **WHEN** a customer taps "加购" on a product with SKUs and no SKU is currently selected
- **THEN** the system opens the SKU selection bottom sheet
- **AND** lists all SKUs with spec, price, and stock

#### Scenario: Select a SKU and add to cart
- **WHEN** the customer selects an in-stock SKU and taps "确定" in the sheet
- **THEN** the sheet closes
- **AND** the product detail page updates the displayed price to the selected SKU's `unitPrice`
- **AND** a subsequent "加购" tap POSTs to `/api/cart/items` with `{ productId, skuId, quantity: 1 }`

#### Scenario: Out-of-stock SKU is disabled
- **WHEN** the SKU selection sheet renders
- **THEN** SKUs with `stock = 0` are visually disabled (grayed) and not tappable

---

### Requirement: SKU inventory deduction on order create
The system SHALL deduct inventory from the correct SKU (not from `Product.stock`) when an order is created. The system MUST atomically decrement the SKU's `stock` field by the order quantity and reject the order if the SKU's `stock < quantity`, returning HTTP 409 with `code: "INSUFFICIENT_STOCK"` and the SKU's id in the response.

#### Scenario: Order create deducts from SKU
- **WHEN** a customer submits an order containing 2 units of product P's SKU with id `sku-123`
- **THEN** the system atomically decrements `P.skus[sku-123].stock` by 2
- **AND** `P.stock` is unchanged (because the product uses SKUs)

#### Scenario: Insufficient SKU stock
- **WHEN** a customer submits an order for 5 units of a SKU with `stock: 3`
- **THEN** the system returns HTTP 409 with `code: "INSUFFICIENT_STOCK"`
- **AND** the response body includes `{ skuId, availableStock: 3 }`
- **AND** no inventory is deducted

---

### Requirement: SKU display on mp-03
The system SHALL display a "已选: {spec}" indicator on the product detail page when a SKU is selected. The displayed price MUST be the selected SKU's `unitPrice` formatted with the ¥ symbol. The quantity stepper MUST be bounded by the selected SKU's `stock` (not by `Product.stock`).

#### Scenario: Selected SKU shown
- **WHEN** the customer has selected SKU with spec "500g/份" and unitPrice 68
- **THEN** the page shows "已选: 500g/份" and "¥68" as the unit price
- **AND** the quantity stepper's max is the SKU's stock (not `Product.stock`)

#### Scenario: Switch SKU updates displayed price
- **WHEN** the customer re-opens the spec sheet and selects a different SKU
- **THEN** the displayed price and stock bounds update to the new SKU's values
