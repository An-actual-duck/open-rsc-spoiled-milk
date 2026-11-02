# Silver and Moonsilver sigils

The owner selected Moonsilver (silver + mithril), retained direct Stone carving,
and moved both metal grades to **bar → three blanks → carving → charging**.
The owner also approved Greater Unify's3 Stone Grace +3 Silver Grace cost and
the revised three-recess mould artwork. These decisions replace the earlier
Silver-nugget carving route and temporary private-build spell-cost defaults.

## Player flow and current defaults

1. At an ordinary furnace, choose **Bars → Moonsilver bar** to alloy one
   unnoted Silver bar and one unnoted Mithril bar into one Moonsilver bar.
2. Buy a reusable **Sigil mould** from Dommik in Al Kharid or Rommik in
   Rimmington. Choose **Blank sigils** at a furnace, or use the mould/bar on
   the furnace. Using a Silver bar also retains the existing silver-symbol
   choices and adds the blank-sigil recipe.
3. One Silver or Moonsilver bar makes **three** matching nonstackable blanks.
   Two additional inventory spaces are required; the mould is not consumed.
4. Use a chisel on each blank and select Saradomin, Guthix, Zamorak, Grace or
   Blight. Each blank makes one carved sigil.
5. Bless god carvings at their matching altar; dip Grace in Divine wellsprings
   and Blight in Ichor wells. Increased output and diminishing XP use the
   existing altar/well formula.

| Action | Requirement | Base displayed XP | Internal XP units |
| --- | --- | --- | --- |
| Alloy one Moonsilver bar | Smithing 38 | 15 Smithing | 60 |
| Mould three Silver blanks | Crafting 20 | 10 Crafting | 40 |
| Mould three Moonsilver blanks | Crafting 40 | 15 Crafting | 60 |
| Carve one Silver blank | Crafting 20 | 10 Crafting | 40 |
| Carve one Moonsilver blank | Crafting 40 | 15 Crafting | 60 |
| Charge one Silver carving | Blessing 16 | 10 Blessing before duplication adjustment | 40 base |
| Charge one Moonsilver carving | Blessing 40 | 15 Blessing before duplication adjustment | 60 base |

The owner specified the material, production steps and three-blank output.
The1:1 alloy yield, levels and XP above are manager starting defaults, not a
claim that the owner supplied these numbers. Moonsilver bars are tradeable
and noteable like ordinary bars, with base price 450 (Silver 150 + Mithril 300).
Blanks and carved sigils are nonstackable/non-noteable; charged sigils stack.
The mould's base price is 50, with normal shop pricing and four in each shop.

Stone's Rune-stone input, chisel interface, levels, XP, blessing rules and
artwork remain unchanged. Existing Silver carvings/charged stocks keep their
identities, artwork and effects. Existing raw Silver nuggets still smelt into
Silver bars normally; they no longer carve directly into sigils. No stock
conversion, new ore placement or map migration is required.

## Casting costs

| Tier | Matching god resource | Purpose resource |
| --- | --- | --- |
| 1 | 1 Stone | 1 Stone |
| 2 | 1 Stone +1 Silver | 1 Stone +1 Silver |
| 3 | 1 Silver +1 Moonsilver | 1 Silver +1 Moonsilver |

The purpose is Grace for blessings and Blight for hostile spells. Tier 3 spends
no Stone. The full vector is checked and spent atomically once per useful cast,
not once per recipient. Unify costs2 Stone Grace; Greater Unify costs3 Stone
Grace +3 Silver Grace. The active roster remains17 spells; this does not enable
Zamorak's deferred hostile chain or Tier 4.

The authoritative cost object, cast admission and native book payload use the
same material vector. Existing schema 4 already carries up to four resources;
the decoder, inventory availability checks and guide/tooltip presentation use
that list without a protocol/schema change. Legacy client compatibility
accessors retain their old API and have no production cost consumers.

## Identities and assets

| IDs | Contents |
| --- | --- |
| 3430 | Moonsilver bar |
| 3431 /3432 | Blank Silver Sigils / Blank Moonsilver Sigils |
| 3433 | Sigil mould |
| 3434 /3435 | Carved/charged Moonsilver Saradomin |
| 3436 /3437 | Carved/charged Moonsilver Guthix |
| 3438 /3439 | Carved/charged Moonsilver Zamorak |
| 3440 /3441 | Carved/infused Moonsilver Grace |
| 3442 /3443 | Carved/infused Moonsilver Blight |

Existing Silver IDs 3301–3308/3428–3429 and all Stone IDs are unchanged.
The legacy ItemId enum is not enlarged. JSON definitions and native client
external-PNG selectors supply the new content; the standalone editor discovers
these game-owned definitions/assets without a built-in content roster.

The bar retains the native Silver-sized 48×32 canvas and silhouette. Sigils retain
the Silver source 28×25 canvas and 24×21 presentation. Only substrate tones are
recolored from dark blue through silvery blue; existing glyphs and alpha are
preserved. Fourteen editable Aseprite sources, reproducible authoring and exact
maintained hashes are recorded in
[`dev/myworld/assets/authoring/items/moonsilver-sigils`](../../../dev/myworld/assets/authoring/items/moonsilver-sigils/README.md).
Original Stone/Silver PNGs and native archives are untouched.

## Transaction and validation boundary

Moulding checks the actual current session, recipe, material, unnoted mould,
skill, fatigue, output admission, furnace reach and Shilo access. Those checks
are repeated after the delay, before consumption. One exact bar becomes three
complete output instances under the inventory lock; insufficient space or
changed admission preserves the input. Unprocessed bars remain after a partial
batch. The mould is a reusable prerequisite, never a consumed ingredient.
Alloy output also rechecks furnace reach/skill/client/world admission after its
delay, before spending either ingredient. Other bar recipes keep their prior
behavior.

The separate native metal-sigil fixture needs a disposable prepared map, like
the existing well fixtures, and is intentionally excluded from the general
combat-fixture compilation target. Its own Python entrypoint compiles it
against the actual server/plugins and a fresh isolated fixture. No repository
test assertion is skipped or patched externally.

Worker evidence is retained under `output/reviews/moonsilver-sigil-review/`. Exact
commands/results are below; builds and fixtures run sequentially with Java8,
`ANT_OPTS='-Xmx512m -XX:+UseSerialGC'`, and 512 MiB or smaller fixture heaps.

| Command | Result |
| --- | --- |
| `bash scripts/build-server.sh` | PASS core, gameplay overlay, plugins and shipped-build audit |
| `bash scripts/build-client.sh` | PASS matching client and all native assets |
| `python3 tests/myworld/test-cleric-metal-sigils.py` | PASS: 194 native production, exact inventory, cancellation/admission, charging and four-resource atomic-cast assertions |
| `python3 tests/myworld/test-cleric-sigil-production.py` | PASS C05 production/level/XP/Devotion model |
| `python3 tests/myworld/test-cleric-well-infusion.py` | PASS: 439 native packet, six grade/purpose combinations, 40/50-level boundaries, yield, identity and overflow checks |
| `python3 tests/myworld/test-cleric-consistency-buffs.py` | PASS: 88 native consistency assertions and packet export |
| `python3 tests/myworld/test-cleric-hostile.py` | PASS: 121 native hostile/phase assertions and packet export |
| `python3 tests/myworld/test-cleric-blessings.py` | PASS: 373 active catalog/packet/cost/composite/lifecycle/combat/Unify assertions |
| `python3 tests/myworld/test-cleric-three-tier-client.py --wire /tmp/cleric-schema4.bin --render output/reviews/moonsilver-sigil-review/client-proof` | PASS: 6,551 actual-packet/client checks (5,068 truncations and 1,483 other assertions), 68 generic HUD assertions, native-font render bounds |
| `python3 tests/myworld/test-cleric-sigil-item-assets.py` | PASS maintained receipts, old/native artwork, 3,444 definitions, all 14 new source/packaged sprites, render scales and canonical fallback IDs |
| `python3 tests/myworld/test-furnace-mould-flow.py` | PASS unchanged existing furnace category flow |
| `python3 tests/myworld/test-smithing-production-coverage.py` | PASS existing Smithing production coverage |
| `sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml test_combat` | PASS: 145/145 combat characterization scenarios |
| `git diff --check` | PASS |

The two independent native packet producers emitted identical 5,068-byte
seventeen-spell schema 4 payloads, SHA-256
`46e1f9fa1c765b6b09ea9a07bce9fed02c7289d026d7c18c09a18730ca08707e`.
The checked-in schema4 fixture is refreshed from those real bytes. Earlier
schema fixtures are preserved for rejection/compatibility checks. Native guide
images for Saradomin Tier 3 and Greater Unify were visually checked: all four
resource names and both neutral grades wrap without clipping.

The two existing native assertions expecting 2 Silver Blight and 6 Stone Grace
were updated to the newly approved 1 Silver Blight and 3 Stone + 3 Silver Grace
contracts. Existing production/asset tests now expect blank inputs, the third
grade and appended item identities. No assertion is bypassed or externally
patched, and no inherited failure is presented as passing.

No public/private server listener was started, no account database accessed,
and no owner map/export/import or deployment was performed. Artwork approval
is recorded; the new production flow's private manual gameplay acceptance
remains unrun.
