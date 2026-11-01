# Layered actor scene recovery — 2026-11-01

Worker: `fix/layered-npc-cache-recovery`, based on published `94fcee395`.

## Cause and production change

The private release-review client disconnected after a normal signed-level
teleport. `mudclient.resetLayeredSceneIdentityCaches()` discarded its NPC and
player identities, but `GameStateUpdater` retained the viewer's old local
actor lists. The next NPC coordinate packet therefore declared old cache
entries that no longer existed on the client. The strict client check correctly
threw `IllegalArgumentException: Invalid NPC local cache count`.

`GameStateUpdater` now records a separate immutable actor scope per viewer:
world space, signed level, projection and native package ID/version/manifest.
After a context is queued, a changed actor scope clears local NPCs, local
players and known player appearances. The following existing encoders emit
zero old locals, full additions and required player appearances. A first
context also establishes a complete baseline, including when server actor
lists were populated before that context.

Terrain context generations include chunk centers; actor identity does not.
An ordinary same-scope context replacement or adjacent-center shift retains
the actor baseline. The separate actor scope survives removal of the terrain
context scope. An accepted missing-reference resync marks the actor baseline
for replacement after the recovery context is queued, because already emitted
actor deltas may have been quarantined by the client.

No client parser/count guard, wire layout, client version, map/content ID,
terrain asset, account/database, `PlayerService`, release or live code changed.
No public/practice/private process was operated by this worker.

## Failure-first evidence

Initial test checkpoint `23237691e` reproduced the real signed-level failure
against the frozen private review artifacts. The final isolated fixture also
fails against those artifacts and passes against the fixed source build.
The four review artifacts were inspected before assuming a source defect:

| Review artifact | SHA-256 |
| --- | --- |
| `server/core.jar` | `eb8288b5ef546ff5bd5e6a1a926f7f920204b008a7763b1011e408a5ce678346` |
| `server/plugins.jar` | `16f070810c6cde7721912b29673e7877103e3071d7c3b26c60b4d006281795e3` |
| `server/core-gameplay-overlay.jar` | `c89c4808077b3c31e1afff10fb5483f66e5772ad04213be71e4f54bcbeeef105` |
| `Client_Base/Open_RSC_Client.jar` | `a38d7cd06b2a9655e19af9c8c6ceb1fad088ca615659829608d5b3d18cfcd458` |

The frozen core contains `GameStateUpdater`; neither plugins nor gameplay
overlay contains that class. The frozen client contains the failing real
`PacketHandler` and `mudclient`, rather than test substitutes.
Their class bytes are identical in the newly source-built client and frozen
review client (`54e6597b2adf...` and `d9767e03b618...`, respectively). The fixed
source-built `GameStateUpdater.class` is
`fde00d0406c858d008e9d869a9827f6c9ff7283fc10713288aaa12a4d974cb01`.

## Regression coverage

`tests/myworld/test-layered-actor-scene.py` compiles the fixture against actual
server/client jars. It copies only versioned configuration, definitions and
query XML into a disposable working directory and creates synthetic flat
terrain. It uses the production server encoders, public
`PacketHandler.handlePacket`, actual client actor storage and actual native
context application. There are no sockets, game startup, account DBs or owner
installed map dependencies. The fixture bypasses the AWT constructor and
region drawing; it still builds the real native context terrain product.

The replay covers:

- Populated surface to empty `L-1`, return, and populated `L-37`.
- Nonempty NPC and player caches, new client actor lifetimes, projectile state
  removal, and player appearance resend even with an unchanged appearance ID.
- Explicit zero-old-count NPC/player wire headers on scope changes/recovery;
  preserved old counts and object identities on same-scope refreshes/shifts.
- Strict duplicate/stale context rejection without actor identity mutation.
- Real lost native references, quarantined coordinate deltas, and fresh actor
  baselines after both same-scope and cross-level recovery.
- A prepopulated server actor cache before its first context.

The NPC capacity gate now includes this replay. The final focused validation
passed source-built server/client builds and artifact audit, all 13 Python
gates below, and the server network encoder/decoder/native terrain wire gates:

```text
test-layered-actor-scene.py
test-npc-capacity-profile-configs.py
test-npc-capacity-production.py
test-npc-overlap-layout.py
test-entity-list-safety.py
test-packet-shape-guards.py
test-client-custom-movement-stability.py
test-layered-spatial-runtime-authority.py
test-layered-protocol-client-authority.py
test-scene-entity-lifecycle.py
test-native-terrain-recovery.py
test-native-terrain-readiness.py
test-native-terrain-atomic-activation.py
```

The production NPC fixture includes 16,384 registry entries, high-index and
definition carriers, removal/re-add lifetimes, 1,024-view priority and frame
budget, and classic/retro golden bytes. The actor replay intentionally prints
a caught stale-context rejection stack while finishing successfully.

Use JDK 8, `-Xmx512m -XX:+UseSerialGC`, and the shared
`/home/justin/.local/share/core-heavy-build.lock`. Optional
`ACTOR_SCENE_CORE_JAR` / `ACTOR_SCENE_CLIENT_JAR` select frozen artifacts for
independent failure reproduction.

## Limits and manager follow-up

The worker proof is headless. Manager-owned final combined checks and a fresh
normal teleport through the disposable private client remain the GUI/runtime
acceptance step. The manager integrates the independent legacy player-location
bootstrap fix separately. No public server shutdown or release is authorized
or performed by this handoff.
