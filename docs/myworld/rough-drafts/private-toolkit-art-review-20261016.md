# Fresh private toolkit/art review — October 16, 2026

Status: source-built private copy ready, NOT public integration/release, toolkit
discovery acceptance or runtime/owner visual acceptance.

The existing approved-art topic was clean at `50985ee43`. A non-mutating
merge-tree check against published Core `fc7111368` found no conflicts; normal
merge `f3189faaa425c640e5641a27299d9854dbf0db81` preserves the full prior
approved art/combat work and current loader/runtime-contract sources. No main,
live deployment, owner configuration or prior review copy was changed.

Fresh durable root:
`/home/justin/spoiled-milk-toolkit-test-20261016-0FfM1xqG/game`.
The Git archive contains that coherent source plus approved asset sources, not
existing generated client/server JARs. Legacy archives, ignored outputs/logs,
old databases, UID and existing connection/profile files were excluded.
The private `toolkit-test.conf` is derived from the tracked topic's MyWorld
template, with a new SQLite database/name and loopback-only port43641;
websockets are disabled. A new connections file specifies SQLite only.
No owner credentials, accounts or UID were copied. The canonical seed has zero
players, logins and account/security rows; its exact bytes seed the new private
database. No game server/client has been launched during preparation.

The selected paired map is copied read-only from the maintained Core root,
not an owner-edited/imported project. Both roles independently reproduce all
1,789 file inventories, fingerprint
`f699e5a37709a0f127d676d91f07f64e256825bef6d65a7a4ccd13c8310f3b75`
and manifest `f3489503d2c323e669ab3004345271c6137a58698cc8c0bf0b0fb2587eb52b29`.
The source primary/installed-client selectors and full per-file hashes are
retained in the private root's sibling `evidence/` directory.

The read-only literal-delta inventory reports 60 changed item selectors,
no duplicate server IDs and zero absent approved PNGs. It is not an effective
runtime proof: the existing source-built asset fixtures remain required.
The contained-file inventory separately covers all 20 declared equipment
folders/352 frame files and all seven bespoke held/worn appearance assignments.
The 15 charge variants are accounted for separately from literal setter rows.
Naga's pending middle-pose candidate remains dev-only (`approved:false`);
the actual production image retains SHA256
`64b89c6235d7b988deff66c0cd627f91f39254d22e2830458f03b01c894d52e6`.

Next gates, after the exclusive JVM slot is returned: build matching server,
plugins/overlay and client from this private source; run existing material,
unique equipment, poison/leather, held-family/shears and relevant loader/combat
checks; record source/artifact/package provenance. No live startup is needed.
Private own-terminal server/client launchers are supplied for later owner game
testing, not executed as a hidden service. Updated toolkit installation and
fresh automatic source discovery are separate toolkit-repository operations;
no prepared catalog, per-ID editor roster or cross-repository merge is used.

## Source-built private-copy verification

The frozen private source is checkpoint
`4b0c8561c70aee1141f0dc81b6189e333d080dc5` (documentation after the coherent
merge; no additional production edits). Exact Git blob comparison verifies
5,219 authored source/runtime-contract/asset/dependency files, 64,713,902 bytes,
before and after builds with identical inventories. The maintained client build
and server generator-check/core/plugins/overlay builds pass from this copy only.
JDK8 Ant is capped384MiB, child JVMs768MiB. No existing game JAR was an input.

Eleven existing asset scripts pass: whip, antidote, held equipment families,
shears, bow, leather/poison, shield, material icons, pendant, dagger and staff.
Their 14 PASS assertions include actual client data/appearance catalogs,
packaged-only material PNG lookup, exact accepted pixels/anchors and append-only
families. An initial successful loop omitted the heap environment; the complete
loop was repeated with the explicit768MiB cap and passed. Both logs are retained.
These are focused art/build checks, not a new whole gameplay regression claim.

All 3,104 source-declared client asset payloads match their packaged JAR bytes;
both server/client map-runtime resources match the shared source contract.
The seed/private databases remain byte-identical and account-related counts
remain zero; no UID is present. The pending naga image was not activated.

Private artifact SHA256:

- Client: `672dae99e31519cefc19b8742658cc735951bf6f244a49b2b9a9c7f8e5c998a0`.
- Core: `fced307e8b08b9275272268d82893a4bae491ead89ae165c5913e35b9ea68447`.
- Plugins: `a9ec112dcb0b29eb4a60655c9b65319263f4e64425d8b60dae676e7f2325f04a`.
- Gameplay overlay: `2096a19ae8f9effff8006e4b3debedcfca824968b110786ea71650812f9bef88`.

The sibling `evidence/` directory retains build logs, capped asset checks,
literal/held-file inventories, before/after source hashes, map-pair inventories
and `artifact-closure-2.json` with reproducible verifier sources. The initial
artifact verifier's pathlib/Ant trailing-glob mismatch is preserved as a failed
diagnostic; it did not change assets/builds. No server/client was launched.
The exclusive JVM slot was explicitly returned to the manager for independent
toolkit diagnostics. Its first fresh source-read probe honestly refused the new
`Math.round(.../3.0F)` antidote price recipe before loading artwork; that is a
separate generic toolkit support gate, not a reason to alter game source or
provide an editor content roster. Actual owner editing/game review remains next.

This final handoff adds documentation only after the frozen private build.
Keep this art topic separate from Core main/live until manager/owner integration
approval; do not recycle it or treat private test readiness as public release.
