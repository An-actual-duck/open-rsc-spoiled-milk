# A09 contribution and kill-credit roles

## Status

Implemented: a typed factual NPC-lifetime ledger, compiled parity coverage,
and the repository-wide settlement audit below. This is not a reward-policy or
balance change. The previous four contribution maps in `Npc` now live behind
`NpcContributionLedger`; all existing settlement decisions retain their
established owners and order.

## Settled role boundary

`NpcContributionRole` records four factual categories:

| Role | Counts toward | Does not count toward |
| --- | --- | --- |
| `MELEE` | threat, top-damage selection, personal loot, kill contribution, melee XP | — |
| `RANGED` | threat, top-damage selection, personal loot, kill contribution, ranged XP | — |
| `MAGIC` | threat, top-damage selection, personal loot, kill contribution, Magic XP | — |
| `SUMMON` | threat, top-damage selection, personal loot, kill contribution | the legacy style-XP split |

The ledger is factual only. It preserves zero-damage entries, the legacy
per-role `int` accumulation, latest recorded username hash, and each
`HashMap`'s iteration behavior. It deliberately does not choose a final-hit
owner, award XP, roll a drop, consume a charge, or dispatch a callback.

## Current settlement order and owners

For an ordinary NPC death, `Npc.processLegacyDeath` retains this order:

1. Resolve the direct causal source: the current player `Mob`, or the related
   player of a summon/NPC source.
2. Clear periodic state and dispatch `KillNpcTrigger` with that causal player.
3. Snapshot positive, online personal-loot recipients from all four factual
   roles. Each gets `clamp(damage / NPC definition Hits, 0.05, 1.0)`.
4. Award legacy melee/ranged/Magic damage-share XP only from the three style
   roles; retain the current group multiplier and the recipient's Hits-focus
   split. Summon damage is intentionally absent.
5. Award pending Summoning XP only if the username-hash player is online,
   live, unexpired, and still has factual contribution.
6. Resolve the reward/kill-credit owner as the strictly greatest total factual
   damage across all four roles. If that UUID is no longer online, retain the
   existing fallback to the direct causal player, then the first personal-loot
   recipient.
7. Apply victory, Slayer/kill count, Death Ring/Amulet/Soul Amulet effects,
   logs and packets to that resolved owner; then generate every recipient's
   personal drops and invoke the existing listener boundary.

`Npc.killedBy` and A05's `DeathLifecycleAuthority` remain the exactly-once
owner for ordinary death processing. The typed ledger neither changes lethal
timing nor turns a direct damage source into a retroactive final-hit source.

## Live route inventory

| Route family | Factual role / source | Settlement owner | Notes |
| --- | --- | --- | --- |
| Primary melee and direct player melee helpers | `MELEE` | `CombatEvent`, `PvmMeleeEvent`, `Player` | A05 transaction remains the HP authority. |
| Primary ranged, thrown, and Magic projectile impacts | `RANGED` or `MAGIC` | `ProjectileEvent` | A06 launch/impact ledgers retain identity and resource timing. |
| Child/secondary player effects | role chosen by existing owner, most commonly Magic or melee | A05.4/A07 owners | Chain Lightning, Splinter, robes, cleave, amulets, dragon effects, and Divine Retribution retain their characterized attribution. |
| Delayed spells and spell-handler splash | `MAGIC` | `SpellHandler` / projectile owner | Delayed resource and effect ordering remain outside A09. |
| Generic poison | `MELEE` only when its A08 live source is a player | `PoisonEvent` | Source-less lethal poison removes the NPC without manufacturing player credit. A08 retains periodic lifecycle authority. |
| Combat summon impact | `SUMMON` | `Summoning.creditSummonProjectileDamage` | Owner receives factual loot/kill/threat contribution, never style XP. |
| Player's combat-summon engagement | pending Summoning XP keyed by username hash | `Summoning.recordCombatSummonEngagement` and `Npc` | It requires later factual contribution before award. |
| Personal normal/rare/hidden drops | all positive factual roles | `Npc.getPersonalLootRecipients`, `DropTable`, `NpcDrops` | Contribution scale gates rare rolls and hidden uniques; ordinary guaranteed drops retain existing behavior. |
| Plugin/quest kill handling | direct causal player, before top-damage reward selection | `KillNpcTrigger` | This is active compatibility, not a generic top-damage hook. |
| Script/admin/debug damage/removal | caller-defined causal `killedBy` or removal | caller / `Npc` lifecycle | Retained compatibility paths require individual policy review before any migration. |

The full caller audit includes `CombatEvent`, `PvmMeleeEvent`,
`ProjectileEvent`, `ThrowingEvent`, `SpellHandler`, `Player`, `Summoning`,
`PoisonEvent`, `ElderGreenDragonArmorEffect`, `DivineRetribution`, and the
development/quest compatibility callers. Every route reaches the same typed
factual recorder only where it previously called one of `Npc`'s four damage
methods; it does not receive a new credit rule.

## Eligibility, ties, and cleanup parity

- A zero-damage style touch remains an entry in the legacy style-contributor
  list, but does not establish positive damage, loot eligibility, or XP.
- A player who logs out remains in factual history but is excluded from XP and
  personal drops until their stable player UUID is online again. `Player` UUID
  is username-hash-derived, so the current relog behavior resolves the new
  live instance without moving contribution between accounts.
- A strict-greater comparison means equal damage does not displace the current
  candidate. Cross-role scans retain the existing order: melee, ranged,
  Magic, summon. Equal contenders within one legacy `HashMap` retain its
  current iteration behavior; A09 intentionally does not invent a new tie
  policy.
- Ordinary respawn reuses the NPC object only after clearing all ledger roles.
  A replacement NPC object begins with a fresh ledger. Terminal removal does
  not need a late reward cleanup because ordinary death already settles once;
  non-death removal remains an A10/lifecycle policy concern.
- Pending Summoning records are intentionally separate from factual damage:
  they are currently cleared after ordinary death settlement. Despawn-before-
  death behavior is documented below rather than silently changed.

## Active compatibility boundaries

The ten `PLUGIN_OWNED_DEATH_NPC_IDS` documented by A05 remain live quest and
tutorial compatibility. They bypass ordinary reward settlement and let
`KillNpcTrigger` decide removal/progression. The broader plugin set contains
quest, tutorial, arena, Combat Odyssey, minigame, and PK-bot `KillNpcTrigger`
implementations; they receive the causal attacker before top-damage selection.
Changing that contract would change quest semantics and is out of A09.

Likewise, `Server`'s stuck-NPC cleanup may call `killedBy(npc.getOpponent())`,
which its source explicitly notes is not necessarily the actual ranged/Magic
killer. The ledger preserves later top-damage selection but must not be used
to redefine the callback's causal source.

## Executable characterization

`CurrentCombatContributionLedgerCharacterization` runs in the authoritative
compiled combat gate and proves:

- typed role eligibility, per-role accumulation, zero-touch retention, latest
  hash behavior, and lifetime clear;
- separate style-XP versus summon factual roles;
- cross-role top-damage credit and minimum personal-drop scaling;
- offline exclusion and stable-UUID relog restoration;
- and fresh contribution state for a replacement NPC lifetime.

Existing gate scenarios continue to cover exact-once NPC listeners/death,
damage-share XP and Hits focus, summon credit, primary/secondary damage
attribution, poison source loss, and projectile lifecycle replacement. The
combined gate now has 128 scenarios.

## Explicitly deferred owner decisions

These are evidence-backed follow-ups, not permissions to change behavior:

1. **Tie policy:** preserving per-map iteration is required for parity. A
   deterministic player-ID or final-hit tie rule would be a visible policy
   change and needs owner approval plus migration/replay fixtures.
2. **Final-hit versus causal plugin source:** top contribution currently drives
   ordinary rewards, while `KillNpcTrigger` receives the causal source. A
   unified callback/reward identity would alter plugins and is an A11 adapter
   decision.
3. **Pending Summoning XP on non-death despawn:** it is retained on the NPC
   object until ordinary death settlement, while ordinary respawn clears the
   factual ledger. Defining whether such stale pending credit should expire,
   migrate, or clear requires a summon-economy decision and belongs with the
   A10 lifecycle inventory.
4. **Contribution cap semantics:** each role is capped independently when
   read, so a multi-style owner can exceed definition Hits in total factual
   credit. This is current behavior. A global cap would change loot, threat,
   ties, and XP and is not an A09 cleanup.
5. **Plugin-owned and script/admin kills:** their individual progression and
   compatibility paths must be characterized before they can share ordinary
   settlement adapters.

## Verification required for future changes

Run `./server/test_combat`, focused loot/drop, XP, Summoning, My World and
plugin fixtures, `./scripts/build-server.sh`, artifact exclusion, and
changed-code static analysis. Any change to a role boundary must additionally
exercise multi-player equal damage, direct causal/final-hit divergence, zero
factual damage, offline/relog, same-object respawn and replacement NPCs, and
exactly-once callbacks.
