# Layered World Inventory v1

Manifest type: `layered-world-inventory`

Manifest schema: `layered-world-inventory-v1`

Coordinate model: `signed-layered-v1`

## Purpose

The inventory is a deterministic, non-relocating interpretation of recognized
legacy world sources. It is written only inside the Layered Maps workspace. It
is not a runtime map, Builder project, conversion acceptance, or export.

## Included sources

- Each terrain entry records its original archive name, legacy plane, signed
  level, sector X/Y, and uncompressed payload hash.
- Every recognized placement record retains non-coordinate attributes and
  names each known coordinate field explicitly.
- Every `ObjectTelePoints.xml` entry remains a directed edge with its command,
  original endpoints, layered endpoints, deltas, and geographic-anchor result.
- Java files detected by preflight remain unresolved source owners with hashes
  and detection signals. Slice 2 does not parse or rewrite Java.

## Lossless normalization

For each representable legacy coordinate, normalization applies
`legacy-packed-y-v1` and immediately reverse-encodes it. For each placement
record, the tool reconstructs the complete semantic JSON object from retained
attributes, normalized locations, and raw unresolved locations, then compares
it with the parsed source record.

Terrain entry names and transition endpoints receive the same checked reverse
encoding. Payload bytes are never rewritten; their SHA-256 hashes establish
entry identity.

An input outside the named codec is retained as a raw unresolved location with
an actionable finding. The tool does not guess a correction. Unsupported file
structure is refused before output is written.

## Output split

`world-inventory.json` is the complete machine manifest. Because a full world
contains tens of thousands of records, the separately versioned
`normalization-summary-v1` JSON report and `normalization.md` provide stable,
compact AI/operator views with fingerprints, counts, source summaries, all
transition edges, unresolved Java owners, and all findings.

No output is eligible for game import or final export under this schema.
