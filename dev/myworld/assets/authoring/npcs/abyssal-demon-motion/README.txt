ABYSSAL DEMON UPPER-BODY WALK MOTION — CANDIDATE V1

Owner request: directions 3 (side) and 4 (diagonal rear) need subtle head and
shoulder rocking. Directions 1, 2 and 5 are already satisfactory.

reference/approved-before.png preserves the exact production sheet.
art/ holds the layered Aseprite candidate and lossless PNG export.
The original sheet is retained as a hidden layer in the native project.
Only the stride poses in directions 3 and 4 were altered.

Rebuild with Aseprite:
  mkdir -p output/art/npcs/abyssal-demon/motion-v1
  aseprite --batch --script-param root="$PWD" \
    --script-param out="$PWD/output/art/npcs/abyssal-demon/motion-v1" \
    --script dev/myworld/assets/authoring/npcs/abyssal-demon-motion/author.lua

The Lua recipe rotates the upper body by opposite 2.3-degree offsets, blended
into the unchanged lower torso. It uses the source palette and native nearest
neighbour pixels. The walk sequence remains 0,1,0,2.

Status: owner approved November 2, 2026; promoted into the game runtime sheet.
The compact base is exported by author.lua and assembled without recoloring by
tools/myworld/PackAbyssalSpikes.java.
