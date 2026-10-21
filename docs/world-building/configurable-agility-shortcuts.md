# Configurable agility shortcuts

October 20, 2026. Maintained Core implementation; private acceptance required before deployment.
This is an explicit runtime feature, not an editor import-time source patch. Existing
agility courses, quest shortcuts and rewards retain their existing plugins.

## Initial template

`jump-v1` handles a generic scenery placement with reserved action `agility-jump`
in either command slot. A new nonblocking **Agility jump** scenery definition uses
the existing `stonedisc` model; server XML and client definitions are paired. The
editor discovers it from the selected server/client's own definitions, not from
an editor-maintained content ID list. Ordinary jumps and quest commands do not
grant generic override permission.

Each placement can carry its own requirements, chance curve, and absolute success
and failure destinations. This initial template faces the object, announces the
jump, and completes after one game tick. It awards **zero XP** and deals **zero
failure damage**, avoiding repeatable XP exploits while the generic workflow is
proved. Dedicated crawl/swing/balance animations and configurable rewards are
not implemented aliases.

## Four chance controls

Required level and maximum-chance level are integers 1–99. Percentages are
integers 0–100. Starting chance cannot exceed maximum chance. Maximum level must
exceed required level unless the chance is flat; a flat curve can use equal
levels. Below the requirement there is no attempt, random roll or failure landing.
Use current effective Agility, including boosts/drains. No cape bypass overrides
the configured maximum.

For positive level span, chance is calculated in basis points:

```
startPercent * 100
  + floor((maximumPercent - startPercent) * 100
      * min(effectiveLevel - requiredLevel, maximumLevel - requiredLevel)
      / (maximumLevel - requiredLevel))
```

An independent uniform roll 0–9999 succeeds strictly below that result. Thus 0%
always fails, 100% always succeeds, and fractional percent interpolation is not
lost to early rounding. The proposed new-template defaults are level 1, 25%, 95%,
level 31. The 30-level span follows the existing shortcut convention, but these
are deliberately chosen **linear defaults**, not the legacy nonlinear formula.

## Placement and runtime contract

The optional scenery-only `agilityShortcut` requires placement schema version 5,
encoding `layered-world-placements-v5`. Existing v1–v4 payloads remain unchanged;
older payloads must not contain this critical field. Every nested key is strict:

```json
{
  "schemaVersion": 1,
  "template": "jump-v1",
  "action": "agility-jump",
  "requiredLevel": 30,
  "startChancePercent": 60,
  "maxChancePercent": 95,
  "maxChanceLevel": 65,
  "successDestination": {"worldSpace": "global", "level": 2, "x": 300, "y": 400},
  "failureDestination": {"worldSpace": "global", "level": 0, "x": 300, "y": 401}
}
```

Both destinations are required, even at 100% success. Global X/Y are 1–32766;
levels are signed integers and must actually have package-owned terrain. The
behavior is resolved through the object's package, generation and stable
placement identity, not its object ID alone. Runtime replacement or removal
invalidates a pending action. No configuration on a generic placement gives a
short explanatory message, never an arbitrary legacy plugin action.

The installed `core.jar` embeds `map-runtime/agility-shortcuts-v1.json` through
the standard build's `runtime-contract` resource copy. This bounded declarative
capability declares encoding, template/action, skill and coordinate ranges,
landing rule and chosen defaults. Editor support must read this resource from the
selected installed archive, retain its whole-JAR hash, and recheck the actual
target archive at import. Source files or a loose copied JSON capability alone
are not proof that an installed runtime can execute the feature. Ordinary map
imports must not install runtime code. Classic map encoding does not support this
metadata; the rest of classic editing remains available.

## Safe floor rule

`homogeneous-full-floor-3x3-v1` deliberately proves more than walkability:

- All nine surrounding tiles must be authored in the **same package** as the
  obstacle, with identical raw floor overlay and base color.
- Overlay 0 is valid only on base-color levels (not native levels 1/2); 250 and
  255 are refused. Positive overlays need a discovered definition with server
  `objectType == 0` (movement), `unknown` in 0–2 (visual type), and color other
  than the transparent `12345678` sentinel. Water, bridges, invisible floors,
  partial seams and missing definitions cannot be used as ambiguous landings.
- The 3×3 area must have no terrain walls or diagonal fields. Far-side landing
  boundary anchors and authored scenery/boundary collision are also checked.
- Runtime checks the effective collision of all nine tiles again at action time,
  before movement. Dynamic blocking scenery can therefore cancel a previously
  valid jump. Players/NPCs are not themselves treated as static blocked floors.

Startup preflight validates both destinations before population. The player's
actual approach tile is checked at action start and completion, as are both
landings. The rule is conservative: build a complete clear landing pad rather
than guessing whether an upper-level void or half-floor is safe. The editor may
reject an even more conservative boundary neighborhood; it never weakens the
authoritative server check.

## Cancellation and action ownership

Only one configured jump can be pending per player. Busy state prevents repeat
clicks. The scheduled callback checks the current token, login/alive/combat state,
current effective level, unchanged start location, stable object and terrain
before making its single roll. Resetting player actions removes the token and
releases only that jump's busy state. Old callbacks cannot teleport later or
release a newer action. The scheduler allows separately token-fenced callbacks
to coexist for one tick, allowing cancellation followed by an immediate retry;
enqueue failure releases the token rather than leaving the player stuck.

## Verification

`bash scripts/test-configurable-agility.sh` compiles maintained Core and runs the
deterministic parser/chance/full-floor/action-lifecycle suite. A separate real
World/Player/packet/scheduler fixture uses the production classes without starting
network listeners or reading accounts. Prepare a fresh disposable fixture with:

```
python3 tests/myworld/prepare-configurable-agility-fixture.py /absolute/fresh/evidence/fixture
ANT_OPTS='-Xmx512m -XX:+UseSerialGC' bash tools/vendor/apache-ant-1.10.5/bin/ant \
  -f server/build.xml -Dagility.fixture.dir=/absolute/fresh/evidence/fixture \
  test_configurable_agility_integration
```

No public-server restart, release or map change is part of these tests. Full
editor save/reopen/export/import and owner private gameplay review are separate
acceptance gates coordinated by Core.
