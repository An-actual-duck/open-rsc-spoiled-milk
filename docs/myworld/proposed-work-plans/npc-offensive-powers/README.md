# NPC offensive Power assignments

2026-11-16. Concrete assignments for all298 attackable definitions, using the
established loadouts, bands and owner exceptions. **Authoring data only; not
installed gameplay values.** Current and assigned powers remain separate.

- [Full assignments and ceiling comparisons](powers.md).
- [CSV](powers.csv), [structured assignments](powers.json).
- [Loadouts, tags and protected boundaries](../npc-attack-loadouts/README.md).
- [Owner offense decisions](../../in-progress-work-plans/npc-offense-owner-decisions.md).

## Ordinary assignment

| Band | Primary | Secondary | Tertiary |
| --- | ---: | ---: | ---: |
| Low | 100% | 40% | 10% |
| Mid Low | 100% | 50% | 20% |
| Mid | 100% | 60% | 30% |
| Mid High | 100% | 70% | 40% |
| High | 100% | 80% | 50% |
| Very High | 100% | 90% | 60% |

Use the original loaded Strength after existing Strength overrides, never
max(Attack,Strength). Apply percentages once while authoring, rounding down.
A positive reference gives each enabled style at least Power1 where integer
rounding would otherwise erase a deliberately enabled attack. Disabled styles
receive0; the enabled-style list, not inference from a stat, owns availability.
Only explicit integers are intended to reach runtime, not these percentages.

Already-authored NPC powers need special care: many new Slayer enemies have old
Strength1 as a compatibility placeholder. Where direct Melee Power is greater
than1, use that authored Strength-equivalent reference; otherwise retain a
positive authored primary Power if present. This preserves meaningful baselines
such as Cockatrice40, Bloodveld80 and Stirge55. Reference provenance is explicit
per row. Protected/scripted cases below override this ordinary process.

Naga is an intentional emphasis change: authored reference65 gives Ranged65 /
Melee39 at Mid60% secondary, instead of current Ranged55/Melee65. This follows
the agreed ranged-primary loadout but changes its delivered damage mix; keep
its existing per-style cadence/protections and verify the resulting encounter.

## Specific exceptions

| NPC | Assigned Power M/R/G | Reason |
| --- | --- | --- |
| Ice Queen254 | 104 / 0 / 104 | Owner requested full original Strength in both enabled styles |
| Balrog809 | 250 / 0 / 250 | Retain current melee; replace Attack999 fallback with Strength250 Magic; individual boss assignment |
| King Black Dragon477 | 224 / 0 / 201 | Full Strength-reference melee,90% supporting Magic; individual boss assignment |
| Elder Green Dragon844 | 250 / 235 / 270 | Preserve three intentional explicit powers and scripted attack owners |
| Banshee865 | 55 / 0 / 55 | Owner requested unchanged encounter, including wail/protection |
| Dark beast869 | 110 / 0 / 210 | Preserve intentional powers and existing charge/lightning owner |
| Giant Frog863 | 0 / 30 / 0 | Preserve active authored spit Power30; no melee enabled |
| Protected minigame/role rows | Exactly current raw powers | No migration; do not rewrite incidental getter values from nominal style labels |
| Charlie550 | 0 / 0 / 0, role hold | Level0/HP3 special role; no new enabled-zero attack semantics decided here |

Balrog's ordinary generic raw Magic ceiling becomes56 rather than226. This is
an intentional correction, not a claim of unchanged boss damage. Its specials
and actual mitigation still require proper regression coverage.

The 22 protected entries remain exactly unchanged, including Gnome Ballers,
Mage Arena forms/Battle mages, shared Rat29 and the conservative PK-bot hold.
No generic power/weight assignment overrides their protection boundary.

Charlie is not silently removed or made harmless: current ordinary Melee Power0
can still produce a raw maximum1 under the existing formula. Its numbers are
retained with a role-review hold, pending explicit enabled-zero semantics.
This does not authorize changing all ordinary offense minimums.

## Attack-selection weights

To make the roster reviewable without another per-NPC discussion, numeric
weights are assigned alongside powers as implementation recommendations:

- Single style:100%.
- Ordinary two-style:75% primary /25% secondary.
- Black/White/Grey knights and Black Knight titan:90% Melee /10% Magic,
  retaining rare supporting holy casts.
- Ice Queen:75% Magic /25% Melee; equal powers do not imply equal frequency.
- Balrog:75% Magic /25% Melee. KBD:75% Melee /25% Magic.
- Preserved script owners/minigames: no new generic weights. Their existing
  state machines and timing remain authoritative.

These are explicit assigned recommendations, not a claim that current source
already delivers those shares or that numeric weights were separately owner-
approved. Current65/85/10 preference checks apply at adjacency/poll points, so
old and proposed percentages are not direct apples-to-apples attack shares.
A mixed actor chooses among eligible attacks at a committed attack decision;
weights do not authorize attacking through walls or at invalid ranges.
No ordinary three-style actor currently needs numeric weights: Elder's three
styles remain scripted. The tertiary power rule is retained for future use.

## Damage-ceiling comparison

M/R/G tables show unmitigated ordinary raw bounds, before defense, NPC low-biased
rolls, robes, prayers, god alignment, status effects, HP caps or attack timing:

- Melee/Ranged: `max(1, 1 + floor(Power / 7))`.
- Generic NPC Magic: `ceil(max(1, Power / 12)) + floor(Power / 7)`.
- A dash means the style is not enabled for that comparison; it does not promise
  that every incidental historical melee route has already been removed.

Current-style comparison includes reachable generic melee for nominal pure
profiles, except the existing Frog/Kolodion-human no-melee guards, plus Dark
beast scripted Magic and Elder scripted Ranged. Special replacements/breath/
burn do not use these ordinary maxima and are not represented as if they did.
Protected rows preserve the existing observed capability rather than enforcing
new exclusivity. No DPS, hit probability or average HP-loss claim is made.

Notable review deltas:
- Black Demon568 changes Magic195 ->91 under the Strength reference; its raw
  Magic maximum falls44 ->21. This explicitly removes an old Attack fallback,
  rather than silently compensating with an invented larger power.
- Some low mages lose raw maximum from scaled/rounded Attack-to-Strength changes.
  For example Darkwizard57 changes Magic15 ->12, raw4 ->2. The comparison exposes
  these cases; gameplay acceptance requires testing them with the new defense
  roll and relevant player equipment.
- New caster attacks have no prior ordinary Magic ceiling for parity comparison.
- Secondary powers can decrease even when the primary Strength reference is
  preserved. This is deliberate specialization, not a universal unchanged-DPS claim.

## Validation and remaining runtime work

`python3 scripts/validate-npc-offensive-powers.py` checks complete roster and
source bindings, power derivation/rounding, inactive zeros, the Ice Queen and
boss/special exceptions, protected raw powers, weights and independent ceiling
arithmetic, plus consistency of JSON/CSV/Markdown.

These files do not solve or rewrite deferred dragon-breath, Banshee-wail or Elder-
burn magnitude mappings. Existing special mechanics are retained; their final
Power-source contract remains a separate decision. Runtime installation still
requires canonical stat loading, authoritative zero/missing distinctions, style
exclusivity/pursuit and tag/trait handling, followed by actual combat/quest and
minigame regressions. No public server, accounts or private fixtures were touched.
