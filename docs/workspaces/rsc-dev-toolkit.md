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

## Current loader acceptance gate

Owner direction, October 11, 2026: the initial Linux classic editor passed its
packaged end-to-end private-game test, including discovered content. The next
assignment is cleaning Spoiled Milk's maintained map loader, not extending the
editor first.

Every loader change must work in Spoiled Milk's maintained server and matching
client. Synthetic tests and editor fixtures support this acceptance but cannot
replace it. Use an isolated private Spoiled Milk copy with disposable accounts
to verify startup, map/layer transitions, collision, placements, custom visuals
and gameplay, save/reload behavior and relevant failure/recovery paths. Preserve
original maps, custom content and account state; do not operate the public
server as part of this gate.

Before editor or general upgrader development resumes, the Core manager must
review the cleaned loader and record evidence that Spoiled Milk is operational
and no unresolved major correctness, preservation, authority or migration issues
remain. Review must be thorough and proportionate, not an open-ended cosmetic
rewrite. Minor nonblocking debt can remain with a documented reason. The owner
delegates this technical readiness judgment to Core; it is not a claim that no
future bug is possible or authorization for public deployment.

The implementation order and test checklist are maintained in
`/home/justin/rsc-dev-toolkit/docs/MODERN-LOADER-CLEANUP.md`; toolkit
`docs/README.md` and `docs/ROADMAP.md` must retain this Spoiled Milk-first gate.

## Active versus historical documentation

Toolkit `docs/README.md` is authoritative for new tool work. Core's previous
World Builder compatibility plans remain recovery/history references, not
current implementation assignments. Existing owner saves and private test
fixtures remain outside Git. The first milestone includes a migration strategy
for those saves without reusing their old embedded runtime.
