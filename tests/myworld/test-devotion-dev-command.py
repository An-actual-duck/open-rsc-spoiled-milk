#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEVOTION = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "Devotion.java"
DEVELOPMENT = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "commands" / "Development.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    devotion = DEVOTION.read_text(encoding="utf-8")
    development = DEVELOPMENT.read_text(encoding="utf-8")

    require(devotion, "public static void setDevotionLevel", "direct devotion setter")
    require(devotion, "clampDevotionLevel(devotionLevel)", "devotion setter clamp")
    require(
        devotion,
        "clampedDevotionLevel * DevotionHalfOfferingBalance.HALF_UNITS_PER_DEVOTION_LEVEL",
        "level-to-exact-half-offering storage",
    )
    require(devotion, "ActionSender.sendDevotion(player);", "devotion client refresh")
    require(devotion, "ActionSender.sendEquipmentStats(player);", "dynamic equipment stat refresh")
    require(devotion, "player.getPrayers().deactivateOverflowingPrayers();", "prayer overflow cleanup")

    require(development, "import com.openrsc.server.content.Devotion;", "development devotion import")
    require(development, 'command.equalsIgnoreCase("devotion")', "devotion command branch")
    require(development, "setDevotion(player, command, args);", "devotion command dispatcher")
    require(development, "private void setDevotion(Player player, String command, String[] args)", "devotion command handler")
    require(development, "args.length != 1 && args.length != 2", "self or online-target syntax")
    require(development, "player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]))", "online target lookup")
    require(development, "targetPlayer.getPrayerBook()", "target current god line")
    require(development, "Devotion.setDevotionLevel(targetPlayer, godLine, devotionLevel);", "devotion setter call")
    require(development, "Devotion.MIN_DEVOTION_LEVEL", "minimum devotion bound")
    require(development, "Devotion.MAX_DEVOTION_LEVEL", "maximum devotion bound")
    require(development, "[value] OR ::", "documented command syntax")

    print("PASS: devotion dev command is wired")


if __name__ == "__main__":
    main()
