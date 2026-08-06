# A05.4E Delayed Spell Damage Transaction

## Bounded authority change

A05.4E migrates only delayed spell Hits and presentation mutations that the
current resolved-damage transaction can represent without changing behavior:

- every Salarin elemental-strike second hit uses the transaction for its Hits
  subtraction and damage update while intentionally emitting no second
  hitsplat; and
- nonlethal NPC children of god-spell and Iban Blast area effects use the
  transaction for their Hits subtraction, damage update, and standard
  hitsplat.

No formula, random draw, mitigation, target selection, rune cost, Magic XP,
contribution, chase, god-spell special, aggregate lifesteal, packet, scheduling,
or death authority moves. Each request contains damage already resolved by its
original caller. The scheduled `MiniEvent` remains the owner of the one-tick
delay and supplies its UUID to the request.

## Stable identities and presentation

All three families use `OWNED_EFFECT` and Magic style.

| Family | Stable effect identity | Event identity | Presentation |
| --- | --- | --- | --- |
| god-spell area child | `delayed-god-spell-secondary` | area `MiniEvent` UUID | damage update and standard hitsplat |
| Iban Blast area child | `delayed-iban-blast-secondary` | area `MiniEvent` UUID | damage update and standard hitsplat |
| Salarin strike second hit | `delayed-salarin-strike-secondary` | strike `MiniEvent` UUID | damage update only |

`DamageRequest.Presentation` makes the existing output contract explicit. Its
default is `DAMAGE_AND_HITSPLAT`, so earlier transaction callers retain their
behavior. `DAMAGE_ONLY` exists solely to represent legacy settlements such as
Salarin's delayed strike without inventing a hitsplat.

## Preserved family policies

### God spells and Iban Blast

Rune consumption and ordinary Magic XP remain at cast time. The primary
projectile still settles before the area event, and children remain unchanged
until the scheduled tick. Radius-two selection, signed-level filtering,
attackability, living-NPC and non-summon requirements, and Guard Dog area
suppression remain caller-owned.

The existing secondary formula and cap stay unchanged. Positive nonlethal
children retain Magic contribution followed by surviving-NPC chase. God-spell
special effects remain after each child, the primary special remains after all
children, and Saradomin healing remains one outer aggregate operation. Iban
Blast still applies no aggregate healing.

### Salarin's elemental strikes

The four special Salarin strikes retain their fixed primary damage, separately
rolled delayed damage, ordinary rune consumption, and intentional absence of
Magic XP. The delayed hit keeps player-only potion Magic mitigation, overwrites
the damage update from the primary hit, emits no second hitsplat or explicit
stat packet, records capped Magic contribution for an NPC, performs the
existing party update check, and invokes direct death only after contribution.

## Deliberate terminal compatibility boundary

Lethal god/Iban child hits remain on `Mob.damage(int)`. That helper does not
have the same terminal contract as `ResolvedDamageTransaction`: for an NPC it
invokes `killedBy(getOpponent())` before presentation and before the delayed
caster contribution, displays requested overkill, and leaves the pre-death raw
Hits value in place. The executable fixture pins all of those facts and proves
that no transaction result is emitted for the lethal target.

Moving that path would require either changing observable death order or
expanding the transaction into death authority. Both are explicitly outside
A05.4E. The non-NPC private-helper compatibility path is retained for the same
reason, although active god/Iban area selection supplies NPC children. A later
death-lifecycle branch must decide the intended terminal contract before these
paths can migrate.

## Executable parity

Three production-runtime scenarios were committed before production migration,
growing the combat gate from 63 to 66. They use `SpellHandler.process`, the
real walk-to cast action, production projectile and `MiniEvent` scheduling,
real rune inventory mutation, and deterministic production random seams. The
fixtures cover:

- god-spell resources, XP, one-tick scheduling, child eligibility, standard
  presentation, Magic contribution, chase, aggregate Saradomin lifesteal, and
  lethal helper ordering;
- Iban Blast resources, XP, one-tick scheduling, child eligibility,
  contribution, chase, and absence of lifesteal; and
- Salarin resources, absence of XP, primary-before-secondary order, exact
  combined damage, sparse presentation, and combined contribution.

Post-migration assertions pin each safe request's category, stable key, style,
event UUID, presentation policy, resolved and factual damage, overkill, and
terminal result. They also pin the deliberately retained lethal path's raw
Hits, callback count/order, contribution order, displayed overkill, and absence
from transaction observation.

## Exclusions and next boundary

A05.4E does not migrate `Mob.damage`, delayed environmental/script damage,
poison, burn, player-visible combat balance, or any death adapter. Typed poison
and burn provenance remains A08. Broad compatibility-helper migration remains
last, after its plugin callers are classified. Terminal god/Iban settlement is
a concrete input to the later atomic death-lifecycle work, not unfinished
authority silently claimed by this branch.
