# Zamorak hostile spells — implementation review

November 1, 2026. Required release feature; offline verification complete.
Private gameplay acceptance and Core integration/staging remain separate. No
game listener, client login session, public maintenance, owner map or account operation
was performed by this worker.

## Result and stable identities

The active Cleric book now contains twenty spells: nine god blessings, nine
god-aligned curses and the two Unify forms. Retired identities remain reserved;
Saradomin's internal legacy Warmth identity still presents the approved Light
name. The owner explicitly superseded the temporary Zamorak hold. Cleric Tier 4,
the additional boss roster and Ante remain deferred.

| Stable code | Spell | Unlock | Radius | Inherited effects |
|---|---|---:|---:|---|
| 28 | Zamorak's Malice | Worship 1 | 2 | Expose Weakness |
| 29 | Zamorak's Cruelty | Worship 20 | 3 | Expose Weakness, Weak Legs |
| 30 | Zamorak's Ruin | Worship 40 | 4 | Expose Weakness, Weak Legs, Weak Constitution |

All three reuse the existing enemy-NPC targeting, useful-cast transaction,
same-god exclusive slot, replacement precedence, snapshot power and common
60-second expiry. There is no PvP or summon admission. Each useful cast consumes
the matching Zamorak and Blight resources once: Stone at Tier 1, Stone + Silver
at Tier 2, Silver + Moonsilver at Tier 3. Lower-tier casts cannot downgrade a
stronger active god slot or consume resources for an ineffective application.

Holy Power thresholds stay **0/20/45/75/115/160**, capped at **P2/P4/P6** by spell
tier. Higher tiers inherit earlier components at their resolved power.

| Component | P1 | P2 | P3 | P4 | P5 | P6 |
|---|---:|---:|---:|---:|---:|---:|
| Critical chance | 5% | 7% | 9% | 11% | 13% | 15% |
| Stagger chance per critical, Tier 2+ | 25% | 30% | 35% | 40% | 45% | 50% |
| Execution pre-hit HP threshold, Tier 3 | 15% | 17% | 19% | 21% | 23% | 25% |
| Execution chance per eligible critical, Tier 3 | 50% | 55% | 60% | 65% | 70% | 75% |

## Actual combat boundaries

`ClericCriticalRuntime` supplies a victim-owned vulnerability to the existing
`CriticalHitResolver`. Any eligible player's primary hit can benefit, regardless
of who cast the curse. The resolver carries `AttackRoll.Critical.AUTHORED`
explicitly; reaching a normal maximum never invents a critical. It receives the
actual existing combat style for eligibility: melee, ranged (including the
existing throwing classification), or Magic.

Primary production paths covered are both melee settlement implementations,
bow and throwing launches, normal damaging Magic, God spells, Iban Blast and
Salarin's fixed-damage Strike primary. Salarin's fixed amount and allowed-spell
rules are unchanged; its primary can carry a critical identity while its
separately delayed extra remains outside the chain.

The callback admits only positive authored primary damage from a current,
registered, eligible player to a current enemy NPC. It checks the request's
source and target lifetime/session snapshots. Its narrow source keys are:

- `pvm-melee-primary`, `reciprocal-melee-primary`;
- `projectile-player-ranged-primary`, `projectile-player-thrown-primary`;
- `projectile-player-magic-primary`, `projectile-iban-primary`.

Offhand, legacy extra hits, proc/splash/chain damage, summons, cannon, poison,
recoil and other periodic/reflected damage do not gain this chain. Integer-only
compatibility helpers retain their old behavior. The optional OSRS formula/cape
profile, disabled in Spoiled Milk, is deliberately unchanged and has no Zamorak
critical provider. No broader multi-hit or OSRS redesign is claimed.

The existing non-corporeal rejection happens **before** the new settlement
callback. A blocked hit, miss, Stagger-forced zero, stale participant or natural
maximum cannot trigger Weak Legs or Weak Constitution. Magic retains its
existing non-corporeal exception. Projectile launch outcomes remain immutable:
a curse applied after launch cannot promote an ordinary roll, while expiry
before impact removes the curse callbacks without changing a committed critical
amount. Existing delayed producer lifetime, registration and level fences still
reject invalid impacts.

Inactive/unafflicted paths keep their original ordinary supplier and add no
chance RNG or installed hostile state. With an active critical provider, the
reviewed resolver selects a maximum instead of invoking the ordinary damage
supplier on success. Cosmic favors each pure chance check at most once; it does
not rerun an attack, consume resources twice or invoke callbacks twice.

## Stagger, execution and rewards

Weak Legs requests **one** attack through the existing actor-owned `AttackState`
and `StaggerState`. The next performed NPC attack is forced to zero, followed by
five performed, unstaggered attack turns of immunity. Ogre and Startle share the
same authority. Incoming hits, elapsed ticks, multiple attackers, recasting and
curse expiry cannot clear or bypass pending losses or recovery. No pending
Stagger queue is added.

Execution tests the NPC's HP **before** the triggering hit, inclusively against
its normal maximum HP using bounded integer arithmetic. A critical that already
kills normally consumes neither execution nor Stagger RNG. Otherwise execution
rolls first; success changes the **same** damage request to the remaining HP and
leaves the existing producer responsible for its normal death path. If the NPC
survives, Weak Legs can roll when its shared Stagger authority permits it.

Execution immunity uses existing explicit authorities, not names or combat
level: the 25-ID `SlayerRewardBosses` set, `MonsterSlayerBossTasks.Boss` tags, and
`Npc.isPluginOwnedDeathCompatibility()`'s existing eleven scripted identities.
The read-only scripted-death predicate is now public; its roster and existing
death behavior did not change. The feature additionally protects the actual
Witch's House transformation identities `244–247` and restricted-spell Salarin
`567`, using their existing `NpcId` constants. This is feature-specific ordinary
enemy admission; the unrelated Slayer percentage-damage boss roster is
unchanged. These protections do not blanket-disable ordinary critical damage
or Stagger on otherwise eligible targets. Normal killing criticals still reach
the real quest kill callback and advance one Witch's Experiment form.

Both melee callers and the primary projectile caller now use the settled
request amount in their local contribution bookkeeping. An execution's remaining
HP is therefore credited once, and existing settled-damage lifesteal/on-hit
hooks receive that same hit's amount. Highest-contributor kill ownership, both
players' XP shares, normal listeners, loot and death cleanup are preserved.
Raw pre-execution values used by legacy overkill/secondary logic remain raw;
execution does not manufacture an overkill splash or replay a child attack.

## Protocol, guide and art cutover

The matched protocol is **schema 5/client 10056**, with new numeric component
kinds 17–19. Old payloads remain negative fixtures and are rejected, including
the recorded schema-4 seventeen-spell packet. The generic HUD now recognizes
both beneficial and hostile composites, including their power badge, canonical
icon and detailed component text. This presentation support does not grant a
new player-affliction or PvP capability.

The approved compact spell hover layout remains unchanged. The detailed in-game
guide gives chance/HP curves, pre-hit timing, shared immunity and ordinary enemy
protection in plain language. All twenty spells fit the native fonts and the
guide remains bounded at 33 pages in the verified build.

Core first merged the independently reviewed owner art into published main;
this worker then merged that published main normally, switched to the twenty
canonical keys and retired the eleven old non-Unify PNGs. Exactly twenty source
and embedded PNG resources remain. Supplied pixels/PNG bytes were not altered;
the two historical spelling aliases were normalized by the art intake. The
provenance receipt retains the retired names/hashes as history, and the original
owner files remain preserved separately.

## Verification

The runtime/contract/art source freeze is checkpoint
`e4ede109ab4c1b8784b79aa2a53eb2a3d5435f29`. The final delta after that checkpoint
adds these notes, sanitized evidence and three versioned retained-request
regression cases; no further production behavior changes were needed.
[`zamorak-hostile-evidence/validation.json`](zamorak-hostile-evidence/validation.json)
records commands, final outcomes, markers, log hashes and the four matched
artifact hashes. Raw synthetic fixture/build logs, native PNG reviews and frozen
artifact copies remain under ignored `output/zamorak-hostile-review/`.

All heavy commands ran sequentially under
`/home/justin/.local/share/core-heavy-build.lock`, using
`JAVA_HOME=/home/justin/jdk8u482-b08` and a 512 MiB Serial GC bound. No game
listener, authentication session, owner account/database or owner map was used.

Passed gates:

- Maintained server/client builds and generated-contract parity.
- `test-zamorak-hostile.py`: **264** production packet/combat assertions:
  all P1–P6 probability/HP boundaries, actual NPC recovery turns, other attackers,
  Startle, expiry/recast, Cosmic's one additional pure candidate, primary
  melee/bow/throwing/normal/God/Iban/Salarin launches and settlement, phase,
  protected encounters, top-contributor XP, single death/loot and the actual
  Witch's House transformation callback. Retained request tests reject callback
  use across target lifetime, attacker lifetime and attacker session changes.
- Existing Cleric catalog/foundation, blessings **376**, consistency **88**,
  hostile **121**, hostile producers **24**, projectile **30** and metal sigil
  production **218** checks.
- Shared attack-state **88**, shared runtime **173**, Cosmic pure **32** and
  Cosmic runtime **90** checks.
- Real client decoder/model/transport/guide **7,696** checks, including all
  6,050 truncated-packet cases; generic mixed-status HUD **68**; actual native
  spellbook/guide viewport/input/navigation **63,492** assertions; all twenty
  owner icon/source/embedded/cache/native-font checks **162**.
- Full native combat gate **145/145**.
- All nine Slayer targets: leather effects, rewards, mobility shield, Abyssal
  Demon, Dark Beast, Bloodveld, Terror Dog, Naga and thunder casting.
- Shared Slow **115** boundaries plus adapters/lifecycle fixtures, battleaxe
  coatings **1,042**, antidote/poison cleansing **102**.
- Python syntax and Git whitespace checks.

The first full validation run exposed two old fixture writers still setting a
literal schema-4 header. They were corrected to the current contract constant;
the affected blessings/hostile fixtures were rerun successfully. Original old
wire fixtures remain untouched as rejection coverage. Stale 17-entry assertions
and informational render labels were updated to enumerate the actual twenty
entries; no layout relaxation was introduced.

Independent Core review on a frozen first checkpoint reproduced a stale-request
callback gap. The explicit snapshot guards at the source freeze resolved all
three cases: the unchanged independent adversarial fixture passed **27/27**.
The reviewer also independently verified the unified client with **162** icon/
native-cache checks and found no remaining concrete blocker. Final READY delta
audit is retained by Core's review worker.

Actual private gameplay, the final paired release package, map assembly and any
public activation are Core's next gates. This worker does not claim those
operations or change the owner's deferred feature scope.
