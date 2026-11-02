# Spoiled Milk visual assets

All maintained visual assets and their authoring records live in this tree.
Choose a role first, then the subject. Use lowercase directory names for new
folders; preserve supplied artwork filenames when provenance depends on them.

```text
assets/
  runtime/                 files read by the game and packaged into the client
    ui/                    Cleric, Magic, Prayer, summons, equipment and editor UI
    sprites/               equipment, inventory items, NPCs and world sprites
    animations/            moving projectiles, static projectiles and on-entity VFX
    legacy-animations/     explicitly retained specialty-effect fallbacks
    remastered-sprites/    generated ready frames; the user-facing option is hidden
  authoring/               editable masters, source inputs, recipes and provenance
    equipment/
    items/
    world/
    animations/
    remastered-sprites/
  references/              preserved reference exports and contact sheets
    items/
  CREDITS.txt              existing attribution record
```

## Where new work goes

- Put Aseprite/PSD masters, supplied originals, derivation scripts and source
  provenance under `authoring/<subject>/<asset>/`. Keep a short README and a
  manifest with source and approved-output identities. Existing project-specific
  historical approval records remain with their projects.
- Put generated drafts, comparisons, contact sheets and temporary exports in
  `output/art/<subject>/` or a complete `output/reviews/<topic>/` bundle.
  An output folder must never be the only retained copy of an editable master.
- Promote owner-approved game-ready PNGs to `runtime/`. Reference files are
  examples, not production definitions. Promotion preserves approved bytes and
  records the destination in the project's manifest.
- All UI artwork belongs under `runtime/ui/`; do not recreate `sprites/UI`,
  `sprites/ui`, or another parallel UI tree. Editor icons occupy `ui/world-editor/`.
- Runtime animation folders describe rendering behavior rather than a particular
  spell or NPC. Preserve the maintained animation catalog and explicit legacy
  exceptions. New effects go in `runtime/animations/`.
- Add references under `references/`. Read the item library's README and family
  notes before designing an inventory/ground icon; preserve full-canvas anchors.
- The client build reads only `runtime/`. Editable sources, incoming deliveries,
  project templates, reference sheets and review outputs are excluded.

## Existing pipelines

The remastered authoring workspace retains its descriptors, imported originals,
work frames and credits under `authoring/remastered-sprites/`. The existing
`./scripts/remastered-sprites.sh generate` command publishes only cataloged
ready frames and the manifest to `runtime/remastered-sprites/`. `--check`
verifies both the authoring catalog and runtime publication. The hidden option
and its default remain unchanged; this move does not change contributor rights.

Approved item/well projects live under `authoring/equipment`, `authoring/items`
and `authoring/world`. Their scripts and receipts point to the current paths.
Historical intake paths and versioned lineage remain historical records.

Native game archives remain in `Client_Base/Cache/video/`; those are game inputs,
not disposable downloads. Application bootstrap icons remain with the client
module in `Client_Base/src/res/`. These are deliberate module boundaries.

## Validation and credits

Run `python3 scripts/workspace-layout.py check` and the feature's maintained
asset/source/embedded-resource checks after promotion. Build the client to
verify the real packaged resources. Folder reorganization never authorizes
image resizing, palette changes, gameplay changes or a public-server restart.

The existing attribution record is [CREDITS.txt](CREDITS.txt). Pimen supplied the
added animation collection; Atelier Pixerellia supplied the selected Cleric
icons. Detailed contributor records remain beside the affected assets and in
`release/player/ASSET-SOURCES.txt`. Preserve those records when moving files.
