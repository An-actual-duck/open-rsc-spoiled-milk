Bat wing drops — initial native inventory/ground icon

Three separate, tradeable, noteable, non-stackable material items:
3467 Giant bat wing (NPC 43)
3468 Death wing wing (NPC 768; owner explicitly requested this repeated name)
3469 Stirge wing (NPC 882)
Palette variants share the approved shape and texture: Giant bat wing keeps
its gray membrane/brown supports; Stirge wing is light tan; Death wing wing
is dark gray/near black with subdued supports. Native canvas 48x32, visible wing 34x19.
Generated source is preserved in inputs/; export-native.lua performs nearest
native sampling, binary-alpha cleanup and mapping to six classic bat colors.
Editable native master: bat-wing.aseprite. Generated inputs are authoring-only.

Uncommon independent drops use the existing extra-drop pipeline, one item at
base 1/16 for full contribution, with its existing personal-contribution scaling
and five-percent minimum. Ordinary tables, remains, bat eyes and the Stirge's
independent 1/128 Sanguinary gland roll are unchanged; both materials may occur
on the same kill. No Slayer assignment is required. MyWorld-only registration.

Canonical chance: NpcDrops.BAT_WING_DROP_DENOMINATOR.
Server IDs use MyWorldItemId rather than growing the full ItemId enum, whose
initializer is already at the JVM method-size limit. No broad enum refactor.

Validation: production server/client builds; test-bat-wing-drops.py (37 server
checks, exact odds/boundaries/contribution, real ground delivery, independent
gland successes/failures, world gating, client flags and disk/JAR icons in both
sprite modes). Full Stirge combat/roster/supply regression also passes.
Owner review preview: output/art/bat-wing-drops/index.html.
No public deployment or saved-map changes.
