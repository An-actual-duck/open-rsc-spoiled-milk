#!/usr/bin/env python3

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")


def main() -> None:
    path = ROOT / "docs" / "myworld" / "work-items.md"
    text = path.read_text()

    require(text, "## Full Quest Shortcut Matrix", "full matrix heading")
    require(text, "## Clarifications To Review", "clarifications heading")
    require(text, "## Quest Shortcut Status", "implemented rollout status heading")
    require(text, "The current free, members, and MyWorld shortcut rollout is implemented.", "implemented rollout status")
    require(text, "deferred until limited-release field sessions", "post-release branch testing status")

    free_quests = [
        "Black Knights' Fortress",
        "Cook's Assistant",
        "Demon Slayer",
        "Doric's Quest",
        "Dragon Slayer",
        "Ernest the Chicken",
        "Goblin Diplomacy",
        "Imp Catcher",
        "The Knight's Sword",
        "Pirate's Treasure",
        "Prince Ali Rescue",
        "Romeo & Juliet",
        "Sheep Shearer",
        "Shield of Arrav",
        "The Restless Ghost",
        "Vampire Slayer",
        "Witch's Potion",
    ]
    members_quests = [
        "Biohazard",
        "Clock Tower",
        "Druidic Ritual",
        "Dwarf Cannon",
        "Family Crest",
        "Fight Arena",
        "Fishing Contest",
        "Gertrude's Cat",
        "The Hazeel Cult",
        "Hero's Quest",
        "Jungle Potion",
        "Lost City",
        "Merlin's Crystal",
        "Monk's Friend",
        "Murder Mystery",
        "Observatory",
        "Plague City",
        "Scorpion Catcher",
        "Sea Slug",
        "Sheep Herder",
        "Temple of Ikov",
        "The Holy Grail",
        "Tree Gnome Village",
        "Tribal Totem",
        "Waterfall Quest",
        "Witch's House",
        "Digsite",
        "Grand Tree",
        "Legends' Quest",
        "Shilo Village",
        "Tourist Trap",
        "Underground Pass",
        "Watchtower",
        "Peeling the Onion",
    ]

    for quest in free_quests + members_quests:
        require(text, f"`{quest}`", f"{quest} entry")

    clarifications = [
        "Shield of Arrav / Hero's Quest state",
        "Biohazard / Underground Pass chain",
        "Dwarf Cannon package",
        "Observatory generosity",
        "Tourist Trap generosity",
        "Legends' Quest generosity",
        "Hazeel Cult branch fidelity",
        "Temple of Ikov canonical state",
        "Gertrude's Cat consumables",
        "Tree Gnome Village memorabilia",
        "Underground Pass staff state",
    ]
    for index, title in enumerate(clarifications, start=1):
        require(text, f"### {index}. {title}", f"{title} clarification")


if __name__ == "__main__":
    main()
