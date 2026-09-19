# Giant frog — first combat integration

Scope: one encounter, NPC 863. The other seven approved NPCs remain harmless
movement previews. No tower placement, shop economy, Slayer credit or loot is
enabled by this change.

## Approved mechanics

- Ranged-only spit; approach until a clear shot is possible, then hold position
  through the attack cooldown. No retreat/kiting when the player approaches.
- Slimy Spit prevents new melee, ranged, throwing and offensive magic attacks
  for ten seconds. A fresh application resets that deadline to ten seconds.
- Slime Solvent prevents and clears only Slimy Spit's attack lock for ten
  minutes. Frog poison remains unaffected. Both named statuses use the existing
  potion HUD and potion artwork.
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
Solvent leaves the spit damage and poison intact. Drinking removes current
Slimy Spit but neither cures existing poison nor prevents new poison, including
poison from the frog itself.

Solvent is a nonstackable three-dose potion placeholder: 3318 (3 doses),
3319 (2 doses), 3320 (1 dose), then an empty vial. Existing 3318 bottles count
as full bottles. Each use replaces the exact bottle in its existing slot and
refreshes ten minutes, not stacks. All variants retain the approved examine text
and are currently untradeable and unpriced pending the shop pass.
Dead players and noted/absent items cannot activate it. Effect deadlines live in
persistent player cache and elapse in real time, including offline time; neither
death nor logout refreshes them. Already-launched attacks are not cancelled.
Movement and non-attack actions remain available while slimed.

The frog's approved three attack frames play on NPC projectile notification;
the existing acid-drop visual is the initial spit projectile. No art regenerated.
Status names appear in the existing HUD tooltips with countdowns.

Owner's first combat playtest passed: range behavior, effects and the selected
attack animation worked as intended. Follow-up feedback cleanup gives solvent
the examine text "Slimy frog spit begone!", removes the poison disclaimer from
the use message, and delegates it out of the generic drink handler so it cannot
also emit "Nothing interesting happens" after applying protection.

## Testing

Spawn with `::spawnnpc 863 4 10` and obtain supplies with `::slayergimmicks` on an
administrator account in a private test world. Use matching updated
client/server builds. Check range holding, close-range ranged attacks, each player
attack style while slimed, refresh, drinking with spit in flight, and expiration.
Inspect animation facing and the HUD in game; automated checks cannot certify
their appearance.

`::slayergimmicks` grants one full three-use item for each implemented Slayer counter to your
inventory, currently Slime Solvent only. It requires room for the entire kit and
does not consume or activate the items. Future implemented counters belong in
`SlayerGimmickTestKit.ITEM_IDS`. Ordinary players cannot use this test command.

Builds: `./scripts/build-server.sh`, `./scripts/build-client.sh`.
HUD and art regressions: `python3 tests/myworld/test-cleric-status-hud.py` and
`python3 tests/myworld/test-slayer-movement-preview.py`.
Combat fixture: `CurrentGiantFrogCharacterization` in the existing Ant combat
test classpath. It tests stats, timers, poison remaining active under solvent, every attack
style's admission/commit gate, ranged shooting and cooldown position holding.
Its standalone runner additionally checks all three doses, full-inventory
conversion, the final empty vial, and rejection of replayed inventory actions:

```sh
sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml compile_combat_tests
cd server
java -cp '../output/combat-test/test-classes:../output/combat-test/core-classes:plugins.jar:lib/*' com.openrsc.server.combat.CurrentGiantFrogCharacterization
```

The full combat characterization gate currently stops at the existing
`current_projectile_impact_lifecycle_policy_is_characterized` terrain-boundary
fixture. The same failure reproduces on unchanged main 0c10ab933; do not claim
the full suite passes or broaden this task into terrain repair.
