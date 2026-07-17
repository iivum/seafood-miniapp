## MODIFIED Requirements

### Requirement: Public product browsing
The system SHALL expose product browsing endpoints that allow any client (authenticated or anonymous) to list and inspect products. Public listing (with or without a `category` filter) MUST only surface products whose `status` is `ACTIVE`, filtered at the persistence-query level (not by post-hoc in-memory override). A single product document with an unrecognized/illegal `status` value in the database MUST NOT cause the entire listing query to fail; the offending document MUST be excluded from results while the rest of the page returns normally.

**Rationale for this delta**: 2026-07-13 E2E found `GET /api/products?category=...` throwing `IllegalArgumentException` and returning an opaque error whenever any product in that category had a non-enum `status` value, taking down the entire category. Root cause: `findByCategory` had no status filter at the query level and instead force-overwrote every returned document's in-memory status to `ACTIVE` post-query (`ProductService.listPublic`) — masking real status while still being vulnerable to the enum deserialization crash for bad data.

#### Scenario: Anonymous client lists products
- **WHEN** a client calls `GET /api/products` without an authentication token
- **THEN** the system returns a paginated list of products with status 200

#### Scenario: Anonymous client lists products by category
- **WHEN** a client calls `GET /api/products?category=<name>`
- **THEN** the system returns only products in that category whose `status` is `ACTIVE`, with status 200
- **AND** the query filters by status at the database level rather than overriding the in-memory status of returned documents

#### Scenario: Category listing tolerates a corrupted status value
- **WHEN** the `products` collection contains a document in the requested category whose `status` field does not match any `ProductStatus` enum constant
- **THEN** `GET /api/products?category=<name>` still returns HTTP 200 with the remaining valid-status products in that category
- **AND** does not return HTTP 500 or an unrelated 403

#### Scenario: Anonymous client views a single product
- **WHEN** a client calls `GET /api/products/{id}` with a valid product id
- **THEN** the system returns the product payload with status 200

#### Scenario: Anonymous client requests a missing product
- **WHEN** a client calls `GET /api/products/{id}` with an id that does not exist
- **THEN** the system returns HTTP 404 with an `ErrorResponse` body whose `code` is `NOT_FOUND`
