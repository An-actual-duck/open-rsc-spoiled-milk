#!/usr/bin/env python3
"""Preserve the Rebirth concept and goblin study as a verified portable bundle."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", default=datetime.date.today().strftime("%Y%m%d"))
    args = parser.parse_args()
    study = Path(__file__).resolve().parent
    root = next(p for p in study.parents if (p / "config/workspace-layout.json").is_file())
    destination = Path(subprocess.check_output([
        sys.executable, str(root / "scripts/workspace-layout.py"),
        "path", "art", "rebirth-reference-bundle", args.run, "--mkdir",
    ], cwd=root, text=True).strip())
    files = {p.relative_to(root).as_posix(): p.read_bytes()
             for p in study.rglob("*")
             if p.is_file() and "__pycache__" not in p.parts}
    concept = "docs/myworld/rough-drafts/rebirth-visual-overhaul.md"
    files[concept] = (root / concept).read_bytes()
    # Optional portable snapshot; never a game build dependency.
    skill = Path.home() / ".codex/skills/multidirectional-sprites"
    for name in ("SKILL.md", "references/review-checklists.md"):
        source = skill / name
        if source.is_file():
            files["workflow/multidirectional-sprites/" + name] = source.read_bytes()
    files["START-HERE.txt"] = (
        "Rebirth concept and goblin reference bundle\n\n"
        "Concept: " + concept + "\n"
        "Art preview: dev/myworld/assets/authoring/npcs/"
        "rebirth-goblin-style-study/preview.html\n"
        "Style notes, prompts, editable masters and history labels sit beside it.\n"
        "Selected art: original front A and away-static-v2 still only.\n"
        "Historical alternatives and rejected animations are clearly identified "
        "in README.txt. No game installation or full directional set.\n"
        "workflow/ is an optional skill snapshot from bundle creation.\n"
    ).encode()
    manifest = {
        "schemaVersion": 1,
        "description": "Rebirth speculative concept and owner-selected goblin reference",
        "files": {name: {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                  for name, data in sorted(files.items())},
    }
    files["manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    # Versioned destination, refuse to replace a different bundle in the same run.
    archive = destination / "rebirth-goblin-reference.zip"
    temporary = destination / "rebirth-goblin-reference.pending.zip"
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED) as out:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo("rebirth-reference/" + name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(entry, data)
    with zipfile.ZipFile(temporary) as check:
        assert check.testzip() is None
        for name, data in files.items():
            assert check.read("rebirth-reference/" + name) == data, name
    if archive.exists() and archive.read_bytes() != temporary.read_bytes():
        temporary.unlink()
        raise SystemExit("Existing bundle differs; choose a new --run to preserve it.")
    temporary.replace(archive)
    print(f"Verified {len(files)} entries: {archive}")


if __name__ == "__main__":
    main()
