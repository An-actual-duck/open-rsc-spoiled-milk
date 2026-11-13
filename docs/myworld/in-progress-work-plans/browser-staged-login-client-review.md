# Browser staged login: client review handoff

The browser now negotiates the server's versioned pre-entry protocol before
credentials. Once authentication settles the real destination, it processes only
the authenticated account settings and checked static scene packets while the
client stays in `LOGIN` with no local character. It prepares the retained RAM
data and actual static WebGL scene before acknowledging readiness. Server COMMIT
is the only transition into the game; actors arrive through ordinary packets.

The codec matches the companion server protocol in
`browser-staged-login-server.md`: same-channel SUPPORT, five-byte capability
trailer, initial raw26, framed254 controls, 240 before157, static143/154, wrapped
154/155 receipts, exact token/digest and context fence, framed COMMIT. A server
without support fails helpfully before credentials after at most ten seconds.
Native/default-off clients retain ordinary login bytes.

Preparation uses the normal context/source decoder and normal terrain/baseline
parsers. It never fabricates a player receipt or changes the ordinary atomic
activation authority. Source hash, signed destination, projection and geometry
parameters must match the authenticated descriptor. Only the first exact
post-COMMIT worldInfo packet may retain that prepared scene; its actual registered
player index is still applied. A changed context or plane takes the original
load path. The input cover additionally requires a fresh completed GAME frame
after COMMIT; pending-frame readiness cannot dismiss it.

Each refreshed descriptor owns a new opaque Java token/digest and a new GPU
generation. Old commits and fences are rejected. JS receives only a numeric
render-generation owner and frame counters, never tokens, account details or
destinations. The single pending loop owns receipts, READY, five-second heartbeats
and cancellation; no competing packet writer was added. The hard300-second
deadline is never extended. Known WebSocket closure drains final buffered
messages, then produces IOException instead of waiting for that deadline.
Disconnection during synchronous CPU preparation is observed when that work
returns, before READY. A Stop discards the owning browser VM/transport.

Passive progress remains separate. Foreground progress can yield for painting;
hidden tabs record progress without awaiting potentially one-second-clamped
paint timers. Account roof preferences are applied before preparing geometry;
no roof preference is overridden to fit cooked data.

## Private integration route

The explicit private `fixture=staged-login-v1&stagedlogin=on` route selects
TCP43659 and WebSocket43559. It requires WebGL, benchmark admission, the complete
RAM/whole-zone backend and the render worker. A staged route without that
fixture/property is rejected in both JS and Java. Use the existing reviewed
public graphics options on HTTP8258, with these two additional private options.
The fixture does not freeze the camera or auto-login: enter the manager's
synthetic fixture account through the ordinary login UI. Public endpoints and
query admission remain pinned. The future public preset enables negotiation;
it must only be activated alongside the compatible server.

## Verification and remaining acceptance

`python3 tests/myworld/test-browser-staged-login.py --client-jar <intact-client.jar>`
compiles all changed core/bridge/bootstrap Java sources against an existing
dependency artifact, then runs real codec/state, signed/source binding,
fence/commit rejection, default-off behavior, one-shot scene retention, pending
EOF cancellation, closed routing, transport, JS render-owner/fresh-frame gates,
public/private frontend, hidden-tab timer, progress, readiness and Stop fixtures.
All thirteen commands passed. `git diff --check` passed. Workspace layout check
continues to report previously retained unclassified output-root entries; all
new evidence is under `output/tests/browser-staged-login-client/`.

These are CPU/source checks, not a full build or browser/GPU acceptance claim.
Manager acceptance still requires a fresh combined source-bound build/cook and
actual cold private Firefox login longer than30seconds, observation that the
account is absent from the world until COMMIT, matching retained static scene
and first self frame, roof preference variants and signed levels, disconnect/
cancel/retry and refreshed source/destination generation, plus normal native
login. No public server was changed and no full build or browser was launched
by this worker.
