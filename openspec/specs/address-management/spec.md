# Spec: address-management

## Purpose

[TBD — see change `v2-visual-redesign` for context. Defines CRUD endpoints, list/edit screen behavior, and checkout integration for customer shipping addresses.]

## Requirements

### Requirement: Address CRUD endpoints
The system SHALL expose the following endpoints for managing a user's shipping addresses, all authenticated with the customer's JWT:
- `GET /api/users/me/addresses` — list all addresses for the current user
- `POST /api/users/me/addresses` — create a new address
- `PUT /api/users/me/addresses/{id}` — update an existing address
- `DELETE /api/users/me/addresses/{id}` — delete an existing address
- `PUT /api/users/me/addresses/{id}/default` — mark an address as the default

Each address SHALL have fields: `id` (UUID), `name` (string, 1-30 chars), `phone` (string, exactly 11 digits, regex `^1[3-9]\d{9}$`), `province` (string), `city` (string), `district` (string), `detail` (string, 1-100 chars), `isDefault` (boolean).

#### Scenario: List addresses
- **WHEN** an authenticated user calls `GET /api/users/me/addresses`
- **THEN** the response returns HTTP 200 with a JSON array of address objects
- **AND** at most one address in the array has `isDefault=true`

#### Scenario: Create a new address with valid fields
- **WHEN** the user POSTs a valid address payload
- **THEN** the system persists the address with a generated `id`
- **AND** if the address is the user's first one, the system automatically marks it `isDefault=true`
- **AND** returns HTTP 201 with the created address

#### Scenario: Create with invalid phone number
- **WHEN** the user POSTs an address with `phone="12345"`
- **THEN** the system returns HTTP 400 with `code: "VALIDATION"`
- **AND** `fieldErrors.phone` contains "请输入正确的 11 位手机号"

#### Scenario: Delete the default address is rejected
- **WHEN** the user calls `DELETE /api/users/me/addresses/{id}` on an address with `isDefault=true`
- **THEN** the system returns HTTP 409 with `code: "DEFAULT_NOT_DELETABLE"`
- **AND** the response message includes "默认地址不可删除,请先将其他地址设为默认"

#### Scenario: Mark a new default address
- **WHEN** the user calls `PUT /api/users/me/addresses/{id}/default` on a non-default address
- **THEN** the system updates the target address to `isDefault=true`
- **AND** atomically updates any previously-default address to `isDefault=false`
- **AND** returns HTTP 200

---

### Requirement: Address list screen (mp-07 list mode)
The system SHALL render an address list screen at the path `pages-sub/user/address/address-list` (list mode) showing all of the current user's addresses as cards. Each card MUST display name, phone (masked middle 4 digits, e.g. `138****1234`), province + city + district + detail, and a default badge when `isDefault=true`. The screen MUST provide per-row "编辑" and "删除" actions, and a bottom "新增地址" button. The screen MUST support a "selection mode" where the header changes to "选择收货地址" and tapping a card returns the selected address to the caller.

#### Scenario: Address list in list mode
- **WHEN** the user navigates to the address list screen from mp-05 (我的)
- **THEN** the screen loads via `GET /api/users/me/addresses`
- **AND** renders one card per address with the default badge on the default card
- **AND** each card has "编辑" and "删除" buttons (delete is disabled on the default card)

#### Scenario: Address list in selection mode
- **WHEN** the user navigates to the address list from mp-06 (订单确认) via "更换地址"
- **THEN** the screen header shows "选择收货地址" with a "返回" link
- **AND** each card has a radio button (not edit/delete actions)
- **AND** tapping a card returns the selected address's id to mp-06 via the page stack's previous-page data

#### Scenario: Delete a non-default address
- **WHEN** the user taps "删除" on a non-default address card
- **AND** confirms in the resulting dialog
- **THEN** the system calls `DELETE /api/users/me/addresses/{id}`
- **AND** the card disappears from the list

---

### Requirement: Address edit screen (address-edit)
The system SHALL render an address edit screen at the path `pages-sub/user/address/address-edit` accepting fields: name, phone, region (WeChat native `picker mode="region"`, submitted as a 3-element array `[province, city, district]` under form field name `region`), detail (textarea), and a "设为默认" toggle. On save, the screen MUST call `POST /api/addresses` for new addresses or `PUT /api/addresses/{id}` for existing ones. Field validation MUST operate on the fields actually present in the form's submit payload (`e.detail.value`): name 1-30 chars (min 2), phone 11-digit regex, `region` array present with all 3 entries populated and not equal to the unselected placeholder (`'请选择'`), detail (`detailAddress`) 5-100 chars.

#### Scenario: Edit an existing address
- **WHEN** the user navigates to the edit screen with an address id in the query
- **THEN** the screen loads the existing address via `GET /addresses/{id}`
- **AND** pre-fills all fields including the region picker display
- **AND** on save calls `PUT /addresses/{id}` with the modified payload

#### Scenario: Validation error on empty phone
- **WHEN** the user taps "保存收货地址" with an empty phone field
- **THEN** the system sets `errorMsg` to "请输入正确的手机号"
- **AND** does not submit the form

#### Scenario: Validation passes after selecting a region
- **WHEN** the user fills name, phone, detail, and selects a complete province/city/district via the region picker
- **AND** taps "保存收货地址"
- **THEN** `validateForm` does NOT report a "请选择所在地区" error
- **AND** the system proceeds to call `POST /addresses` (or `PUT` in edit mode) with `province`/`city`/`district` derived from the submitted `region` array

#### Scenario: Validation error when region is unselected
- **WHEN** the user submits the form without ever changing the region picker from its default placeholder
- **THEN** the system sets `errorMsg` to "请选择所在地区"
- **AND** does not submit the form

#### Scenario: Save success
- **WHEN** the user submits a valid edit form (name, phone, complete region, detail all valid)
- **THEN** the system calls the appropriate POST or PUT to `/addresses`
- **AND** on success shows a toast and navigates back to the address list
- **AND** the address list refreshes to show the updated data

---

### Requirement: Default address selection in order checkout (mp-06)
The system SHALL display the current user's default address at the top of the order checkout screen (mp-06) with a "更换" button. Tapping "更换" MUST navigate to the address list in selection mode. On selection, the checkout screen MUST update the displayed address and persist the selection to the local order draft.

#### Scenario: Default address shown at checkout
- **WHEN** the user navigates to the order checkout screen with items in cart
- **THEN** the screen loads the default address via `GET /api/users/me/addresses?default=true`
- **AND** renders the name, phone, and full address

#### Scenario: Change address during checkout
- **WHEN** the user taps "更换" on the address card
- **AND** selects a different address from the address list (selection mode)
- **THEN** the checkout screen returns from the address list with the new address id
- **AND** re-renders the address card with the new address

#### Scenario: No default address
- **WHEN** the user has no addresses at all
- **THEN** the checkout screen shows a "请先添加收货地址" empty state with a "+ 新增地址" button
- **AND** tapping the button navigates to the address edit screen in "new" mode
