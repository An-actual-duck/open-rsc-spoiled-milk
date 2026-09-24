# Abyssal Whip visual integration

Item 3350 now uses approved inventory art and the 18-frame held set from `dev/myworld/art-candidates/slayer-items/held-abyssal-whip-v2`.

- Client animation index 1128; server one-based appearance 1129. Appended without shifting existing equipment or NPC IDs.
- Main-hand combat equipment, neutral tint; approved inventory icon displayed at 40×22 within normal item canvas.
- First 15 frames use the upright handle and top-attached hanging lash. Original three attack frames are copied unchanged.
- Frame 11 alone uses approved width 66 rather than 64, retaining original grip coordinates and scale. Attack bounds remain 84×102.
- Existing combat effects, speed, requirements, recipe, and stats unchanged.

Validation: client build, `test-abyssal-whip-assets.cjs`, `AbyssalWhipVisualAudit.java`, existing unique equipment and leather/poison Java audits. Asset test verifies packaged JAR PNGs, offsets, bounds, original attack equality, and approved inventory icon.

In-game review remains necessary for eight-direction grip/body occlusion, walking sway, and attack appearance. Private test item: `::item 3350 1`.
