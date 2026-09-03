# Live Deployment

The live server uses `/tmp/spoiled-milk-live-main`, but that worktree is
detached at an exact published `spoiled-milk/main` commit. It does not own the
`main` branch and never receives development edits.

Persistent state lives outside Git:

```text
~/.local/share/spoiled-milk/live/spoiled_milk_alpha.db
~/.local/share/spoiled-milk/live/backups/
```

The live worktree's ignored database path is a symlink into that data root.
Removing or recreating a worktree therefore cannot remove player data.

## Status

```bash
./scripts/live-status.sh
```

A running server may safely remain on the previous published commit while the
manager advances `main`. Status should distinguish that expected state from a
wrong worktree, configuration, database, port, or unexplained dirty checkout.

## Non-Disruptive Release Preparation

From clean, published manager `main`:

```bash
./scripts/ai-manager.sh release-check
```

The authoritative Spoiled Milk release command creates three ZIP artifacts:
the Java client, bundled Windows client, and the server-side layered-world
package. `SHA256SUMS.txt` covers all three. The layered artifact carries its
generation report, package validation, source commit, manifest hash, and
package fingerprint; it is not installed by players.

Building, tagging, uploading, publishing, and advancing manager `main` do not
authorize a public-server shutdown. They also do not alter the detached live
checkout. The current server can continue running its previous published
commit while a release is prepared and published.

Make an online SQLite backup under the external `backups/` directory while the
current server is still running. If the user has not explicitly authorized a
public/live-server shutdown for the current maintenance window, stop here and
report that live activation is waiting for permission.

Do not infer shutdown permission from a request to release, publish, deploy,
or "make it live," from a previous maintenance window, from general approval,
or from a report that no players are online.

## Authorized Live Activation

The in-game admin command is:

```text
::update [seconds] [reason]
```

It immediately informs connected players, displays the system-update
countdown, and schedules a graceful shutdown when the countdown expires. The
default is 300 seconds. Because the command initiates shutdown, the AI must
receive fresh, explicit permission to stop or restart the public server
**before** asking an administrator to run it or issuing it by any available
mechanism.

After that permission is received:

1. Confirm the online database backup and `./scripts/live-status.sh` are safe.
2. Have an administrator run a suitably timed command such as
   `::update 300 Deploying RELEASE_VERSION`.
3. Let the full warning countdown complete. Do not accelerate it with a
   signal, Ctrl-C, or the stop script.
4. Confirm the public listener and live JVM have exited.
5. Deploy and restart from the fixed live checkout:

```bash
./scripts/deploy-live-main.sh
cd /tmp/spoiled-milk-live-main
./scripts/run-hosted-server.sh
```

Deployment deterministically regenerates and validates the exact reviewed
Spoiled Milk layered package, then installs it under the external live-state
root in a manifest-addressed directory before changing the detached checkout.
Hosted startup refuses a missing, changed, wrong-version, wrong-count, or
wrong-fingerprint package. The hosted configuration explicitly enables the
accepted Player-location, spatial, protocol, native-package, residency,
readiness, prediction, symmetric-residency, and atomic-activation gates using
the `world-builder-installed` profile.

If the production-profile rehearsal or later live observation finds a
layered-runtime regression, the guarded runner retains an explicit start-only
rollback:

```bash
./scripts/run-hosted-server.sh --legacy-map-rollback
```

This disables the complete layered authority chain together and records
`legacy-rollback` in the launch attestation. It does not authorize or perform a
shutdown; the same permission, warning, backup, and stopped-port requirements
still apply before using it.

Normally `::update` performs the stop, so `stop-hosted-server.sh` is not
needed. If the JVM remains after the entire authorized update window, inspect
it first. The guarded fallback requires attestations that both gates were
actually satisfied:

```bash
./scripts/stop-hosted-server.sh \
  --yes \
  --shutdown-authorized \
  --update-window-complete
```

Those flags do not grant permission. They may be passed only when the user
explicitly authorized this shutdown and the in-game update warning window has
completed.

If `live-status.sh` reports a wrong or deleted runtime database descriptor,
do not stop the JVM yet. Copy that open descriptor to the backup directory and
integrity-check it first. Only after confirming recovery may the stop command
also include `--database-recovered`; the guard otherwise refuses a shutdown
that could discard an unlinked SQLite inode.

Deployment refuses to change the live worktree while anything is listening on
the public port. It validates the external-database link before and after
switching the detached checkout, so an old JVM can never run over a mixture of
old code and newly deployed definitions.

Run `./scripts/live-status.sh` afterward and confirm both checkout and runtime
report the same current published-main commit, a `verified-live` launch
attestation, and the external database file descriptor.

Ordinary worker activity, manager merges, and pushes never alter the deployed
tree. That separation is what allows the hosted server to remain uninterrupted
while several AI sessions work. Only explicitly authorized activation may use
the warning/deploy/start window.
