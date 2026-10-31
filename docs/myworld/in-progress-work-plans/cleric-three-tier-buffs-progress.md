# Three-tier blessings implementation progress

The `feat/cleric-three-tier-buffs` candidate implements the approved October 31
buff model. Combined verification is complete and the candidate is ready for
Core review; no public deployment, server session or owner-map import occurred.

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

The exact client READY dependency `d2c29d1467bbff2453362020f2eee66b7841b434`
is merged into this task, preserving both ancestries. The combined client handles
schema 3, sparse UI positions, mixed unnoted costs, authoritative equipment-stat
Holy Power preview, component guides/HUD and existing icons.

Combined Java 8 server/client builds passed. Final acceptance includes 354 native
runtime assertions, the actual 2,752-byte server payload decoded through 2,986
client assertions, 68 generic HUD assertions, 21 focused Cleric targets, all 144
combat scenarios, Slow/coatings/poison and all nine requested Slayer targets.
See [the implementation review](cleric-three-tier-buffs-review.md) for exact scope,
arithmetic, timing, retained historical coverage, evidence and limitations.

Saradomin's extra third component/message remains open: Resplendence raises the
cap of its existing two components. Hostile debuffs remain documentation only.
