# Remastered Sprite Workspace

The owner has temporarily paused the optional remastered sprites, including
ThatKidBobo's contributions, while checking whether to continue using them.
The desktop and Android sprite choices are hidden, and the runtime availability
gate keeps overrides off even for previously saved or environment-enabled
settings. Sources, catalog descriptors and historical credits are preserved;
this pause does not change other renderer settings or project-made custom art.

This directory is the authored source of truth for optional remastered game
art. The original sprite archives remain canonical and every runtime override
must be able to fall back to its original sprite or animation frame.

Nothing in this directory is loaded merely because a PNG exists. A leaf set
must have a valid `set.json`, use `status: "ready"`, and pass the workspace
validator before it can enter the generated manifest. The client build packages
only those ready frame paths; selection still requires the player-side setting.

## Layout

```text
remastered-sprites/
  _schema/                  descriptor schema
  _templates/               files used by the scaffold command
  incoming/<artist>/<batch> temporary, untouched deliveries
  npcs/<name>/              NPC animation sets
  players/<name>/           player body-part animation sets
  equipment/<name>/         worn/held animation sets
  items/<family>/           inventory and ground sprites
  textures/<name>/          staged terrain/material textures
  ui/<name>/                interface sprites
  static/<name>/            other non-animated sprites
  manifest.json             generated; never edit by hand
```

Every independently reviewable set is a leaf directory containing:

- `set.json`: identity, targets, status, policies, and provenance;
- `frames/`: validated runtime-ready PNGs only;
- `work/`: incomplete, ambiguous, or incompatible PNGs that cannot ship; and
- `source/`: optional editable masters or original sheets.

Use lowercase kebab-case folder names and zero-padded frames (`00.png`,
`01.png`, and so on). Related frames stay together, for example
`npcs/demon/frames/00.png` through `17.png`. Raw cache IDs belong in
provenance/import metadata, not as the permanent folder identity.

## Commands

Run the wrapper from the repository root:

```bash
./scripts/remastered-sprites.sh inventory
./scripts/remastered-sprites.sh classify dev/myworld/assets/authoring/remastered-sprites/incoming/artist/batch
./scripts/remastered-sprites.sh scaffold npcs new-demon
./scripts/remastered-sprites.sh validate
./scripts/remastered-sprites.sh generate --check
./scripts/remastered-sprites.sh report
```

- `inventory` summarizes authored, ready, staged, and legacy incoming assets.
- `classify` examines `id-NNNNN.png` deliveries without moving or editing them.
- `scaffold` creates a standard leaf from `_templates/set.json` and refuses to
  overwrite an existing path.
- `validate` checks descriptors, PNGs, paths, frame numbering, archive targets,
  duplicate keys, provenance, and ready/work separation.
- `generate` deterministically rebuilds `manifest.json` and the Java catalog
  data used by the client; `--check` fails if either committed file is stale.
- `report` writes a local review report beneath
  `output/art/remastered-sprites/` (ignored by Git).

## Adding Or Updating Art

For a new or flat delivery:

1. Put the untouched batch in `incoming/<contributor>/<batch>/`.
2. Run `classify` and review every ambiguous result.
3. Run `scaffold`, or select an existing named set.
4. Move approved art into `frames/`; put uncertain or incompatible art in
   `work/`. Prefer `git mv` for tracked contributions.
5. Fill in `set.json`, including contributor, source batch/commit, original
   IDs, stable target, and policies.
6. Run `validate`, `generate`, and `report`.
7. Review the report/contact sheets, then checkpoint the coherent set. Remove
   any redundant incoming copy only after the move is accounted for.

For an already-classified set, update its numbered frames directly, validate,
regenerate, and review. Editing Demon must not require touching unrelated item
or texture descriptors.

## Status And Packaging Rules

- `work`: incomplete, ambiguous, dormant, or policy-incompatible; excluded
  from the generated runtime entries.
- `ready`: validated art in `frames/`; included in the generated manifest.
- `retired`: retained metadata for an intentionally withdrawn set; excluded.

Only PNGs declared by a ready descriptor may be packaged. The build always
excludes `incoming/`, `work/`, `source/`, `_schema/`,
`_templates/`, and local `output/`. Textures remain `work` until the separate
texture resolver and cache-invalidation phase is implemented.

If validation fails, fix the descriptor or move the questionable image back to
`work/`. Never bypass the check by marking unknown art ready or deleting its
provenance. A missing or invalid remaster must degrade to canonical art.

## Stable Targets

Sprites use `sprite/<lowercase-subspace>/<lowercase-entry>/<zero-based-frame>`.
Textures reserve `texture/<lowercase-entry>` and are not ordinary sprite
resolver entries. Authentic array offsets are import evidence only.

See [CREDITS.md](CREDITS.md) for the collaborator/batch index. Machine-readable
credit and provenance remain required in every `set.json`.
