# Abyssal demon combat test contract

Owner-approved encounter implemented for private testing, 2026-09-20.
NPC **870**, tower floor **6 / Hero**, displayed combat level **125**.
No permanent spawns, drops, task assignments, shops, gates or public deployment.

## Initial balance

- 250 HP; explicit modern melee offense 125.
- Melee defense 110; ranged and magic defenses 220 each.
- Legacy attack/strength/defense remain 1 as unused adapter placeholders.
- Melee only: no projectile attacks, no new poison.
- Stabs once per server tick (640 ms in MyWorld), with normal melee rolls.
- Both attacks use the existing melee settlement/mitigation/death pipeline.

## Sticky Flesh and shared Slime Solvent

A positive, nonlethal resolved hit from a stab or spike attack can trap the player.
Misses and fully blocked hits do not apply effects or erode protection.

Without solvent, apply **Sticky Flesh** for **3 seconds**, not three ticks.
Message: **Its sticky flesh wraps around you, you're trapped**

The trap prevents movement, all attack styles, eating, drinking (including
solvent), equipment/prayer changes, spells, dialogue choices, interactions and
other gameplay actions. Cancel pending walking/following, walk-to actions,
menus and plugin batches; retain combat ownership so incoming attacks continue.
The network dispatch guard drops gameplay requests rather than queuing them.
Chat, heartbeat, terrain receipts, reporting, and normal logout handling remain
available. Already launched effects/passive effects are not undone.

On natural release, display **You manage to break free** once. The first free
server tick and the following tick are immune to retrapping. All demons share
this target-owned immunity. Active traps and recovery hits never refresh the
effect or repeat its message. The three-second expiry is observed on the first
server tick at/after expiry (five 640-ms ticks at the normal tick rate).
Death/logout/new player lifetimes invalidate the transient trap; stale release
events cannot affect a new lifetime.

Slime Solvent remains the existing three-use item **3318 / 3319 / 3320**.
Each dose gives ten minutes, clears frog spit, and protects against both
frog attack-lock and Abyssal full-action trap. The examine remains
**Slimy frog spit begone!**. Use text now mentions sticky flesh and saliva.
It cannot be consumed during the full trap; use it before engagement or in the
escape window. Each damaging Abyssal stab or spike removes **5 seconds** from
the single shared solvent timer; simultaneous demons each erode it.
The hit consuming the final seconds is protected; the next hit can trap.
The timer clamps to zero, refreshes the HUD, and does not create extra items.
Frog poison still applies and frog hits do not erode solvent.
The existing **::slayergimmicks** already supplies one full solvent bottle.

## Spikes and cadence

Initial tunable selection: 20% of eligible melee opportunities, with at least
four ticks after the recovery before another spike selection.

- Tick 0: spike damage immediately to living players in the same spatial domain
  within radius 1 (the surrounding 3x3 square). Walls block it. Each target rolls
  independently; late arrivals are not struck. No one-tick escape windup.
- Client shows approved sinking frame 1 for 80 ms, then extended frame 2.
- Tick 2: rising/retracting frame 3.
- Tick 4: normal melee/chasing resumes.

There are no stabs or chase movement during recovery and no additional damage
from held visual frames. Death/removal cancels the pose; respawn starts fresh.
Server pose codes 82–85 are NPC-870-only controls (spikes, rise, cancel, stab),
not new generic overlay effects. Each stab displays retract/thrust/partial
retract in its one-tick cycle. Recovery cleanup cannot replace a fresh stab.

## Artwork

The approved-base.png in dev/myworld/assets/sprites/npcs/abyssal-spikes comes
from Sprite Trainer output abyssal-cosmic-demon-attack-v6-sunken-abdomen/full-sheet.png.
Its first five columns exactly match the previous movement import; the 259
changed attack pixels are the later owner-approved sunken-abdomen correction.
The approved-spikes-strip.png is the approved
abyssal-cosmic-demon-aoe-v2-color-match/strip.png.

tools/myworld/PackAbyssalSpikes.java copies pixels without scaling/recoloring:
columns 100/100/100/100/100/112/144, three 112px-high rows. Original 100px-high
frames receive six transparent pixels above and below to share the AoE canvas.
The 2.4-world-unit/native-pixel scale remains unchanged.

## Verification

Run test_abyssal_demon_combat through the vendored Ant server build.
Coverage includes current-stat definitions, actual melee damage settlement,
shared target trap, exact expiry, two free ticks across different attackers,
packet dispatch rejection, movement/attack guards, use rejection without bottle
consumption, one-tick cadence, AoE bounds, immediate damage, recovery and lifecycle.
Client tests verify pose timings, decoded status labels, approved pixel identity,
all directional frames and packaged/disk loading.

Private acceptance: spawn **::spawnnpc 870 4 10**, grant **::slayergimmicks**,
compare protected/unprotected fights, eat/drink/move during both trap and
escape window, then test multiple demons. Inspect blade/spike alignment and
camera rotations. Automated tests do not replace in-game visual acceptance.

The full legacy combat suite has a previously reproduced native-terrain fixture
failure in CurrentCombatProjectileLifecycleCharacterization; focused encounter
tests are used in addition to client/status regression tests.
