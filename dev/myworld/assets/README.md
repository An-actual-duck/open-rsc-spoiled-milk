# MyWorld Assets

This directory is the source of truth for distributable MyWorld visual assets.
Use `output/` for generated exports, diagnostics, and manual review images.

## Layout

- `sprites/equipment/`: player equipment sprite sources and numbered frame sets.
- `sprites/UI/prayer/`: royalty-free prayer UI icons used for power, protection,
  and faction skill-XP prayer tiers.
- `sprites/UI/summon/`: summon menu icons named after their display names.
- `sprites/UI/magic/`: magic menu icons named after their display names.
- `animations/`: runtime animation frame folders. The current client expects the existing category folder names, such as `On Enemy`, `On Player`, and `Projectiles`.

The client searches these folders before the legacy `output/` paths, so new assets should be placed here first.

## Credits And Provenance

- Pimen supplied the included added animation assets and has confirmed
  distribution with source code available.
- The project author created additional original sprites, including the fishing
  rod equipment sprites.
- Held shears equipment sprites are planned author-created work and are not yet
  part of the current asset set.
- Prayer UI power, protection, enchanting XP, smithing XP, and crafting XP icons
  are sourced from a royalty-free repository.
- Summon UI icon provenance is tracked in `dev/myworld/assets/credit`.
- Magic UI icon provenance is tracked in `dev/myworld/assets/credit`.
- CraftPix-derived icons and Phoenix/Kraken animations were removed because
  their redistribution terms do not permit extractable downloadable assets.

Source links:

- Pimen: https://pimen.itch.io/
- Pixerelia: https://pixerelia.itch.io/
