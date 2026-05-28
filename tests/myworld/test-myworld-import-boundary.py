#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
PLUGINS_ROOT = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins"
MYWORLD_NAMESPACE = "com.openrsc.server.plugins.custom.myworld"
ALLOWED_NON_MYWORLD_REFERENCES = {
    PLUGINS_ROOT / "custom" / "quests" / "free" / "PeelingTheOnion.java",
}
MYWORLD_PEELING_THE_ONION = (
    PLUGINS_ROOT / "custom" / "myworld" / "quests" / "free" / "PeelingTheOnion.java"
)
BRIDGE_PEELING_THE_ONION = (
    PLUGINS_ROOT / "custom" / "quests" / "free" / "PeelingTheOnion.java"
)
PEELING_THE_ONION_TARGET = (
    "com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion"
)
STATE_PATTERN = re.compile(
    r"public\s+static\s+final\s+int\s+"
    r"(?P<name>STATE_[A-Z0-9_]+)\s*="
)
METHOD_PATTERN = re.compile(
    r"public\s+static\s+"
    r"(?P<return_type>[A-Za-z0-9_<>]+)\s+"
    r"(?P<name>[A-Za-z0-9_]+)\s*"
    r"\((?P<params>[^)]*)\)"
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def is_myworld_file(path: Path) -> bool:
    return (PLUGINS_ROOT / "custom" / "myworld") in path.parents


def parse_state_names(text: str) -> list[str]:
    return STATE_PATTERN.findall(text)


def normalize_parameter(parameter: str) -> str:
    parts = parameter.strip().split()
    if len(parts) < 2:
        fail(f"Unexpected method parameter format: {parameter!r}")
    return " ".join(parts[:-1])


def parse_method_signatures(text: str) -> list[tuple[str, str, tuple[str, ...]]]:
    signatures: list[tuple[str, str, tuple[str, ...]]] = []
    for match in METHOD_PATTERN.finditer(text):
        params = match.group("params").strip()
        param_types = tuple(
            normalize_parameter(parameter)
            for parameter in params.split(",")
            if parameter.strip()
        )
        signatures.append(
            (match.group("return_type"), match.group("name"), param_types)
        )
    return signatures


def validate_peeling_the_onion_bridge() -> None:
    bridge_text = BRIDGE_PEELING_THE_ONION.read_text(encoding="utf-8")
    myworld_text = MYWORLD_PEELING_THE_ONION.read_text(encoding="utf-8")

    if PEELING_THE_ONION_TARGET not in bridge_text:
        fail("PeelingTheOnion bridge does not forward to the MyWorld quest class")

    bridge_states = parse_state_names(bridge_text)
    myworld_states = parse_state_names(myworld_text)
    if bridge_states != myworld_states:
        fail(
            "PeelingTheOnion bridge state constants drifted from the MyWorld quest "
            f"surface: bridge={bridge_states}, myworld={myworld_states}"
        )

    bridge_methods = parse_method_signatures(bridge_text)
    myworld_methods = parse_method_signatures(myworld_text)
    if bridge_methods != myworld_methods:
        fail(
            "PeelingTheOnion bridge method surface drifted from the MyWorld quest "
            f"surface: bridge={bridge_methods}, myworld={myworld_methods}"
        )


def main() -> None:
    violations: list[Path] = []

    for path in PLUGINS_ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        if MYWORLD_NAMESPACE not in text:
            continue
        if is_myworld_file(path) or path in ALLOWED_NON_MYWORLD_REFERENCES:
            continue
        violations.append(path)

    if violations:
        formatted = ", ".join(
            str(path.relative_to(ROOT)) for path in sorted(violations)
        )
        fail(
            f"Unexpected non-MyWorld package references to MyWorld handlers: {formatted}"
        )

    validate_peeling_the_onion_bridge()

    print("PASS: MyWorld import boundary validated")
    print(f"Allowed bridge files: {len(ALLOWED_NON_MYWORLD_REFERENCES)}")


if __name__ == "__main__":
    main()
