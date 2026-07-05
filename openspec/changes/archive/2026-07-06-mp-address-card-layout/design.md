## Context

Current `.address-card__actions` (`frontend/pages-sub/user/address/address-list.wxss:126-135`) is a `flex-direction: column` sidebar with a `border-left`, sitting to the right of the card body — three actions (设为默认/编辑/删除) stacked vertically, each rendered as icon-above-label (`.address-card__action`, `flex-direction: column`, `align-items: center`). The OD golden (`frontend/e2e/od-golden/mp-07-address.png`) shows the same three actions laid out horizontally along the card's bottom edge, separated from the address text above by a dashed divider, with each action's icon and label sitting side by side on one line.

## Goals / Non-Goals

**Goals:**
- Change `.address-card__actions` from a right-side vertical sidebar to a bottom horizontal bar with a dashed top divider
- Change each `.address-card__action` from icon-above-label to icon-beside-label
- Preserve all existing `bindtap` targets, `data-*` bindings, and the `wx:if="{{!item.isDefault}}"` visibility rule for "设为默认" — this is a pure layout change, zero interaction/data changes

**Non-Goals:**
- Any change to which actions show or when (e.g., making "设为默认" a persistent radio shown on every card, matching the OD mockup's visual — that's an interaction-model decision, not layout, and is explicitly logged as out of scope in proposal.md)
- Any OD content the current domain model can't produce at all (cold-chain banner, per-address delivery notes, relationship tags like 公司/家人) — these need new domain fields, not a layout fix
- Replacing the ✏️/🗑️ emoji icons with `van-icon` components (that was mp-05's fix pattern for a different reason — unrequested here, would expand scope beyond the layout gap this change targets)

## Decisions

**D1 — Row layout via flex-direction + justify-content, dashed border-top replaces border-left.** `.address-card__actions`: `flex-direction: column` → `row`; drop `border-left`, add `border-top: 1rpx dashed var(--border, #eae3e1)`; `justify-content: center` → `space-around` (or `space-between`) so the three actions spread evenly across the card's width instead of stacking centered in a narrow side column. Padding changes from a tall narrow column (`28rpx 20rpx`) to a shorter full-width bar.

**D2 — Each action becomes a row, not a column.** `.address-card__action`: `flex-direction: column` → `row`; `gap` stays for icon-to-label spacing but now applies horizontally.

**D3 — No wxml restructuring needed.** The current wxml (`address-list.wxml:63-93`) already has icon and label as sibling `<text>`/`<view>` elements inside each `.address-card__action` — flipping the parent's `flex-direction` is sufficient. No DOM reordering, no new elements, no `bindtap`/`data-*` changes.

## Risks / Trade-offs

- [Risk] `justify-content: space-around` vs `space-between` is a visual judgment call not pinned by the geometry gate (`mp-07-address.json` only checks card count + add-button presence, not action-row internals) → Mitigation: match the OD golden's visual spacing by eye during implementation; the 5% perceptual-diff threshold on the whole screen is the actual gate, not a pixel-perfect spec requirement
- [Risk] Existing `frontend/pages-sub/user/address/__tests__/address-list-wxml-contract.test.js` and `address-list.test.js` may have layout-adjacent assertions (unlikely, since they test bindtap-wiring and data flow, not CSS) → Mitigation: run the full test file before and after to confirm no incidental breakage
