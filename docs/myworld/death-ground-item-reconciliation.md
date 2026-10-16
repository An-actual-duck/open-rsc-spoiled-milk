# Ground items across deferred death/area loads

The client may receive respawn ground-item announcements while the death screen
still prevents `loadNextRegion` from loading terrain. Previously the later hard
load discarded those announcements, although the server already considered them
known. Walking out and back caused new announcements and made them visible again.
This is separate from the authored item's server-side respawn timer.

The existing delayed static-scene replay (`08c767047984346a798e437afc71d03c5734d2f5`)
restores objects and walls, not ground-item records. The later hard ground reset
(`b5f209b3f2ca7baa66ddca5731f12594d5a0c511`) did not cover announcements arriving
before completion. These paths are present in published main `fc7111368` and are
not introduced by the private approved-art topic.

## Reconciliation boundary

`beginAreaLoad`, reached by the area packet and native scene scope reset, now
discards the previous ground set immediately and starts a pending epoch. Existing
arrays then contain only that epoch's announcements. All unchanged single-remove
and chunk-unload packet bodies operate on this same set, without parallel masks,
deduplication, or an accumulating replay journal.

Completion consumes the pending set once, rebases from the previous
`midRegionBase` to the completed base, and clips against the loaded local window.
Packed player positions minus the previous base plus relative ground offsets
already put deferred updates in that previous-base coordinate frame; the same
delta used by normal movement therefore handles positive/negative origins and
world offsets. An unannounced hard/plane load still clears stale items. A new
area start supersedes its predecessor; logout/cancel discards pending items.
Native scope resets start the same epoch and clear old identities. Packet order
is still the existing ordered stream, not a new network epoch/ack protocol.

Ground support heights are recomputed **after** the destination static baseline
replay, so an announcement received against old scenery cannot retain its old
platform height. Separate identical drops remain separate, and a single removal
still removes only one instance. Ordinary soft-region shifts are unchanged.

## Verification

`tests/myworld/test-death-ground-item-reconciliation.py` compiles/runs the actual
production Java epoch helper, extracted opcode99 add/remove/chunk methods,
opcode211 ground compaction body, and support-height method against a small
client/buffer fixture. It tests announcements before deferred completion, normal
ordering, same-region replacement, signed origin changes, clipping only after
rebase, duplicate drops and single removal, both chunk paths, unload/return,
superseding transitions, one-shot consumption, cancellation, and destination
support-height replacement. Wiring assertions cover the native scope reset,
death early-return ordering, and post-baseline height refresh. Registered in
`tests/myworld/test-all.sh`.

The new test and these existing focused scripts pass:

- `test-ground-item-area-reset.py`, `test-ground-item-rendering.py`
- `test-scene-entity-lifecycle.py`, `test-rowboat-respawn-lifecycle.py`
- `test-authored-ground-item-lifecycle.py`, `test-ground-item-cleanup.py`
- `test-ground-item-identity.py`, `test-ground-item-stack-amounts.py`
- `test-client-region-load-performance.py`, `test-layered-scene-visibility-rings.py`
- `test-renderer-v2-ground-item-direct-input.py`

The complete main-based desktop client compiles/packages successfully with
JDK8, `JAVA_TOOL_OPTIONS=-Xmx768m ANT_OPTS=-Xmx384m ./scripts/build-client.sh`
(302 sources). No server/game process was launched, stopped, or modified.

Full gameplay/runtime death retest remains manager/owner acceptance; the tests
do not claim a live packet trace. The unrelated `test-ground-item-bulk-take.py`
already fails on published main for the missing unstackable inventory-slot cap;
this patch does not change pickup/bulk-take behavior. The main-based test JAR
must not replace the private approved-art client: that client requires the
narrow source fix applied to its complete art source tree and rebuilt separately.
