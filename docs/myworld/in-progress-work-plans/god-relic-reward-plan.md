# God Artifact Reward Plan

This document forks the prayer-side god equipment plan into a dedicated reward
track for god artifacts. It supersedes the simpler "pray at 1000 devotion for a
god staff" direction where the two conflict.

Terminology note:

- `Artifact` is the player-facing and design-facing name for this system going
  forward.
- Older notes may still say `relic`; those references mean the same artifact
  reward pool unless a future cleanup explicitly separates the terms.

## Goals

- Make god equipment feel like direct divine rewards rather than ordinary
  crafted or Mage Arena rewards.
- Use Worship level and devotion as the main access gates.
- Keep artifact rewards aligned with the player's current god.
- Prevent duplicate artifact rewards for the same god line.
- Cover all combat styles with artifact options.
- Move god staves and god capes out of Mage Arena-centered reward logic.

## God Artifact Set

God artifacts are high-end god-aligned equipment awarded by the player's god.

Existing artifacts already in game:

- Saradomin Cape
- Zamorak Cape
- Guthix Cape
- Staff of Saradomin
- Staff of Zamorak
- Staff of Guthix

Initial implementation checkpoint:

- the first live artifact pool awards the existing cape, staff, and mace for each god
- paladin shields and crossbows remain future artifact expansion work
- this keeps the reward logic live without blocking on new shield/crossbow item
  creation

New artifacts needed for combat-style coverage:

- Mace of Saradomin
- Mace of Zamorak
- Mace of Guthix
- Saradomin Paladin Shield
- Zamorak Paladin Shield
- Guthix Paladin Shield
- Saradomin Crossbow
- Zamorak Crossbow
- Guthix Crossbow

Each god should therefore have five artifacts:

- cape
- staff
- mace
- paladin shield
- crossbow

Design intent:

- cape and staff cover Magic and god identity
- mace covers melee
- paladin shield covers defensive Prayer/combat identity
- crossbow covers ranged

Existing-content note:

- There is already an old `Mace of Zamorak` item in the game. Implementation
  must audit whether it should become the Zamorak artifact mace, be migrated, or
  be separated from the new artifact system to avoid duplicate/confusing Zamorak
  maces.

## Reward Requirements

To request a god artifact, the player must meet all of these:

- Worship level `80`
- `800` devotion with the aligned god
- active worship/alignment with that god
- praying at the matching god altar
- at least one unowned artifact remaining in that god's artifact pool

Reward cost:

- claiming an artifact removes `400` devotion from that god
- devotion loss should happen only after an artifact is successfully awarded

The devotion cost means a player at exactly `800` devotion drops to `400` after
claiming. A player at `1000` devotion drops to `600`.

Devotion is an acquisition gate, not a wield gate. A player who spends `400`
devotion to claim an artifact must still be able to equip and use that artifact
after the claim.

Equipment requirements:

- all artifacts remain aligned to their matching god and should only function
  while the player worships that god
- maces require Worship `80` plus the normal melee/Attack requirement
- staves require Worship `80` plus the normal Magic requirement
- capes require Worship `80` plus matching worship/alignment
- future shield and crossbow artifacts should follow their combat role's normal
  stat requirement plus Worship `80`
- artifacts should not require `800` current devotion to equip

## Altar Flow

The artifact dialogue is a hidden qualifying trigger, not a visible altar menu.
When a qualified aligned player prays at the matching god altar, the player
receives dialogue with that god. If the player does not meet the requirements,
the altar should behave normally and give no hint that artifact rewards exist.

Dialogue purpose:

- acknowledge the player's devotion
- offer the option to request an artifact
- make it clear that accepting costs devotion
- award a random unclaimed artifact if the player accepts

The player does not choose the exact artifact. They request an artifact, and the
god awards one from the remaining aligned artifact pool.

No failure-message design:

- do not show low-Worship, low-devotion, wrong-god, or all-claimed artifact hints
  from ordinary altar prayer
- if requirements are not met, do only the normal altar behavior
- if the player qualifies but declines the offer, do not charge devotion
- if the player qualifies but cannot receive the item because of inventory or
  transactional safety, show only the minimum practical item-delivery message
  needed to avoid item loss

## Artifact Selection Rules

Artifact selection must follow these rules:

- only the player's aligned god can award artifacts
- only artifacts belonging to that god can be awarded
- the player never receives the same artifact twice from that god's artifact pool
- the exact artifact is unknown before the reward is granted
- if all aligned artifacts have already been claimed, the altar should not
  trigger artifact dialogue and should not charge devotion

Suggested implementation model:

- track claimed artifacts with per-player cache keys
- derive the eligible reward list from the player's active god
- filter out already claimed artifacts
- select randomly from remaining eligible artifacts
- award the item
- record the artifact as claimed
- subtract `400` devotion

If an artifact item is later lost, the "never twice" rule means
replacement/reclaim must be a separate explicit system rather than another
random artifact claim.

## God Alignment Pools

Saradomin artifact pool:

- Saradomin Cape
- Staff of Saradomin
- Mace of Saradomin
- Saradomin Paladin Shield
- Saradomin Crossbow

Zamorak artifact pool:

- Zamorak Cape
- Staff of Zamorak
- Mace of Zamorak
- Zamorak Paladin Shield
- Zamorak Crossbow

Guthix artifact pool:

- Guthix Cape
- Staff of Guthix
- Mace of Guthix
- Guthix Paladin Shield
- Guthix Crossbow

## Artifact Dialogue Draft

Shared player options:

- `Yes, I desire it.`
- `No, I am not worthy.`

Saradomin should feel benevolent, wise, and calm.

```text
Saradomin:
"You have walked long in the light of wisdom."
"Your devotion has not gone unseen."
"If you desire it, I will entrust you with one of my sacred artifacts."

On accept:
"Then carry it with purpose, not pride."
"Let it serve as a reminder that power is a duty."

On decline:
"Wisdom often begins with restraint."
"Return when your heart is ready."
```

Guthix should feel neutral, natural, and balanced.

```text
Guthix:
"You have kept faith with the balance."
"Devotion given freely returns in its own season."
"If you desire it, I will grant you one artifact of the natural order."

On accept:
"Then let it be used with balance."
"Take only what is needed. Give back what you can."

On decline:
"Then the balance remains undisturbed."
"Return when the moment is right."
```

Zamorak should feel wrathful, punishing, and contemptuous of weakness.

```text
Zamorak:
"You have endured, and you have not broken."
"Your devotion has strength enough to be rewarded."
"If you dare claim it, I will grant you one of my artifacts."

On accept:
"Then take it, and prove you deserved it."
"Weak hands will make even power useless."

On decline:
"Then crawl away with your doubt."
"Return when your will is sharper."
```

## God Excommunication

God excommunication is a permanent late-game dedication path for players who
want to fully commit to one god. It starts when a player destroys another god's
artifact at the altar of their preferred god.

This must be treated as irreversible account progression, not a normal item
exchange.

### Trigger

The player must:

- be actively aligned with the chosen god
- be praying at the chosen god's altar
- have an artifact from a different god available to destroy
- explicitly confirm the permanent excommunication warning

The destroyed artifact's god becomes the excommunicated god. The third god
becomes the neutral god.

Example:

- player is aligned with Saradomin
- player destroys a Zamorak artifact at a Saradomin altar
- Saradomin becomes the permanent chosen god
- Zamorak becomes permanently hostile at `-1000`
- Guthix becomes permanently neutral at `0`

### Required Warning

Before any item, devotion, alignment, or cache state changes, the game must show
a strong warning that explains:

- this permanently locks the player to the chosen god
- the player will no longer be able to switch god alignments
- the destroyed artifact's god will become permanently `-1000` devotion
- the untouched god will become permanently `0` devotion
- all other-god artifacts in inventory, worn equipment, and bank will be removed
- the action cannot be undone through ordinary gameplay

Implementation should use a deliberate confirmation step rather than a single
dialogue click.

### Excommunication God-Communication Event

Excommunication should reuse the same broad "god communication" idea introduced
by relic attainment, but with much heavier presentation. The relic flow is a
reward audience with the player's god. Excommunication is a hostile divine
judgement from the god being rejected.

This is a good candidate for the first RuneScape Classic remaster sequence that
intentionally uses camera and lighting as part of gameplay presentation.

Target sequence:

- freeze or heavily restrict player movement while the event plays
- swing the camera around until it faces the player
- tilt the camera down toward an eye-level composition
- zoom in on the player
- dim the scene to low brightness
- optionally suppress or soften ordinary UI distractions while dialogue is
  active
- present special god text treatment for the excommunicated god
- play that god's monologue warning the player about the horrible consequence
  they should expect for their decision
- restore the player's previous camera, zoom, brightness, and UI state after
  the sequence completes

The god monologues are owner-authored content. AI should not invent final
speech text unless explicitly asked. Implementation can add placeholders or
wire the event system, but the final Saradomin, Zamorak, and Guthix speeches
should come from the project owner.

Presentation goals:

- make the choice feel permanent and weighty
- make the rejected god feel personally aware of the player's betrayal
- use the new renderer's camera, zoom, tilt, lighting, and text capabilities
  without changing the underlying RuneScape Classic world style
- keep the sequence deterministic and recoverable if the client disconnects
  mid-event

Technical notes:

- The server should own the permanent excommunication state change.
- The client can own the cinematic presentation once the server starts the
  confirmed event.
- The state change should not depend on the client successfully finishing the
  visuals; if a disconnect happens after confirmation, the account state still
  needs to resolve safely.
- If possible, apply the permanent account changes at a clear commit point
  after the warning confirmation but before or during the cinematic, then make
  the cinematic a presentation of the already-confirmed decision.
- Any camera override must preserve and restore the player's prior camera
  preferences.

### Permanent Consequences

After confirmation:

- lock the player's alignment permanently to the chosen god
- set the chosen god's devotion to `1000`
- increase the chosen god's devotion cap from `1000` to `2000`
- permanently lock the excommunicated god's devotion at `-1000`
- permanently lock the neutral god's devotion at `0`
- remove all artifacts from non-chosen gods from inventory, worn equipment, and
  bank
- prevent future artifact rewards from non-chosen gods
- prevent future alignment switching

The devotion locks should be enforced anywhere devotion can be gained, lost, or
set directly.

### Rewards And Unlocks

The player receives:

- all remaining unclaimed artifacts from the chosen god
- a sizable Worship XP award
- access to new prayers tied to full dedication
- the ability for chosen-god blessed armor and weapons to progress toward
  tier-11 equivalency as devotion climbs above `1000`

Awarding the remaining chosen-god artifacts must still avoid duplicates. If the
player already has some chosen artifacts, only missing artifacts are awarded.

Inventory overflow needs an explicit implementation decision. Options:

- require enough free inventory space before confirmation
- place overflow artifacts at the player's feet
- send overflow artifacts to the bank if space exists there

The safest initial implementation is to require enough free inventory space
before allowing confirmation, because this feature has permanent consequences.

### Devotion Above 1000

After excommunication, devotion above `1000` should require more offerings than
ordinary devotion progression.

Design intent:

- `0-1000` remains the normal devotion climb
- `1000-2000` is a slower dedication climb
- tier-11 blessed equipment equivalency is only reached through this higher
  devotion range

The exact offering curve is still open. Implementation should make the scaling
data-driven or centralized so the cost curve can be tuned without touching every
offering script.

### Implementation Notes

State likely needed per player:

- permanent chosen god
- excommunicated god
- neutral god
- devotion cap override for chosen god
- alignment-switch lock
- excommunication completed flag

Systems that must respect the new state:

- alignment switching
- devotion gain/loss
- devotion caps and clamps
- god aggression logic
- god altar reward dialogue
- artifact claim tracking
- bank, inventory, and equipment cleanup
- Worship skill tab and devotion UI
- blessed equipment scaling
- prayer unlock checks

The excommunication flow should be transactional where possible: validate all
requirements first, then apply all state changes together after confirmation.

## Mage Arena Separation

This plan overlaps with the tier-11 magic gear plan.

Relevant direction from that plan:

- god staves should no longer be Mage Arena rewards
- god-staff acquisition moves to devotion altar artifact rewards
- god capes should no longer be Mage Arena rewards
- Mage Arena should instead focus on high-end non-god Magic staves:
  - Staff of Elements
  - Staff of Power
  - Staff of Enlightenment
- Mage Arena now sends players to the chamber after the Kolodion fight to claim
  one combination staff from the Elemental, Power, or Enlightenment stone, then
  lets the Chamber Guardian sell the remaining combination staves.
- Mage Arena stones no longer award god capes or set god-choice state.

God capes also become part of the artifact reward pool here. Their future handling
should be implemented through Prayer/devotion altar rewards rather than
restoring the old Mage Arena god-stone reward flow.

## God Spell And Charge Follow-Up

God-staff spell casting was tied to Mage Arena mechanics. The old Mage Arena
cast-count training and outside-arena unlock checks have been removed. Before
the artifact system is implemented, audit:

- matching god-staff requirements for god spells
- Charge spell behavior
- whether god spell access should require the matching artifact staff, devotion,
  altar dialogue, or another god-aligned unlock
- reclaim rules for god staves and capes once artifact rewards exist

Target direction:

- god artifact acquisition comes from Prayer/devotion altar content
- Mage Arena no longer exists primarily to award god equipment
- Charge is no longer part of god-spell power. God spells use their normal max
  hit unless any god cape is equipped; with a god cape, the old charged max-hit
  caps apply directly.
- God capes are support artifacts rather than combat-stat gear. They should
  provide a flat `+10` Prayer bonus, no normal armour/weapon/Magic stat gains,
  and improve god-aligned effects such as reducing special-prayer allocation
  cost from the no-cape cost back to the original cape-supported cost.
- god spell access should either become devotion/artifact driven or require the
  matching god staff directly. It should not return to Mage Arena cast-count
  training.

## Open Decisions

- Exact item IDs for the nine new artifacts.
- Whether artifact items are tradeable.
- Whether artifacts can be reclaimed if lost, and at what cost.
- Whether claiming an artifact should require inventory space or drop overflow
  at the player's feet.
- Whether artifact claim order should be fully random or use weighted
  randomness.
- Whether existing owned god capes and god staves should automatically count as
  already claimed artifacts.
- How to migrate existing Mage Arena completion and god-choice cache state.
- Whether god artifacts should scale with current devotion after being claimed, or
  have fixed high-end stats.
- Exact stat budgets for artifact mace, paladin shield, crossbow, cape, and staff.
- How the legacy Mace of Zamorak should be handled.
- Exact Worship XP award for god excommunication.
- Exact offering curve for devotion `1000-2000`.
- Which new prayers unlock after god excommunication.
- Whether excommunication requires only one off-god artifact or a larger artifact
  prerequisite.
- How the devotion UI should display permanently locked gods and the increased
  devotion cap.
- Final owner-written Saradomin, Zamorak, and Guthix excommunication
  monologues.
- Exact camera timing, text styling, and lighting values for the
  excommunication god-communication cinematic.
