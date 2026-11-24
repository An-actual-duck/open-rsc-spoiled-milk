# Native 1080p game interface

The browser and the standalone client must be able to render and pick the world on a 1920×1080 game surface. The interface needs its own text, spacing, and control sizes at that resolution. A browser layout measurement or a 1920×1080 screenshot alone cannot establish the game's render dimensions.

Use the existing Classic bitmap lettering and approved assets for development. Size each interface around its content and anchor it to the viewport. Derive drawing, hover regions, mouse clicks, and scroll regions from the same layout. Keep the current world-loading choices independent of display mode. Asset decisions are pending.

## Interface coverage

| Area | Surfaces to migrate and exercise |
| --- | --- |
| In-game HUD | Chat history, text entry, filters, scrollbar and message actions. Sidebar launchers and pinned panels. Minimap, compass, zoom, menu hints, inventory count, combat and gathering styles, wilderness indicators, timers and experience displays. |
| Toolbar | Scaled launcher buttons, centered original icons, and visible hover, open and pinned states. |
| Sidebar panels | Inventory, equipment, stats, quests and tasks, spellbook, prayers, summoning, friends, ignores, clan and options. Include tooltips, item grids, sub-tabs, scroll and dragging. |
| Right-click menus | Inventory, Equipment, ground items, objects and characters. Scale the menu frame, text, row spacing and hit targets. |
| World interaction | Overhead speech and status bars, item labels, action feedback, NPC responses and keyboard shortcuts. |
| In-game guides | Skill guides, quest guides and their existing guide tabs. Give longer entries room for requirements, progress and rewards, and improve scrolling at both resolutions. |
| Gameplay windows | Bank and PIN, shop, auction house, trade and duel through confirmation. Crafting and other skill actions, social and party management, achievements, rewards, point configuration, account options, death and recovery, territory and minigame displays. |
| Session screens and modal states | Login and registration, appearance, loading and reconnect, welcome and notices, reporting, input prompts, confirmations, password and recovery pages, sleep, and logout. |
| In-game developer tools | World Builder panels, definitions browser and selection controls. Check their hit regions against terrain picking in the same viewport. |

Chat and dialogue responses take their dimensions from the UI size setting and their fonts from the text size setting. The two preferences appear under **Options → Graphics → Interface preview** and use the existing local client settings store.

## Dialogue and item windows

The dialogue preview moves existing response choices into a translucent panel
above chat. It uses the same geometry for wrapping, drawing and selecting
responses. Keyboard selection and the existing server response indices carry
over.

The banker preview puts the player's head on the left, the banker's head on the
right, and the current line between them. It uses the existing side-facing
head sprites and dims the listener. Speech and response choices share the panel.
The server sends the speaker, line and conversation ID while the banker script
runs. Response menus carry that same ID, and the existing response indices and
script pacing still govern the conversation. Ambient speech uses its usual
channel. The prototype currently sources portraits from humanoid head sprites
with a hair-colour mask. Other head layers can still use the dialogue text.

A private server can enable the presentation with
`-Dspoiledmilk.dialoguePreview=true`. Clients opt in after login if they have
the interface preview enabled and the server advertises support. Prototype
coverage is limited to talking to bankers. The bank opens through its existing
interface and the usual bank rules still apply.

## Inventory and Equipment preview

The sidebar uses the interface size for item tiles and the text size for stack
counts, equipment stats and tab labels. Inventory and Equipment share the tab
strip and use the same bounds for drawing and pointer input. The preview uses
the existing item and slot artwork, context menus, equip and remove actions,
and Items Kept on Death control. The sidebar stays above chat entry and chat tabs
where they span the same part of the screen.

The minimap will have its own design pass after the owner supplies notes. Bank, crafting and shop
windows are separate work. The Monster Slayer reward shop remains a candidate
for in-place stock and currency tabs in that work.

## Toolbar preview

The toolbar shares its draw and input bounds. UI size controls the button
dimensions and spacing. Each button centers the installed icon at its original
size. The red Satchel caption is part of the installed menu artwork. The
preview places that caption strip below the toolbar with its original lettering
and colours. Existing panels dock to the toolbar, and minimap design remains a
separate pass.

## Accomplishments preview

Stats and Quests use the sidebar layout and the current UI and text settings.
The Stats list preserves current and base levels, XP details, equipment
bonuses and overall totals. Quest names wrap and the journal shows quest
points and progress. Both lists scroll, and clicks use the visible row bounds.
Rows open the existing guides through the same actions.

### In-game guides to update later

- Give skill guides room for longer training and unlock lists, levels and
  requirements, with clear tabs and scrolling.
- Give quest guides room for the start contact and location, requirements,
  progress and rewards. Use layouts that can hold additional guide information
  as it is written.
- Verify guide readability, list controls and opening the correct guide from
  Accomplishments at 540p and 1080p.

## Ability books preview

Mage, Cleric, Prayer and Summoning each have a toolbar button. M, C, P and S
stand in for artwork. Each book starts in its icon grid and saves its own
Icon/Text choice from the button in its header. Spell, prayer and summon
activation still use the existing game actions. Cleric has its own book for
Worship levels, Holy power and the sigils made through Blessing. Use the
preview to compare the width of the expanded toolbar at 540p and 1080p.

## Right-click menus

Bring the shared right-click menu up to the same visual and readability
standard as the other panels. Use the UI size setting for padding and row
spacing, and the text size setting for labels. Use the same row bounds for
drawing, highlighting and selecting an action. Keep the existing action order,
default click behavior and dismissal. Start verification with Inventory and
Equipment, then check world targets and menus near the viewport edges at 540p
and 1080p.

## Preview

The private browser selectors are `interface=1080p` and `interface=540p` on loopback. Standalone development uses `-Dspoiledmilk.nativeHdInterface=true` with a render surface such as `-Dspoiledmilk.renderSurfaceMode=1920x1080`. Choose both UI and text sizes in each resolution and confirm the selections persist after restarting with the same client profile.

## Verification

- Check that Java, camera and picking, and the presented framebuffer all use 1920×1080. Verify camera framing against the 960×540 mode.
- Inspect native-size screenshots in both clients. Test actions through the displayed controls, including edge clicks, overlapping panels, right-click, scrolling, keyboard entry, and return to normal world input.
- Repeat browser pointer checks with the viewport fitted at a fractional CSS scale. Verify both world-loading modes and that the 540p path still works.
- Check sustained play and representative open panels for frame pacing and browser overlay bandwidth. Measure actual world and UI frame submissions.
