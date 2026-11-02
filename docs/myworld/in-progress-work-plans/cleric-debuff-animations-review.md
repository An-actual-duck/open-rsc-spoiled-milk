# Cleric curse effect animations

Status: ACTIVE checkpoint; production builds and focused native/server tests pass.
Broader focused regression gates and final compatibility receipts are pending.

All nine owner-selected curse effects are mapped by component kind. Tier 2
plays its first and second effects sequentially; Tier 3 plays all three.
Only useful accepted NPC applications emit the sequence through the existing
atomic sigil transaction. Curses are visible to nearby observers; recipient-only
blessings retain IDs86–100 and their existing visibility, assets and timings.
Unify and Greater Unify remain animationless. NPC pose IDs78–85 stay reserved.

| Effect | Canonical on-entity sheet | Source → playback frames | ID |
| --- | --- | ---: | ---: |
| Magical Allergy | general-buffs-debuffs/defense/mag-def-down.png | 11 | 101 |
| Muscular Atrophy | general-buffs-debuffs/melee/melee-down.png | 10 | 102 |
| Molecular Antipode | general-buffs-debuffs/bubble-pop/bubble-pop.png | 12 | 103 |
| Toxiphobia | general-buffs-debuffs/heart/heart-down.png | 9 | 104 |
| Agrizoophobia | poison-snake/poison-snake.png | 28 → 12 | 105 |
| Hematophobia | cut/cut.png | 15 → 12 | 106 |
| Expose Weakness | general-buffs-debuffs/accuracy/accuracy-down.png | 10 | 107 |
| Weak Legs | general-buffs-debuffs/break-defense/break-defense.png | 12 | 108 |
| Weak Constitution | general-buffs-debuffs/skull/skull-down.png | 13 | 109 |

Cumulative tier2/3 IDs: Saradomin110/111, Guthix112/113, Zamorak114/115.
The largest sequence is35 frames, below the unchanged64-frame per-ID slots.
The relocated projectile/mirror/static ranges remain non-overlapping and below
65536. Shared wire constants are generated from the canonical contract.
Schema5/client10056 remain unchanged: these are optional cosmetic IDs.

The36 owner PNGs are copied byte-for-byte, including the general effects pack
kept together. The committed asset manifest records every SHA256. Runtime
normalization changes only playback sampling, not PNG pixels, cell crops or
per-frame origin. The two longer Guthix sheets sample their complete first-to-last
source range into12 frames. All curse frames use2 client ticks (40ms), giving
each of those normalized effects480ms; native small effects retain360–520ms.
Visible art fits24px on the normal64px native canvas, drawn at64px to avoid
shrinking twice. Existing blessing and general combat frame speeds are unchanged.

Lazy NPC curse state shares the bounded queue mechanics with blessings: one
active and at most three pending god sequences, FIFO across gods. Same-god
refreshes coalesce and cannot erase a queued upgrade. The NPC packet path
explicitly admits curses; player/generic paths refuse them before detaching
normal effects. NPCs similarly refuse local-recipient blessing cosmetics.
Normal combat overlays, pending projectiles, phase state and special NPC poses
remain independent. Both software and native WORLD_OVERLAY capture come through
the actual drawNPC path, rather than the unused scene effect helper.

NPC removal clears transient visuals and is the authoritative death/despawn
boundary; reappearance/slot and type replacement, scene/area changes and
logout/reconnect clear pending curses too. Raw byte HP is deliberately **not**
used as a death signal: living256HP or512HP NPCs can serialize as zero. A native
fixture covers both legacy and typed HP zero aliases, followed by an actual NPC
removal packet that clears the queue. No HP protocol or gameplay changes occur.

Initial evidence: Java8 server/client builds PASS;206 actual nine-curse,
two-target/two-observer, resource/refusal and appearance-count assertions PASS;
1687 native source/embedded, full-frame sampling, actual NPC packet/draw,
24px footprint, queue, lifecycle and normal-overlay coexistence assertions PASS.
Final gate commands, artifact hashes and limitations will follow in the READY
update. No server/client sessions, listeners, accounts, owner maps, manager
source or public processes were used.
