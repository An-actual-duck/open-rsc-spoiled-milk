# Naga combat test contract

NPC **866**, combat level **60**, Veteran floor 3 alongside Banshee. No gimmick,
counter-item, permanent spawn, loot, shop or task-roster changes in this stage.

## Starting balance

75 HP; modern melee offense 65 and ranged offense 55. Defenses: melee 20,
ranged 65, magic 95. Legacy attack/strength/defense fields remain structural 1s.
Under the modern formula, unmitigated melee caps are 10 main-hand and 5 off-hand;
normal defense rolls, protections and reactive effects still apply.

## Behavior

- Beyond adjacency, throw within five tiles and clear projectile line of sight.
- Pause to throw, then approach between throws; no retreat or distance keeping.
- Adjacent: only dual-scimitar melee, two independently rolled hits on the same
  tick. Main-hand red, off-hand yellow. Off-hand rolls against the normal melee
  defense but uses half the normal attack cap (rounded down), not half the first
  hit or a capped copy of its roll. A main-hand miss can still be followed by an
  off-hand hit. Suppression prevents both; dead participants cannot follow up.
- One shared cooldown: melee two ticks, throw three ticks. Movement does not
  reset it, and crossing into melee cannot grant a free immediate strike.
- Ordinary leash, obstruction, death, damage ownership and retaliation paths
  remain in effect. One dual strike counts as one attack swing.

## Presentation

Approved movement frames 0–14 are unchanged. Melee uses 15–17; throwing uses
18–20, including its original wider canvas. Both custom and fallback sprite
loading retain all 21 frames. Throw animation is triggered by the NPC projectile
packet and uses the existing 600 ms attack presentation window.

Projectile visual **41** uses item **83**, the iron scimitar. Its actual item
sprite and color masks are sampled at load time into 16 nearest-neighbor rotated
frames, two revolutions over the flight. The original item pixels and approved
NPC artwork are not edited. Both software and OpenGL use the shared projectile
sprite frames. No new projectile item is added or dropped.

## Verification

Bundled Ant target `test_naga_combat` checks stats, distance-only selection,
actual movement during cooldown, no duplicate throws, shared melee cooldown,
separate capped rolls, simultaneous red/yellow transactions, and suppression.
`tests/myworld/test-slayer-movement-preview.py` checks source-pixel fidelity for
both attack columns and movement, registration, palette-preserving rotation,
and disk/JAR loading. Run the frog, Cockatrice, Banshee, ranged-approach and
gimmick-kit fixtures as regressions.

Manual test: `::spawnnpc 866 4 10`. Check throwing from range, closing movement,
dual hits at adjacency, projectile rotation and separate attack animations.
