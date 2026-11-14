Stirge front/right near-wing correction — review candidate

Frozen inputs preserve both Dot candidate 3 and the prior Core gap repair.
The previous gap repair and Dot's original working folder remain untouched.

Native Aseprite recipe: correct-near-wing.lua. Only frames 03–05 change.
Separate the near and far wings so they attach on opposite dorsal shoulders,
six native pixels apart. The far wing keeps its previous position and pixels
outside the separated central bridge. The near wing is mounted higher on the
body; mid/upstroke render behind the body, while downstroke renders in front.
Its extracted wrist-to-shoulder contour excludes the opposite wing's folded
edge. Root membrane uses the source's rust palette. Span foreshortening of 4%
and raised-pose vertical foreshortening of 10% keep the wing inside the canvas.
The body, head, four legs, proboscis and all other directions are unchanged.

Rebuild from worker root (create output/art/stirge-near-wing-correction first):
aseprite -b --script-param root=ABSOLUTE_WORKER_ROOT --script dev/myworld/assets/authoring/monsters/stirge/near-wing-correction/correct-near-wing.lua

Validation: validate-layers.lua checks all 108 original nonwing layer/frame
combinations, six tags, eighteen frames, nine layers and phase overlap order.
PNG comparisons confirm only 03–05 changed, original canvas sizes and binary
alpha preserved. See validation.json for native export hashes.

Preview: output/art/stirge-near-wing-correction/index.html
Pending owner visual review; no runtime installation or public deployment.
