# NPC offense: owner decisions after the style audit

2026-11-16. Approved design direction, not installed gameplay changes. This
supersedes conflicting recommendations in the earlier offense audit. Individual
power values, attack weights, loader schema and implementation remain pending.

## Accepted and revised style choices

| Group / NPC | Direction |
| --- | --- |
| Ordinary Darkwizards and the existing specialist-caster group | Magic-only, enforce actual exclusivity and hold position with a valid shot |
| Monks of Zamorak139/140/293 | Magic-only; matching-god hidden damage trait below |
| Salarin567 | All magic; preserve its separate player-spell response and vulnerability rules |
| Ordinary ghosts / Otherworldly being | Magic-only; quest-related variants retain melee under the quest safeguard |
| Melzar182 | Enable proper casting; retain melee under the subsequent blanket quest safeguard |
| Iban Disciple658 | Enable proper casting; retain melee where this quest-related actor falls under the safeguard |
| Ice Queen254 | Melee and Magic, each at 100% of the original Strength reference; unique two-style exception |
| Lucien364 | Melee and Magic |
| Gaia798 | Melee and Magic; owner did not author this non-vanilla addition, so do not assume a pure-caster role |
| Ungadulu766/767 and Viyeldi772 | Add Magic while retaining Melee and quest mechanics |
| Banshee865 | Leave as is: retain existing attacks, wail, earplug protection and special cadence |
| Dragons | Retain Melee, Magic and existing breath where provided; ensure Melee is available rather than accidentally removed |

The owner said yes to Melzar/Iban casting, followed by the general rule that
anything quest-related retains melee. The conservative interpretation above
preserves that melee option; do not silently apply the earlier magic-only
recommendation to these quest actors. If exclusive magic is later wanted for a
particular quest actor, record it as a deliberate exception.

Ice Queen's 100%/100% exception is **offensive Power**, not defense or automatic
boss classification. Her attack weights are not chosen by equal Power values.

The earlier audit's ranged-primary candidates remain recommendations awaiting
selection. The current message does not approve every suggested new attack.

## Quest and minigame safeguards

Keep Melee available on quest-related NPCs in addition to approved new Magic,
unless an explicit separately reviewed exception exists. Preserve quest phases,
attack/spell triggers, protection requirements, vulnerabilities and progression.
Ordinary family names do not establish whether every ID is quest-related;
classify the actual IDs and consumers before installing changes.

**Do not change Gnome Ballers or other minigame participants.** This is a hard
exclusion for this work, including generic combat/profile side effects. Mage
Arena/Kolodion behavior is therefore retained, including its deliberate human
form melee suppression, rather than applying the generic quest melee safeguard
or new specialist rules to it. Keep owned-summon dispatch separate as well.
A later minigame rework would need its own authorization.

## Hidden matching-god magic trait

The owner extended the original Monks of Zamorak request to **all god-aligned
NPC magic**, not just Zamorak monks:

- A player aligned to the same god takes **20% less damage** from that god's
  aligned magical attack.
- Applies symmetrically to Saradomin, Zamorak and Guthix magic.
- Hidden trait: do not add a player-facing description/tooltip merely to expose it.
- It reduces qualifying Magic damage, not the enemy's Melee/Ranged attacks or
  every attack made by a religious-looking NPC.
- Explicitly identify the magic's god. A generic elemental cast, demon identity
  or holy-looking projectile is not sufficient evidence of alignment by itself.
- This NPC audit does not authorize changing PvP/player-spell balance.

Existing player affiliation is represented by the selected prayer book,
`Player.getPrayerBook()` (a `PrayerCatalog.GodLine`), also used for offerings.
Use that existing alignment as the proposed matching check rather than inventing
another allegiance field. Devotion balances can exist independently for multiple
gods; a positive balance alone must not qualify as multiple active alignments.
No minimum Devotion, cape, item or active-prayer requirement was requested.

NPC follower/aggro classification already exists in NpcBehavior, but it relies
on names and is not sufficient as the new attack metadata contract. The actual
attack-to-god mapping, final rounding/mitigation order and special secondary
Magic damage coverage need explicit implementation decisions and tests. Do not
quietly extend the reduction to poison, bleed, burn or unaligned special damage.

## Existing agreements retained

Dedicated users pursue with existing pathing only until an in-range clear shot
is available, then stop, including on cooldown. They do not switch to melee
merely because the player reaches them. Quest/minigame exclusions take priority.
Mixed-style users retain RNG weighted toward the intended primary style, with
weights separate from Power ratios. No tactical repositioning system is needed.

Ordinary authoring ratios by band remain100/40/10 through100/90/60, only for
actually enabled styles. Primary uses the original Strength reference unless a
reviewed existing explicit stat or exceptional correction needs separate handling.
Boss offense remains individually authored. Preserve the current broad damage
feel and current calculation scale; correct known outliers deliberately.
