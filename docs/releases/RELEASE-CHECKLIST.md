# Limited Alpha Release Checklist

The first GitHub download is published as the prerelease tag
`v0.1.0-alpha.1`. The release assets are created locally and uploaded
manually.

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

## Build Assets

From the packaged revision:

```bash
./scripts/test.sh
./scripts/build-server.sh
./scripts/download-windows-jre.sh
./scripts/package-player-release.sh \
  --version v0.1.0-alpha.1 \
  --host game.example.org \
  --port 43605 \
  --windows-jre output/runtimes/temurin-17-windows-x64-jre \
  --assets-cleared
```

The packager writes ignored artifacts under
`output/releases/v0.1.0-alpha.1/`:

- `spoiled-milk-v0.1.0-alpha.1-java.zip`
- `spoiled-milk-v0.1.0-alpha.1-windows-x64.zip`
- `SHA256SUMS.txt`

The client jar embeds runtime PNG assets under `myworld-assets/`; player
archives do not need the loose development asset tree.

The Windows archive can be built on Linux because it only stages the downloaded
Windows runtime files. Local validation confirms the expected `runtime/bin/java.exe`
layout, endpoint config, archive contents, and checksums; actual Windows launch
verification still requires a Windows machine or a tester.

## Verify And Publish

1. Extract each archive into a clean directory and confirm it contains no
   `uid.dat`, saved credentials, local settings, server database, or inherited
   OpenRSC launcher.
2. Launch the Windows archive on Windows and the Java archive with Java 17+,
   then connect to the hosted candidate server.
3. Verify account creation, login, logout, and reconnect before publication.
4. Create GitHub release `v0.1.0-alpha.1` as a prerelease and upload the two
   zip files and `SHA256SUMS.txt`.
5. Download the uploaded files again and run:

```bash
sha256sum -c SHA256SUMS.txt
```

6. Link invited testers to the GitHub prerelease and
   [PLAYER-DOWNLOADS.md](PLAYER-DOWNLOADS.md).
