# Giant frog — first combat integration

Scope: one encounter, NPC 863. The other seven approved NPCs remain harmless
movement previews. No tower placement, shop economy, Slayer credit or loot is
enabled by this change.

## Approved mechanics

- Ranged-only spit; approach until a clear shot is possible, then hold position
  through the attack cooldown. No retreat/kiting when the player approaches.
- Slimy Spit prevents new melee, ranged, throwing and offensive magic attacks
  for ten seconds. A fresh application resets that deadline to ten seconds.
- Slime Solvent protects against **both** frog slime and frog poison for ten
  minutes. Both named statuses use the existing potion HUD and potion artwork.
- Explicit modern offense and all three defenses; melee is the weakest defense,
  ranged intermediate, magic strongest.

## Initial test tuning and behavior

Level 20, 30 Hits, ranged offense 30, melee defense 10, ranged defense 20,
magic defense 35, projectile range five tiles. These are initial balance values,
not owner-approved final tuning. Legacy attack/strength/defense remain structural
placeholders of one; they do not supply the frog's used combat values.

An admitted spit dealing positive damage applies both effects. Misses/zero damage
do not. Poison uses power 10 (one Hit on its first normal poison tick), with the
existing poison decay and cadence. Ordinary antidote prevents poison but not slime.
Solvent leaves the spit damage intact and does not protect against other sources
of poison. Drinking also removes current Slimy Spit and cures poison whose
recorded source is the frog; it is not a general antipoison.

Solvent is a single-use potion placeholder, item 3318, currently untradeable and
unpriced pending the shop pass. Re-drinking refreshes ten minutes, not stacks.
Dead players and noted/absent items cannot activate it. Effect deadlines live in
persistent player cache and elapse in real time, including offline time; neither
death nor logout refreshes them. Already-launched attacks are not cancelled.
Movement and non-attack actions remain available while slimed.

The frog's approved three attack frames play on NPC projectile notification;
the existing acid-drop visual is the initial spit projectile. No art regenerated.
Status names appear in the existing HUD tooltips with countdowns.

## Testing

Spawn with `::spawnnpc 863 4 10` and obtain solvent with `::item 3318 5` on an
administrator account in a private test world. Use matching updated
client/server builds. Check range holding, close-range ranged attacks, each player
attack style while slimed, refresh, drinking with spit in flight, and expiration.
Inspect animation facing and the HUD in game; automated checks cannot certify
their appearance.

Builds: `./scripts/build-server.sh`, `./scripts/build-client.sh`.
HUD and art regressions: `python3 tests/myworld/test-cleric-status-hud.py` and
`python3 tests/myworld/test-slayer-movement-preview.py`.
Combat fixture: `CurrentGiantFrogCharacterization` in the existing Ant combat
test classpath. It tests stats, timers, poison-source isolation, every attack
style's admission/commit gate, ranged shooting and cooldown position holding.
Its standalone runner additionally checks consuming one bottle and rejecting
replayed inventory actions:

```sh
sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml compile_combat_tests
cd server
java -cp '../output/combat-test/test-classes:../output/combat-test/core-classes:plugins.jar:lib/*' com.openrsc.server.combat.CurrentGiantFrogCharacterization
```

The full combat characterization gate currently stops at the existing
`current_projectile_impact_lifecycle_policy_is_characterized` terrain-boundary
fixture. The same failure reproduces on unchanged main 0c10ab933; do not claim
the full suite passes or broaden this task into terrain repair.
