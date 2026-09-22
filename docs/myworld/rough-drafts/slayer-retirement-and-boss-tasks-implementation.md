# Slayer material retirement and optional boss assignments

Implemented 2026-09-22. No deployment, player-data rewrite, artwork or placement.

## Retired material families

Regular giant, moss giant, ice giant, fire giant and ogre/jogre acquisition is
retired. The 35 existing definitions (1807–1816, 1875–1884, 1895–1904,
1915–1919) retain their IDs, names, stats, equipment effects, values and note
forms; only tradability changes. Inventories, banks and equipped items are not
deleted, converted or rewritten. Server and client both bind note forms too.

Removed sources include guaranteed NPC drops and variants, tanning, leather
crafting, crafting-guide entries, master-tanner stock, and fishing's special
glove/boot rewards. Stale crafting requests fail without consuming materials.
Normal trade, duel and shop restrictions use the existing untradable policy.
Old auction listings cannot be purchased; owners can still cancel/reclaim them.
Ground items remain owner-only under existing untradable rules. Death behavior
is unchanged: this is not permanent-loss insurance or a new death system.

Cow, regular unicorn and bear materials remain available outside Slayer.
Black unicorn remains a Slayer task with its existing leather. Other approved
exceptions and existing legacy armor effects are unchanged. Alongside the
previous Banshee withdrawal, 26 leather/carapace families remain craftable.

## Roster and account compatibility

The seven assignment families removed from mandatory and repeatable pools are
giant, moss giant, ice giant, fire giant, ogre, jogre and bear. Historical JSON
rosters are immutable migration inputs, not edited in place:

- v1: original roster; v2: original tower-expanded roster.
- v3: retired base roster; v4: retired tower-expanded roster.

Reads validate historical saves against their original roster before proposing
the current snapshot. The next normal state write persists that snapshot.
Migration is idempotent and preserves earned ranks/completed tiers, balances,
backpack upgrades, lifetime completion totals, promotion acknowledgements and
unrelated active task progress. Removed active assignments are cancelled with
no payout or automatic replacement. Completed surviving tasks remain credited;
removing a final obsolete requirement cannot strand progression. Backpack
prices remain fixed at their existing values. Enabling the tower later remains
supported; disabling an already-enabled tower roster is rejected.

Before eventual live activation, back up player data using the normal deployment
procedure. Old code cannot read v3/v4 saves: rollback requires a compatible
reader or restoration of the corresponding pre-activation backup, not a reset
of progression. Item binding itself is definition-only and needs no holdings
migration. No live activation or backup was performed by this implementation.

## Optional bosses

Legends associate 857 offers **Boss tasks**, with separate Balrog and Elder
Green Dragon choices. Readiness options say “I'm ready for Balrog tasks” and
“I'm ready for Elder Green Dragon tasks”; an enabled preference instead offers
“I'm unable to complete”. Both default off and persist independently.

Each joins only the Legends repeatable pool: one kill, 80 Hero points, ordinary
weight 1. Mandatory King Black Dragon remains one kill/60 Hero points. Existing
rank eligibility is unchanged. Access does not imply opt-in. Access is checked
both when enabling and assigning, including cached task previews:

- Balrog: custom quests enabled and the dwarven-youth rescue access flag,
  matching the actual forge ladder.
- Elder Green Dragon: current Mining level at least 80, matching its door.

Opting out cancels only that boss's current task without rewards or rerolling.
An unrelated active assignment and the other preference are preserved. Task
advice names the boss-specific preparation. No boss, associate, door or tower
placement changed.

## Verification

`test_slayer_retirement` covers historical mandatory boundaries, repeatables,
completed accounts, later tower enablement, idempotence, unchanged balances and
upgrades, all 35 bound definitions/notes, owner-only ground items, absent tanning
and stale crafting routes, retained passive materials, NPC drop variants,
boss opt-in/access/preview/cancel behavior and exact one-kill payouts.

`test-retired-leather-items.py` compares the retired server definitions to the
immutable pre-retirement revision, allowing only the binding change, then checks
client normal/note forms. Broader player-state, tower dialogue, associate,
gimmick shop, leather and combat regressions accompany this pass. The old full
contact-route suite has a separate Heroes' Guild terrain assertion incompatible
with the installed native map; focused tower/boss dialogue tests do not assume
that historical map layout.

Spritework and owner-authored tower placement remain separate. The broader
[effect-standardization follow-up](../in-progress-work-plans/effect-standardization-follow-up.md)
is still deferred until after Slayer Tower work.
