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
| Gameplay windows | Bank, shops, trade through confirmation, crafting and other skill actions, social and party management, achievements, quest rewards, account options, death and recovery. |
| Session screens and modal states | Login and registration, appearance, loading and reconnect, welcome and notices, reporting, input prompts, confirmations, password and recovery pages, sleep, and logout. |
| In-game developer tools | World Builder panels, definitions browser and selection controls. Check their hit regions against terrain picking in the same viewport. |

Chat and dialogue responses take their dimensions from the UI size setting and their fonts from the text size setting. The two preferences appear under **Options → Graphics → Interface preview** and use the existing local client settings store.

Quest completion rewards use the preview below. Activity rewards stay tied to
their respective windows.

## Pre-login screens

Preserve the Classic first impression from the initial loading screen through
the main account screen and sign-in. Use the existing screens as the visual
reference for the backgrounds, composition, controls, and transitions.
Higher-resolution redraws and a more readable font are options where needed.
Match the original look at both preview resolutions. The main screen offers
**Create Account** and **Log In**.

### Create Account

Keep the game username, password, and password confirmation. Replace the
email field with optional **Discord Username**. Show the reason beside it.

> This is not required but is necessary if you want to have access to account/password recovery.

When the Discord field is blank, ask **“Are you sure you don't want to link a
Discord account?”** **Yes** continues registration without a link. **No**
returns to the form with the entered information intact. After the server
confirms registration, return automatically to the main screen and show
**Account created** there.

A supplied Discord username starts a pending link. Confirm control of the
Discord account before saving its user ID as the recovery identity. Account
creation can complete while the link is pending. Update client registration
and server handling together so the Discord username reaches the linking flow.

### Log In and password recovery

Keep the normal login controls and place **I forgot my password** in the
existing recovery entry point. Open a form with one **Username** field for
the game account and a **Send** button in place of the question form. After
the recovery request is submitted, show the same status for every username.

> If this account is linked to Discord, check your DMs for a recovery request.

The private UI preview keeps the original loading sequence and sign-in control
positions. The legacy terrain path still renders the three rotating views.
The native terrain path has used a black backdrop since the World Builder map
migration. Restoring the rotating views for native clients needs a separate
pass with approved views from the current map.

Account creation with a blank Discord field works against the preview server's
email-free registration configuration. It returns to the main screen after the
server accepts the account. While linking is pending, a supplied Discord name
prompts the player to clear the field to continue. The recovery form labels its
response as a preview while Discord delivery is pending.

## Retired from the player interface

- **Change Contact Details**. Hide the settings entry in the scaled and
  Classic layouts. The legacy form and server support remain for compatibility.
  Use Discord polls for player input on server locations.

- **Change Recovery Questions**. Hide the signed-in settings entry in the
  scaled and Classic layouts. Retire the question-based reset flow as part of
  the Discord account recovery rework.

## Discord account identity and recovery rework

Use a linked Discord account as the primary identity for account recovery.
The server already pairs a signed-in game account with a Discord user ID and
supports a password reset initiated in a DM with the bot. In the planned
flow, **Send** on the recovery form starts a reset request for the verified
Discord link. The bot DMs the linked account to confirm the request. After
confirmation, the server resets the password and the bot sends a temporary
password by DM. The player can sign in and change it in Settings.

Review the bot and server capabilities during the account rework, including
how to end an active game session after a confirmed reset for a compromised
account. The current bot reset requires the character to log out first.

The account rework will retire recovery questions and email-based recovery.
Remove email and personal contact information from the account forms that
collect them as part of that rework.

## Needs further exploration

Revisit these after the more straightforward UI updates are complete.

- **Clans**. Defer the UI redesign until the owner has reviewed the existing
  creation, search, joining, invitations, and member management flows in game.
- **Bank PINs**. Trace how players reach PIN setup, including any chat commands
  or banker options. Exercise setup, change, removal, and first bank access
  after relogging before deciding where the PIN controls belong.
- **Auction House**. Walk through listing, buying, cancelling, expiry, and
  collection with two accounts. Use that walkthrough to decide what changes
  would help on a small server.
- **Player report review**. Verify the deployed report path end to end with a
  test report. Follow server validation, the `game_reports` record and
  attached evidence, and any configured Discord delivery. Establish where the
  owner can find, review, and follow up on a report. Confirm which evidence is
  available and who can access it before choosing any backend or tooling changes.
- **Activity windows**. Review territory and minigame screens, point
  configuration, and other activity-specific windows individually. Establish
  what players need to see or do in each before choosing a layout.

## Dialogue and item windows

The dialogue preview moves existing response choices into a translucent panel
above chat. It uses the same geometry for wrapping, drawing and selecting
responses. Keyboard selection and the existing server response indices carry
over.

Scripted NPC dialogue puts the player's head on the left, the current NPC on
the right, and the line between them. The listener dims during each line.
Two NPCs in one conversation can take turns on the right. Portraits use the
installed side-facing head and headwear layers for assembled characters.
NPCs with full-body artwork, including the goblin generals, use the intact
side-facing idle frame from their body animation.

Dialogue and choices share the panel and use the existing response indices and
script pacing. The server opens the presentation when a player-owned script
sends its first NPC conversation line and closes it when the script ends or is
interrupted. Ambient speech and message-only interactions use their usual
channels. Menus without a scripted line still use the scaled response list.

A private server enables this presentation with
`-Dspoiledmilk.dialoguePreview=true`. Clients opt in after login if the
interface preview is enabled and the server advertises support.

## Inventory and Equipment preview

The sidebar uses the interface size for item tiles and the text size for stack
counts, equipment stats and tab labels. Inventory and Equipment share the tab
strip and use the same bounds for drawing and pointer input. The preview uses
the existing item and slot artwork, context menus, equip and remove actions,
and Items Kept on Death control. The sidebar stays above chat entry and chat tabs
where they span the same part of the screen.

The bank preview organizes filters, stock and bank actions into three panels.

### Items on Death

Open Items on Death from Equipment. Kept and lost items appear side by side
with their icons, names, and quantities. Identical entries share a row within
each list, and notes retain their own rows. Long lists scroll independently.
The estimated loss value uses base item prices.

The preview requests the server's current inventory and equipment breakdown
using the same selection as normal death drops. This accounts for skulls,
Ultimate Ironman, Soul necklaces, and staff retention. Active duels display
their separate-rules notice. Servers without the preview use the original
window.

## Crafting preview

Crafting, smithing and smelting recipe windows group the recipes for the active
interaction into item families and material tabs. Anvils show recipes for the
bars in the player's inventory in one catalog. The furnace's Jewelry option
opens tabs for rings, necklaces and bangles, with plain gold and gem options
down the left. Material costs stay below the recipe grid, and recipe and
ingredient tooltips go above it. The cost list marks reusable moulds as tools.
A separate panel holds quantity, All and the crafting preferences.

The server supplies the batch count and advances it when production completes.
The bar animates during the server's action delay and appears in the controls
while Keep window open is checked. Closing the window moves it above the
player and the batch continues. Cancel and world actions retain their existing
interruption behavior.

## Toolbar preview

The toolbar shares its draw and input bounds. UI size controls the button
dimensions and spacing. Each button centers the installed icon at its original
size. The red Satchel caption is part of the installed menu artwork. The
preview places that caption strip below the toolbar with its original lettering
and colours. The minimap slot opens the top-right map pane.

## Minimap and fullscreen world map

Reuse the scaled frame, toolbar launcher, pinning, and top-right placement.
Keep the live player and NPC dots sourced from nearby gameplay entities.
The current preview shows a placeholder in that frame and skips generating,
transferring, and publishing the old minimap raster. The Classic client uses
its existing minimap.

Build a shared 2D renderer for the minimap and world map. The old minimap
raster covers the active scene and is built during terrain preparation.
Generate reusable map tiles ahead of time from the published world package,
with terrain, walls, and other recognizable landmarks. Match tiles and
markers to the map revision, world space, and level. Give each view its own
clipped viewport. Load visible map tiles on demand through the 2D map path,
independently of gameplay scene loading. Keep the old raster disabled in the
scaled interface. Closed views do no background map rendering or fetching.

- **Minimap**. Follow the player and show nearby terrain, landmarks, and live
  dots. Use the same map coordinates for drawing and hit testing. Retain
  local click-to-walk through the existing movement path once its positions
  are verified.
- **World map**. Open a fullscreen view from the minimap. Start at the
  player's position and allow click-and-drag panning across the world,
  including areas outside the active scene. The map captures drag input so
  browsing leaves the player and game camera in place.
- **Points of interest**. Use graphical icons in both views and show their
  meanings in the world map key. Start with banks and quest starts. Anchor
  each bank icon to the actual banking location, including bank chests, and
  anchor each quest icon to the verified starting location. Keep these fixed
  markers separate from the moving player and NPC dots.

Prototype one town and its surroundings. Check that the bank and quest
markers line up in both views, panning beyond the loaded scene reveals more
map without requesting distant gameplay scenes, and walking with the minimap
open adds no noticeable hitch. Verify both world-loading modes at 1080p and
confirm the controls and map key fit the compact layout.

## Accomplishments preview

Stats and Quests use the sidebar layout and the current UI and text settings.
The Stats list preserves current and base levels, XP details, equipment
bonuses and overall totals. Quest names wrap and the journal shows quest
points and progress. Both lists scroll, and clicks use the visible row bounds.
Rows open the existing guides through the same actions.

### Quest guides

Every quest in Accomplishments → Quests opens the scaled guide in the preview.
Overview shows the listed start contact, location and requirements. Rewards
shows the existing guide rewards, including formulas and unlocks where recorded.
Progress uses the current quest stage. Cook's Assistant also has a detailed next
step and its ingredient list. Missing entries are identified in the guide.
Long lists and wrapped text scroll within the page at both resolutions.

### Skill guides

Accomplishments → Stats opens the scaled skill guide. The existing categories
stay across the top. The left column shows topics for the selected category,
and the right column holds the selected topic's text, levels and existing
illustrations. The topic list and article scroll independently. UI size sets
the window and control dimensions, and text size sets the fonts and wrapping.

Most sections start with a single page of existing information. Smithing gear
sections split by metal, and Worship → Spells places the existing god-line
filters in the topic column. Future guide articles can add topics as they are
written. Hiscores still use the existing view.

## Ability books preview

Mage, Cleric, Prayer and Summoning each have a toolbar button. M, C, P and S
stand in for artwork. Each book starts in its icon grid and saves its own
Icon/Text choice from the button in its header. Spell, prayer and summon
activation still use the existing game actions. Cleric has its own book for
Worship levels, Holy power and the sigils made through Blessing. Use the
preview to compare the width of the expanded toolbar at 540p and 1080p.

## Social preview

The Social sidebar has Friends, Clan, Party and Display Online along the top.
It scales tabs, member rows and actions with the existing UI and text settings.
Keep the current friend messaging and removal, clan search and setup, and party
creation and management actions available from the sidebar. Honor the existing
server option for clan availability.

Display Online requests the visible player list on entry, then every 15 seconds
while the tab is visible. It keeps the current list on screen during refreshes
and preserves the scroll anchor when that player stays online. Rows show
player names and crowns, with the existing right-click actions. The server
uses the same privacy and staff rules as the online list command and rate
limits requests. Servers without sidebar support still offer the list in
Settings.

Add Friend and Private Message use the scaled text prompt. Enter submits
and Escape cancels. Both also offer explicit buttons. The editor wraps longer
text, and its keyboard and pointer input stay inside the prompt. It uses the
existing friend-list and private-message actions.

Create Party confirms creation with the existing five-player limit. The
active server creates unnamed parties, and the preview only asks for input
the server uses. Invite to Party shares the name editor with Add Friend.
Incoming invitations show the sender and offer Accept, Decline and Ignore
for 2.5 minutes.

Manage Party shows the roster with rank, health and combat level. Owners
can change who can kick or invite from the Permissions tab. Select a
member to change their rank, transfer leadership or kick them when your
rank permits it. The window confirms transfers, kicks and leaving a party.

## Settings preview

Settings keeps the Social, General and Graphics tabs and their existing
actions. Settings labels and values adjust to the selected text size, and
each tab keeps its own scroll position. Graphics groups resolution, UI size
and text size at the top. The browser's game resolution follows the selected
preview at launch. Standalone OpenGL keeps the existing resolution selector.

Terrain shading, object shading, brightness, contrast, gamma and saturation
each have a track, handle and current level. Clicking the track or dragging
the handle uses the existing tuning levels and updates the renderer while
dragging. Release saves the final level. Browser presets still govern which
graphics choices can be changed.

## Right-click menus

The shared game menu uses the UI size setting for padding and row spacing
and the text size setting for labels. Inventory, Equipment, chat and world
actions use the same row bounds to draw, highlight and select an action.
The menu stays inside the viewport. When a list is taller than the available
space, the scroll wheel or scrollbar reaches the remaining actions without
changing their order. Moving away and clicking outside still dismiss the menu.
Trade uses the shared scaled menu frame for Offer and Remove quantities.
The hidden Duel feature and its menus are deferred to the full Duel rework.

## Welcome, server notices, logout, and reconnect

The welcome window respects the existing hide-IP setting and keeps the
security tip and pending recovery request actions. General server notices
wrap and scroll with their original paragraph breaks and inline colors.
Use the wheel, the page buttons, or Page Up and Page Down to read long
notices. Enter and Escape dismiss ordinary notices. Pending recovery changes
offer explicit choices to keep or cancel the request.

Log out uses the existing action and shows scaled progress while the server
responds. These windows capture game input while open.

The scaled reconnect notice appears when an established game connection
drops and the client starts its normal retry. Sign-in and initial loading
use their Classic presentations.

## Change Password preview

Settings → Social → Change password opens a signed-in form with three masked
fields. Tab or click to move between fields. The form shows password
requirements and mismatched confirmation before submitting. It waits for the
server's result and shows the outcome in the form. Only an acknowledged change
updates the password used for reconnect in that session.

## Report Player preview

Open Report Player from General settings or a player's right-click menu. The
right-click action fills in the account name. The player can edit the name,
select a reason and review both before pressing Send Report. Escape or Cancel
closes the window. Staff retain the mute options they already have. The client
uses the existing report request and leaves acknowledgement to the server.

## Gameplay HUD and confirmations

- Combat and gathering XP use the same scaled choice panel. Keep the
  existing focus options, visibility settings, and selection behavior.
- The XP tracker shows the tracked total or skill, level progress, and
  optional hover details. Its settings keep the existing modes, skill
  picker, colors, drop speed, visibility, and session reset.
- The Wilderness warning uses a scaled notice with an explicit Dismiss
  button. The Wilderness level badge keeps the skull and current level.
- The death overlay keeps the Classic message and existing respawn timing.
- Skip Tutorial and Leave the Black Hole use a shared confirmation. The
  named action confirms, while Cancel and Escape return to play.
  The layered-world preview compares the player's projected position with
  the old packed Black Hole bounds. Entering on level -1 leaves the exit
  option hidden. The location check needs a separate fix before testing that route.

The HUD scales the existing health and auto-retaliate controls, active effects,
countdowns, and gathering progress. Above characters, player names and clan
tags keep their colors, speech keeps its Classic style, and combat health bars
and hit splats follow the interface at 1080p. Classic bitmap fonts provide the
larger text. The health and progress bars retain their existing art and colors.

## Small in-game indicators

The top-left action hint uses the selected text size and has space above the
XP focus selector. Ground item names use larger Classic glyphs and keep their
per-tile grouping, duplicate counts, and collision handling. The satchel count
fits its toolbar button. Walk and interaction clicks reuse the original
four-frame markers at a readable size on the preview surface.

The appearance picker belongs with the classic account entry screens. Review
the same picker through the Makeover Mage when updating that flow. Sleep and
the kill feed are disabled in the MyWorld configurations.

## Preview

The private browser selectors are `interface=1080p` and `interface=540p` on loopback. Standalone development uses `-Dspoiledmilk.nativeHdInterface=true` with a render surface such as `-Dspoiledmilk.renderSurfaceMode=1920x1080`. Choose both UI and text sizes in each resolution and confirm the selections persist after restarting with the same client profile.

## Verification

- Check that Java, camera and picking, and the presented framebuffer all use 1920×1080. Verify camera framing against the 960×540 mode.
- Inspect native-size screenshots in both clients. Test actions through the displayed controls, including edge clicks, overlapping panels, right-click, scrolling, keyboard entry, and return to normal world input.
- Repeat browser pointer checks with the viewport fitted at a fractional CSS scale. Verify both world-loading modes and that the 540p path still works.
- Check sustained play and representative open panels for frame pacing and browser overlay bandwidth. Measure actual world and UI frame submissions.

## Shop preview

Standard shops and general stores share a scrollable stock grid. The side panel
has separate Buy and Sell sections with preset quantities, All and custom
amounts. Shops can opt into named stock tabs by mapping catalog IDs to labels.
The general store and standard shop catalogs start with a single grid.

Monster Slayer currency tiers and Rangers Guild item families open as tabs in
the catalog. Material tiers within a Rangers family remain visible together.
The side panel selects a quantity and confirms the purchase. Point prices,
balances and any required ingredients appear below the item grid.


## Custom quantity prompts

Buy X and Sell X in standard shops and Drop X in the Inventory menu show the
item, available quantity and a focused numeric field. Enter confirms a valid
amount and Escape cancels. Amounts above the current stock or matching
inventory count stay in the prompt for correction. Sell X shows the noted item
when the shop will sell notes first. Drop X tracks the selected item and its
note status even if its inventory slot changes.

## Bank preview

The bank grid sits below its tab strip. Players start with one tab and can use
the plus button to add up to six. A tab's icon comes from the first item visible
under that tab's filters. All tabs can show the full bank, including tab one.

The Filters panel has a per-tab Save filters toggle. Saved search, tags, and
AND/OR matching follow the account. Edits are saved while the toggle is on.
With saving off, filters apply until the player switches tabs or reopens the
bank. Every tab uses the same items and logical slot IDs.

The right panel contains the deposit-all action, inventory and equipment views,
preset entry and quick loads, withdrawal form, and rearrangement controls.
The preset screen offers the existing save and load actions. Fixed empty slots
are a separate layout change.


## Trade preview

Trade places both offers beside the carried inventory. Compact viewports put
the three grids in a row to preserve space for item icons. The grids and buttons
follow UI size, with independent text size. Click a carried or offered item for
the standard quantity, or right-click for 1, 5, 10, All or a custom amount. The confirmation screen lists items given and received,
including the exact quantity and note status. Long offers scroll on compact
layouts. The existing trade handshake, server rules and final confirmation apply.


## Quest completion preview

The completion window lists quest points, experience actually gained, items
received, and whether they were banked or placed on the ground. A quest can also
provide text for a verified unlock. The Cook's Assistant preview includes use of
the Cook's range. The panel uses the independent UI and text sizes.

The server reports grants from the quest completion script, including dialogue
shortcuts and the items they award after updating the quest stage. Rewards
collected in a separate interaction continue through their existing screens and
messages. The server flag `-Dspoiledmilk.questRewardPreview=true` advertises the
optional window to preview clients. Minigame and activity rewards stay with
their respective activities.
