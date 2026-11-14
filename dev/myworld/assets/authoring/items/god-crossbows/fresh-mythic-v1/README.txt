Reference-free mythical crossbow exploration
============================================
Owner requested three independently generated sprites for EACH god (nine total)
using only themes and a 48x32 image-size requirement. No crossbow, mace or other
reference image was opened or supplied for this exploration. Previous artwork
is superseded and was not used as an input.

Saradomin: gold/silver, noble pursuit of knowledge.
Zamorak: black/red, demon god living for war.
Guthix: green/brown, druidic natural order, kindness or punishment.
Option subtitles are review labels, not proposed gameplay item names.

prompts.json preserves every exact text-only image_gen prompt; options.json
preserves the three distinct design directions chosen for each god.
The image service returned large sources despite the native-grid request.
Original output bytes are preserved in sources/. Actual 48x32 PNG exports and
editable native Aseprite masters are under native/, validated in receipt.json.
These are exploration sprites, not final equipped animations or installed items.
No runtime changes, integration, release or live server activation occurred.

Regenerate native exports from a worker checkout, substituting its root:
 aseprite -b --script-param root=ROOT --script dev/myworld/assets/authoring/items/god-crossbows/fresh-mythic-v1/export-native.lua

Firefox gallery: output/art/god-crossbow-mythic-exploration/index.html
It shows only these nine new options, at 1x and integer 8x.
