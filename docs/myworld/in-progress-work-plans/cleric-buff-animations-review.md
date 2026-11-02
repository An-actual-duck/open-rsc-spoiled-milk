# Cleric blessing recipient animations

This records the completed blessing-only checkpoint. The later [curse animation
review](cleric-debuff-animations-review.md) documents the current eighteen
assigned god-line visuals; the original receipts below remain historical.

Status: implementation complete; exact READY checkpoint is recorded by the worker handoff.

Nine owner-selected cosmetics attach to named blessing components, independent of
spell display names and numerical potency. Tier 2 concatenates its first two
components; Tier 3 concatenates all three. Sap Blood's cleansing and restoration
share one visual. Curses and both Unify spells remain animationless.

| Effect | Sheet | Frames | Cosmetic ID |
| --- | --- | ---: | ---: |
| Hallowed Strikes | holy-sword | 25 | 86 |
| Quickened Pulse | holy-bells | 13 | 87 |
| Mending Flesh | holy-shield | 17 | 88 |
| Sap Blood | cleanse 2 | 17 | 89 |
| Barkskin | bark | 21 | 90 |
| Thorns | sword-clash | 11 | 91 |
| Unnatural Might | fist | 14 | 92 |
| Bloodrage | sword | 11 | 93 |
| Blood Hunger | lightning-buff | 17 | 94 |

Tier 2/3 sequence IDs are Saradomin 95/96, Guthix 97/98, Zamorak 99/100.
The largest sequence has 55 frames and fits the existing 64-frame slots. NPC
pose IDs 78–85 remain reserved. Legacy alias 73 now loads the byte-identical
canonical holy-sword sheet; alias 74 loads the byte-preserving bark rename.
The owner's 26 duplicate Holy VFX 03 files were removed; original Git history
remains available. The new lightning sheet is copied exactly from the owner's
verified input. No PNG pixels were edited.

Server appearance queues filter these IDs before computing packet record
counts. Only the effect target receives its buff animation; ordinary player
and NPC combat effects retain existing broadcast behavior. Actual application
commit remains inside the successful sigil transaction. No gameplay timing,
resources, registry slots, god replacement, power or duration rules changed.

The client has independent, lazy local-recipient playback: one active sequence
and at most one pending sequence per god, in FIFO order. Pending same-god
refreshes coalesce; a lower sequence cannot erase a queued upgrade. Different
gods cannot overwrite one another or ordinary combat overlays. Logout/book
reset, region transition and actor transient/slot reset clear cosmetic state.
The software draw and native phased replay share a WORLD_OVERLAY sprite
command. No temporary writes to actor combat-effect fields or anchors occur.
Sprites draw on their existing 64px canvas: visible artwork remains within
48px, with Barkskin 32px, avoiding a second shrink.

Schema 5 and client version 10056 are retained: animation metadata and the
one-byte effect carrier already support optional unknown IDs. The retained
pre-animation 10056 JAR (SHA256
`26de7ad6c970dbc2d87412fa29ac7d7ae6a829882c0275fda072656b9d9be60b`)
decodes the fresh real 20-definition packet with nine optional animation IDs.
All 15 new IDs have zero duration in that client and pass its actual player
appearance PacketHandler without a crash. This is safe protocol/gameplay
fallback, **not identical old-client visuals**: the old handler detaches an
active combat overlay and clears a pending projectile-impact cosmetic. The
paired current client preserves both active and pending combat visuals.

Validation (offline, real production paths unless stated otherwise):

| Gate | Result |
| --- | --- |
| Java 8 server and client builds; generated contract check | PASS |
| `test-cleric-buff-animations.py --old-client <retained-JAR> --wire <fresh-wire>` | 260 real cast/resource/player+NPC appearance assertions; 2829 native source/embedded loader, packet, queue, frame, footprint, overlay and reset assertions; all 15 old-client packet fallbacks PASS |
| `test-cleric-stacking.py` | 581 multi-Cleric god-slot/replacement/cost checks PASS |
| `test-cleric-blessings.py` | 386 catalog/cast/lifecycle/combat/Unify/wire checks PASS |
| Consistency buffs / hostile / hostile producers / projectile | 88 / 121 / 24 / 30 assertions PASS |
| `test-cleric-three-tier-client.py --wire <fresh-wire> --render <output>` | 63504 actual spellbook/guide/viewport/input assertions, native tooltip/HUD/font renders and 68 generic HUD checks PASS |
| `test-cleric-spell-icons.py --wire <fresh-wire>` | 162 exact source/JAR/native icon assertions PASS |
| Legacy C12 alias, tier-two spell animation, projectile fallback/range, true-defense, phased overlay gates | PASS |
| `./server/test_combat` | 145/145 scenarios PASS |
| Changed-code Checkstyle, PMD and Ruff | PASS after test-only explicit-import cleanup; Ruff 0 findings |
| Report-only whole-program CPD | Initial 512MiB heap exhausted; manager-authorized isolated 1536MiB retry PASS; original failure retained |
| Whole-program SpotBugs | NOT COMPLETED: bounded client phase was stopped after more than four minutes, before any report; no SpotBugs pass is claimed |

The schema-5 positive hex fixture was regenerated from actual fresh
`PayloadCustomGenerator` bytes. Its length remains 6050 bytes; SHA256
`fca4205c56029773610af7a0307187c0f084885811c22c16bfb53625c6b4b949`.
Old-schema negative fixtures are unchanged.

Four historical substring gates fail on unchanged baseline code; this work
updates only their combat-effect-capacity expectations from 77 to 100:

- `test-npc-elemental-magic.py` expects obsolete `dualElementProcs` argument text
  that is already absent from the unchanged baseline `ProjectileEvent.java`.
- `test-animation-asset-migrations.py` and
  `test-equipment-proc-animation-migration.py` expect projectile count 34;
  the baseline already defines 35. The new actual native/range fixture and
  maintained projectile fallback gate verify non-overlap and valid ranges.
- `test-fire-sword.py` expects retired
  `applyEarthAttackSpeedDebuff(ELEMENTAL_SWORD_EARTH_DEBUFF_PERCENT)` text in
  unchanged baseline `CombatFormula.java`.

Those failures are not counted as passing tests. Unrelated stale expectations
were not rewritten as part of cosmetic implementation. Existing gameplay and
source guard behavior were preserved.

Evidence stays in ignored `output/tests/cleric/buff-animations/`: per-gate logs,
fresh native wire, source/embedded native frame PNGs, three cumulative GIFs,
UI/guide renders, artifact SHA receipts and static-analysis reports. Native
WORLD_OVERLAY command capture and the existing phased integration gate verify
the OpenGL bridge; an actual GPU/gameplay session was not run. Final paired
private visual acceptance remains with Core/the owner.

Built artifact SHA256 receipts (production unchanged after the implementation
checkpoint; subsequent changes are tests, the positive wire fixture and docs):

- `server/core.jar`: `aca0399068b2e219cd85212a73027877d05f5bcff9cba0622b3c768f013dfc0c`
- `server/core-gameplay-overlay.jar`: `8478ecdb55fd6b7332f14e430ceba0212256a00f6d9f7c01aa68e41332bbe848`
- `server/plugins.jar`: `5afed1a856b83fb50b21e399d307271bad0967a4f8494ca08276711aa732f1e5`
- `Client_Base/Open_RSC_Client.jar`: `a48b045007292f5f1b76a70ef6319a969365e4a44fb9b330bd3e61b82a8635a3`

All gameplay/build tests ran serially under the Core heavy-build lock with
Java 8, 512MiB server and 256MiB client heaps. Static-analysis tools require
Java 21. No server startup, network listener, account/database, owner-map,
manager source modification or public process was used.
