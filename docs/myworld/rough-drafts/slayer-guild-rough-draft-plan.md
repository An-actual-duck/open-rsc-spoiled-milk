# Slayer Guild Rough Draft Plan

Status: superseded by
[`monster-slayer-guild-plan.md`](../in-progress-work-plans/monster-slayer-guild-plan.md).

This rough draft is kept as historical context. The current direction is now
the `Monster Slayer` guild plan: rank-and-points progression, deterministic
mandatory kill tasks, repeatable random kill tasks, no monster material
turn-ins, and new non-dragon-armor shop rewards. Dragon armor progression now
belongs to the dragon gear crafting plan.

This is an early concept document for turning the Combat Odyssey monster task
system into a broader Slayer Guild/faction mechanic. The important direction
change is that Slayer should not become a full visible skill right now. It
should behave more like guild rank, reputation, or devotion: a long-term combat
extension the player progresses through by proving they hunt monsters and by
turning in monster materials.

The current Combat Odyssey route asks for `101` tasks and `40,906` total kills
before awarding `Dragon Plate Mail Legs`. That is too much as a single isolated
quest grind, but it is a useful foundation for a game-wide monster-hunting
guild where the work is spread across the whole account journey and pays out
rewards along the way.

## Core Direction

- Build a Slayer Guild/faction, not a standalone Slayer skill.
- Progression is based on Slayer rank or standing rather than skill XP.
- Rank is earned through monster-hunting assignments and turn-ins.
- Turn-ins should focus heavily on reliable monster drops, especially 100%
  drops that represent proof of hunting.
- Higher ranks unlock better guild members, better assignments, better shops,
  and unique rewards.
- Slayer points are earned from accepted hunt proof and spent in rank-gated
  sect shops.
- `Dragon Plate Mail Legs` should become a high-rank Slayer Guild reward instead
  of requiring the old all-at-once Combat Odyssey grind. The player should reach
  that shop tier by progressing through the guild.
- Combat Odyssey should be borrowed from and eventually folded into this system
  as a capstone, legacy challenge, or high-rank guild assignment chain.

## Guild Structure

The guild should feel distributed rather than existing in a single building.
Major bases can live in existing combat prestige locations:

- Champions' Guild
- Heroes' Guild
- Legends' Guild

Smaller Slayer sects can appear in bars, taverns, and other social hubs around
the world. These lower-rank contacts give the system a grounded entry point
before the player reaches the major guilds.

Example early contact:

- A Slayer Guild member in the Varrock bar.
- They introduce the idea that the guild only respects proven monster hunters.
- They ask for simple monster materials and kill proof before recommending the
  player for the first rank.

Example tone:

> "Bring me 200 bones and 200 goblin hides and I'll recommend you for the rank
> of fledgling. And you better get your hands dirty, because I'll know if you
> don't."

## Proof Of Hunt

The important anti-cheese rule is that the player should not be able to simply
buy the required materials from other players and skip the hunting.

The mechanic should use both pieces:

- Tracked kill credit for the assigned monster family.
- Certified item turn-ins for the required monster drops.

That gives the guild two checks:

- Did the player actually hunt the requested monsters?
- Did the player bring the requested trophies/materials?

The item turn-in should use certs where possible so large requirements are not
inventory torture. If a turn-in asks for 200 bones and 200 goblin hides, the
player should be able to bring those in cert form.

Open implementation question:

- Should existing normal certs be accepted if kill credit is also required, or
  should Slayer-specific proof items be created so the materials cannot be
  traded into completion?

Current preference:

- Start with normal monster drops/certs plus kill tracking. The kill tracking is
  what prevents pure market bypassing.

## Core Loop

Basic loop:

1. Player talks to a Slayer Guild contact.
2. Contact offers a rank assignment or material contract.
3. Player kills the required monster families.
4. Server tracks kill credit against the assignment.
5. Player collects the required 100% drops or other monster materials.
6. Player returns with the materials, ideally in cert form.
7. Contact validates kill credit plus item turn-ins.
8. Player earns Slayer standing, Slayer points, rank progress, and any milestone
   rewards.
9. Higher rank unlocks stronger contacts, harder contracts, better reward shops,
   and better items to spend points on.

This should keep the same useful feeling as Slayer without requiring a new
skill slot, XP curve, or skill guide.

## Rank Progression

Ranks should be named and milestone-based. Exact names can change later.

Possible first structure:

| Rank | Typical location | Assignment examples | Reward direction |
| --- | --- | --- | --- |
| Adept | Small bars/taverns | bones, goblin hides, rat tails, spider parts | Basic guild access |
| Fledgling | Varrock/Falador contacts | goblins, skeletons, zombies, guards | First point shop |
| Hunter | Champions' Guild area | giants, darkwizards, hobgoblins, ogres | Stronger supplies, task tools |
| Stalker | Heroes' Guild area | demons, dragons, shadow warriors, paladins | Better shop, special tasks |
| Slayer | Legends' Guild area | black demons, black dragons, high-end monsters | Top-end shop |
| Master Slayer | Legends/high-end contact | capstone contracts, legacy Combat Odyssey tasks | Dragon Plate Mail Legs shop tier |

The rank ladder should not require the exact old Combat Odyssey total. Instead,
the old grind can be broken into many rank contracts and optional prestige
contracts.

## Point Shops And Repeatable Turn-Ins

Each meaningful Slayer Guild sect should have a point shop. As the player earns
rank and gains access to stronger sects, those shops should offer stronger and
more specialized rewards.

The basic model:

- Completing rank assignments grants Slayer points.
- Turning in accepted monster materials grants Slayer points.
- Higher-rank sects accept higher-tier materials and can pay better point rates.
- Each sect shop has its own unlock requirement based on Slayer Guild rank.
- Once a shop is unlocked, the player can keep using that contact for repeatable
  point turn-ins.

Example repeatable contact behavior:

> "I see you've killed 150 goblins since I last saw you, so I'll accept 150 more
> goblin hides and 150 more bones."

That allows the player to keep earning points after the rank requirement is
done. It also gives the system room to grow later: new unique rewards can be
added to existing shops without redesigning the whole guild.

Important rule:

- Repeatable turn-ins still need matching kill credit. A player should not be
  able to buy 150 goblin hides and 150 bones from someone else and immediately
  claim points unless they also have 150 eligible goblin kills recorded since
  the last accepted turn-in.

Top-end shop:

- The `Dragon Plate Mail Legs` should live in the highest relevant Slayer Guild
  shop.
- Unlocking that shop should effectively represent completing the current
  Combat Odyssey journey, but spread across rank progression instead of one
  isolated quest wall.
- Buying the legs should cost a substantial number of Slayer points, so reaching
  the shop is the access gate and continued hunting is the currency gate.

## Rewards

Rewards should arrive throughout the progression, not only at the end.

Potential reward categories:

- Guild shops with combat supplies.
- Rank-gated point shops.
- Access to stronger Slayer contacts.
- Special monster contracts.
- Task rerolls or assignment choices.
- Monster tracking convenience commands.
- Unique cosmetics or titles.
- Combat utility items.
- Dragon equipment rewards at the highest ranks.

Top-end reward:

- `Dragon Plate Mail Legs` should be a major high-rank guild reward.
- It should be sold in a top Slayer Guild point shop after the correct rank is
  unlocked.
- It can still require a capstone contract or major material turn-in before that
  shop becomes available.
- It should no longer require completing every old Combat Odyssey task at full
  count in one isolated quest chain.

## Existing System To Borrow

Borrow directly or adapt from Combat Odyssey:

- Data-driven task definitions.
- Monster family lists with multiple NPC IDs.
- Current task storage.
- Current kill count storage.
- Completed task tracking.
- NPC dialogue hooks for assignment and completion.
- Tier or milestone progression.
- Developer tools for debugging and testing assignments.

Likely changes needed:

- Replace quest-only progress with guild rank/standing.
- Add material turn-in requirements to assignments.
- Add support for cert-based material turn-ins.
- Track assignment-specific kill credit.
- Track repeatable kill credit since the last accepted turn-in per contact or
  monster family.
- Add rank thresholds and contact eligibility.
- Add guild point shops and reward claim dialogue.
- Allow several NPC contacts to use the same underlying guild state.

## Combat Odyssey Integration

Combat Odyssey should eventually become one of these:

- A legacy Slayer Guild quest line.
- A high-rank capstone contract chain.
- A prestige challenge after reaching high Slayer rank.
- Biggum Flodrot's personal monster-hunting challenge within the guild.

Preferred direction:

- Keep Biggum Flodrot and the personality of the quest.
- Do not throw away the monster task work already built.
- Reduce the mandatory grind if it remains a direct reward path.
- Move the broad monster-hunting work into Slayer Guild ranks and contracts.
- Keep `Dragon Plate Mail Legs` tied to top-end monster-hunting achievement.
- Treat the final shop unlock as the new equivalent of completing the current
  Combat Odyssey progression.

## Turn-In Material Ideas

The first pass should prioritize drops that already exist and can be explained
as monster trophies, hides, bones, fangs, scales, eyes, ashes, or other remains.

Good early candidates:

- Bones
- Goblin hides
- Zombie eyes
- Bat-related drops
- Spider-related drops
- Dragon scales
- Demon ashes or demon remains if supported

Design preference:

- Use 100% drops for core rank contracts because they make progress predictable.
- Keep accepting those 100% drops after a shop is unlocked so the player can
  continue earning points from that sect.
- Use uncommon drops for bonus contracts or optional extra standing.
- Avoid requiring rare drops for basic rank advancement.

## Open Questions

- Should Slayer standing be a hidden numeric value, a visible rank only, or
  both?
- Should Slayer points be global across all sects, or should each sect have its
  own point balance?
- Should stronger sects pay better point rates for the same material, or only
  accept harder monster materials?
- Should contacts offer one active assignment at a time, or should rank
  contracts be fixed checklists?
- Should the guild accept all matching certs if kill credit is satisfied?
- Should repeatable kill credit be tracked per monster family, per contact, or
  as a general Slayer Guild ledger?
- Should higher-rank contracts require monster materials from several families?
- Should party kills count if the player dealt damage?
- Should summon damage count toward kill credit?
- Should existing Combat Odyssey progress convert into Slayer Guild standing?
- Should completed Combat Odyssey grant a special title, rank skip, or prestige
  reward when the new system is added?
- Should dragon weapon and armor acquisition be divided between Slayer Guild,
  crafting, and boss drops?

## First Implementation Plan To Write Later

Before implementation, write a proper in-progress work plan that covers:

- Rank names and thresholds.
- First set of guild contacts.
- First set of monster material turn-ins.
- Slayer point earning and spending rules.
- First set of sect shops and shop unlock requirements.
- Cert turn-in rules.
- Kill-credit storage.
- Repeatable turn-in tracking.
- Player save/cache migration.
- Reward shop or reward dialogue design.
- Combat Odyssey migration or coexistence rules.
- Tests for assignment tracking, kill validation, material turn-ins, rank
  advancement, and reward claims.
