# Three-tier god blessings — implementation review

October 31, 2026. Combined server/client candidate for Core review, not a public
release or deployment. The exact client dependency
`d2c29d1467bbff2453362020f2eee66b7841b434` is merged into the server task branch,
preserving both ancestries. Core can integrate one complete exact handoff.

## Result and boundaries

The active book contains the nine named god blessings plus Unify and Greater
Unify. Stable codes 1 and 12–21 are active; legacy 0/2–11 remain reserved and are
refused, not aliased to unrelated spells. No inventory item IDs, quantities,
readiness, map files or art archives are changed by this task.

The [active governing model](cleric-spellbook-concept.md) records the approved
October 31 incremental values. Worship unlocks 1/20/40; Holy Power is current
Worship plus the strongest holy staff. Shared thresholds 0/20/45/75/115/160,
tier caps 2/4/6 and radii 2/3/4 are authoritative. A higher tier raises the cap,
not the minimum. Hostile debuffs remain documentation only.

Every god owns one atomic 60-second snapshot. Different gods coexist; same-god
higher tiers/potencies replace weaker versions, weaker versions cannot downgrade,
and equal strength refreshes. Eligibility does not depend on currently needing
every component: a full-HP ally can receive the whole blessing. Existing party,
no-caster, same-world/level, line-of-effect and PvP exclusions are retained.
Origin scope is snapshotted alongside party/session identities; death, logout,
party changes and invalid spatial/PvP contexts disable the effect.

Saradomin's Resplendence currently raises the power cap of its existing regen/HoT
components. No extra third effect or flavor text was invented; its generic
name confirmation is used. All eight approved recipient messages are preserved.
Approved existing icons/animations are explicitly mapped, so new names do not
invent missing asset paths. The [client review](cleric-three-tier-client-review.md)
records native rendering and UI checks.

## Timing, arithmetic and combat

Sara natural regeneration runs at ×2/2.5/3/3.5/4/4.5 through the existing natural
restoration clock. Its Tier 2+ HoT heals 1–6 HP per delayed pulse, ten scheduled
pulses about six seconds apart. Application never immediately heals. Equal
refresh/upgrades preserve the recipient's next periodic deadline; replacing
Guthix cannot reset Sara's clock or Zamorak fractions.

The registry settles due periodic work before expiry is purged. The scheduled
60-second pulse is accepted at its normal endpoint or first scheduler tick,
then the whole blessing expires. Expired attack/defense effects are not extended.
It settles at most one action per clock query; long scheduler stalls skip
backlogged actions instead of producing catch-up healing bursts. Actual 640 ms
scheduler coverage proves all ten pulses under normal timing, including when
all three gods coexpire and Guthix's Worship restoration reenters derived stats.

Guthix cleansing is an additional 2–7 decay on the existing poison pulse, stacking
with baseline, necklace, leather and antidote sources. It does not instantly
cure poison or grant immunity. One point of every drained non-HP stat restores
every 10/9/8/7/6/5 seconds, capped at the current equipment-adjusted normal level;
boosted stats, HP and XP are untouched. Barkskin changes all three defense
values, not flat incoming damage. Reflection uses factual direct damage.

Zamorak maximum damage applies before direct melee/ranged/Magic rolls and crits,
including capped Magic; dealt damage applies after mitigation. The positive
maximum increment rounds up to preserve useful early low caps, with zero and
overflow handled safely. Defense increments round down. Dealt damage,
lifesteal and reflection use separate fractional carries and never force one
point per actual hit. Refresh/replacement/expiry discards the relevant god's
carry. This integer quantization makes nominal percentages approximate for
very small attack caps.

Only primary direct PvE hits qualify. Offhands, secondary Magic/melee, incidental
breath/proc damage, summons, DoTs, PvP and reflection loops are excluded. A
lifesteal component activates strictly below half HP without suppressing the
rest of its blessing or removing the blessing on recovery. It may cross half
HP on the qualifying hit; half HP is an activation gate, not a healing ceiling.

New Cleric post-hit hooks use `DamageResult.getActualDamage()`/factual projectile
HP delta, leaving unrelated legacy attribution hooks unchanged. Real melee
survival tests prove fractions use 4 factual HP rather than 5 legacy damage when
Goblin Tenacity leaves one Hit.

**Deliberate standalone-Thorns migration:** no new Cleric recoil starts from a
recipient already at 0 HP. Reflection switches from stochastic rounding to
fractional carry; neither guarantees a minimum point. The old simultaneous-death Cleric scenario is renamed for this
new composite policy. Generic reflection transaction/death attribution/order
coverage remains in the existing combat gate; it was not removed. An actual
lethal primary-event test verifies no new Cleric recoil and cleared state,
while a separate dead-player assertion verifies no blessing healing.

## Costs, transport and equipment

Initial private-build economic defaults (manager-selected, reviewable; not a
separate owner specification of every recipe):

| Spell | Flat per-cast resource vector |
|---|---|
| God Tier 1 | 1 stone god sigil  + 1 Stone Grace |
| God Tier 2 | 2 stone god sigils  + 2 Stone Grace |
| God Tier 3 | 2 silver god sigils  + 2 Silver Grace |
| Unify | 2 Stone Grace |
| Greater Unify | 6 Stone Grace |

The entire unnoted vector is committed atomically once per cast, not once per
recipient. Missing/noted resources and entirely ineffective/no-target casts do
not spend a partial vector. God-sigil production and Grace/Blight wells remain
unchanged.

Unify is Worship 3/radius 3, with one actual safe ordinary walking step, stopping
within two tiles. Greater Unify is Worship 30/radius 5 with at most three steps,
also stopping within two. Both preserve path/line-of-effect/party/PvP guards;
neither has Holy Power scaling or further upgrades.

Schema 3 and client 10054 are declared once in the generated shared contract.
Its immutable threshold accessor prevents server/preview drift. Both maintained
build wrappers check generated equality. Older clients fail closed at catalog
and cast admission. Metadata carries bounded numeric costs/components, not
executable formulas or a client-side custom-content roster.

The client uses stable-ID lookup separately from list/grid positions, displays
all components/potency/timer, wraps text and validates full packets before
replacement. Unnoted resource availability drives greyouts and costs. Cast-rank
preview uses the existing authoritative equipment stat 7 Holy Power, without
recreating staff-ID logic or adding Worship twice.

Worship stat updates send a derived-equipment-stat packet only, refreshing Holy
Power without resending worn inventory on HP pulses. Barkskin application/expiry
also refreshes derived defense display when its percentage changes. Real packets
and subsequent casts verify fresh Worship changes affect both preview authority
and the next server-resolved rank.

All 30 blessed staves now use Magic-only requirements at the established
1/8/15/22/30/38/46/54/62/70 ladder. Legacy Sara/Guthix melee and accidental extra
Zamorak Worship gates are removed. Their stats/art/alignment and god staves are
unchanged; no-level-requirements bypass still works.

## Validation and retained coverage

Java 8 builds ran sequentially with 512 MiB SerialGC. No owner project, game
server/client session, accounts, map import, staged candidate or live deployment
was operated. Runtime fixtures use checked-in configuration/schema and a tiny
fresh native map, without listeners or account database access.

- `scripts/build-server.sh` and `scripts/build-client.sh`: combined source builds,
  matching generated contracts and maintained classpath audit passed.
- `test-cleric-blessings.py`: 354 real native runtime assertions. Packet casts,
  all threshold±1 boundaries/caps, mixed costs including notes, full-HP and
  no-target eligibility, retired/old-client requests, periodic refresh/endpoint,
  poison/restoration, all three defenses, actual controlled crit formulas/caps,
  real melee offhand/survival/lethal settlement, carry and lifecycle, fresh Holy
  Power packets, Unify movement/costs and actual schema 3 packet production.
- Actual 2752-byte server schema 3 → client: 2986 assertions (2752 truncation cases,
  234 other behavior checks), plus 68 retained generic mixed-HUD assertions.
  The recorded payload fixture is refreshed to the current server descriptions.
- 21 focused Cleric targets passed: foundation, cast transaction, effect state,
  C09/C10, Purify/Restore helper math, C12 Devotion/animations, equipment power,
  Blessing platform, sigil production/assets, native wells/infusion, Blight art,
  generic server status inventory, client presentation/HUD/guide and staff gates.
- Native well admission 364; native software/GL picking 1420/1428; infusion 223
  remain covered. Approved sigil PNGs/native archives remain unchanged.
- Current combat gate: **144/144** scenarios passed.
- Slow reconciliation 103 plus source adapters; coatings 1042; antidote; all nine
  requested Slayer targets passed: leather effects, rewards, mobility shield,
  Abyssal Demon, Dark Beast, Bloodveld, Terror Dog, Naga and thunder casting.
  Additional leather/tower/components/shops/ordinary loot/retirement gates passed.
- `git diff --check` and canonical/generated checks passed.

Frozen launch metadata exists only under `tests/myworld/fixtures`, retaining
old generic registry/counter/arithmetic characterization while active requests
remain retired. C09/C10 additionally execute the new runtime gate. The old HUD
server inventory bounds/order/count coverage is retained separately, not lost
in the client rewrite. Plugin-specific well fixtures are excluded from the
core-only combat compilation and exercised by their own plugin-aware targets;
this repairs an inherited test classpath boundary without dropping acceptance.

Evidence: `/home/justin/.local/share/cleric-three-tier-buffs-review/2026-10-31`.
The full repository-wide test-all suite and owner hands-on gameplay were not
run; focused combined server/client/Cleric/combat/Slayer gates are the scope of
this handoff. Saradomin's extra component and hostile branch remain explicitly
unfinished, outside this approved buff implementation.
