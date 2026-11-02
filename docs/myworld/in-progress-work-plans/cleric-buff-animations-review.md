# Cleric blessing recipient animations

Status: implementation checkpoint; final regression and compatibility gates pending.

Nine owner-selected cosmetics attach to named blessing components, independent of
spell display names and numerical potency. Tier 2 concatenates its first two
components; Tier 3 concatenates all three. Sap Blood's cleansing and restoration
share one visual. Curses and both Unify spells remain animationless.

| Effect | Sheet | Frames | Cosmetic ID |
| --- | --- | ---: | ---: |
| Hallowed Strikes | holy-sword | 25 | 86 |
| Quickened Pulse | holy-bells | 13 | 87 |
| Mending Flesh | holy-shield | 17 | 88 |
| Sap Blood | cleanse 2 | 17 | 89 |
| Barkskin | bark | 21 | 90 |
| Thorns | sword-clash | 11 | 91 |
| Unnatural Might | fist | 14 | 92 |
| Bloodrage | sword | 11 | 93 |
| Blood Hunger | lightning-buff | 17 | 94 |

Tier 2/3 sequence IDs are Saradomin 95/96, Guthix 97/98, Zamorak 99/100.
The largest sequence has 55 frames and fits the existing 64-frame slots. NPC
pose IDs 78–85 remain reserved. Legacy alias 73 now loads the byte-identical
canonical holy-sword sheet; alias 74 loads the byte-preserving bark rename.
The owner's 26 duplicate Holy VFX 03 files were removed; original Git history
remains available. The new lightning sheet is copied exactly from the owner's
verified input. No PNG pixels were edited.

Server appearance queues filter these IDs before computing packet record
counts. Only the effect target receives its buff animation; ordinary player
and NPC combat effects retain existing broadcast behavior. Actual application
commit remains inside the successful sigil transaction. No gameplay timing,
resources, registry slots, god replacement, power or duration rules changed.

The client has independent, lazy local-recipient playback: one active sequence
and at most one pending sequence per god, in FIFO order. Pending same-god
refreshes coalesce; a lower sequence cannot erase a queued upgrade. Different
gods cannot overwrite one another or ordinary combat overlays. Logout/book
reset, region transition and actor transient/slot reset clear cosmetic state.
The software draw and native phased replay share a WORLD_OVERLAY sprite
command. No temporary writes to actor combat-effect fields or anchors occur.
Sprites draw on their existing 64px canvas: visible artwork remains within
48px, with Barkskin 32px, avoiding a second shrink.

Schema 5 and client version 10056 are retained: animation metadata and the
one-byte effect carrier already support optional unknown IDs. The retained
pre-animation client compatibility test is included; final execution pending.

Initial evidence: server/client builds pass; existing 581 multi-Cleric slot,
replacement and cost checks pass; new actual production fixture passes 224
cast/resource/player+NPC packet assertions; actual source+embedded JAR/native
loader/client packet/queue/frame/software/native-capture fixture passes 2300
assertions. Full regression gates and exact artifact receipts will follow.

Run all Java gates serially under the Core heavy-build lock with Java 8,
512MiB server and 256MiB client heaps. No server startup, network listener,
account/database, owner-map or public process was used.
