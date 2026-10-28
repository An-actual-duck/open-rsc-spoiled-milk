# Guaranteed gem drops — Core review

This supersedes the original gem-dragon loot specification. Combat stats,
unique odds, gear effects, bones, native animation selectors and map placement
are unchanged. No live operations or owner map/account modifications occurred.

## Implemented rule

Every gem-dragon reward recipient gets one independent guaranteed roll. It
selects exactly one type and quantity of **uncut** gem. The first four gem types
(Sapphire, Emerald, Ruby, Diamond) may occur in quantities **1, 3 or 5**, provided
they are at or below the dragon's tier. Thus the old one-own-gem cap is removed
for those types. **No opal, jade or red topaz drops remain.** Dragonstone Dragons
add a **single dragonstone only**; no other dragon can roll it.

Rank = gem tier (Sapphire 0 … Diamond 3) + quantity rank (1→0, 3→1, 5→2).
Initial rank weights are 64/32/16/8/4/2. Dragonstone 1 has weight 1, individually
rarer than Diamond 5 (weight 2). Table totals are 112/168/196/210/211. The detailed
matrix is in `gem-dragon-guaranteed-gems-evidence/expected-weights.json` and the
updated family implementation review.

Equal ranks mean equal **per-outcome weight within one dragon's table**.
Probabilities are normalized within that table; the same outcome does not have
the same absolute probability on different dragons. For example Sapphire 5,
Emerald 3 and Ruby 1 each have weight 16 in the Ruby table, while Sapphire 1 is
64/112 on Sapphire Dragons and 64/211 on Dragonstone Dragons.

The expected quantity is about 2.143 gems for each of the lowest four dragons;
Dragonstone Dragons average 451/211≈2.137 because their rare single dragonstone
adds one outcome. The chosen weights halve at each rank and are initial tuning
values. All five still use the previously approved relative combat hierarchy.

## Delivery and unchanged loot

`GemDragons.rollGuaranteedGem` uses one integer draw. NPC delivery invokes it
once outside ordinary table rerolls and generic guaranteed-drop multipliers.
Cosmic necklaces cannot repeat it; Death necklaces cannot alter 1/3/5 or the
single Dragonstone. They still affect their ordinary eligible loot. The obsolete
`enforceOwnGemCap` method and its old optional-gem tests were removed.

The subsequent October 28 standard-loot expansion supersedes this revision's
ordinary-table details. Current normal weights are gold bars 28, plain ring 8,
own-tier unenchanted ring 8, bolts 16, arrows 4, rune subtable 20, coins 36 and empty 8.
These are conditional common-child weights; amounts remain unchanged. Shared unique
odds and standard dragon bones are unchanged; rare Dragon Metal Scrap now replaces the common child on a 1/D normal roll. No hides are added. See
`gem-dragon-standard-loot-review.md` and `gem-dragon-normal-scrap-review.md` for the latest normal-root structure. The guaranteed
gem matrix and necklace protections documented here remain unchanged.

Single gems are unnoted; 3/5 are noted. The ordinary NPC delivery helper retains
world item restrictions, recipient ownership, personal-loot flags and banking
behavior. The existing Law-necklace helper banks single unnoted rewards;
noted 3/5 rewards remain intact on the ground. Personal loot gets exactly one
outcome per eligible recipient without contribution-chance gating.

## Approved icon replacement

Only the inventory/ground monocle PNG changed, copied without pixel edits from:

`/home/justin/spoiled-milk-next-update/art/gem-cutters-loupe-v2/gem-cutters-monocle.png`

It is the owner-approved jeweller's loupe v2, 48×32. SHA-256:

`4f68d9aadad4a3b45ff586edffcc7362ec493b28ddd769beb0b3fc919bf2db05`

Destination:
`dev/myworld/assets/sprites/items/inventory-ground/gem-cutters-monocle.png`.
The client was rebuilt once and packaged bytes verified. The existing worn
eye-patch placeholder is unchanged; this task does not claim completed worn
monocle artwork or new approval for the glove icon.

## Verification

- The guaranteed-loot fixture exhausts all 897 tickets, 43 outcome boundaries, equal
 rank weights, exact totals, no forbidden/higher-tier gems, Dragonstone 1 only,
 unchanged normal material weights and actual NPC death delivery.
- 258 normal/personal deliveries span every outcome with no necklace, Cosmic
 necklace and Death necklace. The normal table is forced to nothing; gem loot
 still arrives once. Bones and independent unique wins are also verified.
- Law banking and noted 3/5 quantities, personal owner isolation, contribution
 scaling, world restrictions and no RNG draw for unrelated NPCs are covered.
- 232 retained gem equipment/fire/stat assertions pass. Slayer ordinary loot,
 components and rewards regressions pass.
- Client/native asset/renderer checks and 8,024 item-reference checks pass.
- No full combat rerun: no combat behavior or dragon stats changed.

Exact commands, concise summaries and current artifact hashes are in
`gem-dragon-guaranteed-gems-evidence/`. Core still reviews/integrates and updates
the maintained next-update package; no live activation is part of this handoff.
