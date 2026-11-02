# Gorak visual-test sprite

`gorak-sprite-sheet.png` is a developer visual-test export produced with RSC
Sprite Baker from 2009Scape NPC `4418` (`Gorak`, component model `16205`). The
source cache and 2009Scape project are distributed under the GNU Affero General
Public License v3.0; see <https://gitlab.com/2009scape/2009scape>.

The project owner selected the frames and generated this derivative sheet. It
is distributed with Spoiled Milk under the repository's AGPL-3.0 terms.

Runtime contract:

- 768x384 RGBA PNG, SHA-256
  `6696db79071e175c039fbcb13b3584ab54f279a6f403bd0115a3fcc3eecf4e10`;
- horizontally inverted at export so the base orientation faces right, matching
  RSC sprites;
- six 128-pixel columns: facing camera, diagonal, side, diagonal away, away,
  and side combat;
- three 128-pixel rows: the three animation frames for each column; and
- client NPC key `gorak`, assigned only to developer-spawnable NPC ID `861`.

The 128x128 cells deliberately retain shared transparent padding so every
direction and animation stays aligned. Runtime presentation bounds are
327x240, matching the existing cow baseline; the PNG is not resampled or
rewritten to achieve the larger in-game appearance.

The full local Sprite Baker provenance export contains machine-specific cache
paths and renderer diagnostics and is intentionally not shipped. The stable
source identity, layout, attribution, and content hash needed to audit this
runtime asset are recorded above.
