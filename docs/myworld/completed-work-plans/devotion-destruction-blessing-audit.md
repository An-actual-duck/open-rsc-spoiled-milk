# Devotion, Destruction, and Blessing Audit

Status: accepted implementation is complete on `main` and released in
`v0.2.55`.

Date audited: 2026-07-24

## Implementation Status

The accepted direction is implemented, validated, and shipped:

- The Bonecrusher correctness fix rejects noted inputs, removes the exact
  selected inventory instance, and awards nothing on failed removal.
- Ordinary blessings use exact in-slot conversion, require worship/altar
  alignment, and atomically record only successful conversions against the
  ten-per-hour limit.
- Equipment requires 100 Devotion per mapped resource and costs five stored
  offering units (`0.5` displayed Devotion) per resource to bless.
- Symbols require 50 Devotion, remain free to bless, and transfer two stored
  offering units (`0.2` displayed Devotion) when destroyed.
- All nine square-shield, spear, and scythe destruction mappings are present.
- Destruction reports the actual independently clamped gain and loss.
- Every Devotion reduction now cleans up prayers that exceed the resulting
  allocation.
- Existing offering XP, blessing XP, destruction XP, hourly-limit, and artifact
  formulas remain unchanged.
- A later shipped offering-tier change (`v0.2.69`) multiplies both baseline and
  Devotion-derived Worship XP through `OfferingExperience`; historical flat-XP
  examples below should be read as Bones (`1.0x`) examples unless they name a
  different offering.

The detailed “Current” sections below are retained as the pre-implementation
audit snapshot that established the defects. The accepted rules and this
implementation-status section supersede that historical baseline.

Validation completed with the authoritative Ant server core/plugin builds, the
desktop client build, the repository lint gate, the broad offline My World
regression suite, and focused Devotion, blessing, destruction, Prayer, and
skill-guide checks. Startup-dependent tests could not claim port 43615 because
another authorized worker already owned it; no running server was interrupted.

## Accepted Design Clarification

The project owner clarified after the initial audit that the high Worship XP
curves are intentional:

- +1000 Worship XP per offering at 1000 Devotion is considered reasonable for
  one of the game's hardest skills to train. Other high-level skills can award
  more than 10,000 XP per action, so the offering bonus should not be judged in
  isolation as excessive.
- Blessing XP is also intended to scale strongly at high Devotion. Blessing is
  meant to be a useful Worship-training route in a skill with comparatively few
  XP-generation methods.
- Devotion has an opportunity cost through reward choices. Players can retain
  high Devotion and its XP benefits or trade Devotion for rewards such as god
  artifacts.

Accordingly, this document no longer recommends flattening either XP curve.
The large totals below remain useful pacing evidence, but they are documented
as intended rewards rather than balance defects. Free or failed-removal XP
paths, such as the Bonecrusher issue, remain defects because they bypass the
required Devotion progression and item cost.

The project owner also approved the following Bonecrusher correctness fix,
which is now implemented:

- Reject noted bones and ash.
- Remove the exact inventory instance selected by the player, rather than
  looking up an unnoted item with the same catalog ID.
- Stop without rewards if that removal fails.
- Award the existing half-base Worship XP, full offering value, and flat
  Devotion-derived Prayer bonus only after successful removal.
- Preserve the Bonecrusher's reusable ownership/reclaim behavior and the
  separate handling of quest-specific bones.

### Accepted blessing and destruction targets

The project owner selected a small blessing cost and higher initial
requirements. These accepted values are now implemented; the current-state
tables later in this audit intentionally preserve the pre-implementation
baseline for comparison.

For ordinary equipment with mapped resource cost `r`:

```text
blessing requirement = 100 * r displayed Devotion
blessing cost        = 0.5 * r displayed Devotion
destruction transfer = 1 * r displayed Devotion
```

The implementation should perform fractional costs in stored offering units:
one displayed Devotion is ten offering units, so each resource costs five
offering units to bless. This avoids floating-point state.

| Mapped resources | Blessing requirement | Blessing cost | Destruction transfer |
| ---: | ---: | ---: | ---: |
| 1 | 100 | 0.5 | 1 |
| 2 | 200 | 1 | 2 |
| 3 | 300 | 1.5 | 3 |
| 4 | 400 | 2 | 4 |
| 5 | 500 | 2.5 | 5 |
| 6 | 600 | 3 | 6 |
| 7 | 700 | 3.5 | 7 |
| 8 | 800 | 4 | 8 |
| 9 | 900 | 4.5 | 9 |
| 10 | 1000 | 5 | 10 |

For armor, the intended mapping is headwear `1`, gloves and boots `2`, legs
`3`, and chest `4`. Other equipment follows its authoritative mapped resource
cost.

Blessed symbols remain deliberate outliers:

- requirement: 50 displayed Devotion;
- blessing cost: free; and
- destruction transfer: two ordinary offering units, or `0.2` displayed
  Devotion, added to the worshipped god and removed from the symbol's god.

The symbol's "two offerings" equivalence applies only to its Devotion transfer.
Destroying it must not run two offering actions, grant two offerings' Worship
XP, or change its separately mapped destruction Worship XP.

### Accepted alignment, coverage, and tier rules

Ordinary blessing must require the player's active Prayer book to match the
altar's god. Merely using an eligible item on another god's altar must not
bypass alignment. The check must happen before item removal, Devotion spending,
hourly-limit accounting, product creation, or XP.

Blessing and destruction coverage must be symmetric for every active ordinary
god-aligned equipment family:

- every supported ordinary source must have all three intended god-aligned
  blessing products; and
- every such product must have the intended opposing-altar destruction mapping.

The current blessing side already produces square shields, spears, and scythes
for all three gods. The confirmed runtime gap is their nine destruction
mappings:

| Ordinary source | Resource/tier value | Missing destruction products |
| --- | ---: | --- |
| Steel Square Shield (125) | 3 | Black (432), White (2161), Grey (3123) |
| Steel Spear (1089) | 2 | Black (3229), White (3230), Grey (3231) |
| Steel Scythe (3185) | 3 | Black (3232), White (3233), Grey (3234) |

Add all nine using the same resource value as their blessing source. Retain a
cross-check during implementation so any additional source-only or
destruction-only equipment mapping found at that time is completed rather than
silently omitted.

Tiered blessed equipment grants a destruction transfer based on its tier.
Blessed staves are currently the only ordinary blessed family with an explicit
tier ladder: staff tiers 1 through 10 transfer 1 through 10 displayed Devotion,
respectively. The same tier is the resource value used by the accepted
blessing-requirement and blessing-cost formulas. Future tiered blessed
equipment should declare and use its own tier rather than receiving a flat
fallback value.

God artifacts are not ordinary blessed equipment. They must remain excluded
from destruction recognition regardless of their god alignment, equipment
category, or any apparent tier.

## Executive Summary

The current system has three related but distinct loops:

1. Offerings build a separate Devotion balance for Saradomin, Zamorak, or
   Guthix. Every ten stored offering units produces one displayed Devotion
   level.
2. Ordinary blessings use Devotion as an unlock requirement, but consume no
   Devotion. Four blessing families share a persistent limit of ten successful
   blessings per rolling one-hour fixed window.
3. Destroying an opposing blessed item transfers Devotion from the item's god
   to the currently worshipped god and grants five times the item's mapped
   production Worship XP. Destruction is not rate-limited.

God artifact claims are a separate altar reward layered onto this system. They
require 800 Devotion and 80 Worship, consume 400 Devotion, and are not part of
the ten-per-hour blessing limit.

The most important audit findings are:

- The reported "daily" blessing limit is not daily. It is ten blessings in a
  one-hour window that begins with the first successful blessing.
- Ordinary blessing requirements are permanent unlock thresholds because
  blessing consumes zero Devotion.
- A Bonecrusher action does not verify that its attempted removal succeeded.
  In particular, a noted bone is not rejected, the removal searches for an
  unnoted item, and the unchanged noted item can be reused for free Worship XP
  and Devotion.
- God square shields, spears, and scythes can be created by the blessing
  handler, but the destruction handler does not recognize any of their nine
  god-aligned results.
- Blessing code does not require the player to worship the altar's god, despite
  the client guide saying to use "your god's altar." Destruction and artifact
  claims do require matching worship.
- Black, white, and grey equipment and all three blessed symbols have direct
  NPC-drop paths. The blessing requirement therefore gates self-conversion, not
  ownership or destruction of every eligible item.
- At 1000 Devotion, each successful offering grants a flat 1000 displayed
  Worship XP in addition to the bone or ash's normal XP. Blessing XP also
  reaches 11 times its base value. These are intentional high-Devotion rewards,
  with reward-based Devotion spending supplying the opportunity cost.
- Devotion-lowering adjustment paths refresh equipment stats but do not call
  the overflowing-prayer cleanup used by the administrator setter. An artifact
  claim or opposing-item destruction can therefore lower equipment-derived
  Prayer capacity without immediately deactivating excess prayers.

At the time this baseline was recorded, the audit itself had not changed
gameplay values or runtime behavior. The implementation-status section above
now records the subsequently approved changes.

## Authoritative Runtime Map

| Concern | Authoritative implementation |
| --- | --- |
| Devotion arithmetic and cache keys | `server/src/com/openrsc/server/content/Devotion.java` |
| Manual bones, ashes, and Bonecrusher | `server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java` |
| Black-unicorn automatic offerings | `server/src/com/openrsc/server/content/Summoning.java` |
| Symbols | `BlessedSymbols.java` in the My World Prayer plugin directory |
| Staves | `BlessedStaffs.java` in the My World Prayer plugin directory |
| Wool armor | `BlessedWoolArmor.java` in the My World Prayer plugin directory |
| Steel knight equipment | `GodKnightEquipment.java` in the My World Prayer plugin directory |
| Opposing-item destruction | `DestroyOpposingBlessedObject.java` in the My World Prayer plugin directory |
| Hourly limiter | `PrayerBlessingLimit.java` in the My World Prayer plugin directory |
| Artifact rewards and Devotion spending | `server/src/com/openrsc/server/content/GodArtifacts.java` |
| Persistent cache load/save | `Cache.java`, `PlayerService.java`, and `GameDatabase.java` |
| Prayer-book selection | `Player.java` and the authentic `Prayer.java` altar plugin |
| Equipment effects | `Equipment.java` and `EquipmentStatCalculator.java` |
| Server packet | `ActionSender.sendDevotion` and outgoing opcode 145 |
| Client display and guide | `mudclient.java`, `PacketHandler.java`, and `SkillGuideInterface.java` |

The four normal blessing plugins are enabled as My World content. The
descriptions below concern that configuration.

## Current Devotion System

### Storage, level, and caps

Each god has a persistent integer offering balance in the player's generic
cache:

- `devotion_saradomin_offerings`
- `devotion_zamorak_offerings`
- `devotion_guthix_offerings`

Missing keys read as zero. Offering balances are clamped to `-10000..10000`.
Displayed Devotion is:

```text
Devotion = stored offering units / 10
```

Java integer division truncates toward zero. Consequently, stored values from
`-9` through `9` all display as zero. The displayed range is
`-1000..1000`, separately for each god.

The matching-symbol and black-unicorn half-step states are also persistent
player-cache booleans:

- `devotion_<god>_symbol_bonus_toggle`
- `devotion_<god>_black_unicorn_bonus_toggle`

There is no death, logout, or time decay. Normal logout and the default
30-second autosave persist the complete cache. Graceful restart therefore
preserves Devotion. Player inventory, equipment, skills, and cache are saved in
the same database transaction, so an ordinary save cannot persist only one
side of a blessing. An abrupt process or machine failure can roll the entire
player back to the last completed save.

### Earning Devotion

| Source | Eligibility | Offering-unit gain | Notes |
| --- | --- | ---: | --- |
| Bury/scatter normally | Bones 20, Bat bones 604, Big Bones 413, Dragon Bones 814, Demon ash 3112 | 1 | Noted items are explicitly rejected; item removal must succeed before credit |
| Matching blessed symbol equipped | Same successful offering as above | +1 on every other offering | Average total gain becomes 1.5; toggle is per god |
| Mourning/Black Unicorn auto-sanctification | NPC-dropped Bones, Bat bones, Big Bones, Dragon Bones, Ashes, or Demon ash | +1 on every other drop in addition to the base unit | Average total is 1.5 without a symbol and 2.0 with a matching symbol |
| Destroy opposing blessed object | Recognized item used on the currently worshipped god's altar | `0.5`, or `1..10` by mapped resource cost | The same amount is removed from the item's god |
| Administrator command | `::setdevotion <player> <level>` | Sets a whole level | Development authority only; clamps to the normal range |

The god credited by an offering is the player's active Prayer book at the time
the offering is processed. Worshipping a recognized altar switches the active
book immediately, persists `myworld_prayer_book`, resets active prayers, and
unequips mismatched god gear.

The Black Unicorn path differs from manual burial in two additional ways:

- it also accepts ordinary Ashes, while the manual switch has no Worship XP or
  Devotion award for ordinary Ashes; and
- it grants twice the normal mapped base Worship XP before the normal 3x My
  World rate.

The later C12 settlement also makes Mourning Unicorn sanctification activate
the full Black Unicorn hide set's offering heal. Manual and automatic offerings
share one healing authority: bones heal `1`, bat or big bones `2`, demon ash
`3`, and dragon bones `4` per item, with stacked drops multiplied before the
current Hits ceiling. Ordinary ash and Bonecrusher processing remain excluded.

### Worship XP from offerings

The successful offering first reads the completed Devotion level that existed
before the new offering. It then awards a flat bonus of that many displayed
Worship XP:

```text
offering bonus XP = max(0, prior Devotion)
```

The code converts the displayed value to internal quarter-XP units and writes
it directly to the skill. It deliberately bypasses the normal 3x/1x XP-rate
multiplier, jewelry, Worship-skilling, potion, wilderness, and party modifiers.
It still respects frozen XP and maximum fatigue. The base bone/ash XP is a
separate normal `incExp` award and does receive the configured XP rate and
ordinary modifiers.

At the positive cap, further offerings no longer increase Devotion but continue
to award +1000 displayed Worship XP each.

### Spending and removal

In the pre-implementation baseline, normal symbol, staff, wool, and steel
blessings spent no Devotion. The only implemented whole-level spend was the
artifact claim:

```text
artifact requirement = 800 Devotion
artifact cost = 400 Devotion
```

Opposing-item destruction subtracts offering units from the item's god as it
adds the same units to the worshipped god. All arithmetic uses a `long`
intermediate and clamps before conversion to `int`, avoiding arithmetic
overflow.

When either side is already capped, the item is still destroyed and the
message still reports the nominal gain/loss even when clamping made the actual
change smaller or zero.

### Display

The custom server sends only the active Prayer book's displayed Devotion as a
signed short in packet 145. The desktop client shows `Devotion: <level>` in the
Worship skill hover panel. It does not show:

- the other two gods' balances;
- partial offering units;
- blessing slots used or time remaining;
- the current blessing's requirement or cost; or
- artifact eligibility.

Authentic clients do not receive this custom packet. Equipment statistics are
refreshed whenever Devotion changes so matching gear can rescale.

## Current Blessing Flow

For all four normal families, the successful path is:

1. Use an unnoted inventory item on a recognized Saradomin, Zamorak, or Guthix
   altar.
2. Resolve the product from the altar's god and the input item.
3. Check the family-specific Worship and Devotion requirements.
4. Check the shared hourly limit.
5. Remove the selected item.
6. Record one blessing in the hourly limit.
7. Give one product and award Devotion-scaled Worship XP.

Cancellation is not relevant because these conversions have no confirmation
menu. Invalid, noted, stale, unrecognized, under-level, under-Devotion, and
rate-limited attempts do not record a blessing. All supported inputs and
outputs are non-stackable and noteable; noted forms are rejected before
removal.

`CarriedItems.remove(item)` uses the selected unique item ID when one is
present. If a stale selected instance is gone but no same-catalog inventory
item remains, the wrapper can fall through to equipment and remove an equipped
item with the same catalog ID. There is no delay between validation and
removal in these plugins, so the practical window is small, but the removal
API is broader than the inventory-only interaction implies.

Blessing does not verify that the active Prayer book matches the altar. A
player with sufficient stored Zamorak Devotion can bless at a Zamorak altar
while currently worshipping Saradomin.

### Blessing Worship XP

The normal blessing formula, in internal quarter-XP units before the player's
normal XP multiplier, is:

```text
ceil(base production XP * (100 + altar-god Devotion) / 100)
```

This is `1.25x` at 25 Devotion, `1.5x` at 50, `3x` at 200, `6x` at 500,
`9x` at 800, and `11x` at 1000. On the normal My World 3x rate, displayed XP
is the internal result multiplied by `3/4`, before other configured bonuses.

| Family | Base production XP, internal | Additional level gate |
| --- | --- | --- |
| Symbol | 200 | None |
| Blessed staff tiers 1-10 | 12, 20, 28, 37, 46, 57, 68, 80, 92, 120 | Worship 1, 8, 15, 22, 30, 38, 46, 54, 62, 70 |
| Wool armor | `6 * resource cost` | None |
| God knight equipment | `150 * resource cost` | None |

XP is awarded only after selected-item removal succeeds. Blessing Worship XP
does receive normal XP rates and equipment, Worship-skilling, potion,
wilderness, and party modifiers.

## Hourly Blessing Limit

Despite being described as a daily limit in the task report, the implementation
is:

```text
10 successful blessings / one fixed one-hour window
```

The window is shared across all four families and all three gods. Switching
Prayer books or altars does not reset it.

The player cache stores:

- `myworld_prayer_blessing_window_start`: `System.currentTimeMillis()` at the
  first successful blessing in a new window;
- `myworld_prayer_blessing_window_count`: successful blessings, clamped to 10.

The window resets lazily. Once `now - start >= 3,600,000`, the next successful
blessing writes a new start and count 1. This uses elapsed epoch milliseconds,
so host timezone and daylight-saving changes do not define the reset.

Persistence and edge cases:

- Normal autosave, logout, and graceful restart preserve the window.
- An abrupt crash can roll the whole player back to the preceding autosave,
  including item, XP, and count.
- Moving the host clock forward can expire a window early. Moving it backward
  can extend a window because the elapsed value becomes negative.
- `canBless` and `recordBlessing` are separate operations. Normal player
  actions are expected to be serialized by the game/plugin event path, but the
  cache's thread-safe individual reads/writes do not make the pair an atomic
  reservation. Two truly concurrent requests at count 9 could both pass.
- Simultaneous logins for one account are expected to be rejected elsewhere;
  the limiter itself does not provide cross-session database locking.
- Destruction and artifact claims do not use this limit.

## Current Destruction Flow

The handler intercepts only an unnoted, recognized blessed item used on an
altar of a different god. It then requires the player to worship that altar's
god. On successful exact-item removal:

```text
worshipped god += destruction value
item god       -= destruction value
Worship XP       = mapped production XP * 5
```

The two Devotion adjustments are independently clamped to `-1000..1000`.
Destruction is not counted by the hourly blessing limiter and has no separate
rate limit.

The mapped "destruction value" is a transfer amount, not a fee charged in
addition to destruction. Away from either cap, the operation leaves the
player's total Devotion across the two gods unchanged: the worshipped god gains
exactly what the item's god loses. The consumed blessed item and the reduction
to its god's Devotion are already the costs of reallocating that allegiance.
No additional Devotion charge to destruction is proposed.

Because the gain and loss are clamped independently, an account near a cap can
experience an asymmetric actual transfer even though the current messages
describe the nominal mapped value. That is a calculation/reporting correctness
issue to address separately; it is not a reason to add a destruction fee.

All recognized items are non-stackable. Normal interaction therefore destroys
one selected item and awards one mapped result. Noted items are explicitly
excluded. A malformed database item holding an abnormal quantity in one
non-stackable instance could be removed as that instance while still receiving
one result; normal item creation does not produce such a state.

Same-god items, ordinary/unblessed items, god artifacts, unsupported black,
white, or grey equipment, and the nine special conversion outputs identified
below are silent non-matches.

### Destruction XP

| Result family | Devotion transferred | Destruction XP, internal | Displayed at normal 3x |
| --- | ---: | ---: | ---: |
| Blessed symbol | 0.5 | 1000 | 750 |
| Wool, resource 1/2/3/4 | 1/2/3/4 | 30/60/90/120 | 22.5/45/67.5/90 |
| Knight, resource 1/2/3/4 | 1/2/3/4 | 750/1500/2250/3000 | 562.5/1125/1687.5/2250 |
| Staff tiers 1-10 | 1..10 | 60, 100, 140, 185, 230, 285, 340, 400, 460, 600 | 45, 75, 105, 138.75, 172.5, 213.75, 255, 300, 345, 450 |

The destruction XP award uses normal `incExp`, so the usual XP-rate and
equipment, Worship-skilling, potion, wilderness, and party modifiers apply.

## Complete Normal Blessing and Destruction Matrix

Each result listed below is a distinct eligible blessing product. "Cost" means
Devotion consumed, not the input item, which is always consumed. The current
Devotion cost is zero for every row.

The destruction value applies to each listed result unless explicitly marked
as an implementation gap. Combat effects require the result's matching Prayer
book.

### Symbols

| Input | Altar | Result | Devotion requirement | Devotion cost | Result destruction value | Result/effect |
| --- | --- | --- | ---: | ---: | ---: | --- |
| Unblessed symbol of Saradomin (45) | Saradomin | Symbol of Saradomin (385) | 25 | 0 | 0.5 | Every-other-offering +1 unit |
| Unblessed symbol of Zamorak (1028) | Zamorak | Symbol of Zamorak (1029) | 25 | 0 | 0.5 | Every-other-offering +1 unit |
| Unblessed symbol of Guthix (3174) | Guthix | Symbol of Guthix (3175) | 25 | 0 | 0.5 | Every-other-offering +1 unit |

### Blessed staves

All three results in a row have the row's requirement, cost, and destruction
value. Blessed staves use fixed tiered stats and Prayer bonus; unlike knight
and wool gear, their combat and Prayer stats do not currently grow with
Devotion.

| Tier | Input | Worship | Devotion requirement | Cost | Destruction value | Zamorak result | Saradomin result | Guthix result |
| ---: | --- | ---: | ---: | ---: | ---: | --- | --- | --- |
| 1 | staff (100) | 1 | 50 | 0 | 1 | Staff blessed by Zamorak (2228) | Staff blessed by Saradomin (3152) | Staff blessed by Guthix (3162) |
| 2 | Pine Staff (2131) | 8 | 100 | 0 | 2 | Pine staff blessed by Zamorak (2229) | Pine staff blessed by Saradomin (3153) | Pine staff blessed by Guthix (3163) |
| 3 | Oak Staff (1764) | 15 | 150 | 0 | 3 | Oak staff blessed by Zamorak (2230) | Oak staff blessed by Saradomin (3154) | Oak staff blessed by Guthix (3164) |
| 4 | Willow Staff (1769) | 22 | 200 | 0 | 4 | Willow staff blessed by Zamorak (2231) | Willow staff blessed by Saradomin (3155) | Willow staff blessed by Guthix (3165) |
| 5 | Palm Staff (2136) | 30 | 250 | 0 | 5 | Palm staff blessed by Zamorak (2232) | Palm staff blessed by Saradomin (3156) | Palm staff blessed by Guthix (3166) |
| 6 | Maple Staff (1774) | 38 | 300 | 0 | 6 | Maple staff blessed by Zamorak (2233) | Maple staff blessed by Saradomin (3157) | Maple staff blessed by Guthix (3167) |
| 7 | Yew Staff (1779) | 46 | 350 | 0 | 7 | Yew staff blessed by Zamorak (2234) | Yew staff blessed by Saradomin (3158) | Yew staff blessed by Guthix (3168) |
| 8 | Ebony Staff (2141) | 54 | 400 | 0 | 8 | Ebony staff blessed by Zamorak (2235) | Ebony staff blessed by Saradomin (3159) | Ebony staff blessed by Guthix (3169) |
| 9 | Magic Staff (1784) | 62 | 450 | 0 | 9 | Magic staff blessed by Zamorak (2236) | Magic staff blessed by Saradomin (3160) | Magic staff blessed by Guthix (3170) |
| 10 | Blood Staff (2146) | 70 | 500 | 0 | 10 | Blood staff blessed by Zamorak (2237) | Blood staff blessed by Saradomin (3161) | Blood staff blessed by Guthix (3171) |

### Blessed wool armor

Each result has baseline Prayer bonus equal to resource cost, up to +10 more
Prayer bonus from Devotion 250..1000, and magic-defense growth over the same
range.

| Input | Devotion requirement | Cost | Destruction value | Zamorak result | Saradomin result | Guthix result |
| --- | ---: | ---: | ---: | --- | --- | --- |
| Wool Hat (2050) | 50 | 0 | 1 | Wool hat blessed by Zamorak (3137) | Wool hat blessed by Saradomin (3142) | Wool hat blessed by Guthix (3147) |
| Wool Robe Top (2051) | 200 | 0 | 4 | Wool robe top blessed by Zamorak (3138) | Wool robe top blessed by Saradomin (3143) | Wool robe top blessed by Guthix (3148) |
| Wool Robe Bottom (2052) | 150 | 0 | 3 | Wool robe bottom blessed by Zamorak (3139) | Wool robe bottom blessed by Saradomin (3144) | Wool robe bottom blessed by Guthix (3149) |
| Wool Gloves (2794) | 100 | 0 | 2 | Wool gloves blessed by Zamorak (3140) | Wool gloves blessed by Saradomin (3145) | Wool gloves blessed by Guthix (3150) |
| Wool Boots (2795) | 100 | 0 | 2 | Wool boots blessed by Zamorak (3141) | Wool boots blessed by Saradomin (3146) | Wool boots blessed by Guthix (3151) |

### God knight equipment

These results retain their line's base black/white/grey stats, require matching
worship for their god bonuses, and grow selected combat stats from Devotion
250..1000. Baseline Prayer bonus is the mapped resource cost, plus any natural
mace/paladin-shield bonus, and each piece can gain up to +10 more Prayer bonus.

| Input | Devotion requirement | Cost | Destruction value | Zamorak result | Saradomin result | Guthix result |
| --- | ---: | ---: | ---: | --- | --- | --- |
| Steel dagger (63) | 50 | 0 | 1 | Black dagger (423) | White dagger (2151) | Grey dagger (3113) |
| Steel Short Sword (67) | 50 | 0 | 1 | Black Short Sword (424) | White Short Sword (2152) | Grey Short Sword (3114) |
| Steel Mace (95) | 50 | 0 | 1 | Black Mace (430) | White Mace (2157) | Grey Mace (3119) |
| Steel Long Sword (72) | 100 | 0 | 2 | Black Long Sword (425) | White Long Sword (2153) | Grey Long Sword (3115) |
| Steel Scimitar (84) | 100 | 0 | 2 | Black Scimitar (427) | White Scimitar (2155) | Grey Scimitar (3117) |
| Steel gauntlets (698) | 100 | 0 | 2 | Black gauntlets (3131) | White gauntlets (3133) | Grey gauntlets (3135) |
| Steel greaves (1988) | 100 | 0 | 2 | Black greaves (3132) | White greaves (3134) | Grey greaves (3136) |
| Steel Helmet (109) | 100 | 0 | 2 | Black Helmet (230) | White Helmet (2158) | Grey Helmet (3120) |
| Steel Spear (1089) | 100 | 0 | **Not recognized** | Black Spear (3229) | White Spear (3230) | Grey Spear (3231) |
| Steel 2-handed Sword (78) | 150 | 0 | 3 | Black 2-handed Sword (426) | White 2-handed Sword (2154) | Grey 2-handed Sword (3116) |
| Steel battle Axe (90) | 150 | 0 | 3 | Black battle Axe (429) | White battle Axe (2156) | Grey battle Axe (3118) |
| Steel Square Shield (125) | 150 | 0 | **Not recognized** | Black Square Shield (432) | White Square Shield (2161) | Grey Square Shield (3123) |
| Steel Paladin Shield (129) | 150 | 0 | 3 | Black Paladin Shield (433) | White Paladin Shield (2162) | Grey Paladin Shield (3124) |
| Steel Plate Mail Legs (121) | 150 | 0 | 3 | Black Plate Mail Legs (248) | White Plate Mail Legs (2164) | Grey Plate Mail Legs (3126) |
| Steel Scythe (3185) | 150 | 0 | **Not recognized** | Black Scythe (3232) | White Scythe (3233) | Grey Scythe (3234) |
| Steel Plate Mail Body (118) | 200 | 0 | 4 | Black Plate Mail Body (196) | White Plate Mail Body (2163) | Grey Plate Mail Body (3125) |

The matrix contains 96 ordinary blessing results: 3 symbols, 30 staves, 15
wool pieces, and 48 knight pieces. The destruction handler recognizes 87 of
them; the three god variants of each bolded knight row account for the nine
missing results.

## God Artifact Claim Matrix

Artifacts are not ordinary blessings and cannot be destroyed by the
opposing-blessed-item handler. A player must have Worship 80, worship the
matching god, possess at least 800 Devotion, and pray at that god's altar a
second time. Accepting gives one random unclaimed artifact and consumes 400
Devotion only after the item was successfully placed in inventory.

| God | Possible result | Item ID | Devotion requirement | Devotion cost | Result/effect |
| --- | --- | ---: | ---: | ---: | --- |
| Saradomin | Saradomin Cape | 1214 | 800 | 400 | Aligned +10 prayer-bonus cape and god-prayer support |
| Saradomin | Staff of Saradomin | 1218 | 800 | 400 | Aligned Worship-80 god staff and Saradomin-spell support |
| Saradomin | Saradomin mace | 3252 | 800 | 400 | Aligned tier-11 mace; also requires Worship 80 to equip |
| Zamorak | Zamorak Cape | 1213 | 800 | 400 | Aligned +10 prayer-bonus cape and god-prayer support |
| Zamorak | Staff of Zamorak | 1216 | 800 | 400 | Aligned Worship-80 god staff and Zamorak-spell support |
| Zamorak | Zamorak mace | 3253 | 800 | 400 | Aligned tier-11 mace; also requires Worship 80 to equip |
| Guthix | Guthix Cape | 1215 | 800 | 400 | Aligned +10 prayer-bonus cape and god-prayer support |
| Guthix | Staff of Guthix | 1217 | 800 | 400 | Aligned Worship-80 god staff and Guthix-spell support |
| Guthix | Guthix mace | 3254 | 800 | 400 | Aligned tier-11 mace; also requires Worship 80 to equip |

Claim state is persistent per god and item ID under
`god_artifact_claimed_<god>_<itemId>`. Each of the three results can be
received only once. Cancellation, insufficient inventory space, failed
inventory insertion, wrong worship, low Worship, low Devotion, and an exhausted
pool consume nothing. Artifact claims do not use the hourly blessing limit.

The broader planning document mentions future paladin-shield and crossbow
relics, but they are not in the runtime pool.

## Supply, Rarity, and Renewability

| Family | Input economics | Other acquisition and balance consequence |
| --- | --- | --- |
| Symbols | Unblessed guide price 200; renewable silver, wool, and shop-supplied mould path | Blessed symbols also drop from Saradomin monks, Zamorak monks, and Guthix druids, bypassing the 25-Devotion blessing gate |
| Wool | Input guide prices 22-90; renewable sheep/wool crafting | Very cheap inputs can produce five scaling equipment pieces once the permanent threshold is reached |
| Steel knight | Input guide prices 600-3040; ordinary mining/smithing and some shop supply | Most core black, white, and grey results also drop directly from combat NPCs; direct results can be destroyed without ever meeting their blessing requirement |
| Staves | Inputs rise from guide price 15 to 7500 and require progressively higher wood/crafting access | Blessed output guide prices rise only from 24 to 720, below their high-tier inputs; requirements and destruction values track tier rather than market value |
| Artifacts | No ordinary production input | One-time per-account acquisition and 400-Devotion spend are the meaningful scarcity controls |

Resource equivalency is not consistently represented by guide price. Steel
gauntlets, greaves, and spear are mapped as two resources despite guide price
600, while the one-resource weapons are also 600. Steel square shield is three
resources at guide price 1200, while other three-resource pieces are
1800-3040. Destruction therefore rewards the hard-coded production mapping,
not rarity, shop value, or real player-market value.

Direct drop supply is important to the progression model. Destruction can take
an account from neutral toward +1000 with one god while driving the item gods
toward -1000, without first earning the destroyed item's blessing threshold.
That may be an intentional opposing-faith route, but it means offerings are not
the exclusive or necessarily fastest Devotion source.

## Client, Server, and Documentation Mismatches

1. `prayer-devotion-equipment-plan.md` says Devotion is clamped from 0 to 1000;
   runtime and the current client guide use -1000 to 1000.
2. The task/report language says "daily." Runtime and its existing automated
   test explicitly implement ten per hour.
3. The client guide says items are blessed at "your god's altar." Runtime
   normal blessings use the altar's Devotion balance but do not require current
   worship to match.
4. The client guide says blessed items grow stronger with Devotion. This is
   true for god knight and wool gear, but not for blessed symbols or staves.
5. The equipment plan names square shields as excluded, while the runtime
   blessing handler creates all three square shields.
6. The blessing handler creates square shields, spears, and scythes, while the
   destruction handler silently excludes them.
7. The artifact plan discusses five relic types per god. Runtime currently
   offers three.
8. The guide does not disclose the hourly cap, blessing XP multiplier,
   destruction XP, Devotion spending status, or artifact cost.
9. Most item examine descriptions do not explain matching-worship
   restrictions or Devotion scaling. Blessed symbols are the exception.
10. The UI exposes only the active god's whole Devotion level and cannot show
    why a blessing is rate-limited.
11. Devotion reduction by destruction or artifact claim can reduce
    equipment-derived Prayer allocation. Those adjustment helpers omit
    `deactivateOverflowingPrayers()`, unlike the administrator setter.

## Balance Analysis

### Progression pacing

The following deterministic examples characterize the current, unchanged
runtime rather than the accepted implementation targets above. They assume:

- ordinary Bones for manual offerings;
- normal My World 3x base Worship XP;
- no jewelry, Worship-skilling, potion, wilderness, party, or double-XP bonus;
- the symbol is equipped as soon as 25 Devotion is reached; and
- "Unicorn + symbol" uses Black Unicorn auto-sanctified bone drops from the
  start, then adds the symbol at 25.

The XP total includes both base Bones XP and the current flat Devotion bonus.

| Target Devotion | Plain offerings / cumulative XP | Matching symbol / cumulative XP | Unicorn + symbol / cumulative XP |
| ---: | ---: | ---: | ---: |
| 25 | 250 / 5,812.5 | 250 / 5,812.5 | 167 / 5,765.5 |
| 200 | 2,000 / 221,500 | 1,417 / 149,674.25 | 1,042 / 123,453 |
| 500 | 5,000 / 1,303,750 | 3,417 / 871,274.25 | 2,542 / 681,453 |
| 800 | 8,000 / 3,286,000 | 5,417 / 2,192,874.25 | 4,042 / 1,689,453 |
| 1000 | 10,000 / 5,107,500 | 6,750 / 3,406,937.5 | 5,042 / 2,611,453 |

The matching symbol and Unicorn correctly reduce action count, but they also
reduce cumulative Devotion-bonus XP because the player crosses more offering
units per action. The current curve awards millions of Worship XP before the
cap even with acceleration. Once capped, cheap Bones still award +1000 Worship
XP each indefinitely.

Requirements do gate initial self-production meaningfully:

- Symbol: 250 plain offerings.
- One-resource item: 500 plain offerings.
- Four-resource item: 2000 plain offerings.
- Tier-10 staff: 5000 plain offerings.
- Artifact: 8000 plain offerings.

Under the accepted target requirements, the symbol instead takes 500 plain
offering actions, one-resource equipment takes 1000, four-resource equipment
takes 4000, and a tier-10 staff takes 10,000. Symbol and equipment blessing
costs then create the small repeatable Devotion sink described above.

After the threshold is reached, however, ordinary blessings are free. A full
five-piece wool set uses five hourly slots and no Devotion. All sixteen knight
conversions use two hourly windows and no Devotion. A player may stockpile
blessed products while retaining the same threshold, then destroy the stockpile
later.

Claiming all three artifacts for one god requires 1600 total Devotion gain:
reach 800, spend to 400, regain 400 twice, and finish at 400 after the third
claim. That is 16,000 plain offering units before symbol/Unicorn acceleration.
Artifact claims are the only currently implemented Devotion spend, but they are
finite: each of the three artifacts per god can be claimed only once.

### Blessing and destruction XP

Representative normal-3x displayed XP, before additional bonuses:

| Recipe | Bless at minimum requirement | Bless at 1000 | Destroy | Bless + destroy at minimum / 1000 |
| --- | ---: | ---: | ---: | ---: |
| Symbol | 187.5 | 1650 | 750 | 937.5 / 2400 |
| One-resource knight item | 168.75 | 1237.5 | 562.5 | 731.25 / 1800 |
| Four-resource knight item | 1350 | 4950 | 2250 | 3600 / 7200 |
| One-resource wool item | 6.75 | 49.5 | 22.5 | 29.25 / 72 |
| Four-resource wool item | 54 | 198 | 90 | 144 / 288 |
| Tier-1 staff | 13.5 | 99 | 45 | 58.5 / 144 |
| Tier-10 staff | 540 | 990 | 450 | 990 / 1440 |

At the hourly blessing cap, ten four-resource knight bless-and-destroy cycles
can produce 36,000 displayed Worship XP at the unlock threshold or 72,000 at
1000 Devotion, before other bonuses. Destruction itself is uncapped, so
direct-drop or previously stockpiled items can exceed that short-term rate.

These figures are not, by themselves, evidence that the XP should be reduced.
The accepted design compares Worship's overall time-to-level and scarcity of
training actions against other skills that can exceed 10,000 XP in a single
high-level action. The high-Devotion offering and blessing rewards are intended
to close that gap. Balance review should instead measure sustained Worship XP
per hour, the time required to reach high Devotion, and how often players elect
to spend Devotion on rewards.

### Exploit and bypass assessment

| Risk | Confidence | Impact |
| --- | --- | --- |
| Bonecrusher does not check removal and does not reject notes | Confirmed | Reusable noted bone/ash can generate unlimited Worship XP and Devotion |
| Blessing is free after threshold | Confirmed design | Permanent unlock and stockpiling are not exploits; their balance depends on optional Devotion rewards |
| Direct blessed drops bypass requirements and blessing limiter | Confirmed design/content | Destruction can replace offerings as progression and can burst XP |
| Destruction is uncapped | Confirmed | Stockpiled/direct items can be converted as fast as interactions permit |
| Host-clock changes | Confirmed arithmetic | Clock forward resets early; clock rollback extends the hour |
| Concurrent cap check/record | Theoretical, low under serialized player actions | Two truly concurrent requests could pass the last slot |
| Noted normal blessing/destruction | Ruled out | All handlers reject noted items before conversion |
| Stack multiplication | Unlikely in valid data | Eligible definitions are non-stackable; malformed stacks remain a data-integrity concern |
| Integer overflow | Ruled out for mapped values | Devotion uses long intermediates/clamps; mapped XP is small |
| God switching resets cap | Ruled out | Shared keys do not contain god identity |
| Restart resets cap | Ruled out for graceful/default saves | Window keys persist in player cache |

## Accepted Decisions and Follow-up Recommendations

The correctness and balance decisions in sections 1 through 4 were accepted
and are implemented. The telemetry and direct-drop policy discussion in
section 5 remains a future recommendation.

### 1. Implemented correctness and exploit fixes

1. The Bonecrusher correction rejects notes, removes the
   exact selected inventory instance, stops on failed removal, and awards the
   existing XP/Devotion only after successful removal. Its valid reward values
   and reusable lifecycle are unchanged.
2. The square-shield, spear, and scythe destruction mappings now cover
   all three gods using their existing resource values, completing
   blessing/destruction symmetry for ordinary god equipment.
3. The active Prayer book must match the blessing altar before any
   mutation, matching destruction, equipment use, and the guide.
4. One serialized, bounded successful-conversion operation replaces the
   separate limiter check/record calls.
5. Destruction reports actual clamped Devotion changes, and reductions
   deactivate prayers that exceed
   the new allocation after any Devotion reduction.
6. Regression checks cover notes, stale selections, exact quantities, all
   ordinary products, altar-alignment failures, tier-based destruction,
   artifact exclusion, mapping symmetry, cap persistence, clock edges, and
   failed conversion accounting.

### 2. Preserve high-Devotion Worship XP

Keep the current formulas:

```text
displayed offering bonus XP = max(0, prior Devotion)
blessing XP multiplier      = 1 + altar-god Devotion / 100
```

This preserves +1000 offering XP and an 11x blessing multiplier at maximum
Devotion. Those outcomes are accepted design targets, not emergency nerf
candidates.

Balance validation should compare sustained methods rather than raw
per-action numbers:

- Worship XP per hour before and after reaching key Devotion thresholds;
- time and item supply required to reach those thresholds;
- XP lost when players spend Devotion and must rebuild it;
- frequency and value of optional Devotion rewards; and
- comparable high-level methods in other skills, including actions worth more
  than 10,000 XP.

Do not use the confirmed Bonecrusher exploit as evidence against the intended
curve. Fixing that exploit restores the required item consumption while
leaving legitimate high-Devotion rewards intact.

### 3. Accepted minimal blessing cost

Ordinary equipment blessing now consumes five stored offering units per mapped
resource, equal to `0.5` displayed Devotion per resource. This is half the
existing `1` Devotion-per-resource destruction transfer. A successful
conversion is atomic: it verifies the requirement, replaces the exact source
item, deducts the cost, records the hourly slot, and awards XP without allowing
a failed action to consume Devotion or a limit slot.

Blessed symbols require 50 Devotion but remain free to bless. Their accepted
destruction transfer is two stored offering units (`0.2` displayed Devotion),
with no offering-XP equivalence.

The modest blessing cost complements, rather than replaces, larger optional
reward choices such as the 400-Devotion artifact claims. Players may still
choose between retaining high Devotion for its XP/equipment benefits and
spending it on rewards or blessed equipment.

Preserve destruction as a symmetric transfer between gods. Do not add an
additional destruction charge: consuming the item and lowering the item's god
already provide its intended cost.

### 4. Ten per hour, correctly identified as hourly

There is no daily allowance. Ten shared blessings per hour is enough to create
a full wool set in one window and intentionally slows large stockpiles. The
number is preserved and the client guide identifies it as hourly. Exposing
remaining slots/time and collecting usage data remain possible future work.

If a true daily rule is desired later, define an explicit UTC reset and a much
larger allowance. Replacing the current rule with ten per UTC day would be an
unnecessary 24-fold reduction.

### 5. Decide whether direct drops are an alternate progression path

Two coherent policies exist:

- Keep direct black/white/grey and symbol drops. Document destruction as a
  combat-based alternate Devotion path and tune drop supply around it.
- Make Devotion a strict production/ownership gate by removing those direct
  outputs and dropping ordinary source gear instead.

The first policy preserves established drops and is recommended. The important
follow-up is telemetry: count offerings, direct blessed drops, blessings,
destructions by item, cap denials, and actual Devotion change after clamping.
Do not infer economy balance from guide prices alone.

## Proposed Implementation Order

1. Correct Bonecrusher removal and all conversion-coverage/state bugs.
2. Add focused server tests and privacy-safe counters before balance changes.
3. Update client guide and item descriptions to match the selected policy.
4. Preserve the current offering and blessing XP formulas.
5. Implement the accepted blessing requirements, fractional offering-unit
   costs, and symbol exception as one atomic conversion change.
6. Enforce blessing alignment, complete all ordinary equipment mappings, and
   retain tier-based destruction plus the artifact exclusion.
7. Review additional optional Devotion rewards after the finite artifact pool.
8. Observe at least one normal play cycle before changing the ten-per-hour cap
   or five-times destruction XP.

This ordering separates exploit closure and client/server agreement from
subjective balance changes.
