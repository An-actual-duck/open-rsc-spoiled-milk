# Three-tier blessings implementation checkpoint

Status: **WORK IN PROGRESS**, not READY for integration or gameplay.

The focused worker branch is `feat/cleric-three-tier-buffs`. The owner requested
implementation of the agreed nine god blessings plus Unify. The owner has now
requested design review before dependent numerical implementation: first power
strengths and inherited P6 combinations, then Greater Unify, then return to
debuff design. This does not abandon the buff implementation. Strengths,
duration and mixed god/purpose costs remain unapproved; no proposed values were
locked in here. Base Unify costs two Grace; exactly one Greater Unify upgrade
is confirmed at six Grace per cast, with final grade/unlock/radius/movement
budget still awaiting review.

Independent foundation completed:

- Reserved appended spell codes12–20 and three composite god-family slots;
  historical codes remain intact. Composite snapshots carry god/tier/power.
- Reserved a generated matching schema3/client10054 contract with bounded costs
  and typed numeric components. This contract is **not activated** yet; current
  wire schema2 remains until serializer/decoder migrate together.
- Client catalog now distinguishes stable protocol identity from ordered UI
  position; UI callers still need migration when active noncontiguous codes ship.
- Blessed staff requirements now consistently use Magic only and the existing
  Zamorak level ladder1/8/15/22/30/38/46/54/62/70. All30 definitions retain stats,
  prices, art and alignment. God staves and ordinary weapons are untouched.
  This removes old Saradomin/Guthix melee gates and the accidental raw Zamorak
  Worship gate that coexisted with its optional Magic admission.
- Generated overrides and the source generator agree. A real isolated World/
  definition/equip fixture passed117 checks across all30 staves: Magic ladder,
  threshold rejection, no extra melee/Worship gate and no-level-requirements
  bypass. Prayer alignment is deliberately preserved by the fixture.

The existing twelve-spell runtime/catalog is retained at this checkpoint, not
claimed as the finished overhaul. The temporary legacy coverage assertion in
ClericSpellCatalog must be replaced during active-catalog migration. No buffs,
costs, strength constants, periodic schedule or gameplay hooks have been replaced
yet. The nine new symbols are reserved but not yet exposed/castable.

Current review checklist (documentation only; no new gameplay/source changes):

- Universal thresholds/caps resolve rank, not one universal effect coefficient.
  Review each component's base/increment or authored table. Standalone Tier 1
  caps at P2; its inherited component can reach P6 within Tier 3. Saradomin's
  confirmed P+1 formulas stand; other 3P/2P/P proposals remain proposals.
- God buff/future debuff radii are 2/3/4, tier+1, not Holy Power scaling.
- Greater Unify is one upgrade, six Grace (same eventual grade as base), no god
  or Holy Power scaling. Radius 5/max three safe steps/stopping within 2 is the
  recommendation, not finalized movement. Base remains radius 4/two steps/two Grace.
- Future Guthix debuffs: poison/summon/recoil weaknesses. Poison's 10%+5%(P−1)
  example and rounding/cleansing direction are recorded in the active concept;
  no hostile coefficients, caps, stacking/lifecycle or PvP policy are finalized.

Resume implementation after the owner balance/cost and Greater Unify review:

1. Populate the active 11-definition catalog (nine god blessings, Unify and Greater
   Unify) and exact flat mixed-resource vectors;
   retire all legacy requests except Unify without reusing their codes.
2. Replace timed catalog with per-god composite snapshots, safe refresh cadence,
   delayed healing/restoration schedule and complete lifecycle admission.
3. Hook poison/natural regeneration and primary direct combat max rolls, dealt
   damage, three defenses, reflection and lifesteal with indirect/PvP exclusions.
4. Atomically migrate schema3 serializer/client decoder, typed component display,
   mixed-cost inventory greyouts and index-to-stable-ID UI mapping; activate
   matched client admission only with completed transport.
5. Actual packet/runtime/combat/metadata/HUD and compatibility gates, governing
   docs, full review evidence and exact READY handoff.

Read-only combat audit findings are available from Core's audit agent. Important
points: explicit primary CombatFormula max-roll hooks (not global offenseToMaxHit),
all three Player defense methods, additive PoisonEvent drain, preserve natural
regen's restoration clock, and the delayed tenth HoT pulse at the expiry boundary.

No server launch, account operation, owner-map change/import, staged-package edit,
asset change or live operation occurred. Fresh fixtures use only checked-in
configuration/schema and a tiny authored native map.
