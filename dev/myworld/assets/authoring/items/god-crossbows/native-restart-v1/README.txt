Fresh native crossbow designs
============================
Owner rejected high-resolution concept conversion. This pass uses only the
unchanged 48x32 standard/Phoenix crossbow reference as its shape and original
god mace icons for colours and emblem themes. All previous generated designs
are historical only and are not inputs or runtime artwork.

Run from a worker checkout, replacing ROOT with its absolute path:
 aseprite -b --script-param root=ROOT --script dev/myworld/assets/authoring/items/god-crossbows/native-restart-v1/author.lua

Layer 1: original crossbow with mace-inspired stock and bow-limb palette.
Layer 2: small stock inset (Saradomin star; Zamorak horns; Guthix pinecone).
The full original opacity mask is preserved, as are the original grey trigger,
stock latch and string pixels. No resampling, concept conversion, partial
transparency or extra canvas area. PNGs and editable Aseprite masters are
review candidates; equipped animations and gameplay are not yet installed.
