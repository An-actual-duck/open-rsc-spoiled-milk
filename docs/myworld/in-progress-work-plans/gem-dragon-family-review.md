# Gem dragon family — implementation review

This is a Core candidate, not a live release. NPC IDs 877–881 and equipment IDs
3408–3409 are new. IDs 3400–3407 remain reserved for antidote reconciliation;
they are not filled with new gameplay items. Future equipment allocation must
start after 3409 (and check other active branches before reserving IDs).

## Content and initial balance

The owner-approved hierarchy is Sapphire < Emerald < Ruby < Diamond <
Dragonstone. Ordinary green through black dragons are only a broad power band,
not required stat matches. Internal crystallization increases physical defense;
it gives no extra magic defense. Bodies remain green/yellow and only the gems
are recolored. Descriptions explain that the dragon is slowly turning to its
respective gemstone from within.

| Variant / NPC ID | Attack | Strength | HP | Base defense | Melee defense | Ranged defense | Magic defense | Combat level |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Sapphire / 877 | 100 | 90 | 100 | 100 | 125 | 93 | 100 | 97 |
| Emerald / 878 | 122 | 110 | 120 | 123 | 153 | 115 | 123 | 119 |
| Ruby / 879 | 146 | 132 | 143 | 148 | 185 | 138 | 148 | 142 |
| Diamond / 880 | 174 | 153 | 166 | 173 | 216 | 162 | 173 | 166 |
| Dragonstone / 881 | 202 | 178 | 190 | 198 | 247 | 185 | 198 | 192 |

Physical multipliers are 1.25 melee and 0.9375 ranged, versus ordinary dragons'
1.0 and 0.75: a 25% increase before integer flooring. Magic stays 1.0. Lower
offensive/HP starting stats account for their added physical durability. These
are starting values for gameplay review, not claims of measured kill times.
Respawn is the ordinary 75 seconds. Existing dragon attack-style selection and
path validation apply. Initial fire maxima scale 45/48/51/54/57 and reuse the
existing anti-dragon equipment reduction path; protected fire receives the
ordinary low, HP-based outcome. No quest-only Elvarg hooks, new access gates,
new hides or permanent spawns are added.

## Crafting loot

**Updated October 28, 2026: these guaranteed-gem rules supersede the original
optional gem roll, variable lower-tier clusters and one-own-gem cap.** Each
gem dragon now makes one separate guaranteed roll, choosing one uncut gem type
and quantity. Sapphire, emerald, ruby and diamond can each occur as 1, 3 or 5,
provided the gem is at or below that dragon's tier. No opal, jade or red topaz
is included. Dragonstone Dragons alone add one dragonstone, never 3 or 5.

Rarity rank is gem tier (Sapphire 0 through Diamond 3) plus quantity rank
(1 → 0, 3 → 1, 5 → 2). Initial rank weights are 64, 32, 16, 8, 4, 2. The single
Dragonstone outcome has weight 1, so it is individually rarer than Diamond 5.

| Guaranteed outcome | Sapphire Dragon | Emerald Dragon | Ruby Dragon | Diamond Dragon | Dragonstone Dragon |
|---|---:|---:|---:|---:|---:|
| Sapphire 1 | 64 | 64 | 64 | 64 | 64 |
| Sapphire 3 | 32 | 32 | 32 | 32 | 32 |
| Sapphire 5 | 16 | 16 | 16 | 16 | 16 |
| Emerald 1 | — | 32 | 32 | 32 | 32 |
| Emerald 3 | — | 16 | 16 | 16 | 16 |
| Emerald 5 | — | 8 | 8 | 8 | 8 |
| Ruby 1 | — | — | 16 | 16 | 16 |
| Ruby 3 | — | — | 8 | 8 | 8 |
| Ruby 5 | — | — | 4 | 4 | 4 |
| Diamond 1 | — | — | — | 8 | 8 |
| Diamond 3 | — | — | — | 4 | 4 |
| Diamond 5 | — | — | — | 2 | 2 |
| Dragonstone 1 | — | — | — | — | 1 |
| **Total weight** | **112** | **168** | **196** | **210** | **211** |

Entries are per-outcome weights, normalized by the selected dragon's total.
Equal-rank outcomes have equal probability **within that dragon's table**;
this does not mean equal absolute probability across different dragons.
For example Sapphire 5, Emerald 3 and Ruby 1 each have weight 16 in a Ruby
Dragon's table. Sapphire 1 is 64/112 on a Sapphire Dragon and 64/211 on a
Dragonstone Dragon. The lowest four dragons can drop 3 or 5 of their own gem;
the old own-gem cap is deliberately removed. Single gems are unnoted and 3/5
rewards are noted, retaining the previous cluster convention.

The guaranteed roll is independent of the ordinary 128-weight material table:

| Ordinary material roll | Weight | Quantity |
|---|---:|---|
| Gold bars | 28 | 2 / 3 / 4 / 5 / 6 |
| Plain gold ring | 8 | 1 |
| Own-tier unenchanted gem ring | 8 | 1 |
| Nothing | 84 | — |

Material probabilities, unique probabilities and standard dragon bones are
unchanged. No generic gem/rare-drop table or hide is added. The Cosmic necklace
can still reroll ordinary materials, but cannot repeat the separate guaranteed
gem roll. Death-necklace guaranteed-drop bonuses still apply to bones and other
ordinary guarantees, but do not turn a 1/3/5 gem outcome into another quantity
or produce more than one dragonstone. The new gem delivery reuses ordinary
NPC delivery helpers and their item restrictions, recipient ownership, personal
loot flags and banking behavior. Existing Law-necklace rules bank a single
unnoted gem; noted 3/5 rewards stay intact on the ground. A personal-loot
recipient gets one guaranteed gem outcome without contribution-chance gating.

Both unique items use the following independent per-kill chances:

| Dragon | Gloves | Monocle | Chance of either, approximately |
|---|---:|---:|---:|
| Sapphire | 1/2048 | 1/2048 | 1/1024 |
| Emerald | 1/1536 | 1/1536 | 1/768 |
| Ruby | 1/1024 | 1/1024 | 1/512 |
| Diamond | 1/768 | 1/768 | 1/384 |
| Dragonstone | 1/512 | 1/512 | 1/256 |

Dragonstone is four times better per kill than Sapphire while still rare.
Independent rolls permit both rewards on an exceptionally lucky kill. Existing
personal-loot contribution scaling applies; these are full-contribution rates.
No additional ring-of-wealth multiplication is added to these hidden rolls.

## Equipment

- **3408 Gem cutter gloves**, glove slot, tradable, noteable, base value 10,000.
  Equipped gloves prevent the existing opal/jade/topaz cutting failures. Other
  gems already succeed normally. Existing Crafting levels still apply.
- **3409 Gem cutter's monocle**, medium headwear slot, tradable, noteable, base
  value 10,000. Each successful cut has a 5/100 chance to award a second cut gem
  while consuming only one input. No recursion and no extra XP. The normal
  single-output inventory precheck remains; if only one slot is free, the bonus
  uses the existing owner-ground overflow rather than disappearing or preventing
  an ordinary cut. Equipment is read after the batch delay at actual resolution. The five
  direct gem conversions on the existing developer Superchisel also use the
  same output helper; its non-cutting item-spawn menus are unchanged. No new
  level bypass is introduced (the developer tool retains its pre-existing rules).
- No combat bonuses or additional skill requirements were invented.
- The 48×32 inventory/ground icons are integrated from Core's Aseprite work.
  The owner approved the monocle's jeweller's loupe v2 icon; the October 28
  update copies its PNG exactly (see guaranteed-gems review for source/hash).
  Glove icon approval remains a separate visual gate. Worn glove geometry reuses the existing
  leather glove appearance. The monocle currently uses the existing eye-patch
  worn geometry as a provisional visual: it is **not an approved finished worn
  monocle**, and should receive a dedicated visual before final release.

## Visual provenance and discovery

`dev/myworld/assets/sprites/npcs/gem-dragons/base-frames` contains the exact
18 approved native PNGs. `provenance.json` records their source hashes and the
shared neutral sheet hash. Native walk canvases are 226×163, attack 335×163.
The shared directional sheet is 1465×489: widths 226/226/226/226/226/335 and three rows.
No frame resampling, silhouette edits or offset normalization is performed.
The Aseprite packer normalizes only the approved near-gray mineral shades to
exact average gray (5,094 samples). Original approved PNGs remain untouched.
Five AnimationDef entries share the name `gemdragon` and carry the native gem
color in charColour, just like the ordinary colored dragons. This uses one
physical sheet and one named Entry; the authentic allocator aliases the five
definitions to one frame range. Green/yellow body, alpha, motion and fire stay
fixed. The existing native >>8 tint/global-transform arithmetic applies. All sprites are included by
the normal client asset packaging rules. Build from approved inputs with:

```
aseprite -b --script-param input="$PWD/dev/myworld/assets/sprites/npcs/gem-dragons/base-frames" --script-param output="$PWD/dev/myworld/assets/sprites/npcs/gem-dragons" --script tools/myworld/pack-gem-dragons.lua
```

Server definitions are ordinary game-owned `GemDragonNpcDefs.json`, loaded after
the existing tower definitions. Client animation definitions and external asset
selectors are also game owned. No custom NPC IDs or assets are added to the
standalone toolkit. Core independently resolved all 40 requested gem-dragon
views (five variants × eight directions) with the shared asset on October 28.
Equipment icon candidates retained correct pixels under the existing declared
classpath authority limitations; the loot revision changes no discovery selector.

## Validation and remaining acceptance

- Source client/server/plugin builds pass.
- `test_gem_dragons`: production definitions, monotonic physical stats,
  retained gem-cutting equipment/fire behavior and unique probability boundaries.
  `GuaranteedGemLootFixture` exhausts all 897 guaranteed tickets and all 43
  outcome boundaries, checks unchanged material weights, and exercises actual
  normal/personal NPC death under Cosmic/Death necklaces, bones/uniques and Law
  banking. It replaces the obsolete own-gem-cap and optional-cluster tests.
  Remaining coverage includes reserved item holes, exact 5/100 bonus boundary,
  equipped/unequipped state, inventory overflow, real gem cutting for all eight
  gems, level checks, dust prevention, unchanged single-cut XP, all five developer
  Superchisel conversions, regular dragon combat profiles and actual protected
  fire-breath damage for every gem tier.
- `python3 tests/myworld/test-gem-dragons.py`: exact approved base hashes,
  neutral-only shade normalization, fixed body/alpha, one packaged sheet and
  both icons, disk/JAR decoding, server/client stat parity, sparse item IDs,
  actual authentic alias allocation, named/classic sprite selection, and all
  18 frames under each of the five native tint masks. It invokes the actual
  software rasterizer and the production OpenGL texture conversion path.
  Software opacity255 compositing uses /256 and is checked explicitly instead
  of claiming its already-composited pixels equal raw GL texture pixels.
- Wider regression gates and independent discovery review are recorded in the
  final handoff evidence when complete.

Private gameplay acceptance, gem equipment visual acceptance, final map/NPC
placement and release/deployment remain Core/owner gates. No public server,
owner map, credentials or player account data were operated on by this worker.

## Shared neutral asset revision after independent discovery

Core's first independent generic discovery accepted all new server/client
sources, but the five separately precolored full sheets exceeded the existing
12-million-pixel NPC materialization budget. The implementation now follows
RSC's native dragon design: one neutral sheet and five appearance/tint aliases.
No toolkit budget was raised and no custom-content roster was added to it.

This reduces the family's raw frame materialization from 3,581,925 to 716,385
pixels. Old precolored candidates are preserved in the earlier worker commit
and in Core's external staging art; they are removed from the runtime asset
folder/JAR. The revised generic discovery verdict is a separate Core gate.
