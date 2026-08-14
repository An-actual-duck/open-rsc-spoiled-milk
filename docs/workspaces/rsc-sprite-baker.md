# Core-managed RSC Sprite Baker

RSC Sprite Baker is an ancillary product managed by the Core manager AI while
remaining an independent Git repository.

| Role | Path |
| --- | --- |
| Manager/integration checkout | `/home/justin/rsc-sprite-baker` |
| Exclusive implementation worker | `/home/justin/rsc-sprite-baker-ai-1` |
| Read-only initial compatibility input | `/home/justin/2009scape` |

The shared manager role is organizational, not a Git relationship. Core and
Sprite Baker have different `main` branches, remotes, handoffs, tests, and
releases. Never collect the Sprite Baker worker with Core's manager script and
never send Sprite Baker work to a `Core-Framework-ai-*` slot.

## Normal lifecycle

Run these commands from `/home/justin/rsc-sprite-baker`:

```bash
git status --short --branch
./scripts/ai-manager.sh status
./scripts/ai-workspace.sh start ai-1 feat/descriptive-task
```

The worker runs checkpoints and a final handoff from
`/home/justin/rsc-sprite-baker-ai-1`:

```bash
./scripts/ai-workspace.sh checkpoint -m "WIP: milestone"
./scripts/ai-workspace.sh handoff -m "Ready: task summary"
```

The manager inspects and merges the exact READY branch, tests, pushes Sprite
Baker `main`, and only then recycles the slot:

```bash
./scripts/ai-manager.sh merge feat/descriptive-task
git push origin main
./scripts/ai-workspace.sh recycle ai-1
```

The repository's wrappers reuse Core's hardened local collaboration scripts,
but force the Sprite Baker repository identity and `origin` remote. The
underlying guards therefore continue rejecting cross-project invocation.

## Asset boundary

Cache contents and generated sprites are not ordinary source dependencies.
Compatibility tests receive cache locations as local configuration, use
neutral fixtures for committed regression coverage, and keep generated output
outside Git. Any sprite selected for Spoiled Milk follows a separate review
and provenance decision before being copied into Core.
