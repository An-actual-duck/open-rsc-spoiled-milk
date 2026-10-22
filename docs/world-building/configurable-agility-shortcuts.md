# Configurable agility shortcuts

October 20, 2026. Maintained Core implementation; private acceptance required before deployment.
This is an explicit runtime feature, not an editor import-time source patch. Existing
agility courses, quest shortcuts and rewards retain their existing plugins.

## Supported templates

`jump-v1` handles a generic scenery placement with reserved action `agility-jump`
in either command slot. A new nonblocking **Agility jump** scenery definition uses
the existing `stonedisc` model; server XML and client definitions are paired. The
editor discovers it from the selected server/client's own definitions, not from
an editor-maintained content ID list. Ordinary jumps and quest commands do not
grant generic override permission.

Each placement carries its own requirements, chance curve, and absolute success
and failure destinations. October 21 expansion adds these exact template/action
pairs, with separately appended, paired server/client scenery definitions:

| Template | Reserved action | Existing model | Footprint | Delay |
| --- | --- | --- | --- | --- |
| jump-v1 | agility-jump | stonedisc | 1×1 | 1 tick |
| crawl-v1 | agility-crawl | obstical_pipe | 1×1 | 2 ticks |
| climb-v1 | agility-climb | obstical_net | 2×1 | 3 ticks |
| swing-v1 | agility-swing | obstical_ropeswing | 1×1 | 2 ticks |
| balance-v1 | agility-balance | log_balance1 | 1×1 | 3 ticks |

All five face the object, announce the activity, wait their template's delay,
then teleport to the rolled destination. They award **zero XP**, deal **zero
failure damage**, and do not claim unique sprite/motion animations. Their delays
are inspired by maintained Yanille pipe (2), Gnome net (3), Yanille/Edge swing
(2), and Yanille ledge (3) code. Those authentic actions likewise use messages,
delays and teleports. Existing staged Gnome log/rope routines are not replaced.
New failure messages do not presume that a configured destination is below,
short of, or back at the obstacle. Jump retains its original text and behavior.

Generic definitions are nonblocking, including the net's native 2×1 footprint;
they are authoring tools, not overrides of existing quest/course objects. The
editor reads their definitions and installed capability without an object-ID
roster. Template and action must form an exact pair; cross-pairs are refused.

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

`homogeneous-full-floor-3x3-wall-adjacent-v2` deliberately proves more than walkability:

- All nine surrounding tiles must be authored in the **same package** as the
  obstacle, with identical raw floor overlay and base color.
- Overlay 0 is valid only on base-color levels (not native levels 1/2); 250 and
  255 are refused. Positive overlays need a discovered definition with server
  `objectType == 0` (movement), `unknown` in 0–2 (visual type), and color other
  than the transparent `12345678` sentinel. Water, bridges, invisible floors,
  partial seams and missing definitions cannot be used as ambiguous landings.
- Cardinal walls and edge-only scenery (including fences) may border the actual
  approach or landing tile. A pipe exit beside a wall is a sensible destination.
  Raw diagonal/embedded fields on the actual tile are still refused. Authored
  placements use the native registration collision planner, not a radius-based
  wall exclusion: full blocking scenery and diagonal boundary collision may not
  occupy the actual tile. Nearby unrelated obstacles do not block a landing.
- Runtime checks the actual tile's effective `FULL_BLOCK` and `OBJECT` collision
  flags again at action time, before movement, while allowing cardinal edge
  flags. Dynamic blocking scenery can therefore cancel a previously
  valid jump. Players/NPCs are not themselves treated as static blocked floors.

Startup preflight validates both destinations before population. The player's
actual approach tile is checked at action start and completion, as are both
landings. The floor rule is conservative: build a complete matching landing pad rather
than guessing whether an upper-level void or half-floor is safe. The editor may
reject an even more conservative boundary neighborhood; it never weakens the
authoritative server check. The former `homogeneous-full-floor-3x3-v1` resource
required a completely wall-free neighborhood; editors must retain that stricter
behavior when targeting an older installed runtime. Existing placement metadata
is unchanged; this resource advertises the corrected policy only after rebuilding
and deliberately installing the server runtime.

## Cancellation and action ownership

Only one configured agility action can be pending per player. Busy state prevents repeat
clicks. The scheduled callback checks the current token, login/alive/combat state,
current effective level, unchanged start location, stable object and terrain
before making its single roll. Resetting player actions removes the token and
releases only that jump's busy state. Old callbacks cannot teleport later or
release a newer action. The scheduler allows separately token-fenced callbacks
to coexist during their token-fenced delay, allowing cancellation followed by an immediate retry;
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
