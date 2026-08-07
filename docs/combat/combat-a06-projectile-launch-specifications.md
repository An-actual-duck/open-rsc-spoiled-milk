# A06.2 Projectile Launch Specifications

Status: implemented on the focused
`refactor/combat-projectile-launch-specifications` branch. Manager integration
remains the publication boundary.

## Scope and authority

A06.2 replaces positional launch construction at every tracked core and plugin
producer with an immutable `ProjectileLaunchSpecification`. The specification
freezes producer identity and parameters that were previously distributed
across constructor tails. It remains descriptive: producers still calculate
damage and mutate launch-time resources before construction, the event still
publishes its visual during construction, and the A06.1 ledger still owns the
single delayed callback.

This branch does not change:

- damage, accuracy, mitigation, prayer, effect, or recovery formulas;
- RNG calls or their order;
- rune, ammunition, thrown-item, cannonball, or quest-item consumption;
- XP, contribution, lifesteal, chase, death, or callback order;
- the one-tick delay, current 15-tile impact gate, cancellation policy, or
  launch/impact packet order; or
- compatibility constructor signatures used by external plugins and older
  content.

Impact eligibility remains an A06.3 decision. Resource and progression
settlement remains an A06.4 decision.

## Production producer inventory

The inventory contains 22 tracked construction sites. Each now selects a named
producer before constructing the event.

| Producer location | Sites | Stable producer identity | Preserved launch facts |
| --- | ---: | --- | --- |
| `RangeEvent.fire()` | 1 | `player-bow` | bow/crossbow visual, ammo identity, dragon breath, chase, one-per-mob |
| `ThrowingEvent.applyThrowingHit()` | 1 | `player-thrown` or `player-shuriken` | weapon identity, visual suppression, shuriken sibling allowance |
| `FireCannonEvent.fire()` | 1 | `cannon` | type 5, no chase, allow-multiple |
| `RangeEventNpc.run()` | 1 | `legacy-npc-ranged` | retained bronze-arrow compatibility path and ordinary visual |
| `Summoning.trySummonProjectileAttack()` | 1 | `summon-ranged` or `summon-magic` | summon style, no chase, bat visual suppression |
| `NpcBehavior.tryProjectileAttack()` | 2 | `npc-ranged` or `npc-magic` | profile visuals, element, impact, debuff, and dual-element proc data |
| `SpellHandler` cast switch | 9 | `magic-scripted-effect`, `player-magic`, or `player-iban-magic` | scripted callback chase; ordinary, god, Salarin, Iban, elemental, dual-element, blood, impact, and visibility data |
| `Admins.npcShootPlayer()` | 1 | `admin-debug` | caller-supplied signed damage/type compatibility |
| `LegendsQuestHolyWater.onOpInv()` | 1 | `legends-holy-water` | scripted one-tick empty callback and holy-water quest ownership |
| `GnomeNpcs.passToTeam()` | 1 | `gnome-ball` | benign ball visual/callback behavior |
| `GnomeBall` player/goal passes | 3 | `gnome-ball` | benign ball visual, transfer, and score callbacks |

`admin-debug`, `legacy-npc-ranged`, `legends-holy-water`, and
`gnome-ball` are no longer identifiable only through event class, attack type,
or participant kind. Their explicit producer keys distinguish maintained
compatibility paths without changing their broader gameplay behavior.

## Typed contract

`ProjectileLaunchSpecification.Producer` supplies a stable producer key,
broader family key, and launch kind (`DAMAGING`, `SCRIPTED_EFFECT`, or
`BENIGN_EFFECT`). The immutable specification additionally freezes:

- proposed damage and attack type;
- chase and duplication policy;
- poison weapon/ammunition identity;
- wind, water, earth, and fire debuff values;
- projectile visual, impact effect, and visual-suppression flag;
- NPC magic element;
- Startle, Acid, Frostbite, and Splinter parameters;
- blood-spell identity; and
- dragon-breath damage.

`ProjectileLaunchSnapshot` owns the built specification and continues to expose
its existing family, kind, attack, visual, impact, and damage accessors. It now
also exposes the stable producer key and the complete immutable specification.
Changing or reusing a builder after `build()` cannot alter a captured launch.

## Compatibility facades

All prior positional `ProjectileEvent` constructors remain available. They
construct a typed specification using the same type/caster/weapon inference
that previously selected the A06.1 family key. The following protected
subclass boundaries also remain:

- positional and typed `CustomProjectileEvent` constructors;
- positional and typed `BallProjectileEvent` constructors; and
- the package-local benign projectile constructor.

Scripted and benign constructors validate their specification kind before any
event or visual is created. The compatibility fixtures compare the richest
element/proc, blood-spell, dragon-breath, scripted, and ball positional forms
against equivalent typed launches field by field.

## Executable evidence

The combat gate grows from 72 to 74 scenarios:

1. `typed_projectile_producer_launches_have_stable_identity` constructs a real
   visual launch for every stable producer, checks producer/family/kind and the
   historically distinct damaging/scripted versus benign update owner, then
   cancels the delayed callback.
2. `projectile_specifications_freeze_constructor_tail_parameters` checks every
   former tail field, builder immutability, positional-facade parity, and that
   the visual is published once before the impact hitsplat.

The existing 72 scenarios continue to cover real ranged, thrown, shuriken,
magic, Iban, cannon, NPC, summon, delayed spell, secondary-effect, resource,
death, and compatibility behavior. Production artifact checks must continue to
exclude every `CurrentCombat*` fixture class.

## A06.3 handoff boundary

A06.3 may consume producer identity and the frozen launch/participant/location
facts to define impact eligibility per family. It must not infer a new policy
from the existence of the typed specification. Source/target death, removal,
logout, teleport, world or layer changes, long movement, and projectile
collision remain unchanged until separately characterized and approved.
