# MyWorld Prune Candidates

This repo still contains inherited OpenRSC/Cabbage files while the standalone extraction is in progress. Do not delete these blindly; use this list to review what should be archived, removed, or kept as inherited engine attribution.

## Server Configs

Archived under `docs/inherited-openrsc/server-configs/`:

- `docs/inherited-openrsc/server-configs/2001scape.conf`
- `docs/inherited-openrsc/server-configs/default.conf`
- `docs/inherited-openrsc/server-configs/openpk.conf`
- `docs/inherited-openrsc/server-configs/preservation.conf`
- `docs/inherited-openrsc/server-configs/rsccabbage.conf`
- `docs/inherited-openrsc/server-configs/rsccoleslaw.conf`
- `docs/inherited-openrsc/server-configs/uranium.conf`

Keep:

- `server/myworld.conf`
- `server/connections.conf`

## Root Legacy Docs

Archived under `docs/inherited-openrsc/`:

- `docs/inherited-openrsc/Linux Getting Started Guide.md`
- `docs/inherited-openrsc/MacOS Getting Started Guide.md`
- `docs/inherited-openrsc/Windows Getting Started Guide.md`
- `docs/inherited-openrsc/Raspberry Pi Getting Started Guide .md`
- `docs/inherited-openrsc/Commands.md`
- `docs/inherited-openrsc/CONTRIBUTING.md`
- `docs/inherited-openrsc/SECURITY.md`

Keep or rewrite:

- `README.md`

## Client Targets

Review before removing:

- `Android_Client/`
- `PC_Launcher/`

The PC client currently still uses `Client_Base/` and `PC_Client/`, so those are not prune candidates.

## Deployment Scripts

Archived under `docs/inherited-openrsc/`:

- `docs/inherited-openrsc/legacy-launchers/Deployment_Scripts/`
- `docs/inherited-openrsc/legacy-launchers/Start-Linux.sh`
- `docs/inherited-openrsc/legacy-launchers/Start-Windows.cmd`
- `docs/inherited-openrsc/legacy-server-launchers/ant_launcher.sh`
- `docs/inherited-openrsc/legacy-server-launchers/run_server.sh`

Keep active:

- `scripts/`
- root `Makefile`
- `docs/myworld/active-workspace.md`

## Database Seeds

Keep:

- `server/inc/sqlite/myworld_seed.db`
- `server/inc/sqlite/myworld_dev.db` as local dev state, not a canonical seed

Archived under `docs/inherited-openrsc/sqlite-seeds/`:

- `docs/inherited-openrsc/sqlite-seeds/2001scape.db`
- `docs/inherited-openrsc/sqlite-seeds/cabbage.db`
- `docs/inherited-openrsc/sqlite-seeds/coleslaw.db`
- `docs/inherited-openrsc/sqlite-seeds/openpk.db`
- `docs/inherited-openrsc/sqlite-seeds/openrsc.db`
- `docs/inherited-openrsc/sqlite-seeds/preservation.db`
- `docs/inherited-openrsc/sqlite-seeds/uranium.db`

## Next Safe Action

Move prune candidates into an archive folder or remove them in a dedicated cleanup commit after confirming no scripts, docs, or tests reference them.
