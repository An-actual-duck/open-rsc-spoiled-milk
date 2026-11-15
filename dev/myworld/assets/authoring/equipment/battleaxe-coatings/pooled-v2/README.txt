Approved B — pooled viscous battleaxe coating
============================================
Owner chose B: pooled slime slightly over the cutting rim (November 14, 2026).
The approved 48x32 sample and layered Aseprite are preserved here. The standard
inventory overlay is copied exactly onto the original neutral battleaxe form;
runtime material tints remain unchanged. Dragon retains its separate red/gold
artwork with corresponding pools transposed to its cutting rims.

All eighteen held poses use the original hand registration. Rounded pools add
only attached pixels within one cardinal pixel of the original metal. Original
metal remains visible and handle pixels stay unchanged. Crops expand by at most
one pixel per side within the original canvas to accommodate the soft lip.
Offsets compensate for this padding; original weapon pixels keep world positions.
No appearance IDs, item IDs, animations, recipes or combat mechanics change.

Reproduce: aseprite -b --script-param root=REPOSITORY --script apply.lua
Then: python3 scripts/generate-battleaxe-coatings.py
Native PNGs and layered masters are the packer's source-owned inputs. Earlier
thin-trim masters are preserved in previous-thin-art/. The native archive
writer appends replacement records after every unrelated entry, preserving
later well/scimitar payloads and all preexisting local ZIP records.

Validation covers all fifteen icons, all eighteen named/classic poses, native
software/GL mask parity, original archive entries, attached rims and hand anchors.
