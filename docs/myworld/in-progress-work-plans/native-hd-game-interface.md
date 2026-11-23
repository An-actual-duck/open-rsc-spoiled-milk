# Native 1080p game interface

The browser and the standalone client must be able to render and pick the world on a 1920×1080 game surface. The interface needs its own text, spacing, and control sizes at that resolution. A browser layout measurement or a 1920×1080 screenshot alone cannot establish the game's render dimensions.

Use the existing Classic bitmap lettering and approved assets for development. Size each interface around its content and anchor it to the viewport. Derive drawing, hover regions, mouse clicks, and scroll regions from the same layout. Keep the current world-loading choices independent of display mode. Asset decisions are pending.

## Interface coverage

| Area | Surfaces to migrate and exercise |
| --- | --- |
| In-game HUD | Chat history, text entry, filters, scrollbar and message actions. Sidebar launchers and pinned panels. Minimap, compass, zoom, menu hints, inventory count, combat and gathering styles, wilderness indicators, timers and experience displays. |
| Sidebar panels | Inventory, equipment, stats, quests and tasks, spellbook, prayers, summoning, friends, ignores, clan and options. Include tooltips, item grids, sub-tabs, scroll and dragging. |
| World interaction | Ground and entity context menus, overhead speech and status bars, item labels, action feedback, NPC responses and keyboard shortcuts. |
| Gameplay windows | Bank and PIN, shop, auction house, trade and duel through confirmation. Crafting and other skill actions, guides, social and party management, achievements, rewards, point configuration, account options, death and recovery, territory and minigame displays. |
| Session screens and modal states | Login and registration, appearance, loading and reconnect, welcome and notices, reporting, input prompts, confirmations, password and recovery pages, sleep, and logout. |
| In-game developer tools | World Builder panels, definitions browser and selection controls. Check their hit regions against terrain picking in the same viewport. |

Chat and dialogue responses take their dimensions from the UI size setting and their fonts from the text size setting. The two preferences appear under **Options → Graphics → Interface preview** and use the existing local client settings store.

## Dialogue and item windows

The dialogue preview moves existing response choices into a translucent panel
above chat. It uses the same geometry for wrapping, drawing and selecting
responses. Keyboard selection and the existing server response indices carry
over.

The conversation panel should frame the player on the left, the NPC on the
right, and speech and responses in the middle. Dim the portrait of the
participant who is listening. Existing NPC speech, player quest speech and
response choices arrive on different packet paths. Associate speech and choices
with an explicit conversation and speaker before displaying portraits or
advancing the conversation in the panel. Test the composition with existing
character sprites before deciding what portrait artwork is needed.

The server's common script helpers already know the NPC involved in many
conversations. Use that identity and the dialogue session lifecycle for
private speech and responses. Keep ambient speech in its existing channel.
Prototype with the banker's branching conversation and verify both participant
turns, choices, cancellation and the handoff into the bank window.

Inventory is the first sidebar layout to use for testing tabs, item cells and
dragging. Test the shared item window in the bank, crafting and shops. Support
tabs as a layout control. The Monster Slayer reward-shop picker is a candidate
for switching stock and currency in place once the shared window works with
the current server-authorized shop actions.

## Preview

The private browser selectors are `interface=1080p` and `interface=540p` on loopback. Standalone development uses `-Dspoiledmilk.nativeHdInterface=true` with a render surface such as `-Dspoiledmilk.renderSurfaceMode=1920x1080`. Choose both UI and text sizes in each resolution and confirm the selections persist after restarting with the same client profile.

## Verification

- Check that Java, camera and picking, and the presented framebuffer all use 1920×1080. Verify camera framing against the 960×540 mode.
- Inspect native-size screenshots in both clients. Test actions through the displayed controls, including edge clicks, overlapping panels, right-click, scrolling, keyboard entry, and return to normal world input.
- Repeat browser pointer checks with the viewport fitted at a fractional CSS scale. Verify both world-loading modes and that the 540p path still works.
- Check sustained play and representative open panels for frame pacing and browser overlay bandwidth. Measure actual world and UI frame submissions.
