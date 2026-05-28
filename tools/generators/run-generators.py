#!/usr/bin/env python3
import argparse
import subprocess
import sys

from generator_common import ROOT, display_path, load_generator_manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run or inspect MyWorld generated-artifact workflows."
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Run each generator in validation mode without rewriting generated files.",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="Print the generator manifest entries without running them.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the exact generator commands that would run, but do not execute them.",
    )
    parser.add_argument(
        "--only",
        action="append",
        default=[],
        metavar="NAME",
        help="Run or inspect only the named generator manifest entry. Repeat to select multiple generators.",
    )
    parser.add_argument(
        "--group",
        action="append",
        default=[],
        metavar="NAME",
        help="Run or inspect every generator in the named manifest group. Repeat to select multiple groups.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.list and args.dry_run:
        print("FAIL: --list and --dry-run are mutually exclusive", file=sys.stderr)
        raise SystemExit(1)

    manifest = load_generator_manifest()
    generators = manifest["generators"]
    groups = manifest["groups"]

    selected_names: set[str] = set()
    if args.group:
        unknown_groups = sorted(set(args.group) - set(groups.keys()))
        if unknown_groups:
            print(
                f"FAIL: unknown generator group(s): {', '.join(unknown_groups)}",
                file=sys.stderr,
            )
            raise SystemExit(1)
        for group_name in args.group:
            selected_names.update(str(name) for name in groups[group_name])

    if args.only:
        selected_names.update(args.only)

    if selected_names:
        known_names = {str(generator["name"]) for generator in generators}
        unknown_names = sorted(selected_names - known_names)
        if unknown_names:
            print(
                f"FAIL: unknown generator name(s): {', '.join(unknown_names)}",
                file=sys.stderr,
            )
            raise SystemExit(1)
        generators = [
            generator for generator in generators if str(generator["name"]) in selected_names
        ]

    if args.list:
        if groups:
            for group_name, group_entries in groups.items():
                if not selected_names or any(name in selected_names for name in group_entries):
                    print(f"group {group_name}: {', '.join(group_entries)}")
        for generator in generators:
            print(
                f"{generator['name']}: "
                f"script={generator['script']} "
                f"source={generator['sourceDir']} "
                f"target={generator['target']}"
            )
        return

    for generator in generators:
        command = [sys.executable, str(ROOT / str(generator["script"]))]
        if args.check:
            command.append("--check")

        if args.dry_run:
            rendered_command = " ".join(command)
            print(
                f"{generator['name']}: {rendered_command} "
                f"# source={display_path(ROOT / str(generator['sourceDir']))} "
                f"target={display_path(ROOT / str(generator['target']))}"
            )
            continue

        result = subprocess.run(command)
        if result.returncode != 0:
            raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
