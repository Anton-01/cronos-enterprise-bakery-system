# Baking Studio backend

Server side of the UI contract `cronos-ui/docs/api/baking-studio.md` (branch `claude/elegant-knuth-xgxdur`):
recipe cover, recipe sections, fixed-cost review, pricing method and the baker's guide. Every response field
is additive and every new request field is optional (B1); existing recipes keep `MARKUP`, so no price moves (B2).

| Method | Path (`/api/v1` +) | Permission | Notes |
|--------|--------------------|------------|-------|
| PUT    | `/recipes/{id}/cover` | `RECIPE.RECIPE.UPDATE` | multipart `file`; JPEG/PNG/WebP by magic bytes (415), ≥ 600 px wide (400 `file`); stored as a recipe file + 400 px thumbnail + 800 px card |
| DELETE | `/recipes/{id}/cover` | `RECIPE.RECIPE.UPDATE` | clears the flag only; idempotent |
| GET    | `/recipe-sections` | `RECIPE.RECIPE.READ` | first call seeds the defaults and the sections already used in the caller's recipes |
| POST / PUT / DELETE | `/recipe-sections[/{id}]` | `RECIPE.RECIPE.UPDATE` | unique per user under the section key; ≤ 60 |
| PUT    | `/recipe-sections/order` | `RECIPE.RECIPE.UPDATE` | listed ids first; unknown or repeated ids → 400 `ids[i]` |
| POST   | `/recipe-sections/restore-defaults` | `RECIPE.RECIPE.UPDATE` | never renames or deletes |
| PATCH  | `/user-fixed-cost/{id}/status` | `FIXED_COST.FIXED_COST.MANAGE` | `{ isActive }` |
| POST   | `/user-fixed-cost/restore-defaults` | `FIXED_COST.FIXED_COST.MANAGE` | optional endpoint of §4.6 |
| GET    | `/baking-guide` | `GUIDE.GUIDE.READ` | `ETag` / `If-None-Match` → 304, `Cache-Control: private, max-age=300`, `Vary: Accept-Language` |
| POST / PUT / DELETE | `/baking-guide/pan-sizes[/{id}]` | `GUIDE.PAN.MANAGE` | own pans; SYSTEM → 403, another user's → 404 |
| CRUD   | `/baking-guide/admin/{articles,pan-sizes,conversions}` | `GUIDE.CONTENT.MANAGE` | staff; audited; bumps the revision |

Changed shapes: `RecipeRequest.pricingMethod`, `fixedCosts[].quantity`; `RecipeSummary/Detail.pricingMethod`,
`RecipeCost.pricingMethod`, `RecipeFixedCost.quantity`; `CostPreviewRequest.pricingMethod`,
`CostPreview.fixedCosts[]`; `UserFixedCostRequest/Response.appliesByDefault, monthlyAmount, monthlyBasis`.

## Layout

```
kitchen/
  costing/PricingMethod      MARKUP / MARGIN price and "achieved %" (quotes, ripple, stats share one rule)
  section/                   controller · service · CustomRepository · defaults (§10.1)
  fixedcost/                 seed catalog (§10.2) · seeder (lazy + on AccountCreated) · V22 backfill migration
  guide/                     controllers (reader, staff) · service · validator · language resolution · V23 seed migration
  shared/KitchenSettingsCustomRepository   per-user seeding ledger (user_kitchen_settings, row-locked)
```

Schema: `V21__baking_studio_schema.sql`. Seeds: `V22__seed_default_fixed_costs` (Java) and
`V23__baking_guide_seed` (Java, reads `db/seed/baking-guide.es-MX.json`, a verbatim copy of the UI's bundled
seed with its UUIDv5 ids; every item passes the staff API rules or the migration fails).

## Decisions and deviations from the contract

- **Revision keys** follow the existing `kitchen.revision.*` convention: `kitchen.revision.coverChanged` /
  `coverCleared` instead of `recipe.cover.changed|cleared`. Audit actions are `RECIPE_COVER_CHANGED|CLEARED`.
  `PATCH /recipes/{id}/files/{fileId}` with `isCover` writes the same revision and audit entry.
- **Optimistic lock vs attachments.** File and cover changes bump `recipes.version` (they write a revision), and
  the studio does not refresh `version` after them, so its next save used to fail with `CONCURRENT_MODIFICATION`.
  `PUT /recipes/{id}` and `PATCH /recipes/{id}/status` now accept a stale `version` when every revision since it is
  an attachment/cover revision; any other change since is still a 409.
- **Section key** is computed only by the database (`kitchen_section_key()` = `kitchen_fold` + whitespace collapse),
  both for labels and for `usageCount`. `name_key` is `VARCHAR(80)` because `unaccent` can expand a letter (æ → ae).
- **Fixed-cost delete.** A cost used by a live recipe → `409 RESOURCE_IN_USE` with `errors[0].details.recipes`
  (`ApiError.details` is a new optional field, omitted when empty). An unused cost is deleted for real; rows kept
  by soft-deleted recipes are detached first (quotes keep their own snapshot). Deactivation is now the PATCH.
- **Seeding** runs on every account-creation path through the `AccountCreated` event (sign-up, legacy admin,
  IAM admin, Google sign-in, data seeder) inside the creating transaction, plus a lazy safety net on
  `GET /user-fixed-cost`. Accounts with their own costs get the seeds inactive. With a default currency other
  than MXN (there are no exchange rates) seeds arrive inactive with amount 0.
- **PERCENTAGE costs** ignore `defaultAmount` (stored 0) and reject monthly figures; the old update path also
  dropped `percentage` changes, which is fixed.
- **Guide ordering**: articles by category (FOOD_SAFETY, TECHNIQUES, COSTING), then `displayOrder`; SYSTEM pans by
  `displayOrder`, then the caller's pans by name.
- **Guide ETag** hashes the revision, row count and last update of each SYSTEM table and of the caller's pans, and
  the resolved language, so deletions and same-day staff edits also change it.
- **User pans** get `code = USER_<32 hex>`; SYSTEM pan codes accept lower case after the first letter (the seed has
  `RECT_20x30`).
- **Plain text only (B5)**: titles, summaries, tags, sources and every block field reject `<` followed by a letter
  or `/`; a lone `<` ("3 < 4") is fine. Unknown block types are rejected when the body is read.
- **processHtml** keeps `li[data-list=ordered|bullet]` (Quill 2) so the book view can number steps.
- `GUIDE.PAN.MANAGE` and `GUIDE.CONTENT.MANAGE` depend on `GUIDE.GUIDE.READ` (there is no PAN/CONTENT READ).
  ADMIN, MANAGER and USER read the guide and keep pans; only ADMIN (and SUPER_ADMIN) manage SYSTEM content.

## Testing

- Unit: `CostEnginePricingTest` (MARKUP/MARGIN at 0 / 65 / 99.9 %, MARGIN 100 rejected, PER_UNIT quantity at scale
  0.5 / 1 / 2.4), `PricingMethodTest`, `RecipeMarginRulesTest` (ranges, ripple comparison per method),
  `RecipeCoverTest`, `RecipeSectionServiceTest`, `UserFixedCostServiceTest`, `FixedCostSeederTest`,
  `Guide*Test`, `ProcessHtmlSanitizerTest`.
- The new SQL was exercised end to end against PostgreSQL 16 (upgrade from a V20 database and a fresh one):
  seeding on creation and lazily, section usage counts and reorder, fixed-cost 409 with details, preview rows,
  guide ETag/304, localisation fallback, pan rules and staff validation. Cover uploads need object storage and are
  covered by unit tests with a mocked `StoragePort`.
- Coverage was not measured: the project has no JaCoCo plugin yet.
