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

Authentic and quest Amulets remain intact. Ordinary Amulets are no longer
accepted as inputs by the My World altars.

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
definition. This moves an old preset's preserved enchanted-Amulet ID from neck
to wrist without rewriting or losing the item.

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

## Base Bangel Crafting

The Bangel mould is item `3281`. Base Bangels are completed directly at a
furnace from one gold bar, the matching gem, and the mould. There is no
unstrung product, ball-of-wool requirement, or stringing stage.

| Item | ID | Crafting level | Internal XP | Gem ID | Active base price | Enchanting tier |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Sapphire Bangel | 3282 | 13 | 260 | 164 | 1,800 | 1 |
| Emerald Bangel | 3283 | 26 | 280 | 163 | 3,000 | 2 |
| Ruby Bangel | 3284 | 44 | 340 | 162 | 6,000 | 3 |
| Diamond Bangel | 3285 | 60 | 400 | 161 | 12,000 | 4 |
| Dragonstone Bangel | 3286 | 70 | 600 | 523 | 35,000 | 5 |

Levels, XP, gem requirements, active prices, and enchanting tiers match the
corresponding complete Amulets. Dragonstone remains members-only.

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
paths, equipment UI and bank/preset views, legacy preset compatibility,
preserved IDs, crafting metadata and lack of wool, all former Amulet effect
getters, simultaneous Ring/Necklace/Bangel slots, hidden Medallion gates, and
client/server sprite/definition coverage.

The existing enchanting, equipment appearance, equipment hover, Entrana, and
jewelry runtime suites remain part of the relevant validation set.
