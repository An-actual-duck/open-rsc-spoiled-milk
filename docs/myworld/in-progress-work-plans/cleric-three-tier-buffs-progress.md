# Three-tier blessings implementation progress

The `feat/cleric-three-tier-buffs` candidate implements the approved October31
buff model. It remains ACTIVE pending final combined verification and exact
READY handoff; no public deployment, server session or owner-map import occurred.

Implemented server work:

- Active sparse identities: Unify1, nine god blessings12–20, Greater Unify21.
  Historical0/2–11 stay reserved and fail closed, without aliases to new effects.
- Universal generated immutable Holy Power thresholds0/20/45/75/115/160; Worship
  tier unlocks1/20/40, caps2/4/6 and radii2/3/4. Worship plus strongest staff
  remains authoritative. Blessed staff Magic-only normalization passed117 actual
  definition/equipment admission checks.
- One60-second atomic snapshot per god, complete components, per-god precedence,
  coexisting different gods, safe equal refresh and preserved periodic cadence.
- Approved incremental values, delayed Sara healing through the final endpoint,
  additive poison cleansing, gradual non-HP restoration, defense and primary
  direct combat hooks, factual-damage fractions and no secondary proc loops.
- Unify Worship3/radius3/one actual safe step/two Stone Grace; Greater Worship30/
  radius5/three safe steps/six Stone Grace, both stopping within2. No Holy Power
  scaling. Manager's initial mixed god/Grace cost defaults commit once per cast.
- Schema3 numeric cost/component metadata, min client10054, matching generated
  contract and derived-stat-only Holy Power/Barkskin refresh packets.
- Real native World/packet/party/inventory/effect/combat fixtures; generic old
  math/counter characterization retained with explicit frozen test-only data.

Client implementation is managed in the separate Core client worker topic and
will be merged by exact READY dependency into this task before its final handoff.
It owns the schema3 decoder, sparse UI positions, mixed unnoted-cost checks,
selected-rank preview using authoritative equipment stat7, guide/HUD components
and existing icon reuse. This repository's manager integrates the combined task.

Current validation includes the real144-scenario combat gate, current native
Cleric acceptance, metadata roundtrip and focused server/client regressions.
Final evidence and exact commands are recorded in the implementation review.
Remaining scope is review/verification, not additional unapproved mechanics.

Saradomin's extra third component/message remains open: Resplendence raises the
cap of its existing two components. Hostile debuffs remain documentation only.
