# MyWorld Testing Quick Reference

## Admin Test Commands

| Command | Purpose |
| --- | --- |
| `::hp1` / `::onehp` | Sets your current Hits to 1 without changing max Hits. |
| `::poisonme` / `::testpoison` | Clears current poison, then applies poison power 30 to yourself. |
| `::poisonme [power]` | Clears current poison, then applies the specified poison power to yourself. |
| `::deathdroptest [on/off]` / `::testdeathdrops [on/off]` | Toggles normal death drops for elevated accounts so soul item-saving can be tested. |
| `::sethp [hp]` | Existing general command for setting your current Hits to a specific value. |
| `::sethp [name] [hp]` | Existing general command for setting another online player's current Hits. |
| `::aggroall [radius]` / `::aggronear [radius]` / `::forceaggro [radius]` | Forces nearby attackable NPCs to attack you for multi-aggro testing. Radius defaults to 8 and caps at 20. |

## NPCs

| NPC | ID |
| --- | ---: |
| Lesser Demon | 22 |
| Lesser Demon variant | 181 |
| Skeleton | 40 |
| Skeleton variants | 45, 46, 50, 179, 195 |

## Runes

| Rune | ID |
| --- | ---: |
| Air-Rune | 33 |
| Mind-Rune | 35 |
| Water-Rune | 32 |
| Earth-Rune | 34 |
| Fire-Rune | 31 |
| Body-Rune | 36 |
| Cosmic-Rune | 46 |
| Chaos-Rune | 41 |
| Nature-Rune | 40 |
| Law-Rune | 42 |
| Death-Rune | 38 |
| Blood-Rune | 619 |
| Soul-Rune | 825 |
| Life-Rune | 37 |

## Food

Common cooked foods for healing tests:

| Food | ID |
| --- | ---: |
| Cooked meat | 132 |
| Salmon | 357 |
| Trout | 359 |
| Tuna | 367 |
| Swordfish | 370 |
| Lobster | 373 |
| Shark | 546 |
| Manta Ray | 1191 |
| Sea Turtle | 1193 |
| Cooked Ugthanki Meat | 1103 |
| Cooked Oomlie meat Parcel | 1269 |

Multi-bite / crafted foods:

| Food | ID |
| --- | ---: |
| Apple pie | 257 |
| Redberry pie | 258 |
| Meat pie | 259 |
| Half a meat pie | 261 |
| Half a redberry pie | 262 |
| Half an apple pie | 263 |
| Plain Pizza | 325 |
| Meat Pizza | 326 |
| Anchovie Pizza | 327 |
| Half Meat Pizza | 328 |
| Half Anchovie Pizza | 329 |
| Cake | 330 |
| Chocolate Cake | 332 |
| Partial Cake | 333 |
| Partial Chocolate Cake | 334 |
| Slice of Cake | 335 |
| Stew | 346 |
| Pineapple Pizza | 750 |
| Half pineapple Pizza | 751 |

## God Staffs

| Staff | ID |
| --- | ---: |
| Staff of Zamorak | 1216 |
| Staff of Guthix | 1217 |
| Staff of Saradomin | 1218 |
| Staff of Iban | 1000 |

## Metal Armor And Weapons

### Tin

| Item | ID |
| --- | ---: |
| Tin helmet | 1959 |
| Tin plate mail body | 1964 |
| Tin plate mail legs | 1963 |
| Tin gauntlets | 1960 |
| Tin greaves | 1961 |
| Tin Paladin Shield | 1962 |
| Tin Square Shield | 2224 |
| Tin Long Sword | 1998 |

### Rune

| Item | ID |
| --- | ---: |
| Large Rune Helmet | 112 |
| Rune plate mail body | 407 |
| Rune plate mail legs | 406 |
| Rune gauntlets | 1993 |
| Rune greaves | 1994 |
| Rune Paladin Shield | 404 |
| Rune Square Shield | 403 |
| Rune long sword | 75 |

Note: rune plate body/legs also have legacy IDs `401` and `402`; the MyWorld-overridden plate body/legs are `407` and `406`.

## Pickaxes

| Pickaxe | ID |
| --- | ---: |
| Tin Pickaxe | 1987 |
| Copper Pickaxe | 2047 |
| Bronze Pickaxe | 156 |
| Iron Pickaxe | 1258 |
| Steel Pickaxe | 1259 |
| Mithril Pickaxe | 1260 |
| Adamantite Pickaxe | 1261 |
| Titan Steel Pickaxe | 2048 |
| Orichalcum Pickaxe | 2049 |
| Rune Pickaxe | 1262 |

## Wool Armor And Staffs

| Item | ID |
| --- | ---: |
| Wool Hat | 2050 |
| Wool Robe Top | 2051 |
| Wool Robe Bottom | 2052 |
| Wool Gloves | 2794 |
| Wool Boots | 2795 |
| Basic staff | 100 |
| Tier 10 staff, Blood Staff | 2146 |

## Sapphire And Dragonstone Enchanted Jewelry

Each cell is `sapphire / dragonstone`.

| Altar | Ring | Necklace | Amulet |
| --- | ---: | ---: | ---: |
| Air | 1673 / 1677 | 1613 / 1617 | 1593 / 1597 |
| Mind | 3076 / 3080 | 1618 / 1622 | 1734 / 1738 |
| Water | 1678 / 1682 | 1623 / 1627 | 1598 / 1602 |
| Earth | 1683 / 1687 | 1628 / 1632 | 1603 / 1607 |
| Fire | 1688 / 1692 | 1633 / 1637 | 1608 / 1612 |
| Body | 3081 / 3085 | 1638 / 1642 | 1739 / 1743 |
| Cosmic | 1701 / 3111 | 1643 / 1647 | 1749 / 1753 |
| Chaos | 1314 / 1696 | 1648 / 1652 | 1719 / 1723 |
| Nature | 1316 / 1700 | 1653 / 1657 | 1744 / 1748 |
| Law | 1714 / 1718 | 1658 / 1662 | 1709 / 1713 |
| Death | 3086 / 3090 | 1663 / 1667 | 1724 / 1728 |
| Blood | 3091 / 3095 | 1668 / 1672 | 1729 / 1733 |
| Soul | 1705 / 1708 | 1759 / 1763 | 1754 / 1758 |
| Life | 3096 / 3100 | 3101 / 3105 | 3106 / 3110 |

Special legacy-backed jewelry:
- Chaos sapphire ring `1314` is Ring of recoil.
- Nature sapphire ring `1316` is Sapphire Ring of Nourishment.

## Jewelry Testing Status

Confirmed working:
- Elemental rings and necklaces apply flat offensive power bonuses.
- Mind jewelry effects are working.
- Body jewelry effects are working.
- Nature jewelry effects are working.
- Blood jewelry effects are working.
- Death jewelry effects are working.
- Chaos recoil and chaos amulet splash are working.
- Law ring and necklace banking are working, with charge display/depletion.
- Soul jewelry item-saving is working.
- Cosmic jewelry is working. Bounty amulets do not increase the chance to find
  a mining gem; they only double a rare gathering reward after it drops.

Pending:
- Consider a deterministic cosmic test command if field testing the amulet is
  too slow.

## Bank Shortcuts

- In the custom bank, `Ctrl` plus left-click on a bank item withdraws its full
  available quantity, subject to inventory capacity.
- `Ctrl` plus left-click on an inventory item while the custom bank is open
  deposits all copies of that item.
- Equipment mode retains its direct equip click behavior.
