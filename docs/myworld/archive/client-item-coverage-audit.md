# Client Item Coverage Audit

## Purpose

Track server-side item content that exists in live MyWorld defs but is still
missing from the client item table in
[EntityHandler.java](/home/justin/Core-Framework/Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java).

This is the authoritative place to record client-facing item-definition gaps
that currently render as `Unobtanium` or otherwise crash/mislead the client.

## Current Snapshot

From:

```bash
python3 tests/myworld/audit_client_item_coverage.py
```

Current totals:

- Server item ids: `2238`
- Client item ids: `2145`
- Missing on client: `93`
- Missing wearable/usable items: `0`

Top missing id bands:

- `1700-1799`: `25`
- `2000-2099`: `49`
- `2100-2199`: `11`
- `2200-2299`: `8`

## Highest-Priority Gap

The highest-risk live wearable/usable gap is currently closed. The remaining
client gaps are still worth sweeping because they can mislead searches,
administrative inspection, or future content placement, but the audit no longer
shows wearable/usable `Unobtanium` risk.

Priority bands:

1. `2000-2109`
   - enchanted wool magic gear
2. `2200-2237`
   - newer utility/tool-family additions such as shears and mould-adjacent
     custom content
3. Remaining non-wearable client gaps
   - mostly custom-family consistency and full ID coverage

## Immediate Fixes Already Made

- Added client coverage for the full enchanted jewelry block `1593-1763`.
- Updated the client mirror for the renamed altar-enchanted jewelry and staff
  naming conventions.
- Added client coverage for the remaining live wearable/usable gaps:
  - `2053` Wool cape
  - `2224-2227` custom square shields
  - `2228-2237` blessed staves
- Removed the temporary admin helper that seeded those items into bank before
  the client knew how to render them.
- Replaced old vanilla elemental staff names with rune-first standard-staff
  products in both server and client definitions, while keeping battlestaff,
  enchanted battlestaff, and orb IDs retired as inert compatibility records.

## Recommended Order

1. Keep the audit at `0` missing wearable/usable items before adding any
   admin item-seeding helpers or live reward sources.
2. Sweep `2000+` MyWorld client items in authored families instead of one-off
   fixes.
3. Re-run the audit after each new item-family pass and update this snapshot.

## Notes

- Server support alone is not sufficient for live testing.
- Any admin/testing command that seeds new wearable or usable items should be
  blocked until the client coverage audit is clean for that family.
