# Oracle public hosting

Oracle became the authoritative Spoiled Milk public host on November 5, 2026.
The PC server is stopped and retained for rollback. Never run both copies as
public worlds against diverging player databases.

## Current deployment

| Setting | Value |
| --- | --- |
| Host / SSH user | `132.145.137.142` / `ubuntu` |
| Game port | TCP `43605` |
| VM | `VM.Standard.A1.Flex`, 1 OCPU, 6 GB RAM, ARM64 |
| OS / Java | Ubuntu 26.04.1 LTS / OpenJDK 17 |
| Server release tag | `v0.3.11` (interaction movement after shuriken combat) |
| Published client release | `v0.3.8` (Duskwind Bat projectile frame fix) |
| Game source | `53adcce6e09d7e2a74862cee989dac7355a2837a` |
| Client protocol | `10059` |
| Public service | `spoiled-milk.service`, enabled at boot |
| Persistent source | `/home/ubuntu/spoiled-milk/live/v0.3.11` |
| Guarded live path | `/tmp/spoiled-milk-live-main` |
| Git object repository | `/home/ubuntu/spoiled-milk/repository.git` |
| External state | `/home/ubuntu/.local/share/spoiled-milk/live` |

The guarded live path is a **bind mount** of the persistent source directory,
recorded in `/etc/fstab`. It is not a symlink or disposable `/tmp` source copy.
The service requires that mount. The source is a clean detached checkout of the
exact published tag; it is never a development workspace.

The service calls `scripts/run-hosted-server.sh` with
`MYWORLD_HOSTED_RELEASE_TAG=v0.3.11`. Existing exact-tag, clean-source, installed
map, generator, external-database, and occupied-port guards remain active.
`JAVA_TOOL_OPTIONS` caps the heap at 3 GiB. The unit uses `Restart=on-failure`;
a successful in-game shutdown exits normally and stays stopped.

The VM firewall and Oracle subnet security list permit TCP 43605. The client
uses the public IPv4 address directly; an address change needs a client endpoint
update. This deployment does not depend on the owner's PC staying online.

`v0.3.7`, `v0.3.9`, `v0.3.10`, and `v0.3.11` are server-only source tags with
no player release entries.
The website and updater remain on compatible client `v0.3.8`, which includes
the projectile correction, single-download ZIP and platform updater assets.
The current server patch needs no client changes, and protocol 10059 is
unchanged. Never publish a newer player release without its updater assets.

The v0.3.11 activation preserved the database and its 66 existing accounts,
with verified backup copies on Oracle and the owner's PC. The v0.3.10 source
is retained for rollback. After startup, all 84 offline interaction checks
passed against the installed Java 17 server JARs: pickup, door opening and NPC
conversation after active and completed shuriken combat. The class-loading
check confirms the movement handler comes from the installed core JAR. Receipts
are under `output/reviews/shuriken-interaction-movement/activation/` and
`/home/ubuntu/spoiled-milk/verification/v0.3.11/activation/` on Oracle.

The v0.3.10 activation preserved all 62 accounts and the existing database,
with verified backup copies on Oracle and the owner's PC. The v0.3.9 source is
retained for rollback. After startup, all 547 offline map checks passed against
the installed Java 17 server JARs, including the complete door packet, approach,
rank dispatch, plugin execution and open/walk/close sequence. Activation and
health receipts are under `output/reviews/slayer-door-plugin-context/activation/`
and `/home/ubuntu/spoiled-milk/verification/v0.3.10/activation/` on Oracle.

## Access and read-only status

The owner's SSH private key remains outside Git at
`/home/justin/.ssh/spoiled-milk-oracle`; never copy or publish its contents.
Use the dedicated known-hosts file and strict host verification:

```bash
ssh -i /home/justin/.ssh/spoiled-milk-oracle \
  -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile=/home/justin/.ssh/spoiled-milk-oracle-known-hosts \
  ubuntu@132.145.137.142 \
  'cd /tmp/spoiled-milk-live-main && ./scripts/live-status.sh'
```

Other read-only remote checks include:

```bash
systemctl status spoiled-milk --no-pager
systemctl show spoiled-milk -p ActiveState -p SubState -p MemoryCurrent
sudo journalctl -u spoiled-milk -n 50 --no-pager
systemctl list-timers spoiled-milk-backup.timer --no-pager
```

Logs may contain account identifiers. Save necessary raw evidence privately;
keep reports limited to the information needed for the task.

## Player data and backups

The production database is
`/home/ubuntu/.local/share/spoiled-milk/live/spoiled_milk_alpha.db`, mode 0600.
The live tree's `server/inc/sqlite/spoiled_milk_alpha.db` is a symlink to it.
Do not initialize a fresh database during startup or replace the authoritative
file with a private test seed.

`spoiled-milk-backup.timer` schedules a daily snapshot at midnight UTC with up
to ten minutes of random delay. Its helper is
`/home/ubuntu/.local/lib/spoiled-milk/backup-database.py`. It uses SQLite's online
backup API, checks integrity, and publishes a completed snapshot under the
external state's `backups/` directory. Existing backups are retained; no
automatic deletion policy was introduced. A fresh verified snapshot can be
requested without stopping the game:

```bash
sudo systemctl start spoiled-milk-backup.service
sudo journalctl -u spoiled-milk-backup -n 10 --no-pager
```

These automatic snapshots stay on the VM. The initial migration also retained
the stopped PC database and its final verified snapshot under
`/home/justin/.local/share/spoiled-milk/live/backups/` on the PC. The migration
copied all 41 tables and 60 accounts with matching hashes and row counts before
startup. The short ARM rehearsal and public login-path check passed; those are
not a load certification or a substitute for the owner's real login check.

## Future maintenance

The [public shutdown gate](../../AGENTS.md#public-server-shutdown-gate) applies
to Oracle. Release preparation, publishing, and a quiet player list never grant
shutdown permission. Obtain fresh explicit permission, take a current online
backup, have an administrator run the in-game `::update` warning, and wait for
the full countdown. Confirm the Oracle listener and game JVM have exited.
Do not signal or stop the service to bypass the warning. If a process remains,
inspect it and follow an explicitly authorized fallback; account for systemd's
restart behavior before using a process-level fallback script.

After the authorized shutdown, prepare the next clean detached release under
the persistent `live/` directory. Verify the exact published source/tag, server
artifacts, matching client/server map package, configuration, and external
database link. Update the persistent bind mount and unit's pinned release only
while the old service is stopped. Preserve the previous source and a consistent
database backup. Record the authorized deployment in the external state's
`cutover-approved.json`; its existence and the database are service conditions.
Never fabricate a shutdown/countdown attestation.

Validate the systemd unit and existing hosted preflight before starting it.
Then confirm remote `live-status.sh`, the public network/login path, and the
published client endpoint/updater. The local PC deployment scripts are not
Oracle orchestration and must not be used to activate the rollback copy.

Initial setup, host unit/helper copies, source and map validation, database
transfer receipts, and release checks are preserved locally under
`output/reviews/oracle-hosting/`. Player database copies remain outside Git.
