# Cleric consistency review and implementation assignment

October 31, 2026. Core's review of the owner-approved design. This is the active
implementation handoff for `/home/justin/Core-Framework-ai-1`, on manager-started
branch `feat/cleric-presentation-and-hostile-effects`. Dot retains exclusive
ownership of `Core-Framework-ai-2` for hit-splat visuals.

## Review result

The eighteen god-themed spell names and their named effects are consistent.
All nine buff recipient messages are selected. The nine debuffs require names
and mechanical descriptions, with no additional recipient flavor messages.
The governing inventory is in [the Cleric concept](cleric-spellbook-concept.md).

Use **spell name → named component effects → plain mechanical descriptions**.
Higher tiers retain previous effects; never show only the newly added component
as though the earlier components disappeared. The status remains one composite
per god/tier/power snapshot, with one expiry, not separately stackable component
buffs. Keep potency, range, durations and actual restrictions visible in the
mechanical text. Preserve owner-selected flavor; punctuation may be normalized.

Specific consistency decisions:

- Saradomin is now **Light/Hallowed Strikes → Aura/Quickened Pulse →
  Resplendence/Mending Flesh**. Move both the healing mechanics and their
  associated flavor to the new tiers. Preserve stable code12 and its existing
  storage/protocol key when renaming Warmth's display title to Light.
- Distinguish **maximum melee damage** (Unnatural Might) from **melee damage
  dealt** (Bloodrage). Neither restores retired accuracy or crit mechanics.
- Barkskin's selected mechanical scope is **ranged defense**. Sap Blood helps
  poison cleansing and drained-stat recovery; its imagery grants no immunity.
- Blood Hunger's existing below-half-health condition must be explicit. The
  owner did not separately approve narrowing lifesteal to melee; retain its
  existing eligible direct-hit scope while narrowing only the two damage buffs.
- Phobia labels name vulnerabilities; they do not add fear, fleeing or AI loss.
  Molecular Antipode induces the separate shared **non-corporeal** state; it is
  neither invisibility nor Stagger.
- The discarded neutral Respite stays absent. Unify/Greater Unify retain their
  existing functionality, names, 2/6 Stone Grace costs and lack of buff flavor.
- Fix stale source/display references in the active implementation. Historical
  launch records remain historical rather than being rewritten as new behavior.

## Worker scope and sequence

Read AGENTS.md, AI_WORKSPACE.md and docs/workspaces/README.md. Run the matching
preflight in **ai-1**, verify the assigned topic branch, and record starting HEAD.
This worktree is exclusive to this implementation worker. No implementation in
manager/ai-2/ai-3, no cross-project merges, no release or gameplay operations.

### A. Approved buffs, presentation and undead classification

1. Establish one authoritative definition of approved spell/effect flavor and
   mechanical descriptions. Use it consistently for the book, guide and active
   effect tooltips, avoiding duplicated manually maintained spelling/formulas.
   Retain useful numeric descriptions beneath named components.
2. Implement Saradomin's new order. Hallowed Strikes gives
   `25 + 10*(P-1)` percent extra damage against undead through **melee, ranged
   and Magic**. Tier caps2/4/6 yield maximum35/55/75%. Regeneration starts at
   Tier2, unchanged200+50*(P-1) percent; ten delayed six-second healing pulses
   start at Tier3, unchanged P HP per pulse. Preserve the60-second composite,
   cadence-safe refresh and expiry-boundary final pulse. No immediate recast heal.
3. Use the existing direct-attack scope as the baseline for Hallowed Strikes;
   do not multiply periodic, summon, reflected or secondary proc damage as a
   side effect. Apply its percentage once, preserving zero suppression and
   fractional carry instead of giving every tiny hit a minimum-one bonus.
   Document combination order with other damage bonuses and test it.
4. Audit server-owned undead classification. There is already a19-ID legacy
   list and an isUndead attribute. Provide explicit server definition metadata
   for missing/custom undead, a single reviewed predicate for this feature and
   compatible legacy defaults. No runtime name guessing, editor roster or
   blanket classification of demons/human necromancers. Record known/ambiguous
   cases and preserve existing classic/quest admission unless deliberately
   reviewed. Test false defaults, explicit overrides and custom definitions.
5. Narrow Barkskin to ranged defense and Zamorak maximum/dealt bonuses to melee.
   Audit all actual style/launch paths and deferred receipts. Do not change
   unrelated gear, prayers, Critical/Stagger, Slow, Whip or Sticky Skin behavior.
6. Checkpoint this coherent slice with its actual-path tests and updated docs.

### B. Approved Guthix/Saradomin hostile effects

Implement the three Guthix and three Saradomin debuffs after the buff foundation.
Use the governing names, six-power curve, tier caps and radii2/3/4. Keep PvP
disabled and target eligible live enemy NPCs. Reuse the existing joint sigil-cost
and lifecycle authorities; god+Blight replaces god+Grace for these casts. The
initial mirrored tier cost vectors and60-second duration are reviewable manager
defaults, not newly claimed owner-selected economy. Commit resources once per
useful cast, never per recipient; no eligible/useful enemy means no spending.
Apply the same-god no-stack/no-downgrade policy; different gods may coexist.

- Guthix: each applicable weakness is10+5*(P-1)% (10/15/20/25/30/35).
  Toxiphobia increases poison damage and reduces cleansing per existing pulse,
  retaining immunity and a positive cleansing floor where applicable; it does
  not inflict poison or reset its cadence. Agrizoophobia increases summon
  damage; Hematophobia increases reflected/recoil damage. Classify actual
  sources, apply each multiplier once, and prevent reflection recursion or
  multiplied kill/loot attribution. Audit integer behavior and report it.
- Saradomin: owner-approved Magical Allergy is10/13/16/19/22/25% Magic-defense
  reduction; Muscular Atrophy is5/7/9/11/13/15% maximum-melee-damage reduction.
  Preserve other styles and document interaction/rounding with existing debuffs.
- Molecular Antipode rolls5/7/9/11/13/15% once per eligible game tick. The owner
  confirmed durations **1/1/1/2/2/3 game ticks**, followed by at least one actual
  corporeal NPC attack attempt before rolls resume. Natural misses count;
  polls/time alone do not. Actor-owned rearm must survive recasts/source changes.
  Keep ordinary action cadence running; no extra free/canceled attack counters.
- Implement non-corporeal as a reusable actor state with independent lifetime,
  expiry and source ownership, suitable for later content without implementing
  that content. Incoming/outgoing nonmagical combat harm must be suppressed;
  Magic remains effective in both directions. Audit direct, secondary and
  actor-owned periodic damage, delayed launches, reflection and custom attacks.
  Do not silently omit a family, grant immunity to unrelated environment/quest
  scripts, mutate all HP setters or claim unaudited coverage. Report a genuine
  semantic ambiguity before encoding it; continue other independent work.
- Synchronize the phase's appearance and reuse existing NPC opacity support
  with an independent flag (not spiritSummon). Start near50% opacity. Verify
  layered sprites, animations, selection, name/health/hit overlays and both
  software/OpenGL paths. No replacement sprite set or art archive is needed.

### Subsequent Cosmic decision; Zamorak hostile activation remains held

The nine debuff flavor names are approved. **Do not activate Expose Weakness,
Weak Legs or Weak Constitution as part of the Cosmic replacement.**
The owner subsequently approved combat luck instead of robe critical chance;
see [the scoped review](cosmic-combat-luck-review.md). The proposed15% hostile
critical source is not activated or combined automatically. Record the remaining dependency and
keep incomplete spells out of the castable roster. Do not claim this finishes
the complete hostile book. No extra legacy secondary critical migration here.

## Compatibility and validation

Version any changed metadata/state carrier explicitly with matching client and
server handling. Keep stable spell/item identities. Generate shared protocol
constants, bound strings/counts and reject incompatible payloads cleanly. Avoid
editing renderer/hit-splat functions owned by Dot; touch shared packet code only
where necessary and list integration concerns. No history rebasing from Dot.

Run heavy jobs serially with bounded512MiB heaps. Use real offline World/event,
damage and client decode/render fixtures with tiny fresh maps and memory actors;
no listeners, owner data or gameplay clients/servers. Capture before/after
descriptions and actual client preview evidence where practical. Test inherited
effects at P1/P2/P4/P6, style restrictions, undead/nonundead, real launch/impact,
zero/crit, AoE, fractional math, no-useful-target resources, refresh/downgrade,
multiple casters, expiry/death/respawn/logout and both renderer paths. Exercise
current Cleric/C10/wire/HUD, combat144, Slow/shared-attack and relevant Slayer,
poison/cleansing/coating regressions. Preserve old-version fixtures as rejection
coverage rather than relabeling them as valid new payloads.

Checkpoint meaningful slices without awaiting manager prompting. Finish with a
clean pushed exact READY handoff, durable commands/results/hashes, changed-file
summary, undead classification audit, implementation limitations and no gameplay
acceptance claim. Core handles final integration, publication and staging.
Never operate the public server, accounts, owner maps, installed toolkit or
`/home/justin/spoiled-milk-next-update` from this worker.

## Completion checkpoint

The scoped source candidate is complete. See [the implementation review](cleric-consistency-implementation-review.md)
for exact identities, nature classification, lifetime/rounding policy and durable
validation receipts. All A buffs/presentation and six B Guthix/Saradomin hostile
spells are implemented; Cosmic's approved combat-luck replacement follows separately.
Zamorak hostile activation/eligible-hit/execution decisions remain deferred. Private gameplay and manager integration/
prepared-package refresh are separate. No owner world, account or game session
was modified by this worker.
