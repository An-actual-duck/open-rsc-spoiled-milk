#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
PRODUCTION_SESSION = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "production" / "ProductionSession.java"
)
PRODUCTION_RECIPE = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "production" / "ProductionRecipe.java"
)
PRODUCTION_STRUCT = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "struct" / "outgoing" / "ProductionInterfaceStruct.java"
)
ACTIONSENDER = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "ActionSender.java"
)
INTERFACE_OPTION_HANDLER = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "InterfaceOptionHandler.java"
)
CRAFTING = (
    ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "crafting" / "Crafting.java"
)
FLETCHING = (
    ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "fletching" / "Fletching.java"
)
SMITHING = (
    ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "smithing" / "Smithing.java"
)
DO_SKILL_INTERFACE = (
    ROOT / "Client_Base" / "src" / "com" / "openrsc" / "interfaces" / "misc" / "DoSkillInterface.java"
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        fail(message)


def main() -> None:
    session_text = PRODUCTION_SESSION.read_text(encoding="utf-8")
    recipe_text = PRODUCTION_RECIPE.read_text(encoding="utf-8")
    struct_text = PRODUCTION_STRUCT.read_text(encoding="utf-8")
    actionsender_text = ACTIONSENDER.read_text(encoding="utf-8")
    handler_text = INTERFACE_OPTION_HANDLER.read_text(encoding="utf-8")
    crafting_text = CRAFTING.read_text(encoding="utf-8")
    fletching_text = FLETCHING.read_text(encoding="utf-8")
    smithing_text = SMITHING.read_text(encoding="utf-8")
    do_skill_interface_text = DO_SKILL_INTERFACE.read_text(encoding="utf-8")

    require(
        session_text,
        "Collections.unmodifiableList(new ArrayList<>(recipes))",
        "ProductionSession should keep recipe lists immutable",
    )
    require(
        session_text,
        'throw new IllegalArgumentException("Unknown production session type: " + type);',
        "ProductionSession should reject unknown session types",
    )
    require(
        session_text,
        'throw new IllegalArgumentException("recipes must not be empty");',
        "ProductionSession should reject empty recipe lists",
    )
    require(
        recipe_text,
        "public int getFlags()",
        "ProductionRecipe should centralize production flag packing",
    )
    require(
        recipe_text,
        "ProductionInterfaceStruct.FLAG_LEVEL_MET",
        "ProductionRecipe should use named production flag constants",
    )
    require(
        struct_text,
        "public static final int ACTION_SHOW = 0;",
        "ProductionInterfaceStruct should define an explicit show action id",
    )
    require(
        struct_text,
        "public static final int ACTION_HIDE = 1;",
        "ProductionInterfaceStruct should define an explicit hide action id",
    )
    require(
        struct_text,
        "public static ProductionInterfaceStruct open(ProductionSession session)",
        "ProductionInterfaceStruct should expose an open(session) factory",
    )
    require(
        struct_text,
        "public static ProductionInterfaceStruct hide(int interfaceId)",
        "ProductionInterfaceStruct should expose a hide(interfaceId) factory",
    )
    require(
        actionsender_text,
        "ProductionInterfaceStruct.open(session)",
        "ActionSender should build production payloads through the shared struct factory",
    )
    require(
        actionsender_text,
        "ProductionInterfaceStruct.hide(interfaceId)",
        "ActionSender should close production payloads through the shared struct factory",
    )
    require(
        handler_text,
        "if (payload.amount < 1)",
        "InterfaceOptionHandler should reject non-positive production quantities",
    )
    require(
        handler_text,
        'session.getRecipeByItemId(payload.id) == null',
        "InterfaceOptionHandler should validate production recipe ids against the active session",
    )
    require(
        handler_text,
        'player.setSuspiciousPlayer(true, "production start quantity < 1")',
        "InterfaceOptionHandler should mark tampered production quantities as suspicious",
    )
    require(
        handler_text,
        '"production start recipe not present in session: " + payload.id',
        "InterfaceOptionHandler should mark tampered production recipe ids as suspicious",
    )
    require(
        handler_text,
        "clearProductionState(player, session);",
        "InterfaceOptionHandler should centralize production session cleanup",
    )
    require(
        handler_text,
        "handleLegacyProductionOption(player, option);",
        "InterfaceOptionHandler should explicitly reject legacy production option packets",
    )
    require(
        handler_text,
        '"unexpected legacy production option: " + option.name()',
        "InterfaceOptionHandler should flag legacy production option packets as suspicious",
    )
    require(
        crafting_text,
        "!session.isType(ProductionSession.TYPE_CRAFTING)",
        "Crafting production should use the shared session type helper",
    )
    require(
        fletching_text,
        "!session.isType(ProductionSession.TYPE_CRAFTING)",
        "Log shaping production should use the Crafting session type",
    )
    if "TYPE_FLETCHING" in fletching_text:
        fail("Live log shaping should not open Fletching production sessions")
    require(
        smithing_text,
        "!session.isType(ProductionSession.TYPE_SMITHING)",
        "Smithing production should use the shared session type helper",
    )
    if "flags |= 1;" in actionsender_text or "flags |= 2;" in actionsender_text:
        fail("ActionSender should not hand-pack production flags anymore")
    require(
        do_skill_interface_text,
        "case 169:\n\t\t\tcase 1955:\n\t\t\t\treturn \"Bars\";",
        "Client furnace category picker should label both legacy and MyWorld bar category icons as Bars",
    )

    print("PASS: Production UI structure validated")


if __name__ == "__main__":
    main()
