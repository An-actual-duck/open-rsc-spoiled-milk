# Shared critical, Stagger and attack-delay foundation — Core review

October 31, 2026. Offline implementation candidate for Core review; no server
session, owner map/account/import, staged package or live deployment was operated.
This completes the shared foundations, not the planned hostile Cleric spells.

## Result

Ogre and Thunder Startle share one actor-owned Stagger authority. Every existing
source requests one attack; the API supports an explicit N. A staggered attack
resolves zero damage while retaining its normal turn, resource and cadence
boundary. Pending losses cannot be replaced or queued. After the final lost
attack, five actual unstaggered attacks must occur before any source can apply
Stagger again. Normal rolled zero/misses count; ticks, polls, incoming hits and
staggered turns do not. Mixed attackers/sources/styles share the actor's state.

Ogre's full-set 20% and Startle's 7/15/25% remain unchanged and independent of
criticals. Existing eligibility/PvP scope remains unchanged. Lifecycle/session
snapshots and the existing lifecycle-clear hook prevent inheritance across
death, respawn or logout. State installation and compound transitions use the
actor lock; pure state transitions are also synchronized.

One immutable `AttackCommitment` snapshots suppression. Ordinary melee commits
only after reach, private cooldown and extra-delay admission. Bow/throwing and
legacy/NPC projectiles commit at the real producer after resource/legal gates.
Damaging Magic commits after the selected spell's quest/staff/target checks,
extra-delay admission and successful rune settlement. FEAR/CONFUSE_R and other
utility spells do not enter the new combat-delay/commit path. Shuriken fanout,
Elder sweeps and Abyssal attacks count once per action rather than per victim.
Bloodveld pull commits once; Dark Beast commits at charge start and retains its
receipt through the telegraph. Later Stagger never retroactively cancels an
already committed projectile or charge. Impact retries cannot count another
attack or spend another recovery step.

`DamageRequest`/`DamageResult` retain explicit suppression and critical facts.
The settlement carrier enforces zero/noncritical for a suppressed commitment,
even if a future caller supplies positive damage or critical metadata. Forced
zero cannot bypass through offhand/Scythe extras, charged Death Ring, Ogre or
other leather offense, NPC poison side-effect scripts, thunder splashes or
other dependent damaging procs. Natural zero retains its previous zero-hit
eligibility. Defensive/survival CombatScripts remain in place. Unrelated
Wind/Smoke/Scorch and leather duration counters retain their legacy per-attempt
semantics; only the new Stagger recovery counter excludes staggered turns.

## Critical identity and compatibility

`CriticalHitResolver` accepts authored chance, maximum, provenance, RNG and an
ordinary-roll supplier, returning immutable `AttackRoll`. Cosmic is the sole
enabled modern chance provider, with its existing per-tier values and 50% cap.
Its compatibility chance method delegates to the shared resolver. Successful
criticals use the existing resolved maximum; ordinary maximum rolls remain
ordinary. Zero maximum/zero chance do not add a chance draw; a successful
critical does not roll ordinary damage. Existing crit damage, caps, prayer,
mitigation and multiplier order are preserved.

True identity travels through actual modern melee, bow/throwing and damaging
Magic launch/impact into resolved damage. No mutable Mob last-critical flag is
used. Existing secondary/offhand Cosmic damage/RNG eligibility is preserved;
typed outcomes are retained at existing migrated transaction boundaries.
Remaining integer-only secondary/death helpers do not have complete critical
provenance. The lethal God/Iban secondary helper preserves its historical direct
death ordering. Those helpers are not approved future hostile-trigger boundaries.
The alternate OSRS Strength-cape label/profile is unchanged and is not relabeled
as a modern Cosmic critical.

Expose Weakness, Weak Legs, Weak Constitution, their chance aggregation and
secondary/offhand hostile-trigger eligibility remain unimplemented. No planned
15% hostile chance was silently added to Cosmic.

## Delay composition

Whip retains its 10% proc, one-tick movement pause and three following immunity
ticks. Sticky retains its 10% qualifying positive-hit proc and attack-only scope.
Each source can own one pending actor-wide token. Ordinary style/base cooldowns
remain with their existing owners. Once an attack is otherwise admitted, the
shared latch adds the tokens; a second source arriving during that wait extends
the same deadline once. Actual commitment consumes the receipt. Polling,
retargeting, event replacement and style changes do not clear or multiply it.
Offensive early Whip locks now use this single delay authority, avoiding an
extra movement-pause tick plus another attack tick when already ready.
`WalkingQueue` still preserves Whip's independent movement pause.

Each cadence owner records an immutable exact Slow tick/tier pair. The admission
latch adds only the current tier not already included in that candidate interval.
Fresh Slow1 plus Whip plus Sticky therefore adds three ticks to the upcoming
otherwise-ready attack. Captured Slow1 raised to Slow2 adds only one new Slow
tick. Style checks share candidate credit; decay/clearing never shortens an
already latched wait, and a later fresh interval can acquire Slow again. Melee,
ranged, Magic and custom shared cooldown owners record their own credit without
replacing their base timers. Reciprocal melee's existing Slow exclusion remains.
Slow power/caps/decay are unchanged.

This upcoming-attack Slow policy is the manager's disclosed working interpretation
of the owner's three-ticks example. The optional clarification was unanswered
when implemented; this does not claim a separate explicit owner reply. There is
no alternate user setting or hidden timing mode.

## Validation

Maintained Java 8 server/client builds use 512 MiB SerialGC sequentially. Native
fixtures use a tiny fresh map/configuration, memory players/NPCs, real handlers,
real attack events and real resolved damage, without network listeners/accounts.

Final commands and receipts are collected under:
`/home/justin/.local/share/shared-critical-stagger-review/2026-10-31`.
The final summary records commands, environment, assertion counts and artifact
hashes. All final gates passed serially: native 171, pure state 88, combat 144/144,
Slow boundaries 115 plus source/legacy adapters, coatings 1,042, antidote 102,
Cleric native 354, client 2,986 and retained generic HUD 68. The nine Slayer
targets were leather effects, rewards, mobility shield, Abyssal Demon, Dark
Beast, Bloodveld, Terror Dog, Naga and thunder casting. C10's retained generic
math characterization also passed.

- `test-shared-attack-state.py`: deterministic N/recovery, token deduplication,
  Slow credit/top-up/decay/style checks and source-independent crit RNG/max rules.
- `test-shared-attack-runtime.py`: real melee, bow, throwing fanout, player Magic
  packet, NPC Magic and legacy NPC ranged launch/settlement. Failed ammo/runes,
  mixed sources, five normal attacks including natural zero, lifecycle, true
  critical versus normal maximum, late Stagger, duplicate impacts, forced-zero
  charged-ring/poison guards, exact additive ready/nonready/midwait timing,
  movement pause, immunity RNG, private cooldown and charge ownership.
- Slow boundaries/source adapters/legacy ranged retained and migrated to the
  explicit upcoming-action delta policy; no damage/RNG coverage removed.
- All 144 combat scenarios, coatings, antidote/poison, all nine requested Slayer
  targets and relevant Cleric runtime/client combat gates.
- Generated contract/classpath checks and `git diff --check`.

No art archives, production IDs, map content or gameplay protocol versions change.
The client changes only existing Ogre/Thunder descriptions. Full repository-wide
`test-all` and owner hands-on gameplay are not claimed. Core integrates, verifies
and stages the exact READY commit separately; live activation is not part of this
handoff.

### Core integration checks

The first manager verification found two source-hook checks still naming the
replaced Cosmic/Ogre helpers. They now check the shared resolver and one-attack
Stagger authority, including the unchanged Cosmic chance/cap and five-attack
recovery. Native behavior is covered by the launch/settlement fixtures above.
The Magecraft source check also still required retired percentage-based Earth
timing; that check already failed on the pre-integration main commit. It now
checks shared Slow timing and the reciprocal profile, and rejects reintroduction
of the old percentage hook. These are integration-test updates, with no further
gameplay or balance changes. Manager matched-build receipts are preserved in
the prepared update's `evidence/critical-stagger-foundation/` directory.
