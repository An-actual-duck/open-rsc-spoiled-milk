# Covered Walking Preview

This is the first private step in the [active seamless streaming plan](seamless-world-streaming-plan.md).
It exercises ordinary walking across the middle of an already installed 3×3
surface scene. The server keeps the same center while the player stays within
the prepared walking area. The matched client keeps using that scene and
applies ordinary movement updates. At the outer margin the server still sends
a replacement scene through the existing path. Independent cell publication
and movement context changes remain follow-up work.

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
An exploratory native route toward the outer margin stopped before reaching
it, so a live exit remains unverified. A fresh Firefox WebGL startup compiled
the production resident shader and connected to the isolated preview socket.
That smoke check ended during sign-in startup.

## Reproduce the private preview

Build with `./scripts/build-client.sh` and `./scripts/build-server.sh` from the
worker checkout. Stage fresh disposable output with distinct unused loopback
ports for each mode.

```sh
python3 tests/myworld/seamless-streaming/prepare-native-preview.py preview \
  --tcp-port 43777 --ws-port 43778
python3 tests/myworld/seamless-streaming/prepare-native-preview.py control \
  --tcp-port 43779 --ws-port 43780 --control
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
client, logs the context at the seam, and checks both server endpoints.
