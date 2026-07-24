# Bangel Wrist Jewelry

## Status

Implemented on `feat/bangel-wrist-jewelry`. This document records the slot,
identity, persistence, crafting, effect, and future-family contracts that must
remain stable when the branch is integrated.

## Player-Facing Model

Spoiled Milk now has three simultaneously usable altar-jewelry families:

- Rings remain in the ring slot.
- Necklaces remain in the neck slot.
- Bangels use the new wrist slot.

The player-facing spelling is **Bangel**. The older `Amulet` names that remain
in method names, product-array names, and two cache keys are compatibility
ownership labels, not current item names.

The standard craftable Amulet family is retired completely. The Amulet mould,
unstrung Amulets, ordinary strung Amulets, and their ordinary enchanted
products no longer appear in production, stringing, shops, drops, rewards,
starter supplies, static spawns, guides, bank classifications, or admin spawn
utilities. Both normal enchantment spells and My World altars now use Bangels.

Uniquely named quest artifacts remain Amulets because quests genuinely own
their identity and lifecycle. The retained exceptions are Ghostspeak, Accuracy,
Gnome Emerald Protection, Glarial's, King Lathas's, Othainian, Doomion,
Holthion, and Zombite Amulets. These exceptions are not members of the retired
craftable family.

## Slot and Protocol Contract

The new server slot is appended after Ring:

| Layer | Existing last slot | Wrist |
| --- | ---: | ---: |
| Server physical equipment | Ring `13` | `SLOT_WRIST` `14` |
| Client logical equipment | Ring `10` | Wrist `11` |

The client already compacts the server's alternate helmet/body/leg slots
`5`–`7`; therefore server slot `14` translates to client slot `11`. Existing
server and client slot numbers were not moved.

The server slot count is now 15 and the desktop client logical count is 12.
Equipment packets, full equipment refreshes, bank equipment views, bank
presets, hover/click/unequip handling, persistence loops, death processing, and
equipment validation use those counts.

Old bank-preset blobs may end after the former 14 server slots. Decoding is
bounded, and loaded equipment is placed according to its current item
definition. This moves a preserved enchanted-item ID from neck to wrist without
rewriting or losing the item.

Equipped-item database rows persist item identity rather than an authoritative
slot number, so login resolves retained IDs through their current wrist-slot
definitions.

## Preserved Enchanted Item IDs

Every formerly active altar-enchanted Amulet product retains its item ID:

- `1593`–`1612`: gathering families
- `1709`–`1713`: Teleportation
- `1719`–`1758`: Chaos through Soul families
- `3106`–`3110`: Command

All 70 active definitions are now named Bangels, use server slot 14, and use
the supplied Bangel inventory sprite. They have appearance and wearable IDs of
zero because Bangels do not draw a character-model layer.

Keeping those IDs preserves inventories, banks, trades, presets, and
item-ID-keyed charges. The following legacy names deliberately remain:

- `myworld_soul_amulet_burst_charge_`
- `myworld_death_amulet_burst_charge_`
- Internal `*Amulet*` effect/getter/product-line names

Renaming those persistence ownership keys would strand existing charge state.
New player-facing text uses Bangel.

The five classic enchantment outputs also retain their IDs and stats while
becoming wrist-slot Bangels:

| Compatibility ID | Current product |
| ---: | --- |
| 314 | Sapphire Bangel of Magic |
| 315 | Emerald Bangel of Protection |
| 316 | Ruby Bangel of Strength |
| 317 | Diamond Bangel of Power |
| 597 | Charged Dragonstone Bangel |

The `ItemId` enum constants for these five products retain `*AMULET*` in their
names because changing serialized/cache-facing ID ownership names offers no
player benefit. Their definitions and all player-facing text say Bangel.

## Base Bangel Crafting

The Bangel mould is item `3281`. Base Bangels are completed directly at a
furnace from one gold bar, the matching gem, and the mould. There is no
unstrung product, ball-of-wool requirement, or stringing stage.

| Item | ID | Crafting level | Internal XP | Gem ID | Active base price | Enchanting tier |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Gold Bangel | 3292 | 5 | 120 | none | 900 | none |
| Sapphire Bangel | 3282 | 13 | 260 | 164 | 1,800 | 1 |
| Emerald Bangel | 3283 | 26 | 280 | 163 | 3,000 | 2 |
| Ruby Bangel | 3284 | 44 | 340 | 162 | 6,000 | 3 |
| Diamond Bangel | 3285 | 60 | 400 | 161 | 12,000 | 4 |
| Dragonstone Bangel | 3286 | 70 | 600 | 523 | 35,000 | 5 |

Levels, XP, gem requirements, active prices, and enchanting tiers match the
retired complete-Amulet recipes. Dragonstone remains members-only. The plain
Gold Bangel carries the former plain Gold Amulet recipe and is intentionally
not an enchantment input.

## Legacy Property Conversion

Legacy definition IDs remain decodable but are not obtainable. On load,
`LegacyAmuletCompatibility` converts existing property as follows:

| Legacy property | Canonical property |
| --- | --- |
| Amulet mould `294` | Bangel mould `3281` |
| Gold unstrung/strung `296`, `301` | Gold Bangel `3292` |
| Sapphire unstrung/strung `297`, `302` | Sapphire Bangel `3282` |
| Emerald unstrung/strung `298`, `303` | Emerald Bangel `3283` |
| Ruby unstrung/strung `299`, `304` | Ruby Bangel `3284` |
| Diamond unstrung/strung `300`, `305` | Diamond Bangel `3285` |
| Dragonstone forms `522`, `524`, `610` | Dragonstone Bangel `3286` |

Conversion mutates the existing `ItemStatus` catalog ID, preserving its unique
ownership token, amount, note state, and durability. It covers inventory,
equipment, bank, bank presets, pinned bank slots, auction listings, and
collectible auction returns. If an equipped legacy Amulet converts into an
already occupied wrist slot, the existing wrist item remains equipped and the
converted item is safely moved to the bank.

The old `ItemId` constants, base item definitions, historic patch definitions,
and database migration records remain as decode-only compatibility data. They
must not be reused as acquisition IDs.

## Effect Ownership

All active effects formerly read from the Amulet/neck slot now read the wrist
slot, including:

- Elemental defense
- Mind and Body XP/duration/regeneration effects
- Cosmic gathering effects
- Chaos rune weaving
- Nature cleansing and alchemy
- Law teleportation item behavior
- Death damage stacks and charged burst
- Blood Siphoning
- Soul charged Renewal
- Life summon damage
- Gathering Bangel yield effects

The combined Mind and Body jewelry calculations independently read wrist,
neck, and ring, allowing all three same-altar effects to contribute at once.
Necklace and Ring effects remain on their original slots.

## Hidden Medallion Gate

Items `3287`–`3291` establish Sapphire through Dragonstone Medallion
definitions and metadata. Their future recipe ladder uses silver, the same gem
tiers, Crafting levels/XP, and enchanting tier numbers.

Medallions are intentionally unavailable:

- `FutureMedallionCatalog.PRODUCTION_ENABLED` is `false`.
- `FutureMedallionCatalog.ALTAR_ENCHANTING_ENABLED` is `false`.
- They are not wearable.
- They are absent from furnace recipes, crafting menus, altar inputs and
  outputs, guides, shops, and active effect lookups.

Enabling Medallions later requires an explicit design decision for their
equipment slot, altar products, effects, and player-facing availability. The
metadata alone must not open any production path.

## Graphics and Credit

The project owner supplied and created these graphics:

- `bangel.png`
- `bengal-mould.png`, tracked as `bangel-mould.png`
- `bengal-slot.png`, tracked as `bangel-slot.png`
- `Medallion.png`, tracked as `medallion.png`

The source files remain untouched. Normalized copies live under
`dev/myworld/assets/sprites/`.

## Regression Contract

`tests/myworld/test-bangel-jewelry.py` covers slot counts/translation, packet
paths, equipment UI and bank/preset views, all property-conversion containers,
wrist collision preservation, retained altar/classic IDs, all six crafting
recipes and lack of wool, standard-spell and altar inputs, retired acquisition
guardrails, explicit quest exceptions, all former Amulet effect getters,
simultaneous Ring/Necklace/Bangel slots, hidden Medallion gates, and
client/server sprite/definition coverage.

The existing enchanting, equipment appearance, equipment hover, Entrana, and
jewelry runtime suites remain part of the relevant validation set.

## Retirement Integration Audit

The 2026-07-24 follow-up audit checked every supported crafting presentation:
the modern production session, category-filtered furnace UI, automatic recipe
detection, authentic dialogue menus, retro client menu, batch production, and
the Crafting guide. All six completed base Bangels are present. Bangel recipes
use the Bangel mould as a reusable requirement and consume no wool.

The authentic dialogue path had one configuration-specific defect:
`WANT_EQUIPMENT_TAB` skipped the gem question but left the internal selection
flag false, causing the menu to choose only a plain Gold product. It now treats
that configuration as direct tier selection and offers Gold through Diamond,
plus Dragonstone on members worlds.

The acquisition audit compared the former mould sources and checked current
shops, My World starter tools, static ground-item definitions, administrator
item utilities, and Superchisel testing support. Crafting-equipment shops and
starter tools supply only the Bangel mould. Static definitions and direct
administrator requests cannot reacquire retired products. Superchisel testing
now opens from the Bangel mould instead of wool, offers all six Bangels,
includes Dragonstone supplies, and no longer falls through into unrelated
supply actions.

The executable compatibility fixture verifies every retired ID-to-Bangel
mapping and proves conversion preserves amount, noted state, wielded state,
durability, the existing `ItemStatus`, and the unique item ownership token. It
also proves conversion is idempotent, current enchanted/custom Bangel IDs stay
stable, quest Amulets are excluded, and coexisting old/new equivalent holdings
retain their total quantity and remain distinct ownership records.

Because a server was running during this audit, no server process, database, or
runtime files were touched. The post-shutdown integration pass should still
exercise an isolated database fixture through login, save, logout, and reload
with:

- Legacy and current equivalents together in inventory and bank.
- A preexisting wrist item plus a converted equipped legacy Amulet.
- Legacy and current preset entries, including an old 14-slot preset blob.
- Pinned legacy bank metadata and a zero-quantity pinned placeholder.
- Active auction and collectible-return rows.
- A full bank during equipment-collision overflow.

That pass should confirm row counts, quantities, item ownership IDs, wrist
placement, overflow handling, pin position, auction/return visibility, and the
second reload state before removing the isolated fixture. It must not use the
live database.
