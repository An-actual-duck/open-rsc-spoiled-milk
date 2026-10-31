# Grace and Blight well infusion — October 30, 2026

Implemented for Core review. No server was launched, no map imported or edited,
and no owner project, account data, staged update or live deployment was changed.
This completes well production; the Cleric spellbook overhaul is a separate task.

## Confirmed rules and resulting behavior

The owner requested functional dipping after authoring the well areas, and
confirmed **the existing altar's increased yield**. Purpose wells have no god
and charge **no Devotion**. All three god-sigil altar recipes, their exact
Devotion charge, and their yield/XP remain unchanged.

| Material | Carving source + chisel | Crafting level / XP | Infusion Blessing level / base XP |
|---|---|---|---|
| Stone | Rune stone1299 | 1 /5 | 1 /5 |
| Silver | Silver nugget383 | 20 /10 | 16 /10 |

Both Grace and Blight use these material rules. Each carving produces one
uninfused, nonstackable sigil. Infused sigils stack; neither form is noteable.
The ordinary graphical crafting selection now contains the three god symbols,
Grace and Blight for each material.

Grace uses **Divine wellspring1355**; Blight uses **Ichor well1354**. Use **Dip
sigils** to process both matching grades, independently. Using an uninfused
sigil on its matching well processes all matching stock of that selected grade.
Wrong wells, god sigils, already infused sigils, notes and unrelated items are
refused without consumption or XP. If one grade is underlevel or its output is
not supported, the other valid grade can still be processed.

For Blessing level `L`, material requirement `R`, input count `N`:

- Below `R`, no production.
- Output multiplier `M = 1 + floor((L-R)/10)`; output count `N*M`.
- Each input grants the same existing altar diminishing XP: base XP multiplied
  by `1 + 1/2 + 1/4 + ...` for `M` terms. The shared catalog computes the exact
  batch internal XP with the existing rounding; internal XP is four times the
  displayed XP. No new arithmetic or alternate rounding is introduced.
- Example: Blessing11, three stone carvings → six infused sigils and90 internal
  XP (22.5 displayed). Blessing16, three silver carvings → three infused sigils
  and120 internal XP (30 displayed).

Successful conversion uses the existing atomic inventory exchange. A full bag
can reuse a source slot/persistent item ID; compatible output stacks are reused.
Overflow, world/client item limits and stack caps are checked before mutation.
XP is awarded only after each successful grade's committed exchange.

Normal land-side reach, walls, registered-object identity, world space and level
are rechecked under the player lock. Use-item additionally requires the actual
captured inventory instance to remain present, including after queued walking.
Fatigue uses the existing production policy. No teleporting or bypassing blocked
shores is added. The current owner Ichor placements need walkable approaches;
that authoring correction is deliberately outside this task.

## Identities, stock and art

| Form | Grace: preserved identity | Blight: new identity |
|---|---|---|
| Uninfused stone |3299 |3426 |
| Infused stone |3300 |3427 |
| Uninfused silver |3307 |3428 |
| Infused silver |3308 |3429 |

Grace changes only player-facing item names/descriptions. All existing quantities,
readiness, properties and art remain unchanged. The original sixteen
`ClericSigilItemId` values and internal NEUTRAL compatibility remain intact.
Blight is a **purpose**, not an added god alignment.

The four new Blight assets use the approved Thorn design in
`dev/myworld/blight-sigil-candidate`. Stone pixels are exact approved pixels;
silver changes only the established eight-tone substrate palette. All44 glyph
pixels and alpha remain unchanged. Editable Aseprite documents and derivation
script are in `dev/myworld/blight-sigil-production`; its manifest records hashes.

The established external-PNG item selector supplies local and packaged sprites,
with canonical native fallback443/134. Stone renders28×25; silver renders24×21
using the existing nearest-neighbor presentation. No native sprite address,
archive rewrite, toolkit item roster or custom discovery override was added.
All17 original sigil PNGs and all three native archives are byte-identical.
Selected-server definition discovery can enumerate the four ordinary new item
rows and resolve their game-owned sprite selectors.

Current casting remains the twelve legacy spells with their existing costs,
effects, alignments and categories. The guide calls the existing neutral resource
**Grace**. God-plus-Grace combined costs and Blight debuff casting are not part
of this implementation.

## Validation

All commands run in the worker checkout, sequentially. Source builds use the
maintained Java8 toolchain, `ANT_OPTS='-Xmx512m -XX:+UseSerialGC'` and bundled Ant.

- `ant -f server/build.xml compile`: server and plugins build passed.
- `bash scripts/build-client.sh`: client build passed, including guide changes.
- `python3 tests/myworld/test-cleric-well-infusion.py`:223 runtime assertions.
  Real native World/Player/immutable terrain, actual object-action and
  inventory-on-scenery packets through walking/plugin dispatch;36 grade/level
  cases; independent output/XP example; all three Devotion balances; both-grade
  and partial-grade handling; actual Blight carving/level/XP; all three matched
  and mismatched god altars; Grace altar refusal; real queued same-catalog stale
  item refusal; wrong-purpose/infused/god/unrelated/notes/fatigue; full bag;
  client/world IDs, short-stack cap and integer overflow. Fresh tiny fixture;
  no game loop/listeners/account database.
- `python3 tests/myworld/test-cleric-well-spots.py`:364 runtime admission checks;
  1420 software and1428 GL-capture native picking/occlusion/cache checks.
  Empty-inventory guidance remains inert; actual production is covered above.
- `python3 tests/myworld/test-blight-sigil-assets.py`: four ASE→PNG roundtrips,
  approved stone/44 glyph preservation, silver derivation, server rows, packaged
  bytes and unchanged old PNG/native archives passed.
- `ANT_OPTS='-Xmx512m -XX:+UseSerialGC' python3 tests/myworld/test-cleric-sigil-item-assets.py`:
  original sixteen identity/property/art checks plus actual new client
  local/classpath sprites, native fallback and render bounds passed.
- `python3 tests/myworld/test-cleric-sigil-production.py`: shared catalog/Devotion
  and production boundary regression passed.
- `python3 tests/myworld/test-cleric-blessing-skill-platform.py`: append-only
  skill/storage/ranking checks passed; approved wells added to narrow scope.
- `python3 tests/myworld/test-cleric-worship-guide.py`: guide values/layout passed.
- `python3 tests/myworld/test-cleric-spellbook-foundation.py`,
  `test-cleric-spellbook-presentation.py`, and
  `test-cleric-support-cast-transaction.py`: legacy book/cost compatibility passed.
- `git diff --check`: passed.

The old pure-catalog fixtures now omit the server-dependent well admission helper
when compiling standalone. Protocol-version assertions follow the already
maintained `NpcCapacityContract` indirection instead of expecting a literal in
Config.java. These are fixture repairs, not runtime/protocol changes.

Evidence logs are `/tmp/grace-blight-{server-build-final,client-build-final,
infusion-fixture-final,well-spots-final,item-assets-final,production,
blessing-platform,worship-guide,foundation,presentation,support-casting}.log`.
Private gameplay/owner acceptance and final integration/staging remain Core's
gates; this worker did not activate any deployment.
