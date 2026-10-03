# Unit catalog (unit types · measurement units · conversions · .xlsx imports)

System-wide master data every recipe, raw material, cost and quote depends on. A wrong factor
silently restates every cost computed with it, so the catalog is treated as controlled master data:
restricted writers, one rule set for every write path, database-enforced invariants, an immutable
audit trail and an append-only import ledger.

## Endpoints (`/api/v1` +)

Writes (✎) require `ROLE_SUPER_ADMIN` or the `MANAGE_CATALOGS` permission (seeded by V8, grant it to
a role through the admin roles API). Reads are open to any authenticated user.

| Method | Path | Notes |
|---|---|---|
| ✎ POST | `/unit-type` | `{codeIdentity, name, dimension}` |
| ✎ PUT | `/unit-type/{id}` | dimension locked once the type has units |
| GET | `/unit-type?search=&dimension=&status=&page=&size=&sort=` | paginated |
| GET | `/unit-type/catalog` | active types, not paginated, cached |
| GET | `/unit-type/{id}` | |
| ✎ DELETE | `/unit-type/{id}` | soft delete; rejected while it has units |
| ✎ PATCH | `/unit-type/{id}/status` | `{status}`; INACTIVE rejected while it has active units |
| ✎ POST | `/unit-type/import?dryRun=true\|false` | multipart `file` (.xlsx, sheet `UnitTypes`) |
| ✎ GET | `/unit-type/import/template` | .xlsx template |
| ✎ POST | `/measurement-unit` | `{codeIdentity, name, namePlural, unitTypeId, multiplierToBase, isBaseUnit}` |
| ✎ PUT | `/measurement-unit/{id}` | **was** `PUT /measurement-unit` with `id`/`status`/`userId` in the body |
| GET | `/measurement-unit?search=&unitTypeId=&dimension=&status=&page=&size=&sort=` | **was** `GET /measurement-unit/system`; rows carry `inUse` |
| GET | `/measurement-unit/catalog` | selectable units (active unit + active type), not paginated, cached |
| GET | `/measurement-unit/{id}` | |
| ✎ DELETE | `/measurement-unit/{id}` | new; soft delete |
| ✎ PATCH | `/measurement-unit/{id}/status` | `{status}` |
| POST | `/measurement-unit/convert` | `{quantity, fromUnitId, toUnitId, rawMaterialId?}` — nothing persisted |
| ✎ POST | `/measurement-unit/import?dryRun=true\|false` | multipart `file` (.xlsx, sheet `MeasurementUnits`) |
| ✎ GET | `/measurement-unit/import/template` | .xlsx template |
| ✎ GET | `/data-imports?resource=&status=&page=&size=` | import ledger, newest first |
| ✎ GET | `/data-imports/{batchId}` | the full report exactly as returned at import time |

Sortable keys are whitelisted (unknown → 400 instead of the former 500): unit types `id, codeIdentity,
name, dimension, status, createdAt, updatedAt`; measurement units additionally `namePlural,
multiplierToBase, unitType, dimension`.

## Model and rules

`multiplierToBase` = how many base units one of this unit is (`kg = 1000` when the base is `g`).
Linear conversion: `qty × from.multiplierToBase ÷ to.multiplierToBase`.

| Rule | Where it is enforced |
|---|---|
| `dimension ∈ {MASS, VOLUME, COUNT, LENGTH}` (temperature excluded: affine, not proportional) | enum + `chk_unit_types_dimension` |
| one unit type per dimension (so every unit of a dimension shares one base) | policy + `ex_unit_types_dimension` |
| unit-type code/name unique, case-insensitive, among non-deleted rows | policy + `ux_unit_types_code_lower`, `ex_unit_types_name_lower` |
| unit code unique and **case-sensitive** (`T` tablespoon ≠ `t` teaspoon); unit name unique, case-insensitive | policy + `ux_measurement_units_code`, `ex_measurement_units_name_lower` |
| factor > 0, ≤ 10 integer digits and 10 decimals | Bean Validation + policy + `chk_measurement_units_multiplier_positive` |
| exactly one base unit per type, with factor 1 | policy (`verifyBaseUnits`) + `ex_measurement_units_one_base_per_type` (deferred) + `chk_measurement_units_base_multiplier` |
| a unit **in use** (raw-material purchase unit, recipe line, density rule) keeps its type, factor and base flag, and cannot be deleted | policy, via `MeasurementUnitUsagePort` |
| `g`, `cup`, `tbsp`, `tsp` are reserved (raw-material density rules look them up by code) | policy (`ReservedUnitCodes`) |
| an inactive unit keeps converting existing data but can't be newly picked (recipes, raw materials) | `RecipeDetailService`, `RawMaterialServiceImplementation` |
| type dimension locked once it has units; type can't be deactivated/deleted while it has (active) units | policy |
| display text can't start with `= + - @` (spreadsheet formula injection on re-export) | policy |

All cross-record rules live in **one** class, `UnitCatalogPolicy`, evaluated on an in-memory snapshot
of the catalog (a few rows). The REST services and the import handlers both call it, so the two write
paths can never disagree. Every catalog write runs under a PostgreSQL advisory transaction lock
(`UnitCatalogLockPort`); the database constraints are the last line of defence against races
(a constraint race now answers 409, not 500). Constraints a multi-row import can legitimately cross
mid-flight (moving the base flag, swapping names) are `DEFERRABLE INITIALLY DEFERRED`.

## Conversions

`UnitConversionService` routes on `UnitDimension`: same unit → IDENTITY, same dimension → LINEAR,
MASS ⇄ VOLUME → DENSITY (needs the raw material's density rule), anything else → 409
`catalog.conversion.incompatible`. Fixed while touching it:

- dimensions used to be compared to the literal strings `MASA`/`VOLUMEN` while the UI suggested
  `mass`/`volume` — any type typed the "other" way silently disabled density conversions. V8
  normalizes existing values (and aborts listing any value it can't map);
- the density rule lookup was a single-result query although raw materials store up to three rules
  (cup, tbsp, tsp): any ingredient with two rules crashed every MASS ⇄ VOLUME conversion with a 500.
  It now prefers the rule measured in the requested volume unit, else the oldest one;
- intermediate scale raised from 6 to 10 decimals (0.5 mg expressed in kg used to round to 0.000001,
  a +100 % error on additives).

## Bulk import (.xlsx)

1. Upload with `dryRun=true` (default) → report `VALIDATED` or `REJECTED`, nothing written.
2. Upload the same file with `dryRun=false` → `COMMITTED` (all rows in one transaction) or `REJECTED`
   (nothing written). An unexpected failure rolls back, is recorded as `FAILED` and answers with the
   regular error envelope (`meta.traceId`).

Controls: extension, client content type, size (2 MB), container signature (`PK\3\4`; OLE2 = legacy
`.xls` or password-protected → rejected), macro-enabled workbooks rejected, POI zip-bomb guards
(inflate ratio, entry/text size), formulas never evaluated (cached result read; error cells reported),
2 000 data rows max, header matching tolerant of case/spaces/`*`, unknown columns reported as
warnings, NFC normalization and control-character stripping, `.`-only decimals (a comma is refused as
ambiguous), in-file duplicate keys, every `UnitCatalogPolicy` rule, row order irrelevant (base-unit
rule judged on the final state). Issues are collected exhaustively (never fail-fast), each with Excel
row, column, stable `code` and a message in the requester's language (`Accept-Language` es/en); the
report also lists each valid row with its action (`CREATE`/`UPDATE`/`UNCHANGED`) and field diff.

Traceability: SHA-256 of the file (a re-upload of a committed file is flagged), batch id, actor,
`traceId`, timings and the full report are stored in `data_import_batches` — append-only, enforced by a
trigger. A committed batch commits atomically with its data; validated/rejected/failed attempts are
recorded in their own transaction. Every created/updated record also gets its own `audit_log` entry
(`UNIT_TYPE_CREATED`, `MEASUREMENT_UNIT_UPDATED`, … with the field diff and `details = "Bulk import
batch <id>, row <n>"`), plus one `DATA_IMPORT_COMMITTED/REJECTED/FAILED` entry per attempt.

Files: templates in `src/main/resources/imports/templates/`; ready-to-load catalog in `docs/imports/`
(`01-unit-types.xlsx` first, then `02-measurement-units.xlsx`). Both are generated by
`scripts/imports/generate_unit_catalog_xlsx.py` — edit the catalog there and re-run, don't hand-edit
the binaries. If your database already holds unit types under other codes (e.g. `MASA`), either
change the `code` column of `01-unit-types.xlsx` / `unitTypeCode` of `02-…` to yours, or the import
reports `catalog.unitType.dimensionTaken` (one type per dimension) instead of creating duplicates.

## Deploying V8

V8 normalizes `unit_types.dimension`, then adds the constraints above. Each constraint is preceded by a
check that aborts the migration with an explicit message (offending codes/names/ids) if existing data
already violates it — fix those rows by hand and redeploy; nothing is guessed or deleted. It also
replaces the column-level `UNIQUE(code_identity)` of both tables with partial indexes on non-deleted
rows (a deleted unit's code can be reused).

Breaking API changes (coordinate with the Angular release): `PUT /measurement-unit` →
`PUT /measurement-unit/{id}` (body without `id`, `userId`, `status`), `GET /measurement-unit/system` →
`GET /measurement-unit`, unit-type `dimension` is now an enum, catalog writes need
`SUPER_ADMIN`/`MANAGE_CATALOGS`, errors of these endpoints are localized `CatalogException`s.

## Not covered here (follow-ups)

- `ingredient_conversions.user_id` and `measurement_units.user_id` are `bigint` while users are UUIDs
  (density rules are still written with a placeholder user id 1). Per-user units/rules need a
  migration of their own.
- `recipe_ingredients.unit_id` has no foreign key; adding one needs an orphan clean-up first.

## Related: SYSTEM categories and allergens

The same `CatalogAccess.CAN_MANAGE` rule now guards `POST /category/import` and every allergen write
(`POST /allergen`, `PUT /allergen/{id}`, `PATCH /allergen/{id}/status`, `POST /allergen/import`):
allergens have no owner (every row is shared and `create` produces system rows), so any write is
catalog maintenance. Both CSV imports are now all-or-nothing: `CsvCatalogFile` validates the whole
file first (required headers, strict UTF-8 with BOM support, 2 000-row limit, required/length/enum
checks, formula-injection guard, in-file duplicates) and answers **400** with one localized error per
problem (`"Line 3: …"`, `field` = column) instead of the former 500; nothing is written unless every
row is valid, and each committed import is recorded as `DATA_IMPORT_COMMITTED` in `audit_log`
(target `CATEGORY` / `ALLERGEN`).
