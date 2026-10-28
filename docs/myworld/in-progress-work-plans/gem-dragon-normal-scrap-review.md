# Gem dragon Scrap as normal loot — Core review

This supersedes the independent hidden Scrap roll in the earlier standard-loot
revision. Dragon Metal Scrap is now a mutually exclusive outcome of the normal
base roll. Guaranteed gems, bones, both hidden equipment uniques, all quantities,
stats and artwork remain unchanged.

## Normal root and conditional common weights

| Dragon | Root total D | Common child weight | Rare Scrap child weight |
|---|---:|---:|---:|
| Sapphire |1024|1023|1|
| Emerald |768|767|1|
| Ruby |512|511|1|
| Diamond |256|255|1|
| Dragonstone |128|127|1|

The common child retains its 128 weights: gold bars 28, plain ring 8, own-tier
ring 8, bolts 16, arrows 4, runes 20, coins 36, nothing 8. Thus base Scrap probability
is 1/D and a specific common outcome of weight w has unconditional probability
((D−1)/D) × w/128. Nothing is a valid outcome, with probability
((D−1)/D) × 8/128. Bones and the separate guaranteed gem still arrive.
The rune child remains 16 total (8/8 for two choices). No ordinary extra gems,
hides, IDs or global DropTable changes were introduced.

Scrap is one unnoted existing item 3228. Its one-item child is flagged rare using
existing DropTable behavior. Cloning preserves the complete weighted structure;
there is no custom subclass or duplicated hidden Scrap path. Hidden equipment
uniques remain independent and can coexist with Scrap and the guaranteed gem.

## Existing equipment and personal-loot behavior

These rates describe an unboosted, full-contribution base roll. Existing luck
potions can increase rare-child weight. The current Cosmic-ring Wealth effect (tested with item 1701) can make its established
additional rare roll after a common result, so Wealth can produce both a common
reward and Scrap. A successful rare base result suppresses the Wealth bonus.
Cosmic's extra standard roll suppresses rare children: if that extra roll selects
Scrap it delivers nothing, and cannot duplicate Scrap. Death still boosts
ordinary guaranteed bones, while the separate gem keeps its approved 1/3/5 or
single Dragonstone quantity. Personal rare-child gates use the existing
max(5%, contribution) floor and recipient metadata, rather than hidden-unique
scaling. No generic equipment rules were bypassed.

## Value context

The previous common-table base EVs remain conditional values. Current expected
normal loot is commonEV × (D−1)/D + 100000/D. The guaranteed-gem EV is added
separately. Prior independent-Scrap subtotals in the historical review are
superseded; this change removes common loot on the base Scrap outcome. These
are nominal item-definition values, not trade/alchemy/profit or kill-time claims.

## Verification

Focused production fixtures pass 86,163 assertions and exhaust all 2,688 normal root tickets and all 10,240
common/rune ticket combinations. Real death delivery covers 304 ordinary and rare
outcome deliveries, empty common outcomes, both hidden gear wins, Cosmic/Death/Wealth,
normal and personal recipients. The guaranteed fixture retains all 897 exact gem
tickets and 258 normal/personal necklace deliveries. Rare contribution gates,
Wealth's bonus and Cosmic's suppression are verified directly through cloned
production tables. The equipment/fire/stat fixture and three Slayer loot
regressions are retained. See `gem-dragon-normal-scrap-evidence/` summaries for final counts.

No client, asset, account, map, toolkit or live-server changes. Core separately
reviews and updates the maintained private candidate.
