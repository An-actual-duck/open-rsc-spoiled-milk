# Bat summon attack return visual

The owner requested that the bat summon's attack effect travel from its enemy
back to the bat. The current summon name is **Duskwind Bat**; this task does not
rename it or change its arrival/summoning animation.

## Change

Both moving-projectile paths (NPC victims and player victims) use the same
coordinate calculation. `SUMMON_BAT_VAMPIRISM` reverses its visual interpolation
and facing. Other projectiles keep their original interpolation. The 40-step
lifetime and sprite-frame clock retain the original progress, so the supplied
frames are neither reversed nor retimed.

The server still identifies the bat as the attacker and the enemy as the combat
target. This matters because projectile packets also trigger source attack poses:
reversing their combat endpoints would make an enemy such as a Naga play its own
attack pose when the bat drains it. There are no server, packet, damage, healing,
XP, collision, targeting, map, account or asset changes in this patch.

## Verification

`tests/myworld/test-bat-attack-return-visual.py` compiles a headless fixture against
the actual client JAR and verifies its production class origin. It checks return
positions on all three axes at launch, quarter, half, three-quarter and completion
points, including negative coordinates. Every other declared projectile retains
forward travel. Facing follows the return destination across camera rotations;
coincident/missing endpoints remain safe. The original authored frame clock is
checked separately from positional progress.

The fixture also feeds a real incoming NPC projectile packet to `PacketHandler`:
the bat remains the shooter, its enemy receives the incoming-projectile state,
and draining an enemy with an attack pose does not falsely animate that enemy.
Source checks ensure both player and NPC render loops call the tested calculation
for X, Y and Z and continue passing original lifetime to frame selection.

The existing lifesteal source check had a stale extraction range: it included the
adjacent Slayer-only contributor method and incorrectly reported summon damage
as combat-style XP. Its assertion now stops at the XP contributor method's own
closing brace. The assertion and production XP behavior are unchanged.

Build and final verification results will be recorded here before READY handoff.
The earlier client JAR failed the new runtime fixture because it lacks the corrected
travel function; that evidence is retained in the task's ignored test directory.

## Scope and remaining acceptance

No graphical owner review or public deployment has occurred. Players need a
rebuilt client for the visual correction; the server's combat semantics stay the
same. Integration/publication belongs to the Core manager, after its ongoing
selected-source editor capture has completed.

Evidence goes under `output/tests/duskwing-attack-return/` and
`output/reviews/duskwing-attack-return/`. The workspace layout check reports 76
pre-existing ignored legacy paths in this older worker slot, including the old
sprites tree. This task adds no new top-level output/art paths and does not prune
those preserved files; the findings are saved as `layout-inherited.txt`.
