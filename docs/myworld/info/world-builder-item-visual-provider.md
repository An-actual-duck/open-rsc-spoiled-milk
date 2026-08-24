# World Builder Item Visual Provider

Core exports a neutral, deterministic item-to-visual contract from its final
client definitions. The producer does not maintain a parallel item list. It
loads `EntityHandler` in members mode and records the definitions after
`MyWorldItemOverrides` and the final bangle visual pass have run.

Generate both artifacts with:

```bash
python3 tools/item-visual-provider/export-item-visuals.py
```

The exporter builds the client so packaged PNG validation uses the current jar.
After a known-current build, `--skip-client-build` avoids rebuilding while still
validating the jar contents. The checked-in outputs are:

- `tools/item-visual-provider/generated/item-visuals-full-v1.json` — every
  final client item definition, sorted by item ID.
- `tools/item-visual-provider/generated/item-visuals-3309-3317-v1.json` — the
  bounded compatibility selection for item IDs 3309 through 3317.

Both use schema version 1 and manifest type
`world-builder-item-visual-mapping`. The companion schema is
`tools/item-visual-provider/item-visual-mapping-v1.schema.json`.

## Portable provider bundle

Create the independent handoff beneath an output parent with:

```bash
python3 tools/item-visual-provider/export-item-visuals.py --bundle-output /absolute/handoff/parent
```

The stable top-level directory is
`/absolute/handoff/parent/world-builder-provider`. The exporter refuses to
overwrite an existing directory. The bundle contains the full and 3309–3317
manifests, the version 1 schema, both authentic and custom sprite archives, and
exactly the external PNG files referenced by the full catalog. Bundle manifests
replace Core/JAR asset locations with safe paths relative to the provider root;
consumption does not need the Core checkout or execute the client JAR.

`package-manifest-v1.json` lists every other file in sorted provider-relative
path order with its role, size, and SHA-256. It deliberately does not hash
itself. Output contains no timestamp, absolute source path, branch, or temporary
directory identity, so generation from identical inputs is byte-identical.

Verify a copied bundle offline with:

```bash
python3 tools/item-visual-provider/export-item-visuals.py \
  --verify-bundle /absolute/handoff/parent/world-builder-provider
```

Verification does not build or run the client. It rejects traversal, symlinks,
duplicate and case-colliding paths, undeclared or missing files, size/hash
mismatches, stale full/compatibility identities, unreferenced PNGs, invalid
asset references, corrupt archives, and archive entry-count/hash drift.

## Resolution contract

The mapping preserves `itemId` and `spriteId` separately. Consumers must never
use the item ID as a sprite ID.

The resolved base-source role follows the client precedence:

1. A valid `external-png:` specification resolves to its packaged PNG.
2. With the default custom-sprite mode, a logical `subspace:entry` resolves in
   `Custom_Sprites.osar`.
3. If the logical source is unavailable and `2150 + spriteId` exists, the
   authentic archive entry is an explicit fallback.

For every item, the manifest retains the logical `spriteLocation`, exact
`pictureMask` and `blueMask`, and all applicable source coordinates. Custom
entries retain both their base-archive entry hash and their spritepack override
key. Authentic entries include their actual archive ID—not merely the logical
sprite ID—and an entry hash when that archive entry exists. External PNGs
include the unmodified specification, resolved dimensions, source path,
packaged resource path, and byte hash. In portable manifests, `providerPath`
replaces the standalone `sourcePath` and `packagedResource` fields. Archive
`path` values and the external `providerRoot` are likewise relative to
`world-builder-provider/`.

Top-level provider input hashes identify the definition sources used to build
the final catalog. Archive hashes identify the two Core sprite providers. The
catalog and selection hashes are over canonical JSON data and do not contain a
timestamp, machine path, Git branch, or jar hash, so repeated exports from the
same inputs are byte-identical.

## Validation and compatibility limits

Generation fails with the item ID/name and missing source when a selected item
cannot resolve. It parses and validates every selected custom entry, tests the
authentic ZIP for corruption, and verifies every selected external PNG both in
the source tree and in the compiled client jar with equal hashes.

The contract describes deterministic **base** visuals. User-enabled
spritepacks can replace an exported `subspace:entry` at runtime, and the
optional remastered-sprite resolver can replace the resulting base frame.
Those configurable presentation layers are intentionally not flattened into
this mapping. A consumer that wants them must implement those providers as
separate, explicitly selected capabilities.

Not every custom-era item has an authentic archive counterpart. This is not a
packaging failure when its selected custom or external base source is valid.
For example, compatibility item 3316 uses custom entry `items:590` but has no
authentic archive entry at `2150 + 590`. Such cases are represented by a null
`authenticArchive` and must not be guessed or substituted.
