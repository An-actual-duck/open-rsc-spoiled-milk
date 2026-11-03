#!/usr/bin/env python3
import json
import os
import shutil
import subprocess
import tempfile
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "dev/myworld/assets/authoring/remastered-sprites"
WRAPPER = ROOT / "scripts/remastered-sprites.sh"


def run(*arguments, env=None):
    completed = subprocess.run(
        [str(WRAPPER), *arguments],
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    assert completed.returncode == 0, completed.stdout
    return completed.stdout


def main():
    assert not (ROOT / "dev/myworld/assets/bobo-resprites").exists()
    descriptors = sorted(ASSETS.glob("**/set.json"))
    descriptors = [path for path in descriptors if "_templates" not in path.parts]
    assert len(descriptors) == 113, len(descriptors)

    records = [json.loads(path.read_text(encoding="utf-8")) for path in descriptors]
    assert Counter(record["status"] for record in records) == {"ready": 88, "work": 25}
    assert all(record["contributor"]["id"] == "thatkidbobo" for record in records)
    assert all(record["contributor"]["sourceCommit"].startswith("abd9d08fc") for record in records)

    legacy_ids = []
    for record in records:
        for target in record["targets"]:
            legacy_ids.extend(target["legacySourceIds"])
    assert len(legacy_ids) == 198
    assert len(set(legacy_ids)) == 198
    assert len(list(ASSETS.glob("**/*.png"))) == 198

    expected_ready_animations = {
        "players/body1", "players/legs1", "players/fbody1", "npcs/demon"
    }
    for relative in expected_ready_animations:
        path = ASSETS / relative
        assert len(list(path.glob("frames/*.png"))) == 18
        assert json.loads((path / "set.json").read_text())["status"] == "ready"
    assert len(list((ASSETS / "equipment/hatchet/work").glob("*.png"))) == 18
    assert len(list((ASSETS / "items/pie-entry-112/work").glob("*.png"))) == 1
    assert all(
        json.loads(path.read_text())["status"] == "work"
        for path in ASSETS.glob("textures/*/set.json")
    )

    validation = run("validate")
    assert "113 sets validated" in validation
    assert "156 ready catalog entries" in validation
    run("generate", "--check")

    manifest = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["entryCount"] == 156
    keys = [entry["key"] for entry in manifest["entries"]]
    assert keys == sorted(keys)
    assert len(keys) == len(set(keys))
    assert not any(entry["png"].startswith("textures/") for entry in manifest["entries"])
    assert not any("/work/" in entry["png"] for entry in manifest["entries"])

    output = ROOT / "output" / "tests/remastered-sprites"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="remastered-classify-", dir=output) as temporary:
        delivery = Path(temporary)
        shutil.copyfile(ASSETS / "items/mace-entry-000/frames/00.png", delivery / "id-02150.png")
        shutil.copyfile(ASSETS / "textures/water-entry-01/work/00.png", delivery / "id-03226.png")
        before = sorted(path.name for path in delivery.iterdir())
        classification = run("classify", str(delivery))
        assert "sprite/items/0/0" in classification
        assert "Iron Mace" in classification
        assert "texture/1" in classification
        assert "active TextureDef: water" in classification
        assert sorted(path.name for path in delivery.iterdir()) == before

    with tempfile.TemporaryDirectory(prefix="remastered-workspace-") as temporary:
        test_assets = Path(temporary)
        (test_assets / "_templates").mkdir()
        (test_assets / "_templates/set.json").write_text(
            (ASSETS / "_templates/set.json").read_text(encoding="utf-8"), encoding="utf-8"
        )
        env = dict(os.environ)
        env["REMASTERED_SPRITE_ASSETS_ROOT"] = str(test_assets)
        run("scaffold", "npcs", "test-demon", env=env)
        scaffold = test_assets / "npcs/test-demon"
        assert (scaffold / "set.json").is_file()
        assert (scaffold / "frames").is_dir()
        assert (scaffold / "work").is_dir()
        assert (scaffold / "source").is_dir()

    print("PASS: remastered sprite workspace and Bobo migration validated")


if __name__ == "__main__":
    main()
