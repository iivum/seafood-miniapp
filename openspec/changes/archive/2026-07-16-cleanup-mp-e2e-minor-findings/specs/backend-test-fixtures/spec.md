## ADDED Requirements

### Requirement: Seed order fixtures belong to a real seeded user
`backend/seed/fixtures/orders.json` entries' `userId` MUST reference a `userId` that also exists in `backend/seed/fixtures/users.json` after `seed.sh` runs. An order fixture whose owner does not exist is invisible to every real login (mp filters orders by the authenticated principal's `userId`) and provides no verification value.

**Rationale**: 2026-07-13 E2E found the seed order fixture's `userId=dev-user-001` had no corresponding entry in `users.json`, making that fixture permanently orphaned — mp correctly filtered it out for every real logged-in user, but the fixture itself was dead weight that looked like coverage without providing any.

#### Scenario: Seed script produces a visible order for the seeded customer
- **WHEN** `backend/seed/seed.sh` runs against a fresh database
- **THEN** the seeded customer user (`openId: customer-seed-001` or equivalent) can authenticate and see the seeded order via `GET /api/orders`
