# Slayer Tower associates

Updated: 2026-09-21. NPC definitions, dialogue and reusable rank policy are
implemented. Placement and actual door/stair enforcement remain pending the
owner's map. No existing shop associate is replaced or moved.

## Definitions and access

All six are named **Monster Slayer Associate**, are noncombat NPCs, and offer
Talk-to only. Their examine text identifies the rank and armor for placement.
They reuse existing humanoid and equipment sprites; no new sprite art is needed.

| NPC ID | Floor entered | Minimum earned rank | Armor | Personality |
| ---: | ---: | --- | --- | --- |
| 871 | 1 (tower entrance) | Fledgling | Bronze | Excessively friendly |
| 872 | 2 | Adept | Iron | Protective and a little patronizing |
| 873 | 3 | Veteran | Steel | Bureaucratic |
| 874 | 4 | Elite | Mithril | Weary of rescuing people |
| 875 | 5 | Champion | Adamant | Morbid sense of humor |
| 876 | 6 | Hero | Rune | Quietly unsettling |

The first three have uncovered heads; the upper three wear their tier's full
helm. All have matching plate torso, plate legs, sword and shield. Female torso
variants are used for the iron and adamant associates. Server definitions load
after the eight monsters (ending at 870), preserving append-order IDs. Client
presentation matches the server catalog.

`MonsterSlayerTowerAccess` reads authoritative earned rank. Higher ranks,
including Legend, qualify for every lower floor. Unstamped players cannot enter
floor 1. Access never depends on combat level, point balances, equipment or
possession of flavor proof. `INITIATE` remains the internal key for Adept.
Reads/dialogue do not enroll, spend points or write progression. Unavailable or
invalid state fails closed, with a temporary verification message rather than
incorrectly telling an enrolled player to join again.

## Approved dialogue

### Floor 1 — Fledgling

Unjoined opening, as two separate lines:

> Welcome to the Slayer Tower, buddy.
>
> I can't let you in until you've joined the Monster Slayer's Guild, pal.

Player choices:

- **How do I join?** → "Head to Falador and speak to Hobart at the Rising Sun,
  friend. He'll get you started."
- **Okay, I'll come back later.** → "See you soon, buddy."

Eligible: "Go on through, friend."

### Floor 2 — Adept

Denied: "Slow down before you hurt yourself. Adept rank or higher past this point."

Eligible: "All right, up you go. Watch your step."

### Floor 3 — Veteran

Denied: "I don't see a Veteran button. No button, no passage. Regulations."

Eligible: "Your credentials are in order. Proceed."

Verified against `MonsterSlayerDialoguePlan.promotion(1)` and
`MonsterSlayerContacts.contactProof(2)`: Veterans receive a **button**, marked
"I heart PS". The crest belongs to **Hero** rank. This is flavor, not an item gate.

### Floor 4 — Elite

Denied: "Elite rank or higher. I'm tired of carrying people back down these stairs."

Eligible: "Through you go. Coming back down is your responsibility."

### Floor 5 — Champion

Denied: "Champions only. Everyone else tends to come back extra crispy."

Eligible: "A Champion! Lovely. Try not to scorch the handrail."

### Floor 6 — Hero

Denied: "Only Heroes beyond this door. Whatever you hear, don't answer it."

Eligible: "You've earned your way in. I hope that's a good thing."

Floors 2–6 have no menu branches. The unavailable-state line is
"I can't verify your Slayer rank right now. Please try again later."

## Later map integration

The owner places the associates and builds the tower. Once coordinates are
known, bind the actual entry doors/stairs to the matching
`MonsterSlayerTowerAccess` entry and call `allows(player)` at passage time.
The dialogue alone does **not** enforce a door or move the player. Do not cache
an earlier conversation result as access permission. Audit all alternate entry
routes; preserve safe exit/backtracking. No coordinates, spawn records, doors,
teleports, shops or task-rollout switches are changed by this preparatory work.

## Verification

- `ant test_slayer_tower_associates`: real server catalog/ID order, six armor
  sets, all eight ranks against all six floors with empty inventory and zero
  balances, dialogue branches, menu cancellation, existing NPC route isolation,
  invalid/missing state rejection, profile isolation and no movement/state writes.
- `python3 tests/myworld/test-monster-slayer-client-npc-definitions.py`: builds
  the client and compares all original contacts plus the six new definitions
  against server presentation data.
- `ant test_monster_slayer_tower_routes`: existing staged task routes/advice.
