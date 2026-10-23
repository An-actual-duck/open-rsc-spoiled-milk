# Core-managed RSC Model Studio

Owner-approved October 22, 2026. This is a dedicated, independent 3D modeling
workspace, analogous organizationally to the separate 2D Sprite Trainer workflow.
It is not a new map editor, a Sprite Baker dependency or another manager AI.

| Role | Path |
| --- | --- |
| Manager/integration | /home/justin/rsc-model-studio |
| Exclusive implementation worker | /home/justin/rsc-model-studio-ai-1 |
| Local Git backup | /home/justin/.local/share/rsc-model-studio/git-backup.git |

Core may directly activate, review, test, merge, publish to the local backup and
recycle its worker. These duties run from the Model Studio repository, following
its AGENTS.md; no Git histories or production source trees are combined.

```bash
cd /home/justin/rsc-model-studio
git status --short --branch
./scripts/ai-manager.sh status
./scripts/ai-workspace.sh start ai-1 feat/descriptive-modeling-task
```

The worker checkpoints and hands off exact READY commits with its own wrappers.
The manager inspects that exact diff, merges with ai-manager.sh, tests, pushes
origin/main, then recycles ai-1. origin is a private local bare backup, not public
publication or off-machine protection. Never use Core-Framework-ai-N, toolkit
workers or Sprite Baker's worker for Model Studio implementation.

## Scope and ownership

Model Studio owns original low-poly Blender authoring recipes and validation
tools, editable local projects, reference notes, previews and export candidates.
The initial creative target is plain-stone architectural ruins matching the
Slayer Tower: broken pillars, fallen blocks and masonry. Workspace setup is not
approval of any particular model or its native compatibility.

Blender is a local, pinned tool. Keep its binaries/notices outside source control;
no new full modeling application, training system or cloud generation service is
required. Keep model/reference files and generated artwork in ignored local
directories, with provenance, hashes and explicit approval status. Ignored files
are user data, never cleanup targets. Neutral source/test fixtures may be tracked.
Game caches and 2009scape assets are not dependencies or redistributable inputs.

Authoring operates independently of game login, networking and databases. Runtime
collision, commands and gameplay properties are not inferred from the model's
appearance. Native export needs a separate reviewed decoder/encoder and material,
scale/origin/winding proof. OBJ/.blend interchange output alone is not game-ready.

Only a separate approved Core task may install an approved model into maintained
game/server content and add its game definitions. Core tests the result in-game;
the toolkit then discovers it through ordinary selected-installation discovery.
Do not put custom assets or model IDs into the editor, merge Model Studio branches
into Core, or mutate the public server as part of authoring.

## Initial delivery gates

Independent workspace/worker, recorded brief and style anchors, local Blender
diagnostics/setup, fresh-output script execution, editable model/preview/interchange
smoke outputs and manifests, neutral unit tests and preserved previous outputs.
Actual native conversion, production art approval and game integration are later
gates, not implicitly included in the workspace bootstrap.
