# RSC World Editor direct import

Core publishes a versioned target contract at
`server/world-builder-capabilities.json` and an active `primary` map
configuration at `server/world-builder-configs/primary.json`. The normal
tracked configuration selects the byte-identical packed
`Custom_Landscape.orsc` pair. It preserves legacy discovery and migration;
starting Core in this state does not enable an Editor package.

The compiled `spoiled-milk-layered-install-v1` mutation profile may replace
the primary configuration with a content-addressed layered selection. Core
accepts that selection only when all of the following are true:

- the configuration is the exact active schema-v1 `primary` contract;
- server and client paths select the same 64-character package fingerprint;
- both package trees have identical bounded inventories and hashes;
- the inventory fingerprint matches both destination paths;
- both manifests and all signed terrain/placement payloads pass the native
  layered-package loader; and
- the package satisfies the `spoiled-milk-editor-installed` replacement
  profile and exact configured manifest pin.

Malformed, incomplete, drifted, linked, oversized, or partially installed
packages fail server startup before world activation. The Editor owns its
transaction journal, offline lease, rollback, and restoration of the two
packed archives. Core never retires or rewrites those archives itself.

## Refreshing target evidence

Definition or packed-placement changes must refresh the committed contract:

```bash
python3 scripts/generate-world-builder-target-contract.py
python3 tests/myworld/test-world-builder-target-contract.py
```

The generator derives the catalog and canonical effective placement inputs
from Core's authoritative definition and location files. It also preserves the
documented NPC 67 roam-bound correction and collapses only byte-identical
legacy duplicate/no-op records. Review every resulting diff. CI reruns the
generator and fails if the committed evidence is not deterministic.

For a disposable manual runtime proof, generate a fresh layered package in a
new workspace, copy it under both configured fingerprint roots, switch only a
copied primary configuration to `layered`, and launch the copied server and
client. Never test an import against the live checkout or a running public
server.
