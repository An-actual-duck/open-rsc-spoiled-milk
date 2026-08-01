SPOILED MILK WORLD BUILDER 2 @VERSION@
======================================

This package edits a compatible Spoiled Milk private server without changing
its active map until you deliberately import your work.

This is the signed-layered second generation of the editor. It is a separate
product from the frozen packed-map RSC World Editor v1 line, whose final tag is
v1.1.0. World Builder 2 uses product and update channel
rsc-world-editor-v2. It never installs as an automatic v1 update, and it does
not open or migrate a v1 workspace.

INSTALLATION
------------

Place the entire "Spoiled Milk World Builder 2" folder directly inside the root
of your private server. The result should look like this:

  Your Private Server/
    Client_Base/
    server/
    Spoiled Milk World Builder 2/

Do not place it inside the server/ or Client_Base/ folder.
Do not extract it over a legacy World Editor or copy a legacy workspace into
this folder. Keep both installations side by side if both map formats are
needed.

STARTING AND SAVING
-------------------

Linux: run "Start World Builder.sh".
Windows: double-click "Start World Builder.cmd".

On Linux, the executable Import and Undo .sh files may also be double-clicked
directly. They open a terminal window for the required preview and confirmation,
then keep the result visible until you press Enter. If the file manager asks
what to do with the script, choose Run rather than Display.

The first launch validates the parent private server and creates an isolated
workspace. Later launches reopen that same workspace. The local Builder account
logs in automatically, is invulnerable, and opens the world editor.

Use the editor's Save control before closing. Save writes the current terrain,
scenery, NPC, and respawning ground-item work to project files inside:

  Spoiled Milk World Builder 2/workspace/

Save does not copy those files into the parent private server. You can save,
close, and reopen World Builder as often as you like while the server continues
using its existing map. The server map changes only when you deliberately run
"Import Map Changes.sh" or "Import Map Changes.cmd" and confirm its preview.

RECOMMENDED WORKFLOW
--------------------

1. Start World Builder and make edits in its isolated map.
2. Select Save regularly and once more before closing World Builder.
3. Continue editing and testing for as long as needed. The parent server is
   unaffected during this work.
4. When the saved map is ready, close World Builder and stop the target private
   server and client.
5. Run "Import Map Changes.sh" or "Import Map Changes.cmd", review the exact
   preview, and type IMPORT only when it is correct.
6. Restart the target server and inspect the imported map.
7. If the imported map is not right, stop the target server again and run
   "Undo Last Map Import.sh" or "Undo Last Map Import.cmd". Review the preview
   and type UNDO to restore the map state from immediately before that import.

EDITOR KEYBOARD SHORTCUTS
-------------------------

Editor shortcuts are active by default and ordinary chat typing is paused.
Press Ctrl+Enter to switch between editor shortcuts and normal chat/command
entry. Numeric editor fields still accept typing when explicitly selected.

  B                 Brush; press again while the Brush tool is active to
                    switch between 1x1 and 3x3
  N                 Navigate; press again to toggle click teleport
  I                 Inspect; press again to copy the current inspection
  D                 Collapse or expand the editor dock
  H / C / T / R     Toggle Elevation, Floor Color, Floor Texture, or Roof while
                    in Terrain mode
  Shift+N / E / D   Toggle North, East, or Diagonal Wall in Terrain mode
  Ctrl+H/C/T/R      Open that terrain value and select its numeric field
  Ctrl+N/E/D        Open that wall value and select its numeric field
  Ctrl+Shift+S      Save world edits
  Ctrl+Enter        Toggle editor-shortcut mode and chat/command-entry mode
  Esc               Cancel numeric entry, close the current flyout, leave the
                    full-size editor view, or begin closing the editor

When a numeric field is selected:

  0 through 9       Replace or append digits
  Backspace         Remove the last digit
  Enter             Apply the entered value
  Tab               Move between the Navigate X and Y coordinate fields

Hold Ctrl while left-clicking and dragging terrain to paint continuously.
A normal left click paints one 1x1 or 3x3 stamp. Middle mouse remains reserved
for rotating the camera. Arrow keys and camera controls remain available in
either input mode.

ERASING TERRAIN AND WALLS
--------------------------

World Builder erases map features by painting their empty raw value. The field
must be enabled (checked) before the value is painted.

- Set a North Wall, East Wall, or Diagonal Wall value to 0 to erase that wall.
  Both the 1x1 and 3x3 brushes can erase walls; use 3x3 carefully because it
  changes all nine tiles around the clicked center.
- Set Floor Texture to 8 to erase terrain and return those tiles to void space.
  Build mode keeps its grid visible over this empty area so you can continue
  locating and editing void tiles.

Inspect or copy a nearby tile when you need a known value to restore terrain or
a wall. Only checked fields are changed when a terrain stamp is painted.

IMPORTING MAP CHANGES
---------------------

1. Close World Builder.
2. Stop the target private server and client.
3. Run "Import Map Changes.sh" or "Import Map Changes.cmd".
4. Review the exact preview.
5. Type IMPORT only if the listed files are correct.

The script exports the complete latest saved signed-layered package from the
World Builder workspace. It verifies that package is an allowed descendant of
the immutable package the project started from, validates the target revision
and layered-runtime capability, and previews every package file plus every
server configuration value that will change.

After confirmation it installs the package under:

  server/conf/server/data/world-builder-layered/package/

It also updates the selected server/myworld.conf in the same transaction so a
normal private-server start uses the exact installed package and its pinned
manifest SHA-256. Every destination is staged, backed up when it existed,
verified, and recorded in a receipt. Unsaved editor changes are not imported.
The script refuses unknown, running, changed, incompatible, or already-active
targets. There is no force option. Undo an active import before importing a
newer saved draft from the same workspace.

UNDOING THE LAST IMPORT
-----------------------

Keep the target private server stopped, then run "Undo Last Map Import.sh" or
"Undo Last Map Import.cmd". Review the preview and type UNDO. This restores the
selected configuration, removes the transaction-owned layered package, and
restores the target to its exact state immediately before the most recent
successful import. It is intended both as the safety net for an unsatisfactory
map and as the required first step before importing a newer saved draft. Undo
refuses if the installed package or configuration changed after the import,
because overwriting those newer changes would be unsafe.

Backups, transaction receipts, exports, logs, and the editable project remain
inside workspace/. Preserve that folder if you move or update World Builder.

UPDATING OR STARTING A FRESH PROJECT
------------------------------------

Each workspace is tied to the exact private-server map, coordinate model, and
definitions it was created from. World Builder 2 intentionally does not rebase
an old project onto a different Spoiled Milk release and does not adopt a
packed-map World Editor v1 workspace.

When updating the target private server:

1. Close World Builder 2 and preserve the entire old World Builder 2 folder.
2. Finish or undo any import made by that old workspace.
3. Install the World Builder 2 package shipped beside the new server release.
4. Start it with no workspace/ folder so it creates a fresh project from the
   new release.

Do not copy only selected files between workspaces. Keep the old workspace,
backups, and receipts until the imported result has been fully verified.

WHEN AN ACTION IS REFUSED
-------------------------

A refusal is a safety result, not a partial import. Read the displayed reason
and the workspace logs. Common causes include a running target server, a
different server release, changed destination files, an incomplete workspace,
or a target layout that is not explicitly supported.

There is no force option. Correct the reported condition and preview again.
If an apply operation fails after starting, World Builder restores the prior
files before reporting failure. Import and undo receipts and their associated
backups remain under workspace/ for recovery review.

REQUIREMENTS AND LIMITS
-----------------------

- Use the package matching your operating system: Linux x64 or Windows x64.
  Both packages include their own Java runtime; users do not install Java,
  Git, Ant, or the project source code.
- macOS is not included in the first public release.
- The first release supports the current Spoiled Milk repository/private-server
  layout using server/myworld.conf and the advertised World Builder 2 layered
  import capability. It imports one complete package containing terrain,
  scenery, NPC, and respawning ground-item data. Similar-looking OpenRSC forks
  are not assumed compatible.
- Use a World Builder package alongside the exact Spoiled Milk release it was
  built for. Cross-version project rebasing and imports are intentionally
  refused.
- RSC World Editor v1 and World Builder 2 use separate product identities,
  update channels, release tags, archive prefixes, install folders, and
  workspace formats. There is no automatic upgrade or workspace migration
  between them.
- The default local Builder port is 43615. Before the first launch, advanced
  users may set WORLD_BUILDER_PORT to another free port from 1 through 65534.
- Boundary-object authoring and automatic project rebasing are not included.
- Workspace backup and receipt management is filesystem-based; there is no
  graphical retention manager in the first release.
- Import and undo require the target private server to be offline.
- After import, start the private server normally. The developer-only
  --layered-production launcher option generates and selects the repository
  baseline instead of the imported World Builder package and is not the
  imported-map launch path.

Release source commit: @SOURCE_COMMIT@
