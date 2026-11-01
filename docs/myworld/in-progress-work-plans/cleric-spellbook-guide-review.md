# Cleric spellbook and guide presentation

Branch: `feat/cleric-spellbook-and-guide`, based on published
`88e82d56addbf5eed9c1c2b5f4831f92782c14ee`.

The spellbook now presents a spell's name, required level, short named-effect
summary and required sigils. Its separate Holy Power popout is removed. Buffs
use “Grants” and curses use “Afflicts”; inherited effects come from the received
component metadata, with repeated names such as Sap Blood shown once. Unify
keeps a short movement summary. Long names keep the entire `(Level n)` label
together when they wrap.

The in-game **Worship > Spells** guide starts with explanations of Worship,
Holy Power, spell tiers, power levels, thresholds and god-line inheritance.
The guide retains the authoritative per-spell costs, targets, radius, duration,
recipient flavor and numeric effect tables. It explains the non-corporeal state
and both parts of poison weakness. Unify's fixed movement and Grace-only costs
remain distinct. Active-status HUD explanations are unchanged.

Guide pages contain at most 12 body lines, including oversized descriptive
blocks. A continuation repeats its opening context if a block itself needs to
be split. Native scrollbar padding now accounts for the fact that a Cleric
guide page occupies one large row: the final page is reachable. The current
17-spell catalog uses 25 guide pages, below the existing list capacity.

The shared abilities panel grows from 200 to up to 280 logical pixels. Wrapped
Magic, Cleric and Summon headings/details and wrapping rune/sigil cost rows fit
within the panel. Draw, custom-UI anchor, input and keep-open boundaries share
the same viewport-clamped height. Android's optional last-Magic-spell footer
reserves its existing 69 pixels. At the minimum 334-pixel game height this gives
225 pixels of authentic-style panel or 223 pixels of custom-style panel, with
the footer still on screen. Current Magic details occupy at most 212 pixels
from the panel top. No icon-grid redesign is included.

## Verification

Run serially with Java 8 and bounded heaps:

```sh
JAVA_HOME=/home/justin/jdk8u482-b08 ANT_OPTS='-Xmx512m -XX:+UseSerialGC' bash scripts/build-client.sh
JAVA_HOME=/home/justin/jdk8u482-b08 python3 tests/myworld/test-cleric-three-tier-client.py --render /tmp/cleric-ui-review
PATH=/home/justin/jdk8u482-b08/bin:$PATH python3 tests/myworld/test-spellbook-text-layouts.py
python3 tests/myworld/test-side-menu-pinning-layout.py
python3 tests/myworld/test-prayer-ui.py
git diff --check
```

Results: client build passed; 6,599 existing schema/catalog/transport/HUD/guide
assertions passed, plus 68 generic status-HUD checks. The new real-client
fixture passed 61,142 native-font/cost/viewport/input/navigation assertions. It
draws every active Cleric hover and every Magic hover at 512x334 and 960x528,
using both UI anchors, and checks all Magic hovers under the Android footer
clamp at both heights. It renders the actual tabs and footer, verifies extended
detail clicks are consumed, and drives the native guide scrollbar to its final
page. Additional layout/pinning/prayer checks passed.

Native proof images and the complete guide transcript are under
`/tmp/cleric-ui-review/`; logs are `/tmp/cleric-ui-client-build.log` and
`/tmp/cleric-ui-check.log`. Core reviewed the compact spellbook, wrapped title,
overview, god-line pages and small Android footer images. This is offscreen
native client rendering, not an Android-device or private-gameplay claim.

Only client presentation and focused verification changed. No server/runtime
source, spell values, protocol, catalog, sigil cost, art, map, database or local
settings changed. The worker did not operate any server or client process.
Core owns final integration, package maintenance and private-session review.
