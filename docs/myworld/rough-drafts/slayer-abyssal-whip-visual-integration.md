# Abyssal Whip visual integration

Item 3350 now uses approved inventory art and the rebuilt 18-frame held set from `dev/myworld/art-candidates/slayer-items/held-abyssal-whip-v3`.

- Client animation index 1128; server one-based appearance 1129. Appended without shifting existing equipment or NPC IDs.
- Main-hand combat equipment, neutral tint; approved inventory icon displayed at 40×22 within normal item canvas.
- First 15 frames use a slender forward-hanging lash, with transparent fist gaps registered to original grip coordinates. Three new attack frames curl back, unfurl and snap forward.
- Runtime uses symmetric transparent padding to cancel the renderer's frame-centering shift. Frames 9/10/11 have widths 68/80/94; final attack is 126. All other widths remain 64 movement / 84 combat and height 102. PNG artwork is unchanged from the candidate; padding is represented by bounds and offsets.
- Whip-specific mirrored hand offset calculation uses canonical 64/84 bounds rather than padded widths. Other equipment is unaffected.
- Existing combat effects, speed, requirements, recipe, and stats unchanged.

Validation: client build, `test-abyssal-whip-assets.cjs`, `AbyssalWhipVisualAudit.java`, existing unique equipment and leather/poison Java audits. Asset test verifies packaged JAR PNGs, offsets, bounds, transparent fist centers, padding/anchor cancellation for both orientations, and approved inventory icon.

In-game review remains necessary for eight-direction grip/body occlusion, walking sway, and attack appearance. Private test item: `::item 3350 1`.
