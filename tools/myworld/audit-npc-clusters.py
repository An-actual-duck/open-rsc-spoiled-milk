#!/usr/bin/env python3
"""Group active MyWorld hostile NPC locations into proximity clusters."""

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONF_DIR = ROOT / "server" / "conf" / "server"
LOCS_DIR = CONF_DIR / "defs" / "locs"


def load_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def truthy(value):
    if isinstance(value, bool):
        return value
    if isinstance(value, int):
        return value != 0
    if isinstance(value, str):
        return value.strip().lower() in {"1", "true", "yes", "on"}
    return bool(value)


def read_config(path):
    config = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.split("#", 1)[0].strip()
            if not line or ":" not in line:
                continue
            key, value = line.split(":", 1)
            config[key.strip()] = value.strip()
    return config


def int_config(config, key, default):
    try:
        return int(config.get(key, default))
    except ValueError:
        return default


def bool_config(config, key, default=False):
    return truthy(config.get(key, default))


def ensure_size(items, index):
    while len(items) <= index:
        items.append(None)


def load_npc_defs():
    npc_defs = []
    for filename in ("NpcDefs.json", "NpcDefsCustom.json"):
        data = load_json(CONF_DIR / "defs" / filename)
        for npc in data.get("npcs", []):
            npc_id = int(npc["id"])
            ensure_size(npc_defs, npc_id)
            npc_defs[npc_id] = npc

    override_path = CONF_DIR / "defs" / "NpcDefsMyWorld.json"
    if override_path.exists():
        for override in load_json(override_path).get("npcs", []):
            npc_id = int(override["id"])
            ensure_size(npc_defs, npc_id)
            base = dict(npc_defs[npc_id] or {"id": npc_id})
            base.update(override)
            npc_defs[npc_id] = base
    return npc_defs


def active_npc_loc_files(config):
    based_map_data = int_config(config, "based_map_data", 64)
    location_data = int_config(config, "location_data", 2)
    if based_map_data == 14:
        files = ["NpcLocs14.json"]
    elif based_map_data == 27:
        files = ["NpcLocs27.json"]
    else:
        files = ["NpcLocs.json"]

    if location_data in {1, 2} and bool_config(config, "want_fixed_broken_mechanics"):
        files.append("NpcLocsDiscontinued.json")

    if location_data == 2:
        if bool_config(config, "want_decorated_mod_room"):
            files.append("NpcLocsModRoom.json")
        if bool_config(config, "want_runecraft"):
            files.append("NpcLocsRunecraft.json")
        if bool_config(config, "spawn_auction_npcs"):
            files.append("NpcLocsAuction.json")
        if bool_config(config, "spawn_iron_man_npcs"):
            files.append("NpcLocsIronman.json")
        if bool_config(config, "want_harvesting"):
            files.append("NpcLocsHarvesting.json")
        if bool_config(config, "want_custom_quests"):
            files.append("NpcLocsCustomQuest.json")
        files.append("NpcLocsOther.json")
        if bool_config(config, "want_myworld"):
            files.append("MyWorldNpcLocs.json")
    return files


def load_npc_locs(config):
    locs = []
    for filename in active_npc_loc_files(config):
        path = LOCS_DIR / filename
        if not path.exists():
            continue
        for loc in load_json(path).get("npclocs", []):
            locs.append({
                "id": int(loc["id"]),
                "x": int(loc["start"]["X"]),
                "y": int(loc["start"]["Y"]),
                "min_x": int(loc["min"]["X"]),
                "min_y": int(loc["min"]["Y"]),
                "max_x": int(loc["max"]["X"]),
                "max_y": int(loc["max"]["Y"]),
                "source": filename,
            })
    return locs


def is_hostile(defn):
    return defn is not None and truthy(defn.get("attackable", False))


def wilderness_level(x, y):
    height = y // 944
    wild = 2203 - (y + (1776 - (944 * height)))
    if x + 2304 >= 2640:
        return 0
    return 1 + wild // 6 if wild > 0 else 0


def wilderness_band(level):
    start = ((level - 1) // 10) * 10 + 1
    return start, start + 9


def distance(a, b):
    return max(abs(a["x"] - b["x"]), abs(a["y"] - b["y"]))


def cluster_locs(locs, radius):
    remaining = set(range(len(locs)))
    clusters = []
    while remaining:
        seed = remaining.pop()
        cluster = [seed]
        frontier = [seed]
        while frontier:
            current = frontier.pop()
            nearby = [idx for idx in remaining if distance(locs[current], locs[idx]) <= radius]
            for idx in nearby:
                remaining.remove(idx)
                frontier.append(idx)
                cluster.append(idx)
        clusters.append(cluster)
    return clusters


def format_npc_counts(entries, npc_defs):
    counts = Counter(entry["id"] for entry in entries)
    parts = []
    for npc_id, count in counts.most_common():
        defn = npc_defs[npc_id] if npc_id < len(npc_defs) else None
        name = defn.get("name", "Unknown") if defn else "Unknown"
        parts.append(f"{name} `{npc_id}` x{count}")
    return ", ".join(parts)


def format_sources(entries):
    counts = Counter(entry["source"] for entry in entries)
    return ", ".join(f"{name} x{count}" for name, count in sorted(counts.items()))


def summarize_clusters(clusters, locs, npc_defs, min_size, limit):
    rows = []
    for cluster in clusters:
        entries = [locs[idx] for idx in cluster]
        if len(entries) < min_size:
            continue
        aggressive = 0
        members = 0
        levels = []
        for entry in entries:
            defn = npc_defs[entry["id"]]
            if truthy(defn.get("aggressive", False)):
                aggressive += 1
            if truthy(defn.get("isMembers", False)):
                members += 1
            if "combatlvl" in defn:
                levels.append(int(defn["combatlvl"]))
        rows.append({
            "entries": entries,
            "count": len(entries),
            "aggressive": aggressive,
            "members": members,
            "min_x": min(entry["x"] for entry in entries),
            "max_x": max(entry["x"] for entry in entries),
            "min_y": min(entry["y"] for entry in entries),
            "max_y": max(entry["y"] for entry in entries),
            "min_level": min(levels) if levels else 0,
            "max_level": max(levels) if levels else 0,
            "min_wilderness": min(
                (wilderness_level(entry["x"], entry["y"]) for entry in entries),
                default=0,
            ),
            "max_wilderness": max(
                (wilderness_level(entry["x"], entry["y"]) for entry in entries),
                default=0,
            ),
        })
    rows.sort(key=lambda row: (-row["count"], row["min_x"], row["min_y"]))
    return rows[:limit]


def print_wilderness_band_summary(locs):
    bands = defaultdict(Counter)
    for loc in locs:
        level = wilderness_level(loc["x"], loc["y"])
        if level <= 0:
            continue
        band = wilderness_band(level)
        key = "MyWorld Overlay" if loc["source"] == "MyWorldNpcLocs.json" else "Base/Other"
        bands[band][key] += 1

    print("## Population By Wilderness Level")
    print()
    print("| Wilderness Levels | Base/Other Hostiles | MyWorld Overlay | Total |")
    print("| - | -: | -: | -: |")
    for band in sorted(bands):
        base = bands[band]["Base/Other"]
        overlay = bands[band]["MyWorld Overlay"]
        print(f"| {band[0]}-{band[1]} | {base} | {overlay} | {base + overlay} |")
    print()


def print_markdown(rows, locs, npc_defs, radius, min_size, wilderness_only):
    title = "Wilderness PvM NPC Cluster Audit" if wilderness_only else "PvM NPC Cluster Audit"
    print(f"# {title}")
    print()
    print("Generated from active `server/myworld.conf` NPC location files.")
    print()
    print(f"- Cluster radius: `{radius}` tiles")
    print(f"- Minimum cluster size: `{min_size}` hostile NPCs")
    if wilderness_only:
        print("- Scope: Wilderness tiles only, using the server wilderness-level formula.")
    print("- Hostile means `attackable` in the NPC definition; aggressive count is shown separately.")
    print()
    if wilderness_only:
        print_wilderness_band_summary(locs)
        print("## Proximity Clusters")
        print()
    wilderness_column = " | Wilderness Levels" if wilderness_only else ""
    print(f"| # | Bounds{wilderness_column} | Count | Aggressive | Levels | NPCs | Source Files |")
    print(f"| - | -{wilderness_column.replace('Wilderness Levels', '-')} | -: | -: | - | - | - |")
    for index, row in enumerate(rows, 1):
        bounds = f"{row['min_x']},{row['min_y']} to {row['max_x']},{row['max_y']}"
        levels = f"{row['min_level']}-{row['max_level']}" if row["max_level"] else "unknown"
        wilderness_levels = (
            f" | {row['min_wilderness']}-{row['max_wilderness']}" if wilderness_only else ""
        )
        print(
            f"| {index} | `{bounds}`{wilderness_levels} | {row['count']} | {row['aggressive']} | "
            f"{levels} | {format_npc_counts(row['entries'], npc_defs)} | {format_sources(row['entries'])} |"
        )
    print()
    print("## Review Notes")
    print()
    print("- High-count, high-aggression clusters are the first places to field-test AoE and multi-aggro behavior.")
    print("- Low-aggression clusters may still be good PvM pockets if the intended behavior is player-initiated pulls.")
    print("- Quest-sensitive areas need manual review before adding density.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--radius", type=int, default=8, help="Chebyshev distance used to join nearby NPCs")
    parser.add_argument("--min-size", type=int, default=3, help="Minimum hostile NPCs required for output")
    parser.add_argument("--limit", type=int, default=80, help="Maximum clusters to print")
    parser.add_argument("--wilderness-only", action="store_true", help="Only include hostile NPCs on Wilderness tiles")
    args = parser.parse_args()

    config = read_config(ROOT / "server" / "myworld.conf")
    npc_defs = load_npc_defs()
    locs = [loc for loc in load_npc_locs(config)
            if loc["id"] < len(npc_defs) and is_hostile(npc_defs[loc["id"]])]
    if args.wilderness_only:
        locs = [loc for loc in locs if wilderness_level(loc["x"], loc["y"]) > 0]
    clusters = cluster_locs(locs, args.radius)
    rows = summarize_clusters(clusters, locs, npc_defs, args.min_size, args.limit)
    print_markdown(rows, locs, npc_defs, args.radius, args.min_size, args.wilderness_only)


if __name__ == "__main__":
    main()
