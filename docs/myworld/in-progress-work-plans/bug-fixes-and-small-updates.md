# Bug Fixes And Small Updates

Status: ongoing
Owner: An-actual-duck
Branch: use short-lived `fix/...`, `balance/...`, `content/...`, or `docs/...`
branches

## Purpose

This is the ongoing maintenance queue for small bugs, small balance changes,
minor content tweaks, documentation updates, and quick quality-of-life work.

This document is permanent. The branches used to complete items should be
temporary.

## Workflow

1. Add the bug or small update to the appropriate section.
2. Create a focused branch for that item.
3. Fix the item.
4. Test the fix.
5. Open a pull request into `main`.
6. After merge, move the item to the completed section or remove it if it was
   only useful as a temporary reminder.

## Branch Examples

```text
fix/bank-filter-jewelry-materials
fix/prayer-tab-points
balance/dragon-smithing-levels
content/fishing-guild-shop-cleanup
docs/player-command-list
```

## Open Bugs

- [ ] Add new bugs here.

## Small Updates

- [ ] Execute the cross-system "Fixes and Changes" queue through independent
      focused branches. The preserved backlog, dependency audit, execution
      order, and implementation-ready first camera task live in
      [fixes-and-changes-plan.md](fixes-and-changes-plan.md).
- [ ] Add the active-potion HUD, safe direct-travel interactions, and Shilo
      furnace unlock described in the AI-2 brief below.

## AI-2 Task Brief: Potion Timers And Shilo Travel Quality Of Life

Recommended branch: `feat/potion-timers-and-shilo-travel-qol`

This is one quality-of-life pass with three player-facing outcomes. Preserve
the server as the authority for durations, access requirements, payments, and
quest state. Checkpoint the three outcomes separately so each can be reviewed
or reverted without losing the others.

### 1. Active Potion HUD

Show active timed potion effects in a compact on-screen display:

- each active effect is represented by its potion icon and a visible remaining
  timer beside it;
- hovering the icon/timer shows the exact potion name at minimum;
- multiple simultaneous potion effects must remain distinguishable and must
  not overlap existing HUD controls;
- activation, refresh/replacement, expiry, login, logout, and reconnect must
  leave the display synchronized with server-authoritative state; and
- expired effects must disappear without requiring another potion action or a
  relog.

Audit all current timed potion families rather than wiring only one example.
This includes the tiered skill potions, XP brews, regeneration, speed, luck,
notation, combat resistances, and other effects that actually have a duration.
Use the exact consumed potion variant for the icon/name where the runtime keeps
enough information to do so. If the current protocol does not expose the
needed identity and expiry, add one bounded synchronization model instead of
trying to reconstruct authoritative timers on the client.

The first implementation should remain intentionally small: icon, countdown,
and hover name. Do not turn this into a general status-effect framework unless
the shared foundation is needed to avoid duplicating state.

#### Potion-HUD implementation (`feat/potion-timers-and-shilo-travel-qol`)

- Custom clients receive a full snapshot on login and after each successful
  timed-potion drink through bounded outgoing opcode `152`. A snapshot contains
  at most 16 exact consumed item IDs and server-derived remaining seconds.
- The HUD covers the active brawn, deftness, and insight skill families;
  skiller and warrior XP brews; stat-reduction protection; and poison
  protection. The dormant legacy insight, regeneration, speed, luck, notation,
  and three combat-resistance timer APIs also expose status if a future caller
  activates them with an item identity; the current dispatcher routes their
  historical item IDs into the newer families instead.
- XP brews retain their existing offline-paused, player-cache duration model;
  their consumed item identity is now cached beside it so reconnect can restore
  the correct icon. Other current effects remain intentionally session-local,
  matching their gameplay state, and reconnect therefore receives an empty
  status for effects the server no longer has.
- The client derives a local display expiry from each authoritative snapshot,
  removes expired entries by wall-clock time, and clears the snapshot on logout
  or reconnect. Non-custom clients receive no new opcode.

### 2. Direct Traversal Interactions

Use the existing direct `Board` behavior around Port Sarim as the interaction
model: when a player has already met every condition, clicking the travel
object should perform the trip without replaying a confirmation dialogue.

Required first route:

- `Board` on the Brimhaven travel cart leading to Shilo Village should
  immediately charge the existing fare and move an eligible player into Shilo
  Village.
- Shilo Village quest completion, the `500`-coin fare, driver availability,
  and every other existing route condition must still be enforced.
- A failed condition should produce a short useful message and must not remove
  money or move the player.

Audit the reverse Shilo-to-Brimhaven cart and the other ship/cart objects whose
primary action is `Board`. Add the same shortcut only where the object action
can call the route's existing checks and payment path safely. Do not bypass
quest progression, customs/captain permission, membership rules, Entrana
equipment restrictions, fares, or any other access gate. Keep NPC conversation
available for players who intentionally talk to the operator.

Record the audited routes, which ones received a shortcut, and why any were
excluded. Quest-specific vehicles whose dialogue is itself progression should
remain unchanged.

#### Direct-traversal audit (`feat/potion-timers-and-shilo-travel-qol`)

- Added direct `Board` travel in both directions for the Brimhaven/Shilo
  carts. Both retain the nearby-driver requirement and 500-coin fare; entry to
  Shilo additionally retains Shilo Village completion.
- Added direct `Board` travel in both directions for the ordinary
  Ardougne/Brimhaven ships. Both retain the 30-coin fare, and the Ardougne-bound
  customs route rejects Karamja rum before attempting payment.
- Port Sarim/Karamja and Port Sarim/Entrana already had direct object
  shortcuts with their fare, rum, membership, and Entrana equipment checks,
  so they were audited but left unchanged.
- The Lady of the Waves already opens a direct destination picker and consumes
  its ticket only after a destination is selected, so it was left unchanged.
- Dragon Slayer ships, quest rafts, the Fishing Trawler, and other
  quest-specific vehicles were excluded because boarding dialogue or object
  state is quest/minigame progression. Decorative/unimplemented ship objects
  without an authoritative route/payment handler were also excluded rather
  than inventing destinations or access rules.

### 3. Shilo Furnace One-Time Unlock

Replace Yohnus's repeatable 20-coin interruption with a permanent furnace
unlock:

- remove the physical furnace-room door/barrier so the furnace can be reached
  directly;
- retire the repeatable `20 Gold` entry flow, including the optional faster
  payment behavior where it no longer has a purpose;
- gate actual use of the Shilo furnace at `399,840`, not merely entry into its
  room;
- on the first attempted use, Yohnus requests one cut `Red Topaz`
  (`ItemId.RED_TOPAZ`, item `892`);
- consume exactly one unnoted Red Topaz only after the player accepts the
  payment, then store a durable per-player unlock; and
- after unlocking, every supported furnace operation should work without any
  later coin or gem charge.

The gate must cover all ways the furnace is used, including direct interaction
and using relevant bars/materials on it. It must not be possible to bypass the
unlock through a different furnace plugin. A declined offer, missing Topaz,
full/busy dialogue state, interruption, or failed inventory removal must not
set the unlock. Existing players are not automatically grandfathered unless a
real prior permanent-payment state is discovered during the audit.

Remove the barrier from the authoritative world data and keep client/server
landscape or collision data synchronized. Preserve Yohnus as the character who
explains and collects the one-time payment even though he no longer controls a
door.

#### Furnace-unlock implementation (`feat/potion-timers-and-shilo-travel-qol`)

- The shared gate is the furnace object at `399,840`; both Smithing direct/use-
  item flows and Crafting's use-item flow call it before opening or starting
  production.
- The durable key is `myworld_shilo_furnace_unlocked`. No previous transient
  coin-payment state is treated as ownership, so there is no grandfathering.
- Acceptance removes one unnoted cut Red Topaz before storing the key. Missing,
  declined, interrupted, stale, or failed-removal attempts leave it locked.
- Yohnus now explains and can collect the same one-time payment. The 20-coin
  boundary handler, logout-scoped fast-pay state, configuration switch, and QoL
  opt-out entry were retired.
- Boundary object 165 at `400,845` and its matching vertical-wall value 166 were
  removed. Server and client `Custom_Landscape.orsc` archives remain byte-
  identical after the terrain/collision edit.

### Verification And Handoff

Add focused regression coverage for:

- potion activation, refresh, expiry, reconnect synchronization, timer
  rendering, and hover hitboxes;
- successful and rejected cart/ship shortcuts, including quest and fare
  preservation with no double charge;
- every Shilo furnace interaction path before and after unlock, one-time Topaz
  consumption, durable state, and removal of the obsolete 20-coin path; and
- client/server map and collision parity after removing the door.

Run the Java 8 client build, authoritative server build, relevant focused
tests, and the broader MyWorld suite appropriate to the changed protocol and
world-data surface. Handoff should list the traversal audit, exact potion
families covered, persistence/protocol decisions, manual checks still needed,
and any compatibility behavior deliberately retained.

## Recently Completed

- [x] `2026-07-13` Fix the Experience Config `Select` crash when Enchanting,
      Harvest, and Summoning produce 21 protocol skill slots. All three copied
      selectors now size their lists from the runtime selectable skill IDs,
      omit retired Firemaking ID `11`, and translate displayed rows back to
      their original skill IDs before tracking XP. This omission is UI-only:
      ID `11` remains in the protocol/server arrays at hidden level `99`, so
      standard and custom Firemaking actions retain access to every log tier.
      The menu's `Reset` control now clears total and per-skill session gains,
      restarts every XP/hour timer, and invalidates its cached rate regardless
      of the current mode or selected skill.

- [x] `2026-07-12` Preserve scenery and walls across deferred death/respawn
      area loads. The production legacy scene packets can arrive while the
      death screen still blocks the hard region load; the later load formerly
      cleared those already-delivered instances, while the server correctly
      considered its local scene current and did not resend them. Legacy
      records received for the pending area-load generation are now retained,
      shifted to the newly loaded origin, and materialized after terrain is
      ready. Old-area records remain discarded, and the default-off complete
      scene baseline remains optional rather than becoming a production
      dependency. The regression covers both scenery and walls with the
      baseline disabled, alongside the existing baseline replay case.
      Live validation used the worktree client against the hosted server and
      reproduced a real death from a distant area to the `120,648` Lumbridge
      respawn. Destination scenery, walls, collision, and interaction remained
      present when the death screen ended; no logout/relogin recovery was
      required.

## Notes

Do not turn this into a catch-all branch. Keep it as the written queue. Each
actual code change should still happen on a focused branch.
