# Browser login preparation protocol v1

This opt-in protocol authenticates a browser and prepares its actual account
destination before the character enters the world. Native clients and browsers
without the negotiated capability keep their existing login bytes. It is not a
gameplay timeout increase or a readiness bypass.

## Negotiation and framing

Only custom WebSocket connections can negotiate staging. The existing fixed
server configuration response is unchanged. Before sending credentials, a new
browser sends C2S opcode 254 with I32 magic `0x42535031`, U8 version `1`, U8 kind
`0`. A supporting server returns the same framed envelope. An unsupported server
must cause the browser to fail helpfully before authentication; the browser must
not fall back to ordinary cold login. Client support probing is bounded to ten
seconds.

After same-channel support, custom login appends the magic I32 and version U8
after its existing Android byte. Staging is disabled in world-builder mode and
requires the existing authoritative native layered terrain and static baseline
features. `-Dspoiledmilk.browserStagedLogin=false` disables negotiation.

Failed authentication retains its original raw response. Successful staged
authentication and settled detached account loading sends raw byte `26`, followed
only by framed pending messages. C2S simplified frame length includes opcode;
S2C length includes its two-byte length and opcode, as in existing encoders.
All I32 fields use the normal big-endian packet encoding. S2C 254 resumes its
ordinary equipment meaning only after COMMIT.

## Pending messages

Every 254 envelope begins with magic I32, version U8 and kind U8.

| Direction/kind | Remaining payload |
| --- | --- |
| S2C PREPARE 1 | token[16], digest[32], remaining hard lifetime I32; nine I32 values: logical X, logical Y, signed level, legacy X, legacy Y, presentation plane, plane width, plane height, distance between floors; newline-terminated UTF-8 world-space, projection ID and native manifest SHA256 strings, each at most 128 bytes |
| S2C COMPLETE 5 | token[16], digest[32], checked context sequence I32 |
| S2C COMMIT 2 | token[16], digest[32], original successful group response U8 |
| S2C ERROR 3 | controlled reason U8: 1 expiry, 2 invalid pending input, 3 preparation/admission failure |
| C2S READY 1 | token[16], digest[32] |
| C2S CANCEL 2 | token[16], digest[32] |
| C2S HEARTBEAT 3 | token[16], digest[32]; does not extend lifetime |
| C2S ACK 4 | token[16], digest[32], original receipt opcode U8, original receipt payload |

The only ACK opcodes are **154 terrain readiness** and **155 stage readiness**.
156 is not a terrain ACK. The ordinary authoritative parsers validate their
payloads and existing context/source receipts. Control messages reject trailing
bytes, wrong namespaces/versions, oversized receipts and gameplay opcodes. Stale
generation echoes have no effect. Token and digest comparisons use constant-time
byte comparison.

After PREPARE the server sends the account's ordinary generated **240 settings**
before **157 context**, then normal **143 static baseline** and **154 terrain
stage/pages** as required. These are the only ordinary outgoing opcodes allowed
while pending. There are no actors, player coordinates, items, announcements or
gameplay actions. COMPLETE requires the existing accepted terrain receipt,
symmetric halo readiness when enabled, and a completely sent static baseline.
The client additionally waits for actual prepared scene/GPU coverage before READY.
Detached static queries reuse the normal signed-coordinate/window filtering but
read only the spatial index's game-object view. They require a live authenticated
pending owner and do not create observer membership. Ordinary live visibility
queries retain their existing mandatory observer-membership checks.

## Account and scene ownership

A 300-second absolute lease reserves the authenticated database account across
native and staged attempts, including different IPs. There are at most 4096
reservations overall, 64 staged, and the configured per-IP staged bound. Expiry,
cancel, disconnect and validation failure release only their exact lease. After
admission, the normal world owns the IP tracker and the old lease timer cannot
disconnect the player. Closed detached snapshots have their persistence session
closed without saving, their timers cancelled and heavy candidate references
dropped. The plaintext password is cleared after initial verification/loading on
every success or failure path.

The candidate remains logged out and outside world/player-region membership.
`Player.setLoggedIn(true)`, its events, login plugins, world registration,
combat/AI, party/clan restoration and online notifications occur only at commit.
Existing settlement barriers and complete transactional account loads remain in
place. Spawn preparation uses actual first-login normalization and layered login
recovery, not a hardcoded surface destination.

The opaque SHA256 binding includes actual signed destination, native package and
manifest identity, projection ID, effective account settings, and deterministically
sorted exact relevant static scenery/wall contents. Unrelated changes elsewhere
do not invalidate it. A changed binding creates a fresh random token/generation
without extending the hard deadline.

READY reloads the complete account through the existing account-load barrier and
rechecks current authentication stamp, bans, shutdown state, live channel and exact
account ownership. On the game thread it rechecks capacity, destination/source/
settings/static binding and exact session ownership. Only checked static transport
state is copied into the fresh account. Changed binding refreshes preparation
instead of entering the world. COMMIT precedes all ordinary login packets. The
ordinary worldInfo packet still supplies the newly registered player index; the
client retains the prepared scene only for its exact matching initial worldInfo.

## Verification and remaining integration

`python3 tests/myworld/test-staged-login-server.py` compiles every changed server
class against an existing server dependency artifact and runs real reservation,
concurrency, pending packet grammar and detached cancellation assertions without
starting a server or touching an account database. Existing player-login policy,
character-creation and layered login-recovery tests also pass.
The fixture also queries a populated real spatial index with an unregistered
observer: scenery/walls are returned, actors/items are absent, other levels and
out-of-window objects are excluded, and observer membership stays absent. Exact
static identity is invariant to collection order and changes with real content.

The manager must still run a fresh combined client/server private fixture,
including cold preparation longer than 30 seconds, cancellation/expiry, normal
native login, account contention and post-COMMIT readiness. This worker has not
started, stopped or changed the public server. Live activation requires the
separate public-server shutdown authorization.
