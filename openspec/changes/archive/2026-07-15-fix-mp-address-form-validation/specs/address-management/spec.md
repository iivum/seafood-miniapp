## MODIFIED Requirements

### Requirement: Address edit screen (address-edit)
The system SHALL render an address edit screen at the path `pages-sub/user/address/address-edit` accepting fields: name, phone, region (WeChat native `picker mode="region"`, submitted as a 3-element array `[province, city, district]` under form field name `region`), detail (textarea), and a "设为默认" toggle. On save, the screen MUST call `POST /api/addresses` for new addresses or `PUT /api/addresses/{id}` for existing ones. Field validation MUST operate on the fields actually present in the form's submit payload (`e.detail.value`): name 1-30 chars (min 2), phone 11-digit regex, `region` array present with all 3 entries populated and not equal to the unselected placeholder (`'请选择'`), detail (`detailAddress`) 5-100 chars.

**Rationale for this delta**: 2026-07-13 E2E found `validateForm` checking a `formData.province` field that the form never produces (the picker's form field name is `region`, yielding `formData.region` as an array). This made the region-selection check unconditionally fail regardless of user input, blocking all address creation.

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
