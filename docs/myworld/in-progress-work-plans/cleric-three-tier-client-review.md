# Three-tier Cleric client review

Status: client implementation ready for coordinated Core review; gameplay and
combined server/client acceptance remain separate gates. No server launch,
login/account operation, map edit/import, asset creation, live operation or
prepared-update mutation occurred in this worker.

## Result

The desktop client uses schema 3 / client revision 10054. Expanded-NPC admission
still begins at its original revision 10053. The generated shared Cleric contract
is consumed unchanged; normal `scripts/build-client.sh` now checks its generated
copy alongside the NPC contract before compiling. Its threshold API arrived through the server worker's
reviewed protocol-only dependency commit `8ea771b31` (client cherry-pick
`945a300d4`). Server writer owns the matching serializer/admission and central
Cleric design/progress documents.

All eleven active blessings use stable protocol codes independently of displayed
positions. Unify remains code 1, the nine god blessings use 12–20, and Greater
Unify uses 21. The UI sorts by Worship requirement then stable code and maps text
rows, icon positions, hover, click, icon cache and cast packets through that
identity. Retired codes 0 and 2–11 cannot enter the active catalog. Ordinary Mage
behavior and the expanded NPC threshold are unchanged.

The catalog is decoded atomically with checked payload/string/count boundaries,
duplicate identities/resources/components, rank ordering/tier caps, numeric
component units, caster flags and exact packet consumption. Unsupported schemas,
truncation or invalid metadata clear the available Cleric book; they do not
leave a partially usable cast catalog. Item costs are immutable bounded vectors,
consumed as flat per-cast requirements. Both affordability and displayed cost
availability count **unnoted inventory only**; notes cannot enable a cast.

Typed numeric components drive applied-effect HUD details and selected-rank
preview. Regeneration is explicitly a total percentage of normal, healing states
its interval/pulse count, restoration states one point for **each drained
non-Hits stat**, and lifesteal says **strictly below** its stated threshold.
No client effect-strength formulas or holy-staff roster are introduced.

The cast preview reads the already transmitted combined Holy Power from equipment
stat index 7, without adding Worship a second time. The generated immutable
threshold curve and the spell's metadata rank count enforce tier caps. Old
short equipment packets mark this value unavailable instead of pretending to
know a cast rank. Actual applied HUD ranks always remain server-authoritative.

Each active god blessing appears as its single composite status, with the reused
approved icon, P badge, timer, name and separately wrapped component details.
The Worship guide includes actual resource names, all authored rank values,
thresholds, intervals, durations and areas. Long guide text continues onto bounded
rows rather than overflowing. Native-font book titles fit without truncation;
no new icons or animation assets are required.

## Meaningful validation

Java 8 desktop build compiled both active roots, `Client_Base/src` and
`PC_Client/src`, using a bounded 512 MiB heap with SerialGC. The final build log
is `/tmp/cleric-client-build-ready.log`.

The real `PayloadCustomGenerator` packet produced by the server worker's native
offline fixture was decoded by the actual client and passed through real
`PacketHandler.handlePacket(158, length)`. A recorded test-only hexadecimal copy
is retained in `tests/myworld/fixtures/cleric-schema3-payload.hex`; its source
checkpoint is `2ec2e3a23b14774cbee6c9cf546ddd417743f545`. The initial 2677-byte
artifact SHA-256 is `296fa6d909fd3b67035526978597cdab6055c32113c664b94132f35a0f2557b1`.
The decoder does not assume that byte length. The server writer's final prose
refresh is to be rechecked with `--wire` after combined integration.

The initial artifact passed **232 non-truncation behavior assertions** plus
**2677 separate truncated-packet cases**. These are distinct coverage categories,
not 2909 independent gameplay scenarios. Behavior includes sparse ordering,
identity-safe lookup, all resource-vector shortage cases, real unnoted versus
noted inventory admission, all threshold boundaries/caps, component values/units,
malformed counts/duplicates/flags, HUD composites/removal/expiry, bounded guide
text, actual opcode dispatch and old/full equipment-stat packets.

An additional **68 generic mixed-status checks** preserve the prior client
compatibility contract: legacy prefix with/without overflow, Slayer and potion
labels, mixed item/Cleric snapshots, counter badges and authoritative counters,
countdown rounding, unknown identity or missing-catalog fallback, malformed and
unsupported status trailers, logout/reconnect clear, expiry, visible-count bounds,
invalid item/timer filtering and saturation at the clock limit. Old accuracy,
Ward and Mend sample metadata is synthetic historical presentation input under
non-retired test codes; it does not enable those casts or assert retired balance.

Existing client gate mapping:

| Existing entrypoint | Active coverage |
|---|---|
| `test-cleric-spellbook-presentation.py` | Schema3 decoder, sparse identity/cost/preview rules and actual PacketHandler |
| `test-cleric-status-hud.py` | New composite HUD plus retained generic mixed-status fixture |
| `test-cleric-worship-guide.py` | Active eleven-spell numeric/resource/area guide and bounded wrapping |

The old server status-inventory fixture is being retained independently by the
server worker as `test-cleric-status-inventory.py`; server casting, combat and
resource transaction gates remain that worker's responsibility. Retired spell
balance/source-text assertions are deliberately replaced, not relabeled as
current behavior.

Reproducible commands from the worker root:

```sh
JAVA_HOME=/home/justin/jdk8u482-b08 \
PATH=/home/justin/jdk8u482-b08/bin:$PATH \
ANT_OPTS='-Xmx512m -XX:+UseSerialGC' \
bash tools/vendor/apache-ant-1.10.5/bin/ant -f Client_Base/build.xml compile

JAVA_HOME=/home/justin/jdk8u482-b08 \
python3 tests/myworld/test-cleric-three-tier-client.py \
  --wire /tmp/cleric-blessings-schema3.bin --render /tmp/cleric-client-render-proof

python3 scripts/generate-cleric-blessing-contract.py --check
```

The three existing entrypoints above also passed separately. Fixtures use bounded
128/256 MiB processes and no networking. Controlled `Unsafe` allocation is confined
to test fixtures so actual client methods can run without starting the application.

## Offscreen visual proof and remaining gates

Actual `mudclient` tooltip/HUD methods were rendered with the game's cached native
fonts and already approved icon files, without taking over the owner's desktop:

- `/tmp/cleric-client-render-proof/native-power-tooltips.png`
- `/tmp/cleric-client-render-proof/native-status-hud.png`
- `/tmp/cleric-client-render-proof/native-guide-row.png`
- `/tmp/cleric-client-render-proof/native-font-widths.txt`

All eleven book names fit the available 182 pixels; the longest measures 131.
Every real-resource-name guide line fits 410 pixels. The real-name guide proof
resolves through `EntityHandler`, including Silver sigil of Guthix and Silver Grace
sigil. Images and logs remain outside Git.

Native Windows execution, owner in-game visual/gameplay acceptance, server combat
integration, full release packaging and public activation are not claimed here.
Core will combine exact client/server handoffs and verify the matched pair before
publication. No intermediate client-only live deployment is appropriate.
