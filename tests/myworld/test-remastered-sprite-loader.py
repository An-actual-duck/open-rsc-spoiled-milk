#!/usr/bin/env python3
import json
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "dev/myworld/assets/remastered-sprites"
JAR = ROOT / "Client_Base/Open_RSC_Client.jar"


def run(command, cwd=ROOT):
    environment = dict(os.environ)
    environment.pop("SPOILED_MILK_REMASTERED_SPRITES", None)
    completed = subprocess.run(
        command,
        cwd=cwd,
        env=environment,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    assert completed.returncode == 0, completed.stdout
    return completed.stdout


def require(source, fragments, name):
    for fragment in fragments:
        assert fragment in source, f"{name} is missing {fragment!r}"


def main():
    run([str(ROOT / "scripts/remastered-sprites.sh"), "generate", "--check"])
    manifest = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["entryCount"] == 156

    generated = (ROOT / "Client_Base/src/orsc/remastered/RemasteredSpriteCatalogData.java").read_text()
    assert generated.count("\t\tnew Record(") == manifest["entryCount"]
    assert manifest["catalogRevision"] in generated

    build = (ROOT / "Client_Base/build.xml").read_text()
    require(build, [
        '<include name="remastered-sprites/**/frames/*.png"/>',
        '<include name="remastered-sprites/manifest.json"/>',
    ], "Client_Base/build.xml")
    assert '<include name="remastered-sprites/**/*.png"/>' not in build
    release_sources = (ROOT / "release/player/ASSET-SOURCES.txt").read_text()
    assert "ThatKidBobo" in release_sources

    resolver = (ROOT / "Client_Base/src/orsc/remastered/RemasteredSpriteResolver.java").read_text()
    require(resolver, [
        "if (!RemasteredSpriteSettings.isEnabled())",
        "return canonical;",
        "if (!matchesCanonical(entry, canonical) || invalid.contains(key))",
        "RendererTransparency.OPAQUE_BLACK_REPLACEMENT",
        "loaded.put(key, override);",
        "RESOURCE_ROOT = \"myworld-assets/remastered-sprites/\"",
    ], "RemasteredSpriteResolver.java")

    graphics = (ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java").read_text()
    require(graphics, [
        "resolveRemastered(ItemDef item, Sprite canonical)",
        "remasteredSpriteResolver.resolve(animation, offset, canonical)",
        "RemasteredSpriteKey.forSprite(sprite)",
    ], "GraphicsController.java")

    client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
    require(client, [
        "RemasteredSpriteSettings.loadFromClientSettings(clientSettings);",
        "@whi@Sprites: ",
        "@cya@Enhanced",
        "@gre@Classic",
        "toggleRemasteredSprites();",
        "getSurface().resolveRemastered(item, externalSprite)",
        "refreshAppearancePanelSprites();",
    ], "mudclient.java")
    profile_applier = (ROOT / "Client_Base/src/orsc/RendererProfileApplier.java").read_text()
    require(profile_applier, [
        "RemasteredSpriteSettings.applyClassicProfile();",
        "host.refreshAppearancePreview();",
    ], "RendererProfileApplier.java")

    build_output = run([str(ROOT / "scripts/build-client.sh")])
    assert "BUILD SUCCESSFUL" in build_output
    with zipfile.ZipFile(JAR) as archive:
        names = set(archive.namelist())
        packaged_pngs = {
            name for name in names
            if name.startswith("myworld-assets/remastered-sprites/") and name.endswith(".png")
        }
        expected_pngs = {
            "myworld-assets/remastered-sprites/" + entry["png"]
            for entry in manifest["entries"]
        }
        assert packaged_pngs == expected_pngs
        assert "myworld-assets/remastered-sprites/manifest.json" in names
        assert not any("/work/" in name or "/textures/" in name for name in packaged_pngs)
        packaged_manifest = archive.read("myworld-assets/remastered-sprites/manifest.json")
        assert packaged_manifest == (ASSETS / "manifest.json").read_bytes()

    with tempfile.TemporaryDirectory(prefix="remastered-loader-audit-") as temporary:
        for key in ("sprite/items/0/0", "sprite/npc/demon/0"):
            audit = run([
                "java", "-cp", str(JAR),
                "com.openrsc.client.tools.RemasteredSpriteLoaderAudit", key,
            ], cwd=Path(temporary))
            assert "PASS: remastered sprite side-by-side loader" in audit
            assert "loaded=1" in audit
            assert "decodes=1" in audit

    print("PASS: remastered sprite loader, fallback, and package boundary validated")


if __name__ == "__main__":
    main()
