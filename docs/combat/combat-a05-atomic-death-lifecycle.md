# A05.5 Atomic Death Lifecycle

## Bounded authority change

A05.5 adds one small, per-mob authority for ordinary reward-eligible death.
The authority atomically decides which `killedBy` invocation owns a mob's
current death, captures immutable identity, rejects reentrant duplicates, and
keeps the identity current through the established respawn delay.

It does **not** own or reorder damage, Hits, the legacy `killed` flag, combat
state, kill type, contribution, XP, rewards, drops, plugins, listeners,
packets, removal, or respawn. `Npc.killedBy` and `Player.killedBy` retain those
policies in their existing order. Existing event-local death adapters also
remain the callers; A05.5 does not move them into the resolved-damage
transaction.

## Lifecycle contract

Each `Mob` owns one `DeathLifecycleAuthority` with a positive generation and
four states:

1. `ALIVE` — no ordinary death owns this incarnation;
2. `DYING` — one immutable context owns active death processing;
3. `DEAD` — processing and terminal removal/respawn setup completed; and
4. `RESPAWNING` — the established delayed reset has not created the next live
   incarnation yet.

`tryBeginDeathLifecycle` returns a typed `STARTED` or `DUPLICATE` transition.
The context captures the generation, target UUID and entity type, target combat
generation, direct killer reference/UUID, signed world location, and server
tick. It intentionally does not pre-resolve a player owner or kill-credit
winner; current NPC contribution and summon-owner code remains authoritative.

NPC and player respawn callbacks may reset only the exact context they were
created for. A successful reset advances the generation, returns to `ALIVE`,
and clears the prior context. A stale callback cannot reset a newer lifetime.
The diagnostic snapshot reports state, context, generation, and duplicate
attempt count without mutating gameplay.

## Preserved NPC phase order

For an ordinary NPC, acquisition occurs after the existing null/already-killed
guards and before poison cleanup, plugin dispatch, or rewards. The current
order remains:

1. resolve direct player or summon owner;
2. clear poison and other cure state;
3. dispatch the existing `KillNpcTrigger` decision/action boundary;
4. update range and blocked-damage state;
5. snapshot personal-loot recipients and distribute contribution XP and
   pending summon XP;
6. resolve the current kill-credit fallback;
7. send victory/reward state and apply charged jewelry effects;
8. log and send kill count;
9. produce personal drops;
10. invoke death listeners; then
11. clear listeners and call the existing `Npc.remove` path.

The authority does not set NPC Hits to zero. Compatibility-helper lethal hits
therefore retain their characterized death-before-presentation and raw-Hits
behavior. The legacy `killed` projection is still set by `Npc.remove`, after
listeners, exactly as before.

A reentrant listener or callback now receives `DUPLICATE` and cannot replay XP,
rewards, drops, or listeners. If an unexpected runtime failure escapes after
ordinary ownership is acquired, the server logs NPC ID and lifecycle ID,
attempts the same terminal removal, and rethrows the original failure. This is
the intentional safety correction in A05.5: already-completed rewards cannot
be replayed by submitting the same lethal request again. Cleanup failure is
attached as a suppressed exception rather than hiding the original cause.

## Preserved player phase order

Players acquire ordinary death ownership after the existing logged-in and
legacy-killed guards. `Player.killedBy` still sets `killed`, cancels attack
intent, advances A04 combat ownership, sends death presentation, handles the
tutorial survivor, clears transient effects, applies PvP/duel/drop/Hardcore
rules, clears combat and summons, teleports, refreshes equipment/inventory and
party state, cures effects, normalizes stats, resets updates/skull, and finally
schedules `Reset Killed`.

The ordinary reset now carries its exact context. It releases the legacy
`killed` guard only when that context still owns the respawning generation.
Peter Skippin's tutorial path retains its immediate survival behavior and
advances to a fresh live generation without scheduling the ordinary reset.
Logged-out death calls remain no-ops and acquire no lifecycle.

Existing Cleric, reflection, and simultaneous-death callers are unchanged.
Their established victim/attacker ordering continues to call each mob's own
independent authority, so one participant's terminal state cannot suppress the
other's death.

## Explicit plugin compatibility adapter

The former `removeHandledInPlugin` array is now clearly named
`PLUGIN_OWNED_DEATH_NPC_IDS`. It contains the same ten IDs:

- tutorial rat;
- Delrith;
- Count Draynor;
- Chronozon;
- Sir Mordred;
- Lucien at Edgeville;
- Black Knight Titan;
- Peter Skippin;
- Spookie; and
- Scarie.

Those NPCs deliberately bypass ordinary lifecycle acquisition. Their
`KillNpcTrigger` still decides quest-specific progression/removal, the tutorial
rat retains its immediate removal exception, and other entries may remain at
zero Hits with no ordinary rewards or listeners until their plugin acts. A
repeat `killedBy` call still requests another plugin decision when the plugin
has not removed the NPC. This is active quest compatibility, not obsolete code.

The dormant `PlayerDeathTrigger` interface remains untouched and unactivated.
Introducing new player-death plugin dispatch would change current gameplay and
requires its own inventory and approval.

## Provenance and adaptation

Classic-Scape commit `e66ec1fa76a759682bdab443c264b4440f5ee08f` demonstrated
immutable death contexts, atomic lifecycle acquisition, state generations,
and stale-respawn rejection. A05.5 preserves that contributor attribution but
independently adapts only the bounded concepts needed by current Spoiled Milk.

It does not copy Classic-Scape's broad `DeathStage` coordinator, change
scripted removal into a terminal lifecycle, set Hits/killed during acquisition,
catch and swallow optional player/NPC stages, move contribution to snapshots,
or force-remove quest-owned NPCs. Those choices would alter current plugin,
presentation, contribution, layered removal, or failure behavior.

## Executable verification

Five runtime scenarios grow the authoritative combat gate from 66 to 71. They
cover:

- ordinary NPC reward, XP, listener, removal, immutable context, reentrant
  duplicate, non-respawn, respawn-generation, and stale-reset behavior;
- a deliberately failing NPC listener, propagated failure, diagnostic cleanup,
  and absence of reward/listener replay;
- player death packet, poison cleanup, combat-generation teardown, stat
  normalization, duplicate suppression, delayed reset, and stale reset;
- plugin-owned Count Draynor decisions, absence of ordinary reward/listener
  authority, and maintained repeat-decision compatibility; and
- Peter Skippin tutorial survival plus logged-out player no-op behavior.

Existing reflection fixtures continue to execute simultaneous deaths across
Cleric Thorns, Divine Retribution, Frostbite, melee jewelry, and projectile
recoil. All prior melee, projectile, secondary, contribution, XP, overkill,
packet, drop-listener, poison/respawn, and callback-order scenarios remain in
the same 71-scenario gate.

## Exclusions and next boundary

A05.5 does not activate new plugin hooks, consolidate kill-credit or
contribution, migrate `Mob.damage`, change script/admin removal, alter death
drops, or make lifecycle state a new combat eligibility rule. The public
`killedBy` compatibility surface remains intact.

A05.6's focused branch finds no redundant post-migration block safe to delete.
It migrates the one representable non-negative unclassified projectile
settlement and records why signed/admin/script/helper mutations remain active.
A later failure-hardening branch may add stage-level retry/diagnostic state,
but must first define which optional callback failures are recoverable.
Plugin-owned quest deaths require individual characterization before any ID can
leave the compatibility inventory.
