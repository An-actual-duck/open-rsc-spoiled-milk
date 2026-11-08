# Movement And Pathing Release Plan

November 8, 2026 (UTC) planning update: use
[Movement and action responsiveness](movement-action-responsiveness-plan.md) for
the next click-to-first-step investigation and action lifecycle sequence. This
earlier diagnosis/proposal ledger is preserved; its measurements and suggested
steps are not proof of current behavior or automatic new implementation scope.

This document tracks the pre-release movement work separately from renderer
work. Movement changes are gameplay-visible and should stay compatible with
older clients unless a protocol break is explicitly chosen for a remaster
branch.

## Current Diagnosis

- Server movement is still tile-authoritative. `WalkingQueue` advances one
  queued point per movement tick, and the custom movement loop broadcasts only
  moved player/NPC tile positions.
- The client smooths visible movement only while an entity has queued
  waypoints. When the client reaches the next tile before another authoritative
  movement update arrives, the entity visually pauses at tile center.
- MyWorld currently enables `want_custom_walking_speed` with `walking_tick:
  430`. The server sends `C_MOVE_PER_FRAME = 6`, which matches classic 50 FPS
  timing but reaches the tile early at a 60 FPS render cadence.
- The client has local breadth-first click pathfinding in
  `World.findPath(...)`, but server `Path.addStep(...)` is still a greedy direct
  stepper. This can truncate paths instead of solving around objects.
- Existing server A* is limited to entity chasing/following paths when
  `want_improved_pathfinding` is enabled. That flag is currently off and should
  not be flipped for release without focused combat/pathing validation.
- NPC roaming felt batched because roam decisions were checked from a shared
  five-tick cadence. Adjacent-tile displacement also used a fixed positive-first
  direction priority.

## Release Baseline

- Keep the global game tick vanilla unless a combat/skilling pass explicitly
  changes it.
- Keep the custom movement stream compatible with older clients. Older clients
  must continue receiving normal movement packets when they do not opt into the
  custom stream.
- Desynchronize passive NPC movement with per-NPC roam jitter.
- Randomize adjacent-tile fallback choice so NPCs do not all step east/south
  from the same interaction case.
- Mirror the custom server movement speed into `C_NPC_MOVE_PER_FRAME` so NPCs
  do not animate at a different client-side speed than players.
- Convert configured classic-frame movement speed by actual frame elapsed time.
  This keeps 60 FPS OpenGL movement from arriving early and pausing at tile
  centers.
- Repath MyWorld melee attack walk-to actions when the target mob moves before
  combat starts. The old behavior planned one adjacent tile at click time and
  could leave the player standing where the NPC used to be.
- Use the existing bounded A* helper for MyWorld melee approach paths to the
  chosen adjacent tile. This is a combat-approach improvement, not the final
  global walking/pathfinding rewrite.

## Next Implementation Steps

1. Field-test client-side frame-time interpolation for custom movement packets.
   The target is one tile arrival per server `walking_tick`, not one tile
   arrival per fixed frame count.
2. Field-test MyWorld combat approach repathing against wandering NPCs, fences,
   counters, building corners, and diagonal walls.
3. Send the authoritative walking interval on the client instead of
   relying only on `C_MOVE_PER_FRAME`.
4. Add server-side pathfinding for normal point walking behind a new MyWorld
   flag. Prefer a bounded BFS/A* that reuses `PathValidation.checkAdjacent(...)`
   and preserves legacy direct stepping when disabled.
5. Validate pathing separately for player ground clicks, NPC chasing, NPC
   returning to spawn, combat catching, doors/gates, scenery blockers, diagonal
   walls, and region boundaries.
6. Decide whether the old `want_improved_pathfinding` flag should be retired,
   renamed, or split into separate `player_point_pathfinding` and
   `npc_chase_pathfinding` controls.

## Validation Notes

- Visual field test: walk long straight paths at 50 FPS and 60 FPS, watching for
  tile-center pauses.
- Visual field test: stand near grouped roaming NPCs and watch whether their
  movement still pulses together.
- Watch for rare NPC server/client desync symptoms: NPCs visually wandering far
  outside their spawn/roam bounds, appearing at a stale client-side tile after
  combat or pathing changes, or snapping back only after a full local NPC cache
  refresh. If seen, capture location, NPC id/name, client type, combat state,
  recent movement/teleport events, and whether older clients reproduce it.
- Pathing field test: click around fences, counters, building corners, doors,
  and diagonal walls.
- Regression tests should guard packet shape before and after any client/server
  movement protocol change.
