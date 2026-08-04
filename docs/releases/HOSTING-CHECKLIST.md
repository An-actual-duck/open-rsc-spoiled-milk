# Hosted Limited-Alpha Checklist

The player downloads connect to a single hosted server. Do not publish a
client archive configured for `localhost` or for the development database.

## Live Configuration

Use `server/myworld-host.conf` for the local-PC hosted alpha. It keeps the live
database separate from the development reset workflow and listens on the game
port expected by the release packager:

```yaml
database:
	db_name: spoiled_milk_alpha

world:
	server_name: Spoiled Milk
	server_name_welcome: Spoiled Milk
	welcome_text: Spoiled Milk limited alpha playtest.
	server_port: 43605
```

Keep `client_version: 10050` and `enforce_custom_client_version: true` aligned
with the released client. If the public port differs from `43605`, provide the
same port to `scripts/package-player-release.sh`.

## Initial Deployment

1. Configure the host firewall and router port forwarding for TCP `43605` only.
   Do not forward the websocket port unless a selected client explicitly needs it.
1. Run `./scripts/live-status.sh` and confirm no unsafe process owns public
   port `43605`.
1. From clean, published manager `main`, run
   `./scripts/deploy-live-main.sh` to create or update the fixed detached live
   worktree, its external-database link, and the exact manifest-addressed
   layered-world installation.
1. Start the hosted server from `/tmp/spoiled-milk-live-main` with
   `./scripts/run-hosted-server.sh`. The command refuses to run unless the
   checkout is detached, completely clean, and exactly at the published
   `spoiled-milk/main` commit.
1. Do not use `scripts/start-fresh.sh` for hosted play; that command recreates
   local development state.
1. Start a configured release client, register a test account, log out, restart
   the server, and confirm the account and character progress persist.

## Routine Operation

1. Run `./scripts/pre-field-test.sh` and compile the server build selected for
   deployment before replacing the hosted server binaries.
2. Keep the published client endpoint and hosted server port synchronized.
3. Record the git revision used for each hosted alpha build and each attached
   player download.
4. Treat packaging and publication as non-disruptive work. They never grant
   permission to stop or restart the public server.
5. Immediately before an authorized gameplay deployment, inspect live status
   and back up the live SQLite database while the server is still running.
6. Before initiating the shutdown, obtain fresh, explicit user permission for
   the current maintenance window. Then have an administrator run
   `::update [seconds] [reason]` and allow its full player-warning countdown to
   finish.

## Hosted Launch Safety

The public hosted server must run from a dedicated clean detached worktree at
an exact published-main commit, not from the manager, a worker branch, or a
dirty development checkout.

Approved live worktree:

```text
/tmp/spoiled-milk-live-main
```

Routine safety commands:

```bash
./scripts/live-status.sh
./scripts/deploy-live-main.sh
./scripts/run-hosted-server.sh
./scripts/stop-hosted-server.sh
./scripts/stop-hosted-server.sh --yes --shutdown-authorized --update-window-complete
```

Advancing and publishing manager `main` does not touch the live checkout. A
running server can therefore remain safely on an older published commit until
activation is scheduled. A release, deployment request, earlier approval, or
empty player count is not shutdown permission. At activation, first obtain
explicit permission to shut down the public server, back up the external
database, run the in-game `::update` warning, wait for its full countdown and
graceful exit, then deploy and start it again. Deployment refuses to switch
tracked files while the public port has a listener.

The stop script is a fallback for a JVM still present after that window, not a
replacement for `::update`. Its flags attest that the user authorized this
specific shutdown and that the warning window completed; the flags do not
create authorization. Never use direct signals or Ctrl-C to skip the warning.

Status separately audits the checkout, recorded launch commit, verified-live
marker, database symlink, database file descriptor, layered runtime mode,
package path, and manifest. A missing or legacy marker, a deleted or wrong
database descriptor, or a mismatched production package is a danger requiring
a controlled restart rather than an OK state.

Never stop a process that reports a deleted or wrong runtime database
descriptor until that open descriptor has been copied and integrity-checked.
The stop guard requires the explicit `--database-recovered` acknowledgement
after that separate recovery, in addition to the shutdown and update-window
acknowledgements.

The public port is only fully safe when it reports:

```text
Verdict:  OK LIVE HOSTED SERVER
Config:   myworld-host.conf
DB:       spoiled_milk_alpha
```

Private development uses `server/myworld.conf`, the `myworld_dev` database, and
port `43615`. It must never bind public player port `43605`.

The live database and backups belong under
`~/.local/share/spoiled-milk/live`, outside every Git worktree. The ignored
database path inside the live checkout must be a symlink to that external
database.

The hosted launcher has no safety bypass and never synchronizes generated
files. Private local testing uses `./scripts/run-server.sh` with the private
configuration, database, bind address, and port. The final production-profile
rehearsal uses `./scripts/run-server.sh --layered-production`.
