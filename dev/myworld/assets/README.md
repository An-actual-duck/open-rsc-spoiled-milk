# MyWorld Assets

This directory is the source of truth for distributable MyWorld visual assets.
Use `output/` for generated exports, diagnostics, and manual review images.

## Layout

- `sprites/equipment/`: player equipment sprite sources and numbered frame sets.
- `sprites/UI/prayer/`: royalty-free prayer UI icons used for power, protection,
  and faction skill-XP prayer tiers.
- `sprites/UI/summon/`: summon menu icons named after their display names.
- `sprites/UI/magic/`: magic menu icons named after their display names.
- `animations/`: preferred animation library grouped by `projectile-moving`,
  `projectile-static`, and `on-entity`. Promoted runtime effects use generic
  catalog keys such as `water-basic`, not names tied to one spell or NPC.
- `legacy animation folder/`: temporary migration fallback for explicitly
  unmigrated specialty effects. Do not add new effects here.
- `remastered-sprites/`: contributor-friendly authored workspace for optional
  sprite and texture improvements. Named leaf sets separate runtime-ready
  frames from incoming, source, and work files; see its README before adding or
  moving art.

The client loads promoted projectile spritesheets from the new animation
catalog and consults the legacy folder only for an explicit fallback. PNG
spritesheets are runtime inputs; editable files such as `.aseprite` sources are
kept for asset work but are not packaged into the client.

## Credits And Provenance

- Pimen supplied the included added animation assets and has confirmed
  distribution with source code available.
- The project author created additional original sprites, including the fishing
  rod equipment sprites and the Bangle/Medallion item and wrist-slot graphics.
- Held shears equipment sprites are planned author-created work and are not yet
  part of the current asset set.
- Prayer UI power, protection, enchanting XP, smithing XP, and crafting XP icons
  are sourced from a royalty-free repository.
- Summon UI icon provenance is tracked in `dev/myworld/assets/credit`.
- Magic UI icon provenance is tracked in `dev/myworld/assets/credit`.
- Cleric UI icon provenance is tracked in `dev/myworld/assets/credit`.
- ThatKidBobo supplied the initial remastered sprite, item, and texture batch.
  Detailed mappings and staging status are tracked in
  `remastered-sprites/CREDITS.md` and each leaf `set.json`.
- CraftPix-derived icons and Phoenix/Kraken animations were removed because
  their redistribution terms do not permit extractable downloadable assets.

Source links:

- Pimen: https://pimen.itch.io/
- Pixerelia: https://pixerelia.itch.io/
