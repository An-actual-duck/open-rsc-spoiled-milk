# Frozen launch metadata for generic regression fixtures

`LegacyClericSpellFixtureCatalog` and `LegacyClericEffectFixtureCatalog` preserve
launch metadata from9f8118dc09b99b767b7f0f847dacffce7d9baaeb for the existing
C08/C09/C10 pure generic registry/counter/arithmetic characterizations. Their
names explicitly differ from the active catalogs. Only fixture compilation
includes these files; neither production build nor active runtime can load them.
Their validation refers to this frozen catalog, not retired active-book requests.

This retains existing replacement/expiry/origin/counter/concurrency/fractional
math coverage while the active catalog rejects all retired spell codes. The
C09/C10 entrypoints additionally run `test-cleric-blessings.py` against the actual
new server packet/runtime paths. Foundation acceptance checks the new eleven
active spells and all approved component values. Generic status inventory
bounds/order coverage is retained separately by `test-cleric-status-inventory.py`.
