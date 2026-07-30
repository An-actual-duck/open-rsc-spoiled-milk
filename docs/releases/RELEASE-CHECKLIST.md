# Player Release Checklist

GitHub release assets are created locally and uploaded manually. Replace
`RELEASE_VERSION` below with the tag being published, for example `v0.2.6`.

## Publication Gates

- Retain Pimen's confirmation permitting distribution with source code
  available.
- Record the royalty-free source and applicable license for the prayer UI
  power, protection, enchanting XP, smithing XP, and crafting XP icons.
- Retain the recorded creator permissions and license notes for Pixerelia and
  the other credited UI icon sources used in `dev/myworld/assets/credit`.
- Confirm the packaged jar has no removed CraftPix icon or Phoenix/Kraken
  animation resources.
- Before public source distribution, confirm repository history does not
  contain removed CraftPix files; deleting them in a later commit is not
  sufficient once they have been pushed.
- Obtain a current Eclipse Temurin Java 17 Windows x64 JRE distribution and
  retain its included license and notice files. This can be prepared from Linux
  with `./scripts/download-windows-jre.sh`; Windows is only needed for final
  launch smoke testing.
- Decide the live host name and TCP port; do not publish packages targeting
  `localhost`.
- Commit and push the exact source revision being packaged, including any
  validated pre-release gameplay fixes.
- Fetch `spoiled-milk/main`, then package only from the clean manager `main`
  worktree with no merge, rebase, cherry-pick, revert, or bisect in progress.
  The packager rejects a dirty worktree, another branch, and any `HEAD` that
  does not exactly match the fetched `spoiled-milk/main` revision.
- Draft the Discord release announcement and release notes using
  [../community/DISCORD-POST-TEMPLATES.md](../community/DISCORD-POST-TEMPLATES.md).

## Build Assets

From the packaged revision:

```bash
git fetch spoiled-milk main
RELEASE_COMMIT=$(git rev-parse HEAD)
./scripts/test.sh
./scripts/build-server.sh
./scripts/download-windows-jre.sh
./scripts/ai-manager.sh release \
  --version RELEASE_VERSION \
  --host YOUR_PUBLIC_HOST_OR_IP \
  --port 43605 \
  --windows-jre output/runtimes/temurin-17-windows-x64-jre \
  --assets-cleared
```

Do not start the public server from the manager checkout. Packaging stops short
of changing the detached live worktree. When the new build should go live,
follow the separate authorization, warning, deployment, and restart procedure
in [HOSTING-CHECKLIST.md](HOSTING-CHECKLIST.md).

Use the same public host/IP and TCP port that your router forwards to the
machine running `scripts/run-hosted-server.sh`. Do not upload packages built
with a placeholder host.

The packager writes ignored artifacts under
`output/releases/RELEASE_VERSION/`:

- `spoiled-milk-RELEASE_VERSION-java.zip`
- `spoiled-milk-RELEASE_VERSION-windows-x64.zip`
- `spoiled-milk-RELEASE_VERSION-layered-world.zip`
- `SHA256SUMS.txt`

The client jar embeds runtime PNG assets under `myworld-assets/`; player
archives do not need the loose development asset tree. The layered-world ZIP
is a server/deployment artifact rather than a player download. It must contain
the exact package, generation report, validation report, source commit,
manifest hash, and package fingerprint.

Each archive records the exact packaged Git revision in
`game-files/SOURCE-COMMIT.txt`. It must match `$RELEASE_COMMIT`, captured
before the build. Keep that immutable value with the archive;
`spoiled-milk/main` may advance after the release is built.

The Windows archive can be built on Linux because it only stages the downloaded
Windows runtime files. Local validation confirms the expected `runtime/bin/java.exe`
layout, endpoint config, archive contents, and checksums; actual Windows launch
verification still requires a Windows machine or a tester.

## Verify And Publish

1. Extract each archive into a clean directory and confirm it contains no
   `uid.dat`, saved credentials, local settings, server database, or inherited
   OpenRSC launcher.
2. Confirm the archive endpoint files contain the public host/IP and port:
   `game-files/Cache/ip.txt` and `game-files/Cache/port.txt`.
3. Confirm `game-files/SOURCE-COMMIT.txt` in both player archives and
   `SOURCE-COMMIT.txt` in the layered-world archive match the full commit in
   `$RELEASE_COMMIT`.
4. Launch the Windows archive on Windows and the Java archive with Java 17+,
   then connect to the hosted candidate server.
5. Verify account creation, login, logout, and reconnect before publication.
6. Create GitHub release `RELEASE_VERSION` and upload all three ZIP files and
   `SHA256SUMS.txt`.
   Confirm `git rev-parse "RELEASE_VERSION^{commit}"` equals
   `$RELEASE_COMMIT`; the immutable tag, not a later `main`, is the durable
   provenance reference.
7. Download the uploaded files again and run:

```bash
sha256sum -c SHA256SUMS.txt
```

8. Link invited testers to the GitHub release and
   [PLAYER-DOWNLOADS.md](PLAYER-DOWNLOADS.md).
9. Post the short Discord notice in `#announcements`.
10. Post the longer Discord notes in `#release-notes`.
11. Update `#known-issues` if the release adds or resolves notable known
    problems.
12. Watch `#installation-help` and `#bug-reports` after publication for update
    failures, launch problems, and regressions.

## Separate Live Activation Gate

Completing this checklist, creating the GitHub release, or being asked to
release/publish/deploy does not authorize stopping or restarting the public
server. Leave it running on its current published commit unless the user gives
fresh, explicit permission for a public/live-server shutdown in the current
maintenance window.

After that permission, back up live data and use the in-game
`::update [seconds] [reason]` command so players receive the warning and full
countdown. The command schedules the graceful shutdown itself. Follow
[../workspaces/live-deployment.md](../workspaces/live-deployment.md) for the
deployment and guarded fallback procedure; never jump directly from release
publication to a signal or stop command.
