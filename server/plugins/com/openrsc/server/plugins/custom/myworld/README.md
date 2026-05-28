# MyWorld Plugins

Use this package area for fork-specific gameplay code that should be clearly
owned by `MyWorld` rather than by stock OpenRSC or stock Cabbage content.

Suggested subpackages:

- `npcs`
- `itemactions`
- `skills`
- `quests`
- `misc`

Keep new fork-specific behavior here when possible so the custom work remains
easy to locate and reason about.

Current MyWorld-owned handlers now include:

- `server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/skills/runecraft`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/misc`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/npcs`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/itemactions`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/quests`

One compatibility bridge currently remains in `server/plugins/com/openrsc/server/plugins/custom/quests/free/PeelingTheOnion.java`
so older hook points can keep stable imports while the real quest
implementation lives under `custom/myworld`.
