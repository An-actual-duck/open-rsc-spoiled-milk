# Cleric curse effect animations

Status: implementation complete; exact READY commit is recorded by the formal worker handoff.

All nine owner-selected curse effects are mapped by component kind. Tier 2
plays its first and second effects sequentially; Tier 3 plays all three.
Only useful accepted NPC applications emit the sequence through the existing
atomic sigil transaction. Curses are visible to nearby observers; recipient-only
blessings retain IDs 86–100 and their existing visibility, assets and timings.
Unify and Greater Unify remain animationless. NPC pose IDs 78–85 stay reserved.

| Effect | Canonical on-entity sheet | Source → playback frames | ID |
| --- | --- | ---: | ---: |
| Magical Allergy | general-buffs-debuffs/defense/mag-def-down.png | 11 | 101 |
| Muscular Atrophy | general-buffs-debuffs/melee/melee-down.png | 10 | 102 |
| Molecular Antipode | general-buffs-debuffs/bubble-pop/bubble-pop.png | 12 | 103 |
| Toxiphobia | general-buffs-debuffs/heart/heart-down.png | 9 | 104 |
| Agrizoophobia | poison-snake/poison-snake.png | 28 → 12 | 105 |
| Hematophobia | cut/cut.png | 15 → 12 | 106 |
| Expose Weakness | general-buffs-debuffs/accuracy/accuracy-down.png | 10 | 107 |
| Weak Legs | general-buffs-debuffs/break-defense/break-defense.png | 12 | 108 |
| Weak Constitution | general-buffs-debuffs/skull/skull-down.png | 13 | 109 |

Cumulative tier 2/3 IDs: Saradomin 110/111, Guthix 112/113, Zamorak 114/115.
The largest sequence is 35 frames, below the unchanged 64-frame per-ID slots.
The relocated projectile/mirror/static ranges remain non-overlapping and below
65536. Shared wire constants are generated from the canonical contract.
Schema 5/client 10056 remain unchanged: these are optional cosmetic IDs.

The 36 owner PNGs are copied byte-for-byte, including the general effects pack
kept together. The committed asset manifest records every SHA256. Runtime
normalization changes only playback sampling, not PNG pixels, cell crops or
per-frame origin. The two longer Guthix sheets sample their complete first-to-last
source range into 12 frames. All curse frames use 2 client ticks (40ms), giving
each of those normalized effects 480ms; native small effects retain 360–520ms.
Visible art fits 24px on the normal 64px native canvas, drawn at 64px to avoid
shrinking twice. Existing blessing and general combat frame speeds are unchanged.

Lazy NPC curse state shares the bounded queue mechanics with blessings: one
active and at most three pending god sequences, FIFO across gods. Same-god
refreshes coalesce and cannot erase a queued upgrade. The NPC packet path
explicitly admits curses; player/generic paths refuse them before detaching
normal effects. NPCs similarly refuse local-recipient blessing cosmetics.
Normal combat overlays, pending projectiles, phase state and special NPC poses
remain independent. Both software and native WORLD_OVERLAY capture come through
the actual drawNPC path, rather than the unused scene effect helper.

NPC removal clears transient visuals and is the authoritative death/despawn
boundary; reappearance/slot and type replacement, scene/area changes and
logout/reconnect clear pending curses too. Raw byte HP is deliberately **not**
used as a death signal: living 256HP or 512HP NPCs can serialize as zero. A native
fixture covers both legacy and typed HP zero aliases, followed by an actual NPC
removal packet that clears the queue. No HP protocol or gameplay changes occur.

Validation uses bounded offline processes and actual production methods:

| Gate | Result |
| --- | --- |
| Java 8 server/client builds; generated contract parity | PASS |
| `test-cleric-debuff-animations.py` | 36 exact source/JAR PNGs and nine source-cell geometries; 206 real casts/resource/two-NPC/two-observer packet assertions; 1688 native frame/resampling/drawNPC/queue/lifecycle/coexistence assertions PASS |
| `test-cleric-buff-animations.py` | Existing 260 server and 2829 native blessing assertions PASS; unchanged recipient-only behavior, frames, timing and footprints |
| `test-cleric-stacking.py` / `test-cleric-blessings.py` | 581 / 386 assertions PASS |
| Hostile / hostile-producer / projectile / Zamorak gates | 121 / 24 / 30 / 264 assertions PASS |
| Actual client UI/guide and generic HUD | 63504 UI assertions, 68 generic HUD assertions and native font/tooltips PASS |
| Exact Cleric icons | 162 assertions PASS |
| Maintained NPC capacity production/client fixture | 16384 registrations, 1024 view budget, classic/retro carriers, high-index decode and removal/re-add PASS |
| Maintained native `SlayerMovementPreviewFixture` from disk and embedded resources | Eight previews, source-frame fidelity, Dark Beast IDs 78–81 and Abyssal IDs 82–85 pose isolation PASS |
| Legacy alias, tier-two animation, projectile fallback/range, true-defense and phased-overlay gates | PASS |
| `./server/test_combat` | 145/145 scenarios PASS |
| Changed-line Checkstyle/PMD/Ruff | PASS; Ruff zero findings |
| Bounded compiler lint for client/server/world-builder | PASS: no new gated javac warnings; 44 existing gated warnings, 327 total reported |

The positive schema-5 fixture was regenerated from the actual native
PayloadCustomGenerator. It remains 6050 bytes, now with eighteen assigned god-line
animations and only the two Unify definitions unassigned. Old-schema negative
fixtures are unchanged. SHA256:
`30b18ef42fce31db24c44d019fede5cbe2e028ede9ce86fbfa2a994331e22d14`.

Compatibility proof compiles a fixture against retained pre-curse client 10056
with effect count 100 (SHA256
`d05a6a6a5be4047864de606a5ee0ec835b2d13ecb5e8cb3acbecc8b2c7a2e77a`).
It decodes the new 20-definition/18-animation metadata and handles all 15 unknown
curse IDs through its actual player and NPC appearance handlers without a
protocol failure. The older pre-buff count-77 client also passes the updated
18-animation metadata and unknown-effect packet fixture. Unknown visuals are
omitted by those clients. Their legacy handler may detach an ordinary overlay
and clear a pending projectile-impact cosmetic; this is documented cosmetic
fallback, not identical rendering. The new paired client preserves all those
independent fields, including phase and special poses.

Focused reproduction (after building the paired source; run under the shared
heavy-build lock with Java 8):

```sh
python3 tests/myworld/test-cleric-debuff-animations.py \
  --old-client /path/to/retained-pre-curse-10056.jar \
  --wire output/tests/cleric/debuff-animations/cleric-schema5.bin
```

`--output` chooses a diagnostic output folder. Without `--wire`, compatibility
uses the maintained positive hex fixture; without `--old-client`, the source/JAR,
server and native client gates still run. The existing pre-buff compatibility
fixture defaults to count 77; the curse runner explicitly requests count 100.

One historical Slayer wrapper fails its obsolete assertion that no other file
may override NPC IDs 863–870. The target NpcDefsMyWorld.json is byte-identical to
the pre-task source. That wrapper is not counted as passing and was not weakened.
The meaningful native pose/frame fixture was run directly and passed. Four
previously documented unrelated substring guards were not rerun. Whole-program
CPD/SpotBugs were not repeated for this focused cosmetic task; no new whole-program
analysis result is claimed.

Ignored evidence: `output/tests/cleric/debuff-animations/` contains per-gate logs, fresh
wire, source/embedded native frame PNGs, three cumulative GIFs, a nine-effect
comparison sheet, UI/guide renders, source-check/compiler-lint reports and
artifact SHA receipts. The actual drawNPC software output and WORLD_OVERLAY
commands are tested. Actual GPU/private in-game visual acceptance remains for
Core/the owner; no gameplay session or desktop client was launched.

Final built artifact SHA256 receipts (compiler-linted builds; focused curse test
repeated successfully against these final builds):

- `server/core.jar`: `726255c60c515f91fdaebf02aaabf162c747139c7e693e69ac4e53b39096d85f`
- `server/core-gameplay-overlay.jar`: `326721ff34dbbb254512b7c6a19e9a6f6d829fe4084592b0632cece26e0f246e`
- `server/plugins.jar`: `58c3a75643435eb38a75efc999b53009354a987de2b03217766f5753e2794e91`
- `Client_Base/Open_RSC_Client.jar`: `69a189885f4a6364090e6a992376e637f61e1d9cfd61dc9d9846c8cbedd6cafd`

Production source and assets are unchanged after the first reviewed implementation
checkpoint `48fbb736c4ebe907ccbf4203f444f507e52eeffd`; the final delta contains test,
positive-wire and documentation refinements only. All heavy jobs ran serially
under the shared Core lock. Java 8 builds/server fixtures use 512MiB bounds;
client fixture launches request 256MiB. Static-analysis tools require Java 21.
No server startup, listener, account/database, owner map, manager-source change,
release operation or public process was used.
