## MODIFIED Requirements

### Requirement: Address management page (mp-07) OD-aligned layout
The address list page (`pages-sub/user/address/address-list`) MUST render, matching the OD golden `frontend/e2e/od-golden/mp-07-address.png` to within a 5% perceptual-diff threshold (`npm run test:visual mp-07-address`) and passing the geometry gate (`npm run test:geometry mp-07-address`):
- **Select-mode title**: "选择收货地址" shown only when `selectMode` is true (entered from mp-06's address card).
- **Address cards**: one per address, showing name/phone/full address, a "默认" badge when `isDefault`, 编辑/删除 action buttons, and — in select mode — a radio circle reflecting `selectedId === item.id`.
- **Action row layout**: the 设为默认/编辑/删除 action row MUST be laid out horizontally along the bottom of the card, separated from the address content above it by a dashed top border — NOT as a vertical sidebar along the card's side. Each action's icon and label MUST sit side by side on one line (not icon-above-label).
- **Empty state**: 📭 icon + "还没有收货地址哦" + "添加收货地址,方便下单收货" when the address list is empty.
- **Bottom add bar**: sticky "+ 添加新地址" button.

This page MUST successfully load the user's addresses via the backend address API (no 403/404 on a valid authenticated request); an auth or route failure that prevents the list from loading is a defect to fix as part of achieving this requirement, not an acceptable "empty" rendering.

#### Scenario: User has no saved addresses
- **WHEN** `addresses.length === 0`
- **THEN** the page shows the empty state illustration and copy, and the add bar remains visible

#### Scenario: Select mode reflects the currently-selected address
- **WHEN** the page opens with `selectMode=true` from mp-06
- **THEN** the "选择收货地址" title is shown
- **AND** each address card renders a radio circle, checked for the address matching `selectedId`

#### Scenario: Authenticated address list loads successfully
- **WHEN** an authenticated user navigates to mp-07
- **THEN** the backend address API returns 200 with the user's addresses (not 403/404)

#### Scenario: Action row renders as a horizontal bottom bar
- **WHEN** an address card renders its 设为默认/编辑/删除 actions
- **THEN** the three actions appear in a single horizontal row along the bottom of the card, below a dashed divider, each with its icon and label on the same line
