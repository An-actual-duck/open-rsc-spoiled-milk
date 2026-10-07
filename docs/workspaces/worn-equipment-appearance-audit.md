# Worn equipment appearance-ID correction

## Cause and scope

The worn-appearance protocol is one-based. The client resolves each player
layer with `player.layerAnimation[layer] - 1`. Some later item definitions
mistakenly stored zero-based animation indices. This selects the preceding
animation, including its directional frames and colours. In particular:

| Item | Old appearance | Old rendered family | Correct appearance |
| --- | ---: | --- | ---: |
| Hood (3191) | 1034 | scythe | 1035 |
| Black gauntlets (3131) | 989 | pickaxe | 990 |

Weapon sprites rendered in an armour layer also explain the apparent hand
changes across viewing directions. No sprite regeneration or client protocol
change is needed.

330 distinct items corrected:

- 282 plain/elemental wool gloves and boots (2794–3075).
- 14 metal/special gauntlets, including authentic Steel, goldsmithing, cooking,
  chaos and Klank's gauntlets.
- Six blessed metal hand/foot pieces (3131–3136).
- Fifteen blessed wool pieces (3137–3151).
- Two Guthix symbols (3174–3175).
- Eight custom square/paladin shields. Their underlying custom definitions and
  final MyWorld overrides both now select the intended shape/colour.
- Hood, Fire sword and Ice sword.

Only appearance fields change in gameplay data. Slots, stats, ownership, item
IDs, inventory sprites, prices and assets remain unchanged. Correct scythe,
Exalted Rune, female platebody and other published animation IDs are retained.
Do not apply a global offset or change the client subtraction.

## Verification

`tests/myworld/test-worn-catalog-animation-resolution.py` compiles the current
client EntityHandler and runs the existing final-definition probe without
starting a server. It checks all 1,812 visible wearable definitions for valid
indices and slot-compatible families; verifies the exact 330 corrected
mappings in underlying and MyWorld-overridden definitions; and verifies wool,
metal and blessing palettes against the executed animation registry.

Run after building a client, or provide a compatible dependency JAR:

```bash
python3 tests/myworld/test-worn-catalog-animation-resolution.py --client-jar /path/to/Open_RSC_Client.jar
```

The registry is compiled from current source ahead of the supplied dependency
JAR. A negative-control run against the original JSON definitions fails on the
first plain wool-glove mapping, proving that the test rejects the old data.

Thirteen related tests pass: worn hand/foot baselines, god-knight conversion,
Hood, Fire/Ice swords, combat data, animation-ID contract, worn appearances,
female platebodies, scythes, god maces, ranged slots, demon pitchfork/shears and
Exalted Rune poisoned weapons. `git diff --check` passes.

The broader `test-blessed-symbols.py` reaches a pre-existing failure:
`devotion should track every-other symbol bonus`. It fails identically on the
unchanged manager baseline. Its appearance expectation was corrected, but the
unrelated devotion behavior is not changed here.

This is a definition/registry audit, not a live visual inspection of every
frame. Runtime item-condition overrides are not executed by the new test;
their retained mappings are covered by the related contract tests. The new
checks resolve base JSON plus the final MyWorld JSON overrides.

## Integration and activation

Prepared in the user-authorized isolated topic clone because all three
registered worker slots are occupied. The manager, those workers and the live
deployment remain untouched. Review the exact topic commit before integration;
refresh derived content catalogs as required by the maintained export pipeline.
Public activation still requires a separately authorized graceful restart.

After activation, inspect Hood and black gauntlets through all eight facing
directions, then sample blessed robes, wool boots/gloves and custom shields on
both body types. Confirm inventory sprites and combat stats remain unchanged.
