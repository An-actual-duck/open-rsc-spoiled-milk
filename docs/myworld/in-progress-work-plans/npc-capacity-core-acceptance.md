# NPC capacity — independent Core acceptance ledger

October 26, 2026. Reviewed Dot exact51378929124162429d2c7d0d6d97b6906219af7a
and corrective READY5893fa14cbae086e16363e043a31210b0a66f7d2.
Source integration is accepted after the offline review below. Toolkit adaptation,
installed-tool packaging and actual private-game acceptance remain separate gates.
No public deployment/restart or owner map import was performed by this review.

## Independent review and correction

Root reviewed protocol/definition carriers, source generator and compatibility
policy, bounded visible budget, source-built production/headless-client fixtures,
stale-target cleanup and output equivalence. An independent read-only registry/
player/lifecycle reviewer found no important regression. Measured gains remain
local measurements; no overall runtime speedup or16K active-combat claim.

Core found the maintained hosted profile still requested oldclient10052.
The correction updates only its client_version to10053 and adds a focused profile
regression alongside the dev profile: default16384, reserve512, insufficient511
refused. Live config/release remains untouched.

The inherited native-placement assertion failed in the starting and Dot commits.
It is corrected to require the registry callback, typed facade delegation and
materialize-before-collision route. All functional checks and later assertions
remain; full suite passed3/3 in the review worker.

Root independently ran scripts/test-npc-capacity.sh after these corrections:
matching builds; profile, registry, production encoder/decoder, actual headless
client, overlap equivalence, packet shape, movement, spatial/protocol, lifecycle,
Slayer definitions, Slow and encounter gates; combat144/144. Exit0.
Evidence: /home/justin/.local/share/npc-capacity-core-review-zhiz2efh/independent-core-gate.log.

## Owner candidate preservation and offline validation

Dot's automated approval denial remains historical evidence; it did not execute
an export. Core performed the task-authorized isolated copy itself, preserving
and independently verifying all13480 original files before and after export.
Original saved work and installed maps were not changed. No approval bypass or
import-validation waiver was introduced into the code.

Read-only original:
/home/justin/RSC Development Toolkit/Projects/modern-session-bc36ad33-dc25-4129-b381-2827c0a793e4/saves/project-a398aa11-dedf-48cb-a1a3-ad9682cb3967.
Isolated copy/evidence: /home/justin/.local/share/npc-capacity-core-review-zhiz2efh/.
The temporary exporter first refused because its helper had not configured the
retained authoring catalog for agility validation. Initializing that catalog as
the normal editor does allowed the ordinary export; the guard was not bypassed.

Export passed with all4047 NPCs and34358 total placements retained in paired
server/client packages. Manifest SHA256:
9555fc0276f873e2d1ea03eaa9a86262bed952820a7c45047be3c2e0e86e8b5d.
The independent helper invoked maintained Core native material, terrain, effective
877-NPC/3400-item definition, placement and capacity validators over the complete
candidate. PASS:16384 capacity,512 reserve,11825 slots beyond that reserve.
No listener/server/client loop or account access was used for this validation.

This resolves the isolated-copy/export/offline-candidate gate only. Actual game
startup/population, user gameplay, paired Toolkit import preview/rollback and
installed editor refresh are not claimed by this ledger yet.
