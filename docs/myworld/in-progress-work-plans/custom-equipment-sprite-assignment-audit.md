# Existing custom equipment sprite assignment audit

Recorded 2026-09-23 at the owner's request. Follow-up backlog, not implemented
or independently diagnosed yet. Do not interrupt the current Slayer artwork
sequence to perform the full audit.

## Reported problem

Custom worn/held spritework was made for the original RSC equipment families
so daggers, for example, have their own held appearance rather than sharing
sword visuals. Later content appears to have inherited older generic sprite
assignments instead of using the matching custom family artwork.

Owner-reported example: Tin dagger (item 1995) appears to use sword held
sprites rather than the custom dagger. Bronze dagger (item 62) is an original
family comparison to inspect, not a verified correct mapping yet. Inventory
icons and held animation assignments are separate; checking the icon alone
does not establish correct equipment rendering.

## Audit scope and approach

1. Inventory existing custom equipment sprite families and identify original
   items that correctly demonstrate each family in game.
2. Trace item appearance IDs through animation definitions, archive subspace/
   entry references, overrides and fallback resolution. Confirm which artwork
   actually renders; names or matching item icons are not sufficient evidence.
3. Compare all related newer items, including metal tiers, poisoned variants,
   unique rewards and other variants, against the correct family. Cover other
   affected equipment categories, not just daggers.
4. Record item ID/name, current resolved artwork, intended existing family,
   relevant palette, evidence and proposed correction. Preserve intentional
   unique designs rather than indiscriminately replacing them.
5. Repair confirmed stale assignments by reusing existing custom spritework.
   Preserve gameplay stats, requirements, item IDs, palettes and inventory
   artwork unless a separately identified mapping correction requires otherwise.
6. Verify idle, walking and combat frames across supported directions and
   applicable equipment layers. Check poison/tier variants, tint behavior and
   fallback paths. Add regression checks for corrected family assignments.

## Rule for new equipment work

Inspect the current custom family artwork and a verified working family item
before selecting a worn/held source. Do not copy a legacy sword assignment for
a dagger merely because it occurs in an older definition. If an existing family
is unsuitable, document that explicitly before creating a new visual.

For the current Slayer pass, apply this rule to selecting the Dagger of Terror
and the other simple worn/held bases. Keep the broader historical audit separate.
The Abyssal Whip remains a deliberately bespoke wielded sprite project.

## Completion criteria

Each discovered stale mapping is fixed or explicitly documented as deferred;
known-good original items and their related newer variants use the appropriate
custom family in game. Report coverage and remaining exceptions. A saved note
or correct inventory icon is not completion of the audit.
