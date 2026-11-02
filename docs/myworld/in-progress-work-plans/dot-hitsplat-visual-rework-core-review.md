# Owner-approved floating hit-number rework — Core review

October 31, 2026. Branch `feat/hitsplat-visual-rework`, exclusive worker ai-2.
Implementation and runtime-tested checkpoint:
`26001e8a51a30803efd325147e1e1d6ead5fdf57`.
Final handoff adds documentation only; its exact pushed READY SHA is recorded by
`AI_WORKSPACE.md`/the workspace handoff receipt and supplied in the relay message.
Starting assignment/base: `9093a09fa` (full base obtained with `git merge-base`).
No upstream code was merged, no manager contacted, no main published or deployed.
Core owns integration and final integrated verification.

## Owner acceptance

Justin explicitly closed the task:

> looks good to me, i can only test a very basic scenario without dev commands but i'm happy with how it's visually flowing and the examples you showed looked good, it looked nice when rotating the camera and with movement so i think we can call this complete

This is basic manual private-client visual acceptance of flow, movement and camera
rotation, plus the supplied examples. It is not exhaustive gameplay acceptance of
all sources, equipment combinations, crowded battles or renderer profiles.
Automated coverage below is separate. This statement satisfies the assignment's
explicit overall-completion gate; this is the single consolidated Core handoff.

## Result and implementation

Previously, characters displayed fixed 24×14 colored boxes around their bodies,
with one slot per type and a yellow secondary catchall. The new shared player/NPC
pass draws small native-font numbers above the head and lifebars, following the
owner's projected position each frame. It renders after health bars in the world
overlay phase. The old fixed-box renderer/fallback is removed.

`FloatingHitSplatState` keeps successive/simultaneous values instead of replacing
same-type hits. Same-batch priority: main, heal, poison, summon, recoil, others.
Normal rise 16px/sec; older obstructing values move up at up to 110px/sec to make
room; 14px row gap, 64px rise cap, 2100ms lifetime, final 450ms fade. Limits are
6 active and 12 pending per owner, with pending expiry at 1200ms. Wall-clock
monotonic time prevents FPS-dependent lifetime. Extreme bursts can expire/drop
values intentionally instead of accumulating unbounded text.

`FloatingHitSplatRenderer` tries nearby horizontal positions within ±24px before
accepting overlap, retains owner attribution, caps each frame at 4096 values and
keeps a 256-sprite cache. It clips viewport edges instead of pushing values down
into bars. The layout is bounded, not a guarantee of collision-free dense combat.

Both styles remain: colored/shadowed text and white digits on tightly fitted
colored backgrounds. Background mode is default. Foreground glyph raster bounds,
not font advance/line metrics, determine the background: symmetric 2px padding
for every digit length. The accepted text-only raster construction is preserved.
Scratch glyph rasters are explicitly software-owned to remain valid under native
UI replacement; final drawing uses existing alpha-sprite/native overlay paths.

Options → General → Interface → **Hit number style - Background / Text only**,
below Spellbook layout, switches active values immediately. Existing local
`clientSettings.conf` persistence stores `hit_splat_style=background|text`;
missing/invalid settings choose Background. It uses action 66 and adds no server
settings packet. Strict authentic Options layout is left as established; the
setting is exposed in the existing custom Options Interface panel.

## Final source palette and protocol compatibility

| Presentation ID | Final meaning | Color |
| --- | --- | --- |
| 0 | Main/weapon/spell attacks and offshoots; unclassified/default | Red |
| 1 | Independent poison | Green |
| 2 | Armor/jewelry procs | Purple |
| 3 | Healing | Blue |
| 4 | Summon main and bonus-trait damage | Yellow |
| 5 | Jewelry recoil (separate queue priority) | Purple |
| 6 | Explicit independent burn pulses | Orange |

Text colors: red `FF6860`, green `65DF80`, purple `CD96FF`, blue `69B8FF`,
yellow `FFE263`, orange `FFB575`. Backgrounds respectively `A62B28`, `256938`,
`663897`, `245B9B`, `876611`, `965126`; foreground is white.

The [source audit](dot-hitsplat-secondary-source-audit.md) follows all 32 old
catchall selectors across 10 production files plus their source-specific callers,
setters, forwarding helpers, numeric assignments and plugins. Its implemented
source-grouping section supersedes the historical multi-element proposal.

Weapon callbacks explicitly choose red: Dragon melee/ranged followups, elemental
swords and Pitchfork Hell Blaze. Frostbite reflection and Splinter are spell
source red; Naga/Terror Dog extra strikes are main attacks. Bear/Ferocious extra
hits are purple because their actual gate is armor, despite occurring during an
attack. Infernal/dragon armor effects, Chaos necklace chains, blood/death robe
splashes, death jewelry, Sullen pendant and Dark Beast discharge remain purple.
Summon bonus traits now use yellow too. Initial Elder armor damage is purple;
its independent burn ticks and Elder boss burn ticks are orange. Initial Elder
boss Fireshot and Balrog splash are red. Cleric Thorns and Divine Retribution use
Justin's red fallback (neither is armor). No remaining existing burn site in the
audit required an unresolved precedence decision.

Only presentation metadata changes in server damage requests. Damage amounts,
RNG, chances, effect policies, settlement, attack timing and gameplay semantics
are unchanged. IDs 4/5/6 extend the existing custom typed update; packet structure
is unchanged. Classic damage updates and authentic clients remain on their
existing red damage fallback. Older custom clients need the matching client for
new IDs/colors; do not claim old clients show the new taxonomy. New client pairs
legacy+typed updates into one value, handles classic-only damage, and normalizes
unknown presentation IDs to red without double-reporting. No duplicate leftover
fixed boxes remain.

## Verification and reproduction

Relevant builds/tests passed on the final implementation; later changes are
review/runtime documentation only. Heavy checks were serial with bounded heaps.

```bash
ANT_OPTS='-Xmx768m' ./scripts/build-client.sh
ANT_OPTS='-Xmx768m' ./scripts/build-server.sh
ANT_OPTS='-Xmx768m' ./server/test_combat
python3 tests/myworld/test-floating-hit-splats.py
python3 tests/myworld/test-renderer-v2-phased-overlay.py
python3 tests/myworld/test-spellbook-text-layouts.py
git diff --check
```

- Client build PASS; server/core/plugin build and classpath/content audits PASS.
- Authoritative combat gate **145/145 PASS**. New executed callback scenario
  verifies actual weapon/armor gates across melee adapters, ranged Dragon and
  Elder armor initial-vs-burn color. Existing summon, recoil/reflection, child
  damage, Naga and Terror Dog scenarios assert updated metadata with unchanged
  damage/HP semantics.
- Final acceptance-stage repeat: software **322**, phased-native **327**, native
  UI replacement **327** behavior/render checks PASS. Includes priority, bounded
  queues, repeated hits, fade/expiry, legacy pairing, unknown types, owner motion,
  crowd spacing, bars and native capture.
- Options/immediate repeated switching/persistence **51**, fresh-JVM reload **3**
  PASS. Glyph/background centering and text-only preservation **9,216** pixel
  checks PASS across byte amounts 0–255 and all seven IDs.
- Phased-overlay guard, neighboring Spellbook layout, whitespace checks PASS.

Build/combat/final rendering logs and combat summary are retained locally in
`/home/justin/Documents/Codex/2026-10-31/task/hitsplat-final-evidence/`.
The normal ignored receipt is `output/tests/combat/summary.properties`.

Known pre-existing supplemental failures: `python3 tests/myworld/test-summon-lifesteal.py`
fails its combat-XP-distribution source guard; `python3 tests/myworld/test-combat-runtime-invariants.py`
fails its old NpcBehavior `dualElementProcs` source guard. Identical failures were
reproduced against untouched starting-HEAD tests/source during the initial pass;
these were not suppressed. Do not represent the whole repository test suite as
passing. Core's concurrent Cleric work may change overlapping combat/UI areas;
review final integration and run relevant integrated tests rather than treating
this isolated branch result as merge validation.

## Reusable visual evidence and provenance

```bash
python3 scripts/preview-floating-hit-splats.py /tmp/hitsplat-preview
```

Requires Pillow and the existing `Client_Base/Cache/video/library.orsc` native
font cache. Exports 151 frames at 40ms, paired comparison, GIF, timed contact
sheet and centering detail using actual production state/layout/font/alpha code
with explicitly schematic actors. These are renderer fixtures, not gameplay
screenshots. Source fixtures and exporter are editable and tracked; generated
assets/binaries remain outside Git. No new raster art is installed in the client.
The screenshot reference `libfile_d8c14cb515fc819188a722593ca3072e` was inspected;
no reference pixels or third-party game assets were incorporated.

Actual local artifacts:
`/home/justin/Documents/Codex/2026-10-31/task/hitsplat-preview/`.
Static comparison and timed native-rendered frames were inspected. The original
local GIF browser playback attempt was denied because file URLs are prohibited;
it was not bypassed/retried, so playback itself remains unverified. Justin's
subsequent real private-client movement/camera review is separate live evidence.

| Artifact | Library ID | Version |
| --- | --- | --- |
| comparison.png | libfile_e80b5fa1d8608191bfd44886f8b3bd6b | 2 |
| floating-styles.gif | libfile_c01be5a836ec8191a55b8f1a896ed1d2 | 2 |
| motion-sequence.png | libfile_7af262c8d2888191b1b3079afe02d58d | 2 |
| centering-detail.png | libfile_d79a46dc5b208191981e89867f409477 | 0 |

All saves succeeded and identities were preserved; `library-identities.json`
retains exact file IDs/local paths/version metadata. Before/after evidence also
exists directly in the diff from assignment base: old fixed boxes and per-type
slots replaced by shared above-head queues. No original in-game before screenshot
is claimed. The working notes preserve the visual revision history.

## Private runtime, retained data and limits

User-authorized private review directory:
`/tmp/spoiled-milk-hitsplat-review-qn22pbsi`, endpoint `127.0.0.1:43825`, WebSocket
disabled. It ran exact code `26001e8a51a30803efd325147e1e1d6ead5fdf57`, with four
compiled jar hashes recorded in `review-manifest.json`. Java 8, separate named
server/client terminals. Client fetched server configs and activated OpenGL
presenter/input/window/world rendering before Justin's manual acceptance.

**Final read-only check: recorded server PID 3931622 and client PID 3931747 are
both absent, and 43825 has no listener.** No shutdown signal was needed or sent
for finalization. Do not reuse these stale PIDs. Public listener PID 2863465 on
43605 remained unchanged. No public action or owner account/map access occurred.

Private test DB/settings/config/map are preserved; no cleanup was done. Backups
of private SQLite DB, settings, old jars/logs/launchers/manifest remain under
`backups/pre-26001e8a5`. Stock shipped classic maps with eight fresh synthetic
validation NPC placements were used, not the owner's installed/authored map.
This limits coverage of custom maps/dense encounters. The original failed Java21
and incomplete fresh-NPC runtime attempts are preserved in earlier runtime logs;
the final Java8 runtime startup passed with no startup errors.

If future review is requested, verify no process/listener occupies this private
runtime/endpoint, then reuse its own `start-server.sh` in a dedicated terminal;
wait for online before `start-client.sh`. Do not launch duplicate instances or
copy owner data. Access is the private desktop client, existing private character,
and Options setting above. To stop a running review, close only its client then
Ctrl-C only in its named private server terminal, or use its `stop-private-review.py`
which checks cwd/command before signaling. Always verify current identity first.
Local access/manifest copies: task/hitsplat-private-runtime.txt and
 task/hitsplat-private-review-manifest.json (runtime-local manifest is authoritative
for the deployed code; final stopped-state observation here supersedes old PIDs).

Remaining practical limits: extreme crowding may still overlap, horizontal
placement can change as owners move, offscreen edges clip, large-crowd performance
is unbenchmarked, and user review was basic rather than every combat source/profile.
No implementation blocker remains. Integration, publication and deployment await
Core's ordinary review and remain outside this worker's actions.

## Complete changed-file inventory

Relative to assignment base, including final review/acceptance documentation:

```text
A	Client_Base/src/orsc/FloatingHitSplatRenderer.java
A	Client_Base/src/orsc/FloatingHitSplatState.java
A	Client_Base/src/orsc/HitSplatDisplaySettings.java
M	Client_Base/src/orsc/ORSCharacter.java
M	Client_Base/src/orsc/PacketHandler.java
M	Client_Base/src/orsc/mudclient.java
A	docs/myworld/in-progress-work-plans/dot-hitsplat-secondary-source-audit.md
A	docs/myworld/in-progress-work-plans/dot-hitsplat-visual-rework-working-notes.md
A	scripts/preview-floating-hit-splats.py
M	server/src/com/openrsc/server/content/DivineRetribution.java
M	server/src/com/openrsc/server/content/ElderGreenDragonArmorEffect.java
M	server/src/com/openrsc/server/content/Summoning.java
M	server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java
M	server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java
M	server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java
M	server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java
M	server/src/com/openrsc/server/model/entity/update/HitSplat.java
M	server/test/com/openrsc/server/combat/CurrentCombatCharacterizationTest.java
M	server/test/com/openrsc/server/combat/CurrentCombatChildDamageCharacterization.java
A	server/test/com/openrsc/server/combat/CurrentCombatHitSplatSourceCharacterization.java
M	server/test/com/openrsc/server/combat/CurrentCombatOwnedDamageCharacterization.java
M	server/test/com/openrsc/server/combat/CurrentCombatReflectionCharacterization.java
M	server/test/com/openrsc/server/combat/CurrentNagaCharacterization.java
M	server/test/com/openrsc/server/combat/CurrentTerrorDogCharacterization.java
A	tests/myworld/FloatingHitSplatFixture.java
A	tests/myworld/HitSplatCenteringFixture.java
A	tests/myworld/HitSplatSettingsFixture.java
A	tests/myworld/test-floating-hit-splats.py
M	tests/myworld/test-renderer-v2-phased-overlay.py
M	tests/myworld/test-summon-lifesteal.py
A	docs/myworld/in-progress-work-plans/dot-hitsplat-visual-rework-core-review.md
```
