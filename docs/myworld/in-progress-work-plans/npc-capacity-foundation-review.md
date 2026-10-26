# NPC capacity foundation review

Starting HEAD: `96cdabf534a123b31db9c763d7a079d3ae812361`.
Worker: `/home/justin/Core-Framework-ai-2`, `feat/npc-capacity-foundation`.
The implementation and offline evidence are ready for a **Core-only review
handoff**. The owner tower-copy/export check remains unapproved and unexecuted;
this handoff does not certify that separate map/import/private-game gate.
No server/client was launched, map imported, owner data modified, main published,
or deployment performed. Java fixtures call production methods without listeners
or a gameplay/client loop.

## Before / after contract

Paths below are relative to the maintained server/client source roots.

| Identity / path | Before | Implemented contract |
| --- | --- | --- |
| Global registry, `World.java`, `ServerConfiguration.java` | Fixed 4000 slots | Checked `npc_capacity`; profile 10053 defaults to 16384 slots, indexes 0..16383; players remain 2000 |
| Canonical capability source | Unrelated literals | `tools/protocol/NpcCapacityContract.java`; generated server/client classes and neutral `runtime-contract/npc-capacity-v1.json`; both source-build scripts reject drift |
| Startup / dynamic budget | Catalog only checked against free slots | Native whole-catalog preflight includes 512 transient slots; common expanded-world load backstop also requires 512 free slots; no placement is silently discarded |
| Custom spawn identity, `GameStateUpdater.updateNpcs`, `PacketHandler.showNPCs` | 12-bit global index / 10-bit definition | Version 10053 uses independent unsigned 16-bit index and definition carriers; runtime indexes remain bounded to 16383; definitions may reach 65535 |
| Spawn shape | Custom local count 16 bits, offsets 8+8, sprite 4 | Same count/offset/sprite shape; new addition is 52 bits; offsets signed -128..127; zero byte padding, no index removal sentinel; truncated/unsupported additions rejected |
| Existing-NPC movement/removal | Ordered local-cache bit flags | Unchanged: flag/direction/stance/removal records refer to ordered local cache, independently of global index |
| Local visible count / selection | Custom upper limit 65535; classic 255 | NPC-only custom limit 1024, retaining combat/distance/existing/index priorities; classic 255 unchanged; player limits unchanged |
| Movement / baseline / snapshots | 16-bit indexes and counts | Retained unsigned-short client reads, seven-byte per-mob movement records and existing context/sequence authority; 1024 NPC + 2000 player view leaves frame headroom |
| Appearance / hits / chat / projectile | Short sender/target, byte update kinds / payloads | Same carriers; client `RSBuffer.getShort` is unsigned, server `Packet.readShort` is signed; high senders/targets round-trip; unknown chat still consumes text; unknown update types rejected |
| Definition delivery / storage | Definition packet ID unsigned 16, direct list.set | Bounded runtime definition setter grows client list through ID 65535; high IDs cannot replace low definitions |
| Inbound attack/range/command/talk/item/magic | Custom signed-short target readers | Custom mob targets and NPC magic target explicitly unsigned; classic readers unchanged and safe within their smaller proven registry budgets |
| Old custom policy, `LoginRequest.validateLogin` | Ordinary version enforcement could be disabled / admin-bypassed | Expanded world profile always requires expanded clients before account lookup; old wire layouts remain available only in compatible older profiles |
| Classic / retro | Modulo in safeNPCIndex; family-specific layouts | No modulo. Profile budgets: 38/39/40/69=1000, 115=1500, 140=2048, 177..235=4096. Existing compatible spawn bytes pinned for all these representative families |
| Client global storage, `mudclient` | 5000 initial slots with growth | Preserved global growth and all six movement companion arrays; actual headless decode proves indexes through 16383; initial allocations are not hard limits |
| Local / queued / reconnect caches | Separate visible/cache arrays and movement targets | Preserved companion growth; removal invalidates queued targets and NPC-origin projectile references in players, reconstructed NPCs and old NPC cache; existing scene/reconnect resets retained |
| Picking / model sprite tags | Visible ordinals encoded in namespaces | Global NPC index never becomes the pick ordinal. NPC sprite 20000+ordinal and pick tag 30000+ordinal remain below the next namespace with the 1024 view bound; menu actions use actor.serverIndex |
| Registry ownership, `EntityList`, `PlayerList` | Array + occupied map + free priority queue; empty removal returned a slot twice | Identity registration map agrees with array/map/pool; duplicate/null add nonmutating; bounded lookup; empty/repeated/stale removal cannot erase replacements or duplicate free slots; clear resets handles |
| Player callers | IP filtering re-registered players and changed indexes; logout removed again by username | IP filtering returns a plain List; one exact-object logout removal; hash cleanup checks identity; World registration retains username duplicate protection |
| Processing order / mutation | Ascending immutable snapshot; live full-array scan | Ascending exact-sized immutable snapshot and upper-bound live scan; higher unvisited callback additions visible, lower indexes next pass, captured-array clear behavior retained; caller owns thread confinement |
| Spatial / lifetime ownership | Authored identity, typed WorldLocation/domain, registry index and combat lifetime separate | Retained. Failed registration removes spatial membership/restoration event and clears index; World despawn advances combat lifetime; delayed projectile cannot hit a reused high slot |
| Client overlap | Quadratic visual-tile comparison, rank counts strictly smaller indexes | Same ranks/ties and offset functions; grouped tile/sorted index algorithm above 32 visible actors; original loop below crossover |

## Validation

`./scripts/test-npc-capacity.sh` passed sequential matching source builds,
production artifact audit, eight registry scenario groups, production capacity and
protocol fixtures, 22 overlap equivalence scenes, packet-shape/movement guards,
signed spatial/protocol/scene lifecycle fixtures, Slayer client definitions,
wire encoder/decoder tests, Slow, five dedicated Slayer encounters and the full
current combat gate: **144/144 scenarios**. Primary damage/RNG/deadline and poison/
Slow/lifetime fixtures remain intact. No content definitions, art, prices or map
packages were changed.

The actual configured production registry accepts 16384 real Npc instances.
Actual GameStateUpdater additions pass through maintained client decoding at
0/2047/4095/4096/8191/16383 and definitions
1023/1024/2047/4096/32768/65535. Coverage includes high hits/projectile identities,
command/talk/item/spell targeting, stale removal, failed registration cleanup,
delayed projectile lifetime reuse, client companion-array growth, stale chat
followed by a hit, malformed updates, local removal/re-add and queued targets.
Golden spawn bodies cover 38/39/40/69/115/140/177/235/10052; profile boundaries
and incompatible login refusal are checked independently of ordinary version
setting. Successful authenticated entry remains a Core private-game check.

Failure-first evidence includes five original registry failures, stale chat
record desynchronization, and acceptance of unknown NPC update types. No test
coverage was removed. The separately run native-placement suite has an inherited
source assertion failure: its expected `nativeLayeredGameObjects.applyCollision`
text is absent at both starting and current commits. Two other tests in that
suite pass; later assertions in the failing method were not reached. See
[evidence](npc-capacity-foundation-evidence/known-baseline-failure.txt).

## Measurements and practical limits

Raw outputs, methodology and commands are in
[the evidence directory](npc-capacity-foundation-evidence/README.md).
Ryzen 9 7900X, Temurin 8u482, sequential bounded JVM jobs. Timings are host-specific.

- At 16K packed occupancy, immutable snapshot median 63.831 -> 29.754 us and
  allocation 284608 -> 65568 bytes/pass. Live traversal 23.564 -> 12.026 us.
- Sparse-low 64/16384 occupancy improves substantially. Scattered holes still
  scan in order: this run regressed 3.126 -> 3.795 us; no scattered-hole gain is
  claimed. A different dense processing order is deliberately deferred.
- At 1024 visible NPCs, overlap ranking median falls from 2.297 ms to 25.422 us
  sparse and 4.368 ms to 33.714 us dense. Grouping adds 147680 / 30328 transient
  bytes respectively; the <=32 path allocates none. Offset functions are unchanged.
- Two complete final runtime repetitions exercise 4K/8K/16K populations, idle
  distant NPCs, 32 nearby active player/NPC combat pairs, real events/statuses,
  behavior, movement, visibility/payload generation and cleanup. At 16K active:
  22.65–22.96 ms median, 27.04–27.37 ms p95, ~28.9 ms p99 per combined phase pass.
  Baseline totals overlap this range; **no whole-runtime speedup is claimed**.

These passes contain one movement iteration, not every high-frequency movement
poll in a wall-clock server tick. Raw files separate each phase. They exclude
socket transport and do not prove 16384 simultaneously active combatants. Heap
readings are post-workload snapshots including loaded map/definition data and
uncollected garbage, not retained-per-NPC or peak memory. The seeded workload pins
setup/draw counts and live/engaged pairs; whole-scheduler hit hashes can differ
because existing event ordering is not made deterministic here. Dedicated combat
characterizations remain the formula/RNG oracle. Active-area scheduling, sleeping
simulation, multithreaded AI and pathfinding changes remain out of scope.

## Toolkit / owner gate and Core acceptance

Toolkit must consume `runtime-contract/npc-capacity-v1.json` and resolve the actual
`npc_capacity`/profile policy. Its old scalar parser for World EntityList<>(4000)
and its independent 4000/1023 Limits are incompatible with this source change.
No Toolkit files were changed and editor import readiness is not claimed.
The default budget leaves 12337 slots after the assignment's reported 4047 tower
placements, including the required 512 allowance plus 11825 additional slots.
That arithmetic does not substitute for exporting and validating the actual map.

Automatic approval review **denied**, before execution, copying the named owner
snapshot to /tmp and exporting the independent copy. It classified the operation
as unauthorized private owner-map access conflicting with the no-import/modify
restriction. The parent relayed the approval question; explicit permission is
pending. The denied operation has not been retried and no copy/export was made.
The required fresh placement/definition/terrain validation and newer-owner-work
check therefore remain blocked. Core-only READY does not certify that pending gate.

Core's remaining gates:

1. Resolve explicit temporary-copy/export permission; export every placement and
   validate the independent candidate with maintained Core definitions/terrain,
   capacity and transient allowance. Preserve original hashes and owner work.
2. Review the inherited stale native-placement assertion without weakening its
   behavioral coverage. Run the separate paired Toolkit adapter/import preview.
3. In an isolated private game, verify high-index picking and attack/range/magic/
   talk/item actions, hit/projectile/chat, deaths/respawns, summons/quest spawns,
   index reuse, reconnect and signed-level transitions, plus representative dense
   tower and mixed combat loads. Confirm actual movement/network cadence.
4. Integrate only an exact reviewed READY commit, then separately assemble content
   and release. No public shutdown, activation or deployment is authorized here.

Player IP query return type is now List rather than EntityList; source-built
maintained callers/plugins pass, but external binary callers would need rebuilding.

## Reconnection verification and handoff scope

After reconnection, prescribed worker preflight confirmed the same assigned branch
and the expected local edits. No unexpected work was overwritten. The final
sequential gate had completed after the crowded-view adjustment: BUILD SUCCESSFUL,
144/144 combat scenarios, production and actual headless-client fixtures passed.
No test process remained running. The actual login refusal gate and high command/
talk/item/spell parsers were included. Crowded selection retains the combat target
with custom movement both enabled and disabled. Artifact hashes and filtered gate
evidence were refreshed from those completed builds; no uncertain push was repeated.

The final handoff is limited to Core code and offline evidence. Toolkit adaptation,
owner candidate export/validation and private gameplay remain explicit Core gates.
The denied export source is:
`/home/justin/RSC Development Toolkit/Projects/modern-session-bc36ad33-dc25-4129-b381-2827c0a793e4/saves/project-a398aa11-dedf-48cb-a1a3-ad9682cb3967`.
Reconnection is not export approval. No retry occurred.

Checkpoint history before the final completion checkpoint:

- Inventory/design: `4c7858b3ea2ec6587b3843fcf2c6e2d78298a46b`.
- Shared registry safety: `745979539ce555345c59045d3460b0b37a2b4359`.
- Paired carriers and measured traversal: `1a0bd9493041e450dac22f8e6b0a71cce90e9918`.

EntityList retains immutable snapshot iterators and its specialized entity/index
removal APIs. No broader bulk-collection mutation API or concurrent AI scheduling
contract was introduced.
