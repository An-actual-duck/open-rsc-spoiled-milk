#!/usr/bin/env python3

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")


def main() -> None:
    path = ROOT / "docs" / "myworld" / "work-items.md"
    text = path.read_text()

    require(text, "Reward-skill remaps", "remap section")
    require(text, "`Attack` XP -> `Melee`", "attack remap")
    require(text, "`Defense` XP -> `Melee`", "defense remap")
    require(text, "`Strength` XP -> `Melee`", "strength remap")
    require(text, "`Fletching` XP -> `Crafting`", "fletching remap")
    require(text, "### Observatory", "observatory section")
    require(text, "### Tourist Trap", "tourist trap section")
    require(text, "### Hazeel Cult", "hazeel section")
    require(text, "### Temple of Ikov", "ikov section")
    require(text, "### Shield of Arrav", "arrav section")
    require(text, "### Family Crest", "family crest section")
    require(text, "### Legends Quest", "legends section")
    require(text, "grant all `12` training rewards", "legends default")


if __name__ == "__main__":
    main()
