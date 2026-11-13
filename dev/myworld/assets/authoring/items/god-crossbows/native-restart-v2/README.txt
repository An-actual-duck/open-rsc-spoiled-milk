Fresh native god crossbows — owner-directed limb designs
========================================================
Start over from the unchanged standard 48x32 crossbow framework. No previous
crossbow design, generated concept or downsampled image is an input.

Zamorak: red stock and black mace-head horns translated into curled limb tips.
Saradomin: silver stock, golden limbs terminating in stars like the mace.
Guthix: brown stock, light green limbs, dark green thorned brambles on stock.

All preserve original stock, trigger, latch and string at native pixel positions.
Limb-tip silhouettes deliberately differ to implement the specified themes.
Three editable Aseprite layers separate materials, god-specific details and
protected mechanical parts. Canvas 48x32; binary alpha; clear outer border.

Rebuild from a worker root with its absolute path substituted for ROOT:
 aseprite -b --script-param root=ROOT --script dev/myworld/assets/authoring/items/god-crossbows/native-restart-v2/author.lua

These are inventory/ground art candidates. Runtime installation, equipped
animations and gameplay remain pending; all earlier versions are superseded.
