#!/usr/bin/env python3

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise AssertionError(f"unexpected {label}: {needle}")


def main() -> None:
    guide = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/QuestGuideInterface.java").read_text()
    client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()

    require(guide, "safeQuestGuideLines(mc.getQuestGuideRequirement())", "safe requirement iteration")
    require(guide, "safeQuestGuideLines(mc.getQuestGuideReward())", "safe reward iteration")
    require(guide, "for (int i = 0; i < questItems.size() + trailingRows; i++)", "native scrollbar includes final-window padding")
    require(guide, "for (int i = 0; i < questItems.size(); i++)", "render list starts at zero")
    require(guide, "int maxTextWidth = width - 16;", "stable quest guide wrap width")
    require(guide, "customAdd(text.substring(split).trim(), font, color);", "recursive trimmed wrapping")

    reject(guide, "for (int i = -1; i < questItems.size(); i++)", "negative quest item loop")
    reject(guide, "width - x - 8", "screen-position based wrapping")

    require(client, "int[] questIdByListIndex = new int[questNames.length + 1];", "quest guide visible-row id map")
    require(client, "questIdByListIndex[index] = questNum;", "quest guide stores real quest id")
    require(client, "position < questNames.length && this.questNames[position] != null", "quest guide selected id null guard")
    require(client, "private boolean hasQuestGuideData(int chosen)", "quest guide data bounds guard")
    reject(client, "getControlSelectedListIndex(this.controlQuestInfoPanel) - 1", "compact quest row used as quest id")


if __name__ == "__main__":
    main()
