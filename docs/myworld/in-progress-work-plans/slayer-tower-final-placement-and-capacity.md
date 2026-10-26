# Slayer Tower final placement and NPC capacity

Owner update, October 26, 2026: tower decoration is satisfactory. Preserve it;
remaining work includes capacity, passage bindings, dialogue and release assembly.

## Approved passage layout

The first Monster Slayer Associate restricts initial tower entry. The five later
associates restrict the stairs leading to the next tier, **not the room doors**.
Those associates stand in safe stair rooms separated from enemies by freely
accessible doors that close behind the player. Preserve that free access and
auto-close behavior; it is intentional protection during dialogue.

Enforce rank on actual upward stair use, independently of talking to the NPC.
Downward travel must remain available. Bind only the intended tower entrance
and upward transitions; do not change all doors/stairs of the same definition.
Confirm actual placements and paired destinations before binding. The inspected
map has no associate placements for IDs871–876 yet, consistent with the planned
Core placement/binding pass; do not claim the guards are already in place.

Preserve existing ranks, personalities, Veteran **button** terminology and entry
joining guidance. Most existing dialogue is compatible. Adjust Hero's "beyond
this door" to refer to upstairs, and later welcome lines as needed. Initial
entry may retain "Go on through, friend." No membership-token consumption.
Existing plugins still explicitly defer passage enforcement until map binding.

## Import failure investigated

Latest import checkpoint inspected read-only:
`/home/justin/RSC Development Toolkit/Projects/modern-session-bc36ad33-dc25-4129-b381-2827c0a793e4/saves/project-a398aa11-dedf-48cb-a1a3-ad9682cb3967`.
Baseline3803 NPC placements; effective edited map4047 after overlay/removals.
By signed level: -2:20, -1:1139, 0:2477, 1:206, 2:78, 3:38, 4:30, 5:30, 10:29.
Counts refer to individual world spawns, not distinct NPC definitions or the
number currently visible to a player.

Maintained `server/src/com/openrsc/server/model/world/World.java` allocates
`new EntityList<>(4000)`. The toolkit reads this scalar in
`ModernServerContent.runtimePolicy`; `ModernRuntimePreflight` correctly refuses
the larger candidate. Its Limits type additionally bounds this runtime family
to4000. Do not bypass the import check or silently drop placements.

Client `PacketHandler` currently decodes new NPC server indexes using12 bits;
`NPC_COORD_ADD_ENTRY_BITS` includes that width. Global client `npcsServer` has
5000 slots, while local visible arrays have500; these are separate budgets.
A registry bump alone beyond4096 would truncate/corrupt those spawn indexes.
A bump to4096 fits these4047 map spawns but leaves only49 slots for all dynamic
spawns. It is a stopgap, not the recommended completion of capacity work.

## Recommended next implementation slice (not yet implemented)

1. Review every global NPC index carrier in spawn/update/interaction/projectile/
   removal and reconnect/lifecycle paths. Choose a bounded larger registry and
   matching protocol/client storage, leaving headroom for temporary NPCs. Keep
   definition-ID width and local-visible budgets distinct. Preserve classic
   profile compatibility through an explicit supported protocol policy.
2. Implement and independently test paired Core server/client changes in a
   focused Core worker; no public deployment or shutdown is authorized here.
   Prove boundary indexes, no truncation/aliasing, target interactions, removal/
   reuse and spawn failure behavior. Exercise summons/quest spawns and actual
   startup with all4047 placements in an isolated private copy.
3. Update the toolkit's reviewed runtime adapter to recognise the actual new
   supported capacity contract, retaining rejection for incompatible clients.
   Improve the rejection to report requested count, supported count and next
   action in ordinary language. No target-specific content roster.
4. Make a fresh import preview, verify backup/rollback behavior and run a private
   game test before owner retest. Preserve every authored placement and map file.
5. Place/bind associates and adjust stair dialogue against the final authored
   coordinates after capacity is resolved. Then final Slayer assembly/testing.

No game implementation, map import, public operation, guard placement or dialogue
change is claimed by this investigation. Do not mark the overall tower complete
or begin deferred effect-standardization merely because decoration is complete.
