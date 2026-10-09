# Core-managed RSC Development Toolkit

Owner-approved organizational and architectural reset, October 9, 2026.

The Core manager also manages `/home/justin/rsc-dev-toolkit`. This is a fresh
standalone Java development-tool repository, not a new independent manager AI
and not an embedded Spoiled Milk tool. Its initial product is an offline map
editor; reviewed loader upgrades and broader tooling follow separate milestones.

## Ownership

- Integration checkout: `/home/justin/rsc-dev-toolkit`, branch `main`.
- Initial dedicated worker: `/home/justin/rsc-dev-toolkit-ai-1`.
- Additional toolkit workers may be created only for bounded independent tasks.
- Core game changes use Core workers; toolkit changes use toolkit workers.
- Read `/home/justin/rsc-dev-toolkit/AGENTS.md` and `docs/README.md` before work.
- Do not merge toolkit Git history into Core or vice versa.

The previous Editor/runtime managers and their workers are retired from new
assignments. Preserve their branches and project data. Do not delete folders,
retire live game loaders, undo map imports or change the public server as part
of this reset. Historical instructions are not the new architecture's rules.

## Lifecycle

Run repository wrappers from the toolkit checkout, never from Core:

```bash
git status --short --branch
./scripts/ai-manager.sh status
./scripts/ai-workspace.sh start ai-1 docs/salvage-audit
```

Workers checkpoint and hand off exact commits with the same guarded workflow
used by the Core-managed Sprite Baker. The wrappers borrow workflow mechanics,
not editor code or gameplay dependencies. Review tests before merge; publish
toolkit `main` before recycling. The initial `origin` is a local bare backup at
`/home/justin/.local/share/rsc-dev-toolkit/git-backup.git`, not a public release
or off-machine backup. External publication requires a deliberate later choice.

## First assignment

Read-only salvage audit and a viewport integration experiment plan. Inventory
reusable formats/decoders/tests versus game-bound code. Do not copy legacy code,
assets or rule trees; do not implement a runtime upgrade or start a game server.
Return evidence, provenance questions and a small offline prototype proposal.

## Active versus historical documentation

Toolkit `docs/README.md` is authoritative for new tool work. Core's previous
World Builder compatibility plans remain recovery/history references, not
current implementation assignments. Existing owner saves and private test
fixtures remain outside Git. The first milestone includes a migration strategy
for those saves without reusing their old embedded runtime.
