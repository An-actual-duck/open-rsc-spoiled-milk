# Fixes-only release candidate

## Baseline and exclusions

Baseline: deployed public revision `fec94c8731b5521410963575ef0f2fa5c05ef0b3`.
Branch: `fix/fixes-only-october-release`, prepared in an isolated clone.

No new Slayer monsters, items, rewards, tasks, shops, leather changes or tower
placements are included. No Slow foundation, tiered antidotes or unfinished
World Builder compatibility changes are included. Existing content already
present in the deployed revision is preserved.

## Included changes and provenance

| Change | Reviewed source |
| --- | --- |
| Blocking overlay 255 collision | `6d768898aa4a01f6cbcdfe7db94d6856812e71a3` |
| Balrog set secondary splash death attribution | `0b1293b975ee3d6450b8f8129049f13c94c7b538` |
| Gnome course authored landings and explicit levels | `c8aa1e7128413c1e18ce776fdbc0865466b57ee6` |
| 330 worn appearance mappings | `94c5bda94367a8fef0b9fc794c72e5e9efa9c36c` |
| Held weapon family normalization | selected production/test paths from `ff39d1f4432ffa1c02e815c6813f13b4c51d58ec` |
| Approved held shears | selected production/test/asset paths from `2c285e4dde2e944e8c5d768b7f9152f9a57aa215` |
| Modern/hybrid spell lookup | SpellDamages-only change from `6d0e3fe66dde666f2d86ac4e16535410ebb49df6` |
| Titan Steel poison tier | PoisonPower-only change from `4d98e17833135ae3f150f7ad953099b184fe91d1` |

Integration adaptations:

- Preserve held-family appearance IDs 1130–1150 with empty reserved slots;
  preserve deployed Foundry Dragon, King Black Dragon and Gorak indices. Do
  not import the omitted Slayer animation table to satisfy held-family IDs.
- Correct the eight shield mappings in the canonical item-override generator
  fragments too, so generator checks/rebuilds cannot undo the correction.
- Remove new Slayer IDs from the held-family test's preserved-item sample;
  the absent IDs are not added merely to satisfy a test.
- Select the three combat fixture changes from `3f852bf2b742c7ff0816eccca77fa3c06de8ec36`
  that use real native terrain and explicit layer teleports. No production
  terrain guard is weakened. Refresh generated R2 ownership evidence.
- Add a standalone real-server-class spell-table/Titan Steel regression.

## Verification

Fresh server/core/plugin and desktop-client builds pass. The strict combat
gate passes all 143 scenarios. Also passing:

- 330 worn mapping checks and slot/range checks across 1,770 visible wearables.
- All 36 effective held-family mappings, authentic fallback mode, gameplay
  field invariance and 21 avatar variants.
- Held client families, archive layer/type, all 18 movement/attack frames and
  packaged elemental sword pixels.
- Held shears disk/JAR frame parity, 15 native frames and no combat artwork.
- All seven agility obstacles in native and legacy modes, including read-only
  checks against the deployed package's actual course terrain.
- Blocking overlay regressions (2 tests).
- Balrog set regression: 20 primary/secondary scenarios plus death ownership,
  contributions, XP/drops, overkill, reentrant callbacks, cancellation and respawn.
- Modern lower-book coverage, all three thunder powers and all five poisoned
  Titan Steel variants.
- Thirteen related equipment/combat definition regressions.
- R2 boundary audit (6 tests), dependency guard (3 tests), generator freshness
  and prerequisite checks.

Catalog comparison against the deployed baseline confirms identical item-ID
sets and no changed item fields besides appearances; deployed NPC definitions,
map/configuration, task rosters and existing Slayer production code are unchanged.

The broader blessed-symbol test has a known unrelated pre-existing devotion
expectation failure; it is not presented as passing. The whole MyWorld suite
has not been claimed as passing.

## Release gate

This candidate is not a published player release and is not deployed.
Current release packaging requires clean published manager `main`; that branch
already contains excluded Slayer work. Do not fake a published-main reference,
skip build/provenance checks or rewrite main to get past that guard.

Obtain approval for explicit exact-commit fixes-only release-branch support,
or defer packaging until a release workflow is agreed. Public activation still
requires fresh shutdown authorization and the full in-game update countdown.
