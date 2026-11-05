# One player download

The website uses one URL:

https://github.com/An-actual-duck/open-rsc-spoiled-milk/releases/latest/download/spoiled-milk-client.zip

The archive contains one Spoiled Milk folder:

| System | Launch file | Java |
| --- | --- | --- |
| Windows 64-bit | Play Spoiled Milk.cmd | Included |
| Linux | play-spoiled-milk.sh | Install Java 17 or newer |
| macOS Intel / Apple silicon | Play Spoiled Milk.command | Install Java 17 or newer for the processor |

Extract the entire archive before launching. The README explains these choices.
The client, map and caches are shared; they are not duplicated by platform.

Normal ai-manager.sh release packaging creates the single-download archive
alongside the existing versioned Java and Windows archives. Keep those versioned
assets published: existing installed updaters still request them, including the
OS-specific updater scripts inside the combined download. The website only
needs the URL above. Each future release must upload spoiled-milk-client.zip
as well as the compatibility assets and checksums.

To consolidate an already-published release without rebuilding its game or map:

~~~sh
./scripts/ai-manager.sh release --version v0.3.5 \
  --consolidate-existing --assets-cleared
~~~

This operation requires clean published manager main, the existing local tag,
and its original Java and Windows ZIPs in output/releases/v0.3.5. It checks that
the game source, version and shared payload agree, preserves that exact game
payload, and adds the combined ZIP and its checksum. Existing archives are not
rewritten. A previous combined ZIP is never overwritten automatically.
The universal-player-package.json report records input/output hashes and game provenance.

Consolidation neither builds a new game version nor activates or restarts the
public server. Uploading the additional ZIP and updated SHA256SUMS.txt to the
existing release is a separate manager publication step. Native Windows and
macOS execution still require testing on those systems; POSIX launcher and
updater fixtures can run locally.
