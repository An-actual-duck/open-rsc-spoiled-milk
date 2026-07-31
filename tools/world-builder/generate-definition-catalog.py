#!/usr/bin/env python3
"""Generate World Builder 2's editor-only scenery and boundary catalog."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DEFS = ROOT / "server/conf/server/defs"
EXTRAS = DEFS / "extras"
CONSTANTS = ROOT / "server/src/com/openrsc/server/constants"
DEFAULT_OUTPUT = ROOT / "dev/myworld/assets/ui/world-editor/definition-catalog-v1.tsv"
DEFAULT_AUDIT = ROOT / "docs/myworld/info/world-builder-definition-catalog.md"
DEFAULT_OVERRIDES = ROOT / "tools/world-builder/definition-label-overrides.json"
SCHEMA = "world-editor-definition-catalog-v1"
OVERRIDE_SCHEMA = "world-editor-definition-label-overrides-v1"


@dataclass(frozen=True)
class Definition:
    kind: str
    id: int
    canonical: str
    description: str
    command1: str
    command2: str
    model: str


@dataclass(frozen=True)
class Behavior:
    category: str
    qualifier: str
    terms: tuple[str, ...]


@dataclass(frozen=True)
class CatalogRow:
    kind: str
    id: int
    canonical: str
    label: str
    source: str
    tags: tuple[str, ...]
    search_terms: tuple[str, ...]


def normalized_space(value: str | None) -> str:
    return re.sub(r"\s+", " ", value or "").strip()


def display_case(value: str) -> str:
    value = normalized_space(value)
    if not value:
        return "Unknown"
    return value[0].upper() + value[1:]


def canonical_key(value: str) -> str:
    return normalized_space(value).casefold()


def child_text(element: ET.Element, name: str) -> str:
    return normalized_space(element.findtext(name, default=""))


def load_definitions(path: Path, child_name: str, kind: str, model_fields: Iterable[str]) -> list[Definition]:
    root = ET.parse(path).getroot()
    definitions: list[Definition] = []
    for definition_id, element in enumerate(root.findall(child_name)):
        model = " ".join(child_text(element, field) for field in model_fields).strip()
        definitions.append(
            Definition(
                kind=kind,
                id=definition_id,
                canonical=child_text(element, "name"),
                description=child_text(element, "description"),
                command1=child_text(element, "command1"),
                command2=child_text(element, "command2"),
                model=model,
            )
        )
    if not definitions:
        raise ValueError(f"No {kind} definitions found in {path.relative_to(ROOT)}")
    return definitions


def strip_java_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//[^\r\n]*", "", source)


def load_constants(path: Path) -> dict[int, str]:
    source = strip_java_comments(path.read_text(encoding="utf-8"))
    constants: dict[int, str] = {}
    for name, raw_id in re.findall(r"\b([A-Z][A-Z0-9_]*)\s*\(\s*(-?\d+)\s*\)", source):
        definition_id = int(raw_id)
        if definition_id < 0:
            continue
        previous = constants.get(definition_id)
        if previous is not None:
            raise ValueError(
                f"Duplicate active ID {definition_id} in {path.relative_to(ROOT)}: {previous} and {name}"
            )
        constants[definition_id] = name
    return constants


def load_item_names() -> dict[int, str]:
    names: dict[int, str] = {}
    for filename in ("ItemDefs.json", "ItemDefsCustom.json", "ItemDefsMyWorld.json"):
        payload = json.loads((DEFS / filename).read_text(encoding="utf-8"))
        values = next(iter(payload.values()))
        for item in values:
            item_id = int(item["id"])
            if item.get("name"):
                names[item_id] = normalized_space(str(item["name"]))
    return names


def entries(path: Path) -> list[ET.Element]:
    return ET.parse(path).getroot().findall("entry")


def entry_id(entry: ET.Element) -> int:
    raw = entry.findtext("int")
    if raw is None:
        raise ValueError("Behavior definition entry has no integer scenery ID")
    return int(raw)


def resource_name(item_name: str) -> str:
    value = normalized_space(item_name).casefold()
    value = re.sub(r"[- ]rune$", "", value)
    value = re.sub(r"\s+(ore|nugget|logs?)$", "", value)
    return value or normalized_space(item_name).casefold()


def item_name(item_names: dict[int, str], raw_id: str | None) -> str:
    if raw_id is None:
        return ""
    return item_names.get(int(raw_id), f"item {int(raw_id)}")


def fishing_method(name: str) -> str:
    value = normalized_space(name).casefold()
    if "fly fishing rod" in value:
        return "lure"
    if "oily fishing rod" in value:
        return "oily rod"
    if "fishing rod" in value:
        return "bait"
    if "small fishing net" in value:
        return "net"
    if "big fishing net" in value:
        return "big net"
    if "lobster" in value and ("pot" in value or "cage" in value):
        return "cage"
    for candidate in ("harpoon", "net", "cage"):
        if candidate in value:
            return candidate
    return resource_name(value)


def load_behaviors(item_names: dict[int, str]) -> dict[int, list[Behavior]]:
    behaviors: dict[int, list[Behavior]] = defaultdict(list)

    for entry in entries(EXTRAS / "ObjectMining.xml"):
        definition = entry.find("ObjectMiningDef")
        if definition is None:
            continue
        product = item_name(item_names, definition.findtext("oreId"))
        behaviors[entry_id(entry)].append(
            Behavior("mining", resource_name(product), (product, "mine", "prospect"))
        )

    for entry in entries(EXTRAS / "ObjectWoodcutting.xml"):
        definition = entry.find("ObjectWoodcuttingDef")
        if definition is None:
            continue
        product = item_name(item_names, definition.findtext("logId"))
        qualifier = resource_name(product)
        if normalized_space(product).casefold() == "logs":
            qualifier = "normal"
        behaviors[entry_id(entry)].append(
            Behavior("woodcutting", qualifier, (product, "chop", "woodcutting"))
        )

    for entry in entries(EXTRAS / "ObjectHarvesting.xml"):
        definition = entry.find("ObjectHarvestingDef")
        if definition is None:
            continue
        product = item_name(item_names, definition.findtext("prodId"))
        behaviors[entry_id(entry)].append(
            Behavior("harvesting", resource_name(product), (product, "harvest"))
        )

    for entry in entries(EXTRAS / "ObjectRunecraft.xml"):
        definition = entry.find("ObjectRunecraftDef")
        if definition is None:
            continue
        rune_name = normalized_space(definition.findtext("runeName", default="")).casefold()
        altar_id = entry_id(entry)
        behavior = Behavior("runecrafting", rune_name, (rune_name, "rune", "altar", "runecrafting"))
        behaviors[altar_id].append(behavior)
        if altar_id > 0:
            behaviors[altar_id - 1].append(
                Behavior("runecrafting-ruins", rune_name, (rune_name, "rune", "mysterious ruins"))
            )

    for entry in entries(EXTRAS / "ObjectFishing.xml"):
        methods: list[str] = []
        fish: list[str] = []
        for definition in entry.findall("./ObjectFishingDef-array/ObjectFishingDef"):
            tool = item_name(item_names, definition.findtext("netId"))
            method = fishing_method(tool)
            if method and method not in methods:
                methods.append(method)
            for fish_id in definition.findall("./defs/ObjectFishDef/fishId"):
                name = item_name(item_names, fish_id.text)
                if name and name not in fish:
                    fish.append(name)
        if methods:
            behaviors[entry_id(entry)].append(
                Behavior("fishing", " / ".join(methods), tuple(methods + fish + ["fish", "fishing spot"]))
            )
    return behaviors


def load_overrides(path: Path) -> dict[str, dict[int, dict[str, object]]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schema") != OVERRIDE_SCHEMA:
        raise ValueError(f"{path.relative_to(ROOT)} has an unsupported schema")
    result: dict[str, dict[int, dict[str, object]]] = {"scenery": {}, "boundary": {}}
    if set(payload) != {"schema", "scenery", "boundary"}:
        raise ValueError(f"{path.relative_to(ROOT)} has unexpected top-level fields")
    for kind in ("scenery", "boundary"):
        values = payload[kind]
        if not isinstance(values, list):
            raise ValueError(f"{kind} overrides must be an array")
        for value in values:
            if set(value) != {"id", "label", "reason", "tags"}:
                raise ValueError(f"{kind} override has unexpected fields: {value}")
            definition_id = int(value["id"])
            if definition_id in result[kind]:
                raise ValueError(f"Duplicate {kind} override ID {definition_id}")
            if not normalized_space(str(value["label"])) or not normalized_space(str(value["reason"])):
                raise ValueError(f"{kind} override {definition_id} requires a label and reason")
            if not isinstance(value["tags"], list):
                raise ValueError(f"{kind} override {definition_id} tags must be an array")
            result[kind][definition_id] = value
    return result


LOWERCASE_QUALIFIERS = {
    "air", "bait", "blood", "body", "broken", "chaos", "closed", "coal", "copper",
    "cosmic", "death", "down", "earth", "empty", "fire", "full", "generic", "gold",
    "iron", "law", "life", "lure", "mind", "mithril", "nature", "normal", "open",
    "runite", "silver", "soul", "stone", "tin", "unused", "up", "water",
}
TRAILING_MODIFIERS = {
    "broken", "closed", "down", "east", "empty", "exhausted", "full", "generic", "locked",
    "north", "open", "rotated", "south", "unused", "up", "west",
}
TOKEN_REPLACEMENTS = {
    "1ST": "first floor",
    "2ND": "second floor",
    "3RD": "third floor",
    "HQ": "HQ",
    "KBD": "KBD",
    "NE": "northeast",
    "NW": "northwest",
    "SE": "southeast",
    "SW": "southwest",
}


def words(value: str) -> list[str]:
    return re.findall(r"[A-Z0-9]+", value.upper())


def format_word(token: str) -> str:
    replacement = TOKEN_REPLACEMENTS.get(token)
    if replacement is not None:
        return replacement
    match = re.fullmatch(r"([A-Z]+)(\d+)", token)
    if match:
        stem, number = match.groups()
        if stem in {"GENERIC", "UNUSED"} or stem.casefold() in LOWERCASE_QUALIFIERS:
            token = stem
        else:
            return f"{stem.title()} {number}"
    value = token.casefold()
    return value if value in LOWERCASE_QUALIFIERS else value.title()


def humanized_constant(constant: str, canonical: str) -> str:
    canonical_tokens = set(words(canonical))
    raw_tokens = [token for token in constant.split("_") if token not in canonical_tokens]
    raw_tokens = [token for token in raw_tokens if token not in {"OBJECT", "SCENERY", "RUNECRAFT"}]
    if not raw_tokens:
        return ""
    split = len(raw_tokens)
    while split > 0 and re.sub(r"\d+$", "", raw_tokens[split - 1]).casefold() in TRAILING_MODIFIERS:
        split -= 1
    context = " ".join(format_word(token) for token in raw_tokens[:split])
    modifiers = ", ".join(format_word(token) for token in raw_tokens[split:])
    if context and modifiers:
        return context + ", " + modifiers
    return context or modifiers


def model_qualifier(model: str, canonical: str) -> str:
    if not model:
        return ""
    candidate = re.sub(r"([a-z])([A-Z])", r"\1 \2", model)
    candidate = re.sub(r"[_-]+", " ", candidate)
    candidate = normalized_space(candidate)
    if canonical_key(candidate) == canonical_key(canonical):
        return ""
    return candidate.casefold()


def behavior_label(definition: Definition, behaviors: list[Behavior]) -> tuple[str, str] | None:
    if not behaviors:
        return None
    behavior = behaviors[0]
    canonical = display_case(definition.canonical)
    qualifier = behavior.qualifier
    if not qualifier:
        return None
    if behavior.category == "mining":
        base = "Rock" if canonical_key(canonical) in {"rock", "rocks"} else canonical
        return f"{base} ({qualifier})", "behavior:mining"
    if behavior.category == "fishing":
        return f"Fishing spot ({qualifier})", "behavior:fishing"
    if behavior.category == "runecrafting-ruins":
        return f"Mysterious Ruins ({qualifier})", "behavior:runecrafting"
    if behavior.category == "runecrafting":
        expected = f"{qualifier} altar"
        if canonical_key(canonical) != expected:
            return f"{canonical} ({qualifier})", "behavior:runecrafting"
        return None
    if behavior.category == "woodcutting":
        canonical_tokens = set(words(canonical.casefold()))
        if set(words(qualifier)) <= canonical_tokens:
            return None
        return f"{canonical} ({qualifier})", "behavior:woodcutting"
    if behavior.category == "harvesting":
        canonical_tokens = set(words(canonical.casefold()))
        if set(words(qualifier)) <= canonical_tokens:
            return None
        return f"{canonical} (harvests {qualifier})", "behavior:harvesting"
    return None


def unique_terms(values: Iterable[str]) -> tuple[str, ...]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        value = normalized_space(value)
        if not value or value.casefold() in seen:
            continue
        seen.add(value.casefold())
        result.append(value)
    return tuple(result)


def catalog_rows(
    definitions: list[Definition],
    constants: dict[int, str],
    behaviors: dict[int, list[Behavior]],
    overrides: dict[int, dict[str, object]],
) -> list[CatalogRow]:
    counts = Counter(canonical_key(definition.canonical) for definition in definitions)
    rows: list[CatalogRow] = []
    for definition in definitions:
        if definition.id in overrides:
            override = overrides[definition.id]
            label = normalized_space(str(override["label"]))
            source = "override"
        elif counts[canonical_key(definition.canonical)] == 1:
            label = display_case(definition.canonical)
            source = "canonical"
        else:
            functional = behavior_label(definition, behaviors.get(definition.id, []))
            if functional is not None:
                label, source = functional
            else:
                qualifier = humanized_constant(constants.get(definition.id, ""), definition.canonical)
                if qualifier:
                    label = f"{display_case(definition.canonical)} ({qualifier})"
                    source = "constant"
                else:
                    qualifier = model_qualifier(definition.model, definition.canonical)
                    if qualifier:
                        label = f"{display_case(definition.canonical)} (model: {qualifier})"
                        source = "model"
                    else:
                        label = f"{display_case(definition.canonical)} (variant #{definition.id})"
                        source = "fallback-id"

        behavior_values = behaviors.get(definition.id, [])
        override_tags = overrides.get(definition.id, {}).get("tags", [])
        tags = unique_terms(
            [definition.kind, source]
            + [behavior.category for behavior in behavior_values]
            + [str(tag) for tag in override_tags]
            + [token.casefold() for token in constants.get(definition.id, "").split("_")]
        )
        search_terms = unique_terms(
            [
                label,
                definition.canonical,
                definition.description,
                definition.command1,
                definition.command2,
                definition.model,
                constants.get(definition.id, "").replace("_", " "),
            ]
            + [term for behavior in behavior_values for term in behavior.terms]
            + list(tags)
        )
        rows.append(
            CatalogRow(
                kind=definition.kind,
                id=definition.id,
                canonical=definition.canonical,
                label=label,
                source=source,
                tags=tags,
                search_terms=search_terms,
            )
        )
    unknown_overrides = sorted(set(overrides) - {definition.id for definition in definitions})
    if unknown_overrides:
        raise ValueError(f"Unknown {definitions[0].kind} override IDs: {unknown_overrides}")
    return rows


def escape_tsv(value: str) -> str:
    return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")


def render_tsv(rows: list[CatalogRow]) -> str:
    lines = [
        f"# {SCHEMA}",
        "# kind\tid\tcanonical_name\tdisplay_name\tlabel_source\ttags\tsearch_terms",
    ]
    for row in rows:
        fields = (
            row.kind,
            str(row.id),
            row.canonical,
            row.label,
            row.source,
            ";".join(row.tags),
            " | ".join(row.search_terms),
        )
        lines.append("\t".join(escape_tsv(field) for field in fields))
    return "\n".join(lines) + "\n"


def duplicate_stats(definitions: list[Definition]) -> tuple[int, int]:
    counts = Counter(canonical_key(definition.canonical) for definition in definitions)
    return sum(1 for count in counts.values() if count > 1), sum(count for count in counts.values() if count > 1)


def markdown_source_counts(rows: list[CatalogRow]) -> str:
    counts = Counter(row.source for row in rows)
    return ", ".join(f"`{source}` {counts[source]}" for source in sorted(counts))


def render_audit(scenery: list[Definition], boundaries: list[Definition], rows: list[CatalogRow]) -> str:
    scenery_rows = [row for row in rows if row.kind == "scenery"]
    boundary_rows = [row for row in rows if row.kind == "boundary"]
    scenery_groups, scenery_ambiguous = duplicate_stats(scenery)
    boundary_groups, boundary_ambiguous = duplicate_stats(boundaries)
    fallbacks = [row for row in rows if row.source == "fallback-id"]
    examples = [
        row for row in rows
        if (row.kind, row.id) in {
            ("scenery", 17), ("scenery", 104), ("scenery", 105), ("scenery", 193),
            ("scenery", 223), ("scenery", 1190), ("boundary", 8), ("boundary", 101),
        }
    ]
    lines = [
        "# World Builder Definition Catalog",
        "",
        "This is a generated audit for World Builder 2's editor-only definition",
        "catalog. Regenerate it together with",
        "`dev/myworld/assets/ui/world-editor/definition-catalog-v1.tsv` by running:",
        "",
        "```bash",
        "python3 tools/world-builder/generate-definition-catalog.py",
        "```",
        "",
        "The catalog does not rename gameplay definitions or alter IDs, maps, saves,",
        "protocols, commands, or server behavior. It gives the Builder concise labels",
        "and searchable metadata derived from the authoritative definitions, behavior",
        "tables, and active ID constants. Curated corrections live in",
        "`tools/world-builder/definition-label-overrides.json`.",
        "",
        "## Coverage",
        "",
        f"- Scenery: {len(scenery)} definitions; {scenery_groups} repeated-name groups covering {scenery_ambiguous} rows.",
        f"- Boundaries: {len(boundaries)} definitions; {boundary_groups} repeated-name groups covering {boundary_ambiguous} rows.",
        f"- Scenery label sources: {markdown_source_counts(scenery_rows)}.",
        f"- Boundary label sources: {markdown_source_counts(boundary_rows)}.",
        f"- Explicit ID fallback rows still needing semantic review: {len(fallbacks)}.",
        "",
        "Equivalent IDs may intentionally share a semantic label. Every editor context",
        "reference still includes its numeric ID, and the future visual browser can",
        "distinguish model variants without inventing unsupported gameplay meaning.",
        "",
        "## Representative Labels",
        "",
        "| Kind | ID | Canonical | Builder label | Source |",
        "| --- | ---: | --- | --- | --- |",
    ]
    for row in examples:
        lines.append(f"| {row.kind} | {row.id} | {row.canonical} | {row.label} | `{row.source}` |")
    lines.extend(
        [
            "",
            "## Unresolved Legacy Variants",
            "",
            "These rows have neither authoritative behavior metadata, an active semantic",
            "constant, a useful model distinction, nor a curated override. Their labels",
            "therefore expose the stable ID instead of guessing.",
            "",
            "| Kind | ID | Canonical | Builder label |",
            "| --- | ---: | --- | --- |",
        ]
    )
    if not fallbacks:
        lines.append("| _none_ | | | |")
    else:
        for row in fallbacks:
            lines.append(f"| {row.kind} | {row.id} | {row.canonical} | {row.label} |")
    lines.extend(
        [
            "",
            "## Naming Policy",
            "",
            "1. Preserve unique canonical names unless a reviewed spelling/meaning override exists.",
            "2. Prefer functional results from mining, fishing, woodcutting, harvesting, and runecrafting metadata.",
            "3. Use active constants for location, quest, state, and direction context.",
            "4. Use technical model qualifiers only when they add a real distinction.",
            "5. Fall back to the stable numeric ID; never fabricate a semantic distinction.",
            "",
        ]
    )
    return "\n".join(lines)


def write_or_check(path: Path, content: str, check: bool) -> bool:
    if check:
        if not path.is_file() or path.read_text(encoding="utf-8") != content:
            print(f"OUT OF DATE: {path.relative_to(ROOT)}", file=sys.stderr)
            return False
        print(f"Validated {path.relative_to(ROOT)}")
        return True
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"Generated {path.relative_to(ROOT)}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if generated files are stale")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--audit-output", type=Path, default=DEFAULT_AUDIT)
    parser.add_argument("--overrides", type=Path, default=DEFAULT_OVERRIDES)
    args = parser.parse_args()

    try:
        scenery = load_definitions(DEFS / "GameObjectDef.xml", "GameObjectDef", "scenery", ("objectModel",))
        boundaries = load_definitions(
            DEFS / "DoorDef.xml", "DoorDef", "boundary", ("modelVar1", "modelVar2", "modelVar3", "doorType")
        )
        scenery_constants = load_constants(CONSTANTS / "SceneryId.java")
        boundary_constants = load_constants(CONSTANTS / "BoundaryId.java")
        behaviors = load_behaviors(load_item_names())
        overrides = load_overrides(args.overrides)
        rows = catalog_rows(scenery, scenery_constants, behaviors, overrides["scenery"])
        rows.extend(catalog_rows(boundaries, boundary_constants, {}, overrides["boundary"]))
        output_ok = write_or_check(args.output, render_tsv(rows), args.check)
        audit_ok = write_or_check(args.audit_output, render_audit(scenery, boundaries, rows), args.check)
        return 0 if output_ok and audit_ok else 1
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as failure:
        print(f"ERROR: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
