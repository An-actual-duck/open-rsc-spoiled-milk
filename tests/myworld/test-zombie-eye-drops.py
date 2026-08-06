#!/usr/bin/env python3

import json
import re
import struct
import sys
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
ZOMBIE_EYE_SPRITE = ROOT / "dev/myworld/assets/sprites/items/inventory-ground/zombie-eye.png"


def fail(message: str) -> None:
	print(f"FAIL: {message}")
	sys.exit(1)


def require(condition: bool, message: str) -> None:
	if not condition:
		fail(message)


def require_drop_table(drops: str, table_name: str) -> None:
	match = re.search(
		rf'currentNpcDrops = new DropTable\("{re.escape(table_name)}"\);'
		r"(?P<body>.*?)"
		r"currentNpcDrops\.addEmptyDrop\(128 - currentNpcDrops\.getTotalWeight\(\)\);",
		drops,
		re.S,
	)
	require(match is not None, f"Missing drop table for {table_name}")
	body = match.group("body")
	require("ItemId.EYE_OF_NEWT" not in body,
		f"{table_name} should use Zombie eye instead of the generic Eye of newt")
	require("currentNpcDrops.addItemDrop(ItemId.ZOMBIE_EYE.id(), 1, 4);" in body,
		f"{table_name} should have an uncommon Zombie eye drop")


def require_target_practice_zombie_table(drops: str) -> None:
	match = re.search(
		r'currentNpcDrops = new DropTable\("Target Practice Zombie \(516\)"\);'
		r"(?P<body>.*?)"
		r"currentNpcDrops\.addEmptyDrop\(128 - currentNpcDrops\.getTotalWeight\(\)\);",
		drops,
		re.S,
	)
	require(match is not None, "Missing target-practice zombie drop table")
	body = match.group("body")

	for banned in (
		"ItemId.COINS",
		"ItemId.BRONZE_MACE",
		"ItemId.BRONZE_DAGGER",
		"ItemId.CROSSBOW",
		"ItemId.TINDERBOX",
		"ItemId.COPPER_KITE_SHIELD",
		"ItemId.TIN_ORE",
		"ItemId.EYE_OF_NEWT",
	):
		require(banned not in body, f"Target-practice zombies should not drop {banned}")

	for item in ("FIRE_RUNE", "WATER_RUNE", "AIR_RUNE", "EARTH_RUNE"):
		require(f"currentNpcDrops.addItemDrop(ItemId.{item}.id(), 2, 8);" in body,
			f"Target-practice zombies should commonly drop 2 {item}")
		require(f"currentNpcDrops.addItemDrop(ItemId.{item}.id(), 4, 4);" in body,
			f"Target-practice zombies should uncommonly drop 4 {item}")

	for item in ("MIND_RUNE", "BODY_RUNE"):
		require(f"currentNpcDrops.addItemDrop(ItemId.{item}.id(), 1, 8);" in body,
			f"Target-practice zombies should commonly drop 1 {item}")
		require(f"currentNpcDrops.addItemDrop(ItemId.{item}.id(), 2, 4);" in body,
			f"Target-practice zombies should uncommonly drop 2 {item}")

	for item in ("COSMIC_RUNE", "NATURE_RUNE", "CHAOS_RUNE"):
		require(f"currentNpcDrops.addItemDrop(ItemId.{item}.id(), 1, 4);" in body,
			f"Target-practice zombies should uncommonly drop 1 {item}")
		require(f"currentNpcDrops.addItemDrop(ItemId.{item}.id(), 2, 1);" in body,
			f"Target-practice zombies should rarely drop 2 {item}")

	require("currentNpcDrops.addItemDrop(ItemId.LAW_RUNE.id(), 2, 4);" in body,
		"Target-practice zombies should uncommonly drop 2 LAW_RUNE")
	require("currentNpcDrops.addItemDrop(ItemId.LAW_RUNE.id(), 2, 1);" in body,
		"Target-practice zombies should rarely drop 2 LAW_RUNE")
	require("currentNpcDrops.addItemDrop(ItemId.ZOMBIE_EYE.id(), 1, 4);" in body,
		"Target-practice zombies should keep the uncommon Zombie eye drop")
	require("this.npcDrops.put(NpcId.TARGET_PRACTICE_ZOMBIE.id(), currentNpcDrops);" in drops,
		"Target-practice zombies should use their dedicated drop table")


def png_visible_colors(path: Path) -> set[tuple[int, int, int]]:
	data = path.read_bytes()
	require(data[:8] == b"\x89PNG\r\n\x1a\n", "Zombie eye sprite should be a PNG")
	pos = 8
	width = height = bit_depth = color_type = None
	idat = bytearray()
	while pos < len(data):
		length = struct.unpack(">I", data[pos:pos + 4])[0]
		chunk_type = data[pos + 4:pos + 8]
		chunk = data[pos + 8:pos + 8 + length]
		pos += 12 + length
		if chunk_type == b"IHDR":
			width, height, bit_depth, color_type = struct.unpack(">IIBB", chunk[:10])
		elif chunk_type == b"IDAT":
			idat.extend(chunk)
		elif chunk_type == b"IEND":
			break

	require(width is not None and height is not None, "Zombie eye sprite should have a PNG header")
	require(bit_depth == 8 and color_type == 6, "Zombie eye sprite should use 8-bit RGBA pixels")
	raw = zlib.decompress(bytes(idat))
	bytes_per_pixel = 4
	stride = width * bytes_per_pixel
	rows: list[bytearray] = []
	offset = 0
	for _ in range(height):
		filter_type = raw[offset]
		offset += 1
		row = bytearray(raw[offset:offset + stride])
		offset += stride
		previous = rows[-1] if rows else bytearray(stride)
		for i in range(stride):
			left = row[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
			up = previous[i]
			up_left = previous[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
			if filter_type == 1:
				row[i] = (row[i] + left) & 0xFF
			elif filter_type == 2:
				row[i] = (row[i] + up) & 0xFF
			elif filter_type == 3:
				row[i] = (row[i] + ((left + up) // 2)) & 0xFF
			elif filter_type == 4:
				p = left + up - up_left
				pa = abs(p - left)
				pb = abs(p - up)
				pc = abs(p - up_left)
				predictor = left if pa <= pb and pa <= pc else up if pb <= pc else up_left
				row[i] = (row[i] + predictor) & 0xFF
			else:
				require(filter_type == 0, f"Unsupported PNG filter type {filter_type}")
		rows.append(row)

	colors: set[tuple[int, int, int]] = set()
	for row in rows:
		for i in range(0, stride, bytes_per_pixel):
			if row[i + 3] != 0:
				colors.add((row[i], row[i + 1], row[i + 2]))
	return colors


def main() -> None:
	item_id_text = ITEM_ID.read_text(encoding="utf-8")
	require("ZOMBIE_EYE(3238)" in item_id_text, "ItemId should define Zombie eye")
	require("public static final int maxCustom = 3311;" in item_id_text,
		"ItemId.maxCustom should include Zombie eye")

	custom_items = json.loads(ITEM_DEFS_CUSTOM.read_text(encoding="utf-8"))["items"]
	zombie_eye = next((item for item in custom_items if item["id"] == 3238), None)
	require(zombie_eye is not None, "ItemDefsCustom should define Zombie eye")
	require(zombie_eye["name"] == "Zombie eye", "Zombie eye should have the expected name")
	require(zombie_eye["isWearable"] == 0, "Zombie eye should not be wearable")
	require(zombie_eye["isNoteable"] == 1, "Zombie eye should be noteable")

	client_defs = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
	require('setCustomItemDefinition(3238, new ItemDef("Zombie eye"' in client_defs,
		"Client should define Zombie eye")
	require('"external-png:zombie-eye@11x11"' in client_defs,
		"Zombie eye should render its recolored external sprite at Eye-of-newt-sized scale")
	require(ZOMBIE_EYE_SPRITE.is_file(), "Zombie eye sprite asset should exist")
	sprite_colors = png_visible_colors(ZOMBIE_EYE_SPRITE)
	require((114, 218, 58) in sprite_colors or (184, 255, 96) in sprite_colors,
		"Zombie eye sprite should include green recolored eye tones")
	require((85, 32, 128) in sprite_colors or (52, 19, 82) in sprite_colors,
		"Zombie eye sprite should include purple recolored red tones")

	drops = NPC_DROPS.read_text(encoding="utf-8")
	for table_name in (
		"Zombie Level 24 (41, 359)",
		"Zombie Level 19 (52)",
		"Zombie Level 32 (68)",
		"Zombie (Entrana) (214)",
	):
		require_drop_table(drops, table_name)
	require_target_practice_zombie_table(drops)

	require("this.npcDrops.put(NpcId.ZOMBIE_WMAZEKEY.id(), currentNpcDrops);" in drops,
		"Maze zombie should keep inheriting the Level 32 zombie drops")
	require("this.npcDrops.put(NpcId.ZOMBIE_INVOKED.id(), currentNpcDrops);" in drops,
		"Invoked zombie should keep inheriting the Level 24 zombie drops")

	print("PASS: Zombie eye drops validated")


if __name__ == "__main__":
	main()
