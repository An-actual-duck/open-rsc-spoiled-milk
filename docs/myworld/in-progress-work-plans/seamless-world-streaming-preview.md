# Covered Walking Preview

This is the first private step in the [active seamless streaming plan](seamless-world-streaming-plan.md).
It exercises ordinary walking across the middle of an already installed 3×3
surface scene. The server keeps the same center while the player stays within
the prepared walking area. The matched client keeps using that scene and
applies ordinary movement updates. At the outer margin the server still sends
a replacement scene through the existing path. Independent cell publication
and movement context changes remain follow-up work.

## Waiting for adjacent coverage

The next private server revision keeps the last acknowledged surface window
as its packet and visibility authority while an adjacent window prepares. It
can continue sending movement and normal updates within that available area.
A queued step cannot leave the installed window while the transition is
pending. At the last tile the server can fall back to sending a complete
context if prediction is still waiting.

The client still installs the eventual replacement scene through the existing
activation path. The reported long pause during sustained browser travel
remains an acceptance case for independent cell publication.

## Review route

On the private test world, start at Lumbridge `(120, 648, 0)` and walk to
`(109, 674, 0)`, then return. The former section boundary is `y = 672`.
Look for continuous walking animation and camera movement as you cross and
reverse. Check nearby actors and try a click or interaction during the walk.
Longer travel past the prepared area can still show a load.

The fresh native fixture sent ordinary walk requests on a matched local server
and client. Both modes reached the same server-confirmed endpoints. The
preview kept context sequence `1` on both crossings and the client sampled
zero retained-scene states. The control advanced `1 → 2 → 3` and sampled a
retained scene on both crossings. The repeat showed changing Java camera
positions, actor positions, walking phases, and scene frames on both legs.
All six shared nearby NPC indexes sampled at each crossing kept their client
actor instances. The fixture measures Java state and the presentation latch.
Displayed cadence, visual NPC motion, doorway interaction, and end-to-end
browser walking still need review.

The bounds fixture exercises the center-retention decision on all four sides.

## Outer coverage route

The signed surface walk from `(239, 609, 0)` to `(269, 609, 0)` crosses
the storage boundary at `x = 240` and the preview's scene handoff at
`x = 264`. The matched native client and server reached the destination in
one walk request. At the handoff the context advanced from sequence 1 to 2,
the fixture sampled the retained scene four times, and all 18 nearby NPCs
sampled on both sides kept their client actor instances. The longest gap
between fresh Java scene frames on the leg was 132 ms on the isolated native
display. That interval measures scene production, with display cadence
outside the fixture's scope.

The server and client had already prepared the predicted terrain and
structure on this run. The diagnostic trace recorded a hit on the prepared
terrain product at the transition. The scene handoff still went through the
client's context and baseline activation and held the previous frame.

The headless Firefox WebGL run waited for the initial world to report ready
before requesting the same walk. The private server confirmed arrival at
`(269, 609, 0)`. The Java client held the old scene for 1,062 ms across
`x = 264`, and the browser's ordinary-walking readiness transition blocked
world input for 1,813 ms. It counted 18 held frames and 37 chunk uploads
totaling 88,526,328 bytes. The longest gap in fresh Java scene production
on the leg was 1,040 ms. These timings describe one headless run and its
software-reported presentation state. They provide a reproduction of the
pause, with hardware display cadence and repeat measurements still pending.

## Reproduce the private preview

Build with `./scripts/build-client.sh` and `./scripts/build-server.sh` from the
worker checkout. Stage fresh disposable output with distinct unused loopback
ports for each mode.

```sh
python3 tests/myworld/seamless-streaming/prepare-native-preview.py preview \
  --tcp-port 43777 --ws-port 43778
python3 tests/myworld/seamless-streaming/prepare-native-preview.py control \
  --tcp-port 43779 --ws-port 43780 --control
python3 tests/myworld/seamless-streaming/prepare-native-preview.py outer \
  --tcp-port 43785 --ws-port 43786 --outer-coverage
```

Each run is under `output/tests/seamless-world-streaming/`. Start it with the
working directory and command recorded in `server-command.json`. Run
`Open Covered Walking Preview.sh` in that run directory to open its isolated
native client. The staging command prints the disposable account credentials.
To stop the private server gracefully, create a file called `STOP` in its
run directory. The process observes that marker and shuts itself down.

The [native fixture](../../../tests/myworld/seamless-streaming/CoveredWalkClient.java)
takes the staged `status.json` path and `true` for the preview or `false` for
the control. Start with the disposable account at the default spawn. Compile
the fixture against the built client JAR, then launch it from the staged
`client` directory with the fixture classes and `client.jar` on the classpath.
Supply the disposable password in `SPOILED_MILK_PRIVATE_WALK_PASSWORD` and set
`-Duser.home` to the staged `home` directory. The fixture uses the normal game
client, logs the context at the seam, and checks the server endpoints. For the
outer run, pass `true --outer-coverage` after the status path. Use a fresh
staged run because the private server saves the disposable character's
position on logout.

To stage the Firefox route, run the following with both browser fixture
ports `43639` and `43539` free.

```sh
python3 tests/myworld/seamless-streaming/prepare-browser-outer-preview.py outer-browser \
  --http-port 8201
```

The browser run is under `output/tests/browser-gameplay-spike/`. Start its
recorded private server, then serve its `web` directory with
`scripts/browser-gameplay/serve.py` on the same HTTP port. Open the URL in
`outer-coverage-fixture.json` and select Play. This fixture signs into its
disposable account, walks once initial graphics are ready, and reports the
crossing through the browser's `browserProof` data. The route emits
`outer-coverage:` events. The browser records the independent world input
gate and upload work in `browserProof.gpu.worldStreamSamples`.
