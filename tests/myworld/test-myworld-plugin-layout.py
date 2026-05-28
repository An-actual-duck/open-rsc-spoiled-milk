#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]

EXPECTED_LAYOUT = {
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "enchanting"
    / "Enchanting.java": "package com.openrsc.server.plugins.custom.myworld.skills.enchanting;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "enchanting"
    / "LawJewelry.java": "package com.openrsc.server.plugins.custom.myworld.skills.enchanting;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "runecraft"
    / "Runecraft.java": "package com.openrsc.server.plugins.custom.myworld.skills.runecraft;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "runecraft"
    / "RawRuneStone.java": "package com.openrsc.server.plugins.custom.myworld.skills.runecraft;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "runecraft"
    / "RuneTalisman.java": "package com.openrsc.server.plugins.custom.myworld.skills.runecraft;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "misc"
    / "GrapeEmpowerment.java": "package com.openrsc.server.plugins.custom.myworld.misc;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "misc"
    / "PeelingTheOnionItems"
    / "MakeoverWaiver.java": "package com.openrsc.server.plugins.custom.myworld.misc.PeelingTheOnionItems;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "misc"
    / "PeelingTheOnionItems"
    / "OgreRecipes.java": "package com.openrsc.server.plugins.custom.myworld.misc.PeelingTheOnionItems;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "misc"
    / "PeelingTheOnionItems"
    / "YellowgreenClay.java": "package com.openrsc.server.plugins.custom.myworld.misc.PeelingTheOnionItems;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "misc"
    / "PeelingTheOnionItems"
    / "LeatherVestCrafting.java": "package com.openrsc.server.plugins.custom.myworld.misc.PeelingTheOnionItems;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "npcs"
    / "Sedridor.java": "package com.openrsc.server.plugins.custom.myworld.npcs;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "npcs"
    / "Kresh.java": "package com.openrsc.server.plugins.custom.myworld.npcs;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "quests"
    / "free"
    / "PeelingTheOnion.java": "package com.openrsc.server.plugins.custom.myworld.quests.free;",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "itemactions"
    / "RunecraftPotion.java": "package com.openrsc.server.plugins.custom.myworld.itemactions;",
}

RETIRED_LAYOUT = [
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "skills"
    / "enchanting"
    / "Enchanting.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "skills"
    / "enchanting"
    / "LawJewelry.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "misc"
    / "EnchantDragonstoneJewellery.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "skills"
    / "runecraft"
    / "Runecraft.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "skills"
    / "runecraft"
    / "RawRuneStone.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "skills"
    / "runecraft"
    / "RuneTalisman.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "misc"
    / "GrapeEmpowerment.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "misc"
    / "PeelingTheOnionItems"
    / "MakeoverWaiver.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "misc"
    / "PeelingTheOnionItems"
    / "OgreRecipes.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "misc"
    / "PeelingTheOnionItems"
    / "YellowgreenClay.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "misc"
    / "PeelingTheOnionItems"
    / "LeatherVestCrafting.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "npcs"
    / "Sedridor.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "npcs"
    / "Kresh.java",
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "itemactions"
    / "RunecraftPotion.java",
]


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def ensure_expected_layout() -> None:
    for path, package_line in EXPECTED_LAYOUT.items():
        if not path.exists():
            fail(f"Missing MyWorld-owned handler: {path}")
        text = path.read_text(encoding="utf-8")
        if package_line not in text:
            fail(f"{path} missing expected package declaration {package_line!r}")


def ensure_retired_layout_is_absent() -> None:
    for path in RETIRED_LAYOUT:
        if path.exists():
            fail(f"Expected migrated shared-path handler to be absent: {path}")


def main() -> None:
    ensure_expected_layout()
    ensure_retired_layout_is_absent()
    print("PASS: MyWorld plugin namespace layout validated")
    print(f"Handlers validated: {len(EXPECTED_LAYOUT)}")


if __name__ == "__main__":
    main()
