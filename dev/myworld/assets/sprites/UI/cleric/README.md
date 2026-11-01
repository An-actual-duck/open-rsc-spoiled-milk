# Approved named Cleric icons

The owner supplied and approved the named 16×16 RGBA PNGs for the twenty-spell
Cleric book. `PROVENANCE.json` records each actual supplied filename, canonical
production path, spell code, serialized PNG SHA-256, decoded RGBA SHA-256,
canvas, visible bounds and alpha counts. Its status and pending list are the
intake authority; incomplete intake is not a READY art handoff.

Source intake reads `/home/justin/Core-Framework/dev/myworld/assets/sprites/UI/cleric/`.
Owner files are never moved, rewritten, renamed or deleted. Production files
are byte-for-byte copies; there is no redraw, generation, recoloring, canvas
normalization, cropping, resampling or PNG re-encoding in this intake.
Only two supplied names need normalization in the worker:

- `guthix_respite.png` → `guthixs_respite.png`.
- `guthixs_vengence.png` → `guthixs_vengeance.png`.

Distinct spell keys are deliberate even where an approved image reuses older
pixels. The owner-provided Unify PNG replaces the former image in place;
Greater Unify has its own file. Unchanged historical byte matches are recorded,
without guessing that a matching image has a newly established artist/licence.

## Integration sequence

Core keeps the eleven old non-Unify PNGs temporarily so the current seventeen-
spell catalog still has its resources when this art branch is integrated first.
The manifest lists their exact filenames and preserved hashes. Complete intake
therefore has twenty canonical PNGs plus eleven temporary compatibility PNGs.
These compatibility files are not aliases for the new twenty-spell catalog.
The gameplay cutover switches to the canonical keys and retires those eleven
old resources in the same reviewed change. Historical artwork remains in Git.

This folder owns artwork and its receipts only. Server catalog/protocol,
spell effects, resource loading and spellbook interaction are separate gameplay
work. Zamorak's three hostile icons are included for that requested cutover;
they are not treated as deferred content in this intake.

## Provenance and review

The owner supplied the final choices. This intake adds no artist attribution
or licence grant. Existing `dev/myworld/assets/credit` and all other credit/
catalog records remain unchanged; their existing attribution is preserved.
The provenance receipt records its hash at intake and the old tracked icon
hashes from `508a059081009584d0174c4d2abf40d66c4d8c3d`. A comparison against all
3,010 PNG paths versioned at that base records exact-byte matching source paths
where found, verified against the original Git blobs by SHA-256. Unmatched inputs
remain owner-provided choices with no newly asserted artist or licence; absence
of a serialized-byte match is not a judgement about their appearance or rights.

Review images, native renders, build logs and source-read preservation evidence
remain in ignored `output/cleric-spell-icons/`. Contact sheets use native 1× and
integer nearest-neighbour review enlargement only; those sheets are not game
assets. The existing client may fit an icon into its internal native sprite
presentation canvas at load time; that existing rendering behavior does not
change the supplied source PNGs.

## Verification

`python3 tests/myworld/test-cleric-spell-icons.py --source-only` requires a
complete twenty-icon receipt and checks exact bytes, dimensions, RGBA pixels,
alpha, visible bounds and the explicit compatibility inventory.

After a real client build, run the same command without `--source-only` under
the maintainer heavy-build lock. It verifies every canonical source/JAR PNG is
identical, rejects unexpected resources, checks any remaining compatibility
bytes, exercises the actual native asset loader from source and embedded-only
locations, and renders icons with native game fonts. `--owner-source PATH`
adds a read-only comparison with the original supplied filenames.

With the gameplay cutover built, supply `--wire PATH` containing a fresh actual
PayloadCustomGenerator packet; the maintained schema-5 hex fixture is used when
available. This additionally checks exactly twenty definitions, all twenty
code/icon bindings and the actual mudclient embedded icon cache. A resource-
only pass does not claim that the newer gameplay catalog has been tested.

### Completed intake review — November 1, 2026

All twenty final owner inputs are present and visually inspected at native size
and integer zoom. Every input is 16×16 RGBA with binary transparent/opaque alpha;
there is no bad canvas, pixel alteration or PNG re-encoding. The source-only
receipt check, read-only original-file comparison and existing credit hash pass.
Ten inputs have verified exact-byte matches in the previously versioned curated
PNG inventory; ten have no serialized-byte match and retain the owner-supplied
provenance note. The complete transition inventory is 20 canonical +11 legacy.

A real Java 8 client build passes. Every canonical/compatibility source PNG has
identical packaged bytes. The actual asset loader's source versus embedded
native sprites and native-font review render pass 80 checks. Separately, the
new gameplay worker's real schema-5/client-10056 build and fresh 6,050-byte
PayloadCustomGenerator packet prove all twenty spell/code/icon bindings and
all twenty source-backed mudclient cache entries (162 checks including native
sprites/rendering). The two native render PNGs are byte-identical:

`81d86071614b7ef4064dbec753e2adeea3b392f02206642aea8f320493499937`.

This two-build proof uses `--wire PATH --catalog-client-jar PATH`; no JAR overlay,
worker-branch merge, copied gameplay source or fixture artwork is used. The art
build proves embedded bytes; the separate new gameplay build proves the new
catalog against the exact source PNGs. After Core publishes this art intake,
the gameplay worker takes that reviewed main and tests the unified actual JAR
when it switches catalog keys and retires the eleven compatibility files.
That cutover remains a separate gate, along with ordinary private gameplay;
this intake does not claim a server/client login or complete gameplay approval.

Commands and artifact hashes are preserved under `output/cleric-spell-icons/`
(`final-results.json`, `artifact-sha256.json`, source preservation receipt,
contact sheets and native renders). No server, owner map, toolkit, production
Java/catalog or global credit file was changed by the art worker.
