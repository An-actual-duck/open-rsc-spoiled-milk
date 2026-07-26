# Legacy God-Robe Retirement

## Status

Implemented. This retirement changes definitions and gameplay sources only. It
does not modify live player records.

## Retired Compatibility IDs

| Legacy ID | Former identity | Active replacement |
|---:|---|---|
| 388 | Monks robe top | Saradomin blessed wool robe top (3143) |
| 389 | Monks robe bottom | Saradomin blessed wool robe bottom (3144) |
| 607 | Guthix robe top / Druids robe top | Guthix blessed wool robe top (3148) |
| 608 | Guthix robe bottom / Druids robe bottom | Guthix blessed wool robe bottom (3149) |
| 702 | Zamorak robe top | Zamorak blessed wool robe top (3138) |
| 703 | Zamorak robe bottom | Zamorak blessed wool robe bottom (3139) |
| 807 | Saradomin robe top / Priest robe | Saradomin blessed wool robe top (3143) |
| 808 | Saradomin robe bottom / Priest gown | Saradomin blessed wool robe bottom (3144) |

The original request named five legacy pieces. The audit expanded the
retirement to the matching Saradomin top and both Guthix pieces because those
items used the same superseded robe-family model and remained live through the
same kinds of sources.

The legacy definitions remain at their stable IDs for save and protocol
compatibility. My World overrides label them as retired, remove their wearable
state and visual slots, and clear their Worship and defensive bonuses. New
content must not use these IDs.

## Acquisition Audit And Resolution

### NPC drops

- Priest (NPC 9) previously dropped 807 and 808 at weight 4 each. It now drops
  3143 and 3144 at the same weights.
- Monk of Zamorak variants 139, 140, and 293 previously dropped 702 and 703 at
  weight 4 each. They now drop 3138 and 3139 at the same weights.
- Druid (NPC 200) previously dropped 607 at weight 6 and 608 at weight 5. It
  now drops 3148 and 3149 at those same weights.

### Authored ground spawns

- IDs 388 and 389 at `(264,1402)` were removed from both the default and
  revision-27 ground-item maps.
- ID 702 at `(703,654)` was removed from the default ground-item map.
- These spawns were removed instead of replaced so persistent ground spawns do
  not bypass the intended active robe sources.

### Quests and content scripts

- Underground Pass now creates and recognizes Zamorak blessed wool robe IDs
  3138 and 3139 for Iban-disciple and temple-disguise interactions.
- Biohazard now recognizes Saradomin blessed wool robe IDs 3143 and 3144 for
  the Guidor priest disguise.
- Grape Empowerment now recognizes the Saradomin and Zamorak blessed wool robe
  sets rather than the retired Monk and Zamorak sets.

### Paths not found

No legacy IDs were found in ordinary shops, Crafting recipes, starter items,
quest-completion rewards, minigame rewards, item conversions, skill guides, or
other production definitions. Generic administrator item spawning remains an
administrative capability and is not an ordinary gameplay source.

## Existing Player Property

No inventory, bank, equipment, auction, preset, or database row is converted
by this change. Existing legacy holdings remain stored under their original
IDs but are inert until a later migration.

A future migration should convert quantities without creating a second copy:

- 388 and 807 to 3143
- 389 and 808 to 3144
- 607 to 3148
- 608 to 3149
- 702 to 3138
- 703 to 3139

That migration must cover inventory, bank and pinned-bank metadata, equipped
items, presets, auction listings, collectible returns, and any offline storage
before the compatibility definitions can be removed entirely. Noted status and
quantities must be retained. This implementation deliberately does not perform
that live-data operation.

## Guardrails

The focused retirement regression checks the generated effective definitions,
all ground-item map variants, NPC replacements, quest/content dependencies,
and the absence of legacy constants from runtime source paths. Existing Prayer
and quest tests also assert the active blessed-wool replacements.
