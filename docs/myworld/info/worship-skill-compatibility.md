# Worship Skill Compatibility

`Worship` is the formal player-facing name of skill ID `5`. The rename is a
presentation change, not a data or protocol migration.

## Player-Facing Boundary

Use `Worship` when referring to the trainable skill:

- Worship XP, experience, and levels
- the Worship skill menu, guide, statistics, highscores, and selectors
- the Worship cape
- quest requirements and rewards tied to skill ID `5`

Continue to use `prayer` for the religious actions and resources governed by
the skill:

- individual prayers and praying
- prayer points and prayer-point bonuses
- prayer effects and prayer books
- ordinary religious dialogue

This distinction is intentional. For example, the Worship guide explains how
to activate prayers using prayer points.

## Compatibility Names Retained

The following identifiers remain Prayer-based so existing accounts, clients,
integrations, and item state do not require a migration:

- skill ID `5`, `Skill.PRAYER`, and `Skills.PRAYER`
- `SkillDef` long and short names of `Prayer`
- database columns such as `prayer` and existing save-data fields
- protocol fields such as `currentPrayer`, `maxPrayer`, and
  `experiencePrayer`
- prayer-book cache keys and Prayer-related class, package, method, and file
  names
- `ItemId.PRAYER_CAPE`, `AppearanceId.PRAYER_CAPE`, and the existing cape item
  ID `1523`
- compatibility command aliases and other external identifiers

`SkillDef.displayName` supplies `Worship` to server-created player-facing text,
while the client uses `Worship` for the corresponding local skill label.
Skill selectors accept both `Worship` and the legacy `Prayer` name. The server
continues to serialize and query the stable internal name.

Older clients may still render `Prayer` in their locally bundled skill labels,
but remain protocol-compatible because the skill ordering and wire values have
not changed.
