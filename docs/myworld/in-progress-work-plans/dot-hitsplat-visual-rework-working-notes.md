# Hit-splat visual rework: working notes

Status: ACTIVE, first implementation for Justin's review. This is a working
record, not a final Core handoff. Justin has authorized implementation, not
accepted a final appearance or declared the overall task complete.

## Agreed direction

- Replace body-centered fixed boxes with small numbers above each entity's head
  and lifebar. Players and NPCs share the behavior; values follow their owners.
- Compare colored text with white/shadowed text on tightly fitted colored backgrounds.
- Red primary, blue healing, green poison, yellow summon, purple recoil. Other
  secondary effects need deliberate classification rather than recoloring all
  existing alternate damage as recoil.
- Slowly rise; incoming same-owner events push older values up faster. Finite
  travel, lifetime and queues prevent long strings. Preserve successive hits.
- Batch priority: primary, healing, poison, summon, recoil, other. Try limited
  sideways crowd spacing while preserving ownership.

## First implementation, October 31, 2026

Starting commit: `9093a09fa8e2818cceeb33fd387244dd90497972` on
`feat/hitsplat-visual-rework`. Core's later main commits have not been merged.

`FloatingHitSplatState` holds each entity's bounded presentation queue. Times use
monotonic milliseconds, independent of the legacy/modern client update rates.
Initial tuning: 16px/sec natural rise, 110px/sec pushed rise, 14px row gap,
64px maximum rise, 2.1s active lifetime, final 450ms alpha fade (also fades near
the height limit), at most 6 active/12 pending events. Pending events expire
after 1.2s. At queue capacity the oldest pending event is dropped; this deliberate
overload limit can omit individual numbers during extreme bursts rather than
creating a long-delayed backlog. Normal successive same-type values are retained.

Events received before the next update/render form one priority batch. New values
wait until the bottom lane clears while older obstructing values accelerate.
They never move downward relative to their owner. Owner movement itself can move
their entire group in either direction on screen.

`FloatingHitSplatRenderer` collects visible player/NPC anchors, draws after
lifebars, and tries horizontal shifts up to 24px in 2px steps. It uses native
font 0, a small shadow, digit-measured width plus 4px total padding, and the
existing sprite alpha path in WORLD_OVERLAY. A bounded 256-entry sprite cache
keeps font rasterization out of the normal repeated draw path. Scratch font
rasters remain software-owned under native UI replacement. The initial implementation used a provisional text-only default; the later
owner-selected Options implementation below supersedes that startup-only choice.

Type 2 remains other/alternate damage, provisionally orange. Its callers include
offhand hits, armor procs, chained/area effects, elemental specials, Frostbite,
Cleric Thorns, Divine Retribution and other owned secondary effects. No attempt
has been made to split all those families into final colors. Summons now use
additive presentation ID 4; chaos jewelry recoil uses ID 5 in reciprocal melee,
PvM melee and projectile paths. Only presentation selections change; combat
formulas, damage settlement, contribution, XP, timing and effect policies stay
unchanged. The executable combat expectations assert the new presentation IDs
while retaining their outcome checks.

No packet shape or classic-client update changes: custom update 11 still contains
type, amount, current HP and maximum HP as bytes. Authentic/classic clients keep
their original damage updates. Older custom clients do not know IDs 4/5 and will
need the updated client for the new colors (their legacy red fallback remains).
The new client coalesces a paired classic damage update plus typed event into one
number; a classic-only event also uses the floating presentation. The old fixed
box renderer has been removed.

## Reproduce previews and checks

From this worker only, serially, with bounded build heaps:

```bash
ANT_OPTS='-Xmx768m' ./scripts/build-client.sh
ANT_OPTS='-Xmx768m' ./scripts/build-server.sh
python3 tests/myworld/test-floating-hit-splats.py
python3 scripts/preview-floating-hit-splats.py /tmp/hitsplat-preview
ANT_OPTS='-Xmx768m' ./server/test_combat
python3 tests/myworld/test-renderer-v2-phased-overlay.py
git diff --check
```

The preview exporter requires Pillow and the worker's existing native font cache
`Client_Base/Cache/video/library.orsc`. It opens no server or login. It exports
151 frames at 40ms intervals, `comparison.png`, `floating-styles.gif` and a timed
`motion-sequence.png`. The exact production overlay/state/font/alpha rendering
path runs against explicitly schematic actors; these are not in-game screenshots.
Both styles appear side by side with identical event feeds and owner movement.

Current review files:
`/home/justin/Documents/Codex/2026-10-31/task/hitsplat-preview/`.
Reference: Library `libfile_d8c14cb515fc819188a722593ca3072e`, materialized and
visually inspected at
`/home/justin/Documents/Codex/2026-10-31/task/reference.png`.
It is a visual reference only; no reference-image pixels or third-party game
assets were incorporated. No new raster art assets are needed by the client.

Validation:

- Client build PASS; server/core/plugin builds and classpath audit PASS.
- Executable state/layout/render checks PASS: 318 software checks, 323 phased
  native-capture checks, 323 native-UI replacement checks. Includes semantic
  ordering across wall-clock boundaries, repeated type retention, pushed spacing,
  legacy deduplication/fallback, fade/expiry, bounds, owner movement, crowd spacing,
  duplicate scene calls, lifebar clearance and full/faded native sprite capture.
- Authoritative combat gate PASS: 144/144 scenarios. Receipt is the task-local
  ignored `output/combat-test/summary.properties`.
- Renderer phased-overlay guard PASS; `git diff --check` PASS.
- Supplemental `test-summon-lifesteal.py` fails its old combat-XP-distribution
  source guard; `test-combat-runtime-invariants.py` fails its old NpcBehavior
  `dualElementProcs` source guard. Both identical failures were reproduced using
  tests and file content from the untouched starting HEAD. They were not changed
  or relaxed to hide those existing failures.

## Review limits and next work

- Justin has not reviewed or accepted either first visual style. Tune size,
  brightness/background colors, rise/spacing/fade and duration with him.
- Static native-font output and a timed frame sequence were visually inspected.
  A browser attempt to play the local GIF was denied because file URLs are
  prohibited. No alternate browser route was attempted; playback is unverified.
- Library saving initially stopped before creation because the prepared-upload
  helper reported `Library prepare_uploads is not available`. Recovery through
  the documented direct-create batch succeeded for all three previews; local
  identity/version metadata was applied successfully. All are version 0:
  - `comparison.png`: `libfile_e80b5fa1d8608191bfd44886f8b3bd6b`
    (`file_000000002e5081fd995bb5062701847f`).
  - `floating-styles.gif`: `libfile_c01be5a836ec8191a55b8f1a896ed1d2`
    (`file_000000006a7c8230abd844ca4b112690`).
  - `motion-sequence.png`: `libfile_7af262c8d2888191b1b3079afe02d58d`
    (`file_00000000689081fdaae6033d1a3031f5`).
  Their Library paths are `/comparison.png`, `/floating-styles.gif` and
  `/motion-sequence.png`; original local paths remain in the review directory
  above. Replace these same Library IDs for subsequent revisions. Local
  `library-identities.json` and file metadata retain the exact correspondence.
- Full client scene/login and GPU presentation have not been visually tested.
  Pixel rendering and native command capture are verified offscreen. Actual
  character sprite anchors, camera/zoom changes, nametags/skulls, dense battle
  attribution and all renderer profiles need in-game review before final acceptance.
- Horizontal placement is intentionally limited. Extreme crowding can still
  overlap; movement can change nearby placement. Large-crowd performance has not
  been benchmarked. Viewport edges clip rather than pulling numbers into lifebars.
- No private/public service has been started, no owner map/config/account data
  changed, no installed client/package overwritten, and no manager contact,
  integration, publication, deployment or READY handoff occurred.

Continue direct collaboration and checkpoints. Only Justin's explicit statement
that the overall task is complete and ready for Core permits the consolidated
handoff and READY transition.


## Owner-selected style toggle, October 31, 2026

Justin chose to retain both styles and requested a player-facing Options toggle.
Background is the reversible default, consistent with his preference for its
appearance. Exact appearance tuning and the overall task remain open.

Location: **Options (wrench) → General → Interface**, directly below Spellbook
layout. Label: **Hit number style - Background** / **Hit number style - Text only**.
The existing scrolling list handles any needed scrolling. Both labels fit the
normal 195px Options control (tested with its actual font).

`HitSplatDisplaySettings` follows the existing client-local Properties pattern:
`hit_splat_style=background` or `hit_splat_style=text` in `./clientSettings.conf`.
Missing, empty or invalid values default to background. Client construction loads
it alongside Spellbook layout; a normal left-click cycles it and performs the
existing read-modify-save operation, preserving unrelated keys. Hover/right-click
does not change it. No setting packet, player-database field, protocol addition
or server change is required. The existing General UI is used; classic and
native rendering paths share the preference. The strict authentic Options menu
layout is unchanged, as with the existing local Spellbook-layout option.

The normal renderer follows the preference on each collection; explicit-style
preview renderers stay fixed so the existing paired comparison remains valid.
Switching restyles existing active values immediately on the next rendered frame,
retaining their amount, owner, queue, age and movement. Cache keys include style,
so repeated switching safely reuses the appropriate native-font sprites. The old
provisional `spoiledmilk.hitSplatStyle` startup property is superseded by this
persisted Options setting; no restart is needed to switch styles.

Validation for this increment:

- Client build PASS.
- `python3 tests/myworld/test-floating-hit-splats.py` PASS: existing 318 software,
  323 phased-native and 323 native-UI checks; 51 actual Options/persistence checks;
  3 checks in a separate fresh JVM confirming saved text selection reloads.
- Options fixture uses the production scrolling-list control and actual General
  row builder/click handler, exercises repeated switches under both custom-UI
  states, verifies both labels fit, compares pixels for a live number in both
  styles, and confirms no event loss/duplication or server connection is needed.
  All settings-file writes occur in a disposable temporary working directory.
- `test-renderer-v2-phased-overlay.py` PASS.
- `test-spellbook-text-layouts.py` PASS (neighboring setting regression).
- `git diff --check` PASS.

No comparison regeneration or Library replacement was needed: both explicit
styles are visually unchanged, and their saved identities above remain current.
No new playback claim, service, final acceptance or READY transition is implied.


## Authorized private in-game review, October 31, 2026

Justin requested launching a private server/client to inspect the implementation.
The assignment explicitly permits an isolated private setup on fresh test data
and unused localhost ports, without another Core review. No manager was contacted.

Running snapshot: `feat/hitsplat-visual-rework` code commit
`6d856b5ee6b2aeac5c77b6f0bf8f927bb644afed`.
Runtime: `/tmp/spoiled-milk-hitsplat-review-qn22pbsi`.
Server PID `3903299`; client PID `3903365`. Both have their own dedicated GNOME
terminal, titled `PRIVATE HIT-NUMBER SERVER :43825` and
`PRIVATE HIT-NUMBER CLIENT :43825`. The matching OpenGL desktop client is open.
Endpoint: **127.0.0.1:43825** only; WebSockets disabled (43826 not listening).
No firewall, router, system security or public-network settings changed.

The runtime contains copies of this branch's compiled artifacts and selected
tracked dependencies, not an owner runtime, credentials or account database.
It uses the shipped classic terrain/map archives with installed layered/native
map authority disabled. The owner installed map/export, custom landscape and
saved authored placement overrides were excluded. The empty tracked seed had
zero players. A private account now exists after the client was opened; do not
reuse or manipulate owner/live account data.

Eight fresh fixed NPC fixture records at `x=300+2*index,y=750` satisfy existing
Monster Slayer startup references: IDs `846,847,848,849,850,862,809,844`. They are
synthetic test placements, not copied from owner placements. Base stock locations
remain available for ordinary combat; this is not a test of the owner map/tower.
No forbidden export or import was retried.

Initial launch failures were confined to this new runtime and preserved under
`logs/`: the first used incompatible fat-JAR dependency precedence under Java 21;
subsequent fixture attempts lacked required custom-NPC catalog references. Each
owned private process was stopped before correction. Final launch uses the
repository's Java 8 runtime and documented server classpath precedence, and the
full world/Monster Slayer/plugins load without errors. Project code was not
changed for these runtime fixes.

Verified: both process working directories match the isolated tree; the listener
is loopback-only; private TCP accepts a connection; client logs `Got server
configs!` from the private endpoint, initializes its OpenGL primary window and
enters world-region rendering. The public server PID/43605 listener remain
unchanged. Logs and file hashes are in runtime `logs/` and
`review-manifest.json`; a manifest copy is also at
`/home/justin/Documents/Codex/2026-10-31/task/hitsplat-private-review-manifest.json`.
This proves startup/connection, not Justin's visual acceptance.

For inspection, use the already-open private desktop client. Register a fresh
character if needed, then use Options → General → Interface → Hit number style.
The first live scene/rendering/overlap review is now for Justin to perform.
No acceptance or overall completion signal has been received.

To stop safely after review: close the private client, then Ctrl-C only in its
`PRIVATE HIT-NUMBER SERVER :43825` terminal. Alternatively execute
`python3 /tmp/spoiled-milk-hitsplat-review-qn22pbsi/stop-private-review.py`, which
checks each exact PID's working directory and command before SIGTERM. Do not
signal any other server. The isolated start scripts may be reused only after the
current copies have stopped; preserve this directory/private database rather
than treating ignored or temporary paths as disposable. Running processes are
intentionally left up for the user's inspection. Assignment remains ACTIVE.


## Owner behavior acceptance, centering correction and catchall audit

Justin reports the movement is working as desired and is happy with its behavior.
He requested centering the number inside its background and auditing the old yellow
catchall for other meaningful damage sources. This accepts the floating behavior;
it does not declare overall completion or approve additional color assignments.

Background centering now uses the actual foreground glyph raster bounds rather
than font advance/line metrics. Every digit has 2px visible padding on all four
sides. Previously, `1`, `11` and `111` had 2px left versus 5px right padding;
ordinary digits had 2px left versus 3px right. All had 3px top/bottom spacing.
The accepted text-only sprite construction remains unchanged. Backgrounds become
more tightly fitted, within the existing row gap. No state/timing/priority/owner
tracking/crowd-placement rules or combat producer types change.

Validation: client build PASS; existing 318 software / 323 phased-native / 323
native-UI checks PASS; Options/persistence 51 plus fresh-JVM reload 3 PASS;
**7,936 pixel centering/glyph-preservation checks PASS** across all byte amounts
0–255 and all six colors, including exact preservation of the text-only sprites.
The paired comparison's entire text-only half is also pixel-identical to its
pre-correction export. Renderer phased-overlay and neighboring Spellbook layout
regressions PASS; `git diff --check` PASS. New centering-detail, paired comparison
and timed frames were visually inspected. GIF playback remains unverified; the
previous file-URL denial was not retried or bypassed.

Previews retain their existing Library identities, now at version 1:

- `comparison.png`: `libfile_e80b5fa1d8608191bfd44886f8b3bd6b`,
  file `file_00000000cddc81fda1ee53779a84979b`.
- `floating-styles.gif`: `libfile_c01be5a836ec8191a55b8f1a896ed1d2`,
  file `file_0000000071408230bdead3a879666944`.
- `motion-sequence.png`: `libfile_7af262c8d2888191b1b3079afe02d58d`,
  file `file_00000000fe1082309a6fe71cd73c67dd`.
- New `centering-detail.png`: `libfile_d79a46dc5b208191981e89867f409477`,
  version 0, file `file_0000000067cc82309edccf5cbace04cd`.

All saves and local identity metadata succeeded. Their actual local paths remain
under `/home/justin/Documents/Codex/2026-10-31/task/hitsplat-preview/` and the local
identity manifest has been updated. Regenerate via the existing preview exporter;
its tests now also emit the centering detail.

The full source inventory and color proposal are in
[dot-hitsplat-secondary-source-audit.md](dot-hitsplat-secondary-source-audit.md).
It covers 32 catchall selector references in 10 production files, all type-setting
APIs/variable forwarders, and the actual callers behind shared settlement aliases.
Key discovery: summon on-hit trait bonus damage still bypasses the normal summon
classifier and remains type 2. Other sources include actual extra physical hits,
multiple elemental armor/weapon effects, several reflection/retaliation families,
blood/death splash, charged jewelry and boss specials. No new taxonomy/colors are
implemented until Justin chooses meaningful grouping/precedence; a color per item
or per adapter method would misrepresent shared sources.

The user's private runtime/client remain unchanged and running the earlier
`6d856b5ee6b2aeac5c77b6f0bf8f927bb644afed` snapshot. The centering correction has
been built and tested separately, not hot-installed or restarted into that session.
Check with the parent/user before interrupting that running review, and verify
process identity before any later runtime action. No manager contact, deployment,
main integration, consolidated handoff or READY transition occurred. ACTIVE.


## Source palette implemented; authorized private refresh

Justin chose source grouping and confirmed red is the default until otherwise
classified. Source decisions now supersede the prior multi-element proposal:
weapon/main damage and offshoots red, armor/jewelry purple, summons (including
bonus traits) yellow, poison green, healing blue, explicit burn pulses orange.
See the updated source audit for caller-level distinctions. Initial Elder armor
hits are purple; its independent burn ticks and Elder boss burn ticks are orange.
Unknown custom types also safely display red. Combat rules and accepted movement,
spacing, timing, both styles and persisted toggle remain unchanged.

Validation: client build and server/core/plugin build/audit PASS; **145/145 combat
scenarios PASS**, including new actual weapon/armor callback gates and explicit
burn presentation assertions. Software 322, phased 327, native UI 327 rendering
checks PASS; Options/persistence 51 and fresh-JVM reload 3 PASS; **9,216** pixel
centering/glyph-preservation checks PASS across seven presentation IDs. Phased
renderer guard, neighboring Spellbook layout regression and diff whitespace PASS.
Updated actual-renderer static comparison inspected; center padding and source
colors readable. GIF playback remains unverified following the earlier denial.

Updated Library comparison, GIF and timed sequence retain the same IDs at version
2. Files respectively: file_00000000cdb481fdb2fa619074b382b6,
file_00000000971c81fd97dd4ce2690ccb10, file_00000000f65c81fd9f7fcf3af5e375c3.
Centering-detail remains unchanged at version 0. Local artifacts and identity
metadata are under the existing task/hitsplat-preview directory.

Justin explicitly authorized relaunching our private server/client after these
changes. Refresh only its compiled jars after checkpoint, back up and preserve
its existing test DB/settings/map/config, verify process cwd and command before
signals, then check localhost readiness and real client connection. No public
runtime, owner data or other worker is in scope. This is an inspection increment,
not overall acceptance, READY or consolidated Core handoff.


### Private review refreshed October 31, 18:55 UTC

Code checkpoint: `26001e8a51a30803efd325147e1e1d6ead5fdf57`, pushed and clean.
Both prior recorded processes were absent; a current process scan found no Java
process in either private cwd and port 43825 was free. No shutdown signal was
needed. Private SQLite backup, settings, previous jars/launchers/logs/manifest
are preserved in runtime `backups/pre-26001e8a5`. The same private DB, map and
configuration were retained. Only four compiled jars and source banners changed.
Runtime artifact hashes match the tested worker outputs, recorded in its manifest.

Private server PID **3931622**, client PID **3931747**; each current /proc cwd and
expected command was checked. Server bound only `127.0.0.1:43825`, reported online,
with no startup errors. Client fetched configs from that endpoint, received them,
and activated OpenGL device/presenter/input/primary window and world rendering.
The config connection is short-lived by design; authenticated gameplay and the
new appearance remain for Justin to inspect. Public listener PID 2863465 on 43605
was unchanged. No owner data or public runtime action occurred.

Use the open private desktop client and existing private test character. Style
control: Options → General → Interface → Hit number style. Runtime remains
`/tmp/spoiled-milk-hitsplat-review-qn22pbsi`; own terminal titles and safe-stop
instructions above still apply. Updated local access and hash/process manifest
copies: task/hitsplat-private-runtime.txt and task/hitsplat-private-review-manifest.json.
The assignment remains ACTIVE, with no overall READY/handoff or manager contact.
