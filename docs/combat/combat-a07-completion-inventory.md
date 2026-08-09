# A07 completion inventory

## Decision

A07 is complete after the bounded shared-policy extractions listed below. It
does not authorize a generic secondary-effect executor. Every remaining
descriptor was reviewed against its live owner and either already has the
smallest compatible shared boundary or has an intentional owner boundary that
would require a gameplay/authority decision to cross.

## Completed shared boundaries

- target selection: player-owned NPC radius, Chain Lightning traversal, and
  Splinter random-single selection;
- player equipment/on-hit policies: Ogre, Baby Dragon, Blue/Earth/Red/Black/
  King Black Dragon, Infernal Fire, Elder Green trigger, Bear Maul, melee
  dragon breath, Elemental Sword, Demon Pitchfork, Kolodion Fire Claw, melee
  and projectile damage composition;
- NPC child selection/presentation: Hell's Inferno and Death Robe terminal
  splash; and
- Chain Lightning's common gate, chance draw, hop loop, child payload decay,
  and visual cycle. Each event retains its own child damage settlement.

## Retained owner boundaries

| Descriptor group | Retained owner | Why A07 does not extract it further |
| --- | --- | --- |
| Compatibility poison/tutorial scripts | combat script loader | Dynamic script order and compatibility callbacks are data/content authority. |
| Poison, armor poison, dragon-poison marker state, and periodic pulses | A08 poison provenance | A08 owns durable source, replacement, lifecycle, and tick authority. |
| Cleric protection, Rally, Thorns, and status effects | Cleric runtime | Party/status lifecycle and direct-damage ordering are C08–C12 authority. |
| Summon mitigation, assist, lifesteal, and traits | `Summoning` | Summon ownership, friendly-fire, and lifecycle policies differ from actor hits. |
| Recoil, Frostbite, Divine Retribution, Ring of Life | A05.4 reflection owners | Their pending-hit reduction, recursion, ranged reset, and death adapters are deliberately distinct. |
| Blood Robe, Balrog, Scythe, Elder Dragon | their content/event owners | Each has a single live owner or a unique target, mitigation, delayed, or boss policy. |
| Projectile dual-element and ranged breath effects | `ProjectileEvent` | Launch snapshots, impact style, and element-specific state are unique to the projectile lifecycle. |
| Kill rewards, Death Ring, Death/Soul Amulets | `Npc.killedBy` / `Player` | Kill ownership and charge/reward ordering are kill-settlement authority, not on-hit policy. |
| God spells, Iban, Salarin delayed effects | `SpellHandler` | Launch resource and delayed-impact ownership must remain intact. |

No unresolved item has a safe behavior-preserving extraction left in A07. A
future change may revisit a row only with an owner-specific plan and executable
parity fixtures; it must not treat this inventory as permission to merge the
policies.

## Completion gates

The combined combat characterization suite covers the shared selection,
traversal, payload, presentation, callback, contribution, and child-death
contracts. The authoritative Ant core/plugin build and artifact exclusion check
remain required for any later owner-specific work.
