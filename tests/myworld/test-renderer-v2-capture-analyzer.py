#!/usr/bin/env python3
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANALYZER = ROOT / "scripts" / "analyze-renderer-v2-capture.py"


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def touch(path: Path) -> None:
    path.write_bytes(b"")


def run_analyzer(capture_dir: Path, *args: str) -> subprocess.CompletedProcess[str]:
    command = [sys.executable, str(ANALYZER), str(capture_dir), *args]
    return subprocess.run(command, capture_output=True, text=True)


def make_capture_fixture(capture_dir: Path) -> None:
    (capture_dir / "world-sprite-snapshots.tsv").unlink(missing_ok=True)
    write(
        capture_dir / "metadata.txt",
        "\n".join(
            [
                "source=1024x768",
                "target=1920x1080",
                "worldReplacementComposite=true",
                "residentTriangles=4",
                "",
            ]
        ),
    )
    write(capture_dir / "summary.txt", "failed=false\n")
    for name in [
        "00-legacy-source.png",
        "00-depth-kind.png",
        "00-entity-occluder-mask.png",
        "07-final.png",
    ]:
        touch(capture_dir / name)
    write(
        capture_dir / "world-faces.tsv",
        "\n".join(
            [
                "index\tmodelKind\tmodelIndex\tfaceId\ttexture\tcolor\torientation\tlegacyDrawOrder\taverageDepth\tvertexCount\trenderVertexCount\tcameraX\tcameraY\tcameraZ\tscreenX\tscreenY\tlight\ttextureU\ttextureV",
                "0\tTERRAIN\t1\t10\t-1\t123\t0\t120\t500\t4\t4\t1,2,3,4\t1,2,3,4\t500,500,500,500\t10,40,40,10\t10,10,40,40\t0,0,0,0\t0.0,1.0,1.0,0.0\t0.0,0.0,1.0,1.0",
                "1\tWALL\t1\t11\t7\t456\t0\t180\t450\t4\t4\t1,2,3,4\t1,2,3,4\t450,450,450,450\t20,60,60,20\t20,20,60,60\t0,0,0,0\t0.0,1.0,1.0,0.0\t0.0,0.0,1.0,1.0",
                "",
            ]
        ),
    )
    write(
        capture_dir / "sprite-commands.tsv",
        "\n".join(
            [
                "index\tsequence\tphase\tlegacySpriteId\tlegacyEntity\trequiresOrderedReplay\tx\ty\twidth\theight\ttopX16\tbottomX16\talpha\tsourceX\tsourceY\tsourceWidth\tsourceHeight\tspriteWidth\tspriteHeight\tmirrorX",
                "0\t0\tSCENE\t20001\ttrue\ttrue\t25\t25\t20\t20\t0\t0\t255\t0\t0\t20\t20\t20\t20\tfalse",
                "",
            ]
        ),
    )
    write(
        capture_dir / "renderer-2d-command-limits.tsv",
        "\n".join(
            [
                "stream\tlimit\tattempted\taccepted\tdropped",
                "sprite\t4096\t1\t1\t0",
                "text\t4096\t4\t4\t0",
                "primitive\t4096\t6\t6\t0",
                "rotated-sprite\t256\t259\t256\t3",
                "circle\t512\t2\t2\t0",
                "",
            ]
        ),
    )
    write(
        capture_dir / "sprite-anchors.tsv",
        "\n".join(
            [
                "index\tfaceId\tspriteId\tlegacyEntity\tpickIndex\tlegacyDrawOrder\taverageDepth\tcameraX\tcameraY\tcameraZ\tscreenX\tscreenY\tdrawX\tdrawY\tdrawWidth\tdrawHeight\tscale\thorizontalSkew\tpickable",
                "0\t99\t20001\ttrue\t30001\t150\t400\t0\t0\t400\t30\t30\t25\t25\t20\t20\t16\t0\ttrue",
                "",
            ]
        ),
    )
    write(
        capture_dir / "world-sprite-commands.tsv",
        "\n".join(
            [
                "index\tsequence\tphase\tlegacySpriteId\tworldSpriteKind\tanchorMatchMode\tanchorMatchScore\tanchorFaceId\tanchorLegacyDrawOrder\tanchorAverageDepth\tanchorCameraZ\tdepthOwned\tx\ty\twidth\theight\ttopX16\tbottomX16\talpha\tsourceX\tsourceY\tsourceWidth\tsourceHeight\tspriteWidth\tspriteHeight\tsourceCropped\tmirrorX\tskewed",
                "0\t0\tSCENE\t20001\tentity\tstrict-id-bounds\t0\t99\t150\t400\t400\ttrue\t25\t25\t20\t20\t0\t0\t255\t0\t0\t20\t20\t20\t20\tfalse\tfalse\tfalse",
                "",
            ]
        ),
    )
    write(
        capture_dir / "scene-commands.tsv",
        "\n".join(
            [
                "index\tkind\tlegacyDrawOrder\tsequence\tminExclusiveOrder\tmaxExclusiveOrder\tfrontOccluderFaces\tlegacySpriteId\tworldSpriteKind\tanchorMatchMode\tanchorFaceId\tsourceCropped\tmirrorX\tskewed",
                "0\tWORLD_SPRITE\t150\t0\t\t\t0\t20001\tentity\tstrict-id-bounds\t99\tfalse\tfalse\tfalse",
                "1\tFRONT_OCCLUDER_RANGE\t150\t2147483647\t150\t\t1\t\t\t\t\tfalse\tfalse\tfalse",
                "",
            ]
        ),
    )
    write(
        capture_dir / "static-world-commands.tsv",
        "\n".join(
            [
                "index\tkind\tmodelKind\tfaceCount\ttriangleCount\tminLegacyDrawOrder\tmaxLegacyDrawOrder",
                "0\tBASE_WORLD\tTERRAIN\t1\t2\t120\t120",
                "1\tBASE_WORLD\tWALL\t1\t2\t180\t180",
                "",
            ]
        ),
    )
    write(
        capture_dir / "static-world-face-ownership.tsv",
        "\n".join(
            [
                "commandIndex\tkind\tmodelKind\tmodelIndex\tfaceId",
                "0\tBASE_WORLD\tTERRAIN\t1\t10",
                "1\tBASE_WORLD\tWALL\t1\t11",
                "",
            ]
        ),
    )
    write(
        capture_dir / "static-world-material-triangles.tsv",
        "\n".join(
            [
                "triangleIndex\tmaterialPass\tmodelKind\tmodelIndex\tfaceId\ttextureId\ttextureHasTransparency",
                "0\tOPAQUE\tTERRAIN\t1\t10\t-1\tfalse",
                "1\tOPAQUE\tTERRAIN\t1\t10\t-1\tfalse",
                "2\tCUTOUT\tWALL\t1\t11\t4\ttrue",
                "3\tCUTOUT\tWALL\t1\t11\t4\ttrue",
                "",
            ]
        ),
    )
    write(
        capture_dir / "resident-material-families.tsv",
        "\n".join(
            [
                "chunkIndex\tplane\tcenterSectionX\tcenterSectionY\tchunkRole\tmodelKind\tmaterialFamily\tshaderId\ttextureId\tfallbackColor\tglowEmitterCount\ttriangleCount",
				"0\t0\t50\t50\t0\tTERRAIN\tTERRAIN\t1\t-1\t123\t0\t1",
				"0\t0\t50\t50\t0\tTERRAIN\tWATER\t2\t1\t12345678\t0\t1",
				"1\t0\t50\t50\t1\tGAME_OBJECT\tFOLIAGE\t6\t4\t12345678\t0\t1",
				"1\t0\t50\t50\t1\tGAME_OBJECT\tORE\t7\t4\t12345678\t0\t1",
                "",
            ]
        ),
    )
    write(
        capture_dir / "static-range-candidates.tsv",
        "\n".join(
            [
                "index\tminExclusiveOrder\tmaxExclusiveOrder\tworldSpriteCommandsAtOrder\tfinalRange\tstaticFaces\tterrainFaces\twallFaces\troofFaces\tgameObjectFaces\twallObjectFaces\toverlapFaces\toverlapTerrainFaces\toverlapWallFaces\toverlapRoofFaces\toverlapGameObjectFaces\toverlapWallObjectFaces\toverlapWorldSpriteCommands",
                "0\t150\t\t1\ttrue\t1\t0\t1\t0\t0\t0\t1\t0\t1\t0\t0\t0\t1",
                "",
            ]
        ),
    )
    write(
        capture_dir / "front-occluder-candidates.tsv",
        "\n".join(
            [
                "index\trangeIndex\tminExclusiveOrder\tmaxExclusiveOrder\tmodelKind\tmodelIndex\tfaceId\tlegacyDrawOrder\taverageDepth\toverlapWorldSpriteCommands\tbounds",
                "0\t0\t150\t\tWALL\t1\t11\t180\t450\t1\t20,20-60,60",
                "",
            ]
        ),
    )
    write(
        capture_dir / "sprite-submissions.tsv",
        "index\tfaceId\tspriteId\tlegacyEntity\tpickIndex\tprojected\tcullReason\tworldX\tworldY\tworldZ\tsourceWidth\tsourceHeight\tcameraX\tcameraY\tcameraZ\tscreenX\tscreenY\tdrawX\tdrawY\tdrawWidth\tdrawHeight\tscale\thorizontalSkew\n",
    )
    write(
        capture_dir / "character-sprites.tsv",
        "\n".join(
            [
                "index\tkind\tfaceId\tspriteId\tserverIndex\tdisplayName\tprojected\tbodyCommands\tbodySourceVisiblePixels\trestoreDepthEvaluations\trestoreDepthVisiblePixels\trestoreDepthOccludedPixels\tdiagnosis",
                "0\tNPC\t99\t20001\t123\tFixture NPC\ttrue\t1\t400\t1\t50\t350\trestore-visible",
                "",
            ]
        ),
    )
    write(
        capture_dir / "entity-restore-stats.tsv",
        "\n".join(
            [
                "legacySpriteId\tcommands\tvisiblePixels\tdirectPixels\tfallbacks\tskipped\tatlasFull\tdepthEvaluations\tdepthVisiblePixels\tdepthMisses\tdepthSourcePixels\tdepthOccludedPixels",
                "20001\t1\t100\t0\t0\t0\t0\t2\t50\t2\t400\t350",
                "",
            ]
        ),
    )
    write(
        capture_dir / "entity-depth-evaluations.tsv",
        "\n".join(
            [
                "index\tsequence\tphase\tlegacySpriteId\tx\ty\twidth\theight\ttopX16\tbottomX16\tanchorFaceId\tanchorLegacyDrawOrder\tanchorAverageDepth\tanchorCameraZ\tanchorMatchMode\tanchorMatchScore\tanchorDrawX\tanchorDrawY\tanchorDrawWidth\tanchorDrawHeight\tanchorDeltaX\tanchorDeltaY\tanchorDeltaWidth\tanchorDeltaHeight\tsourcePixels\tvisiblePixels\toccludedPixels\tclippedPixels\toutOfBoundsPixels\tterrainOccludedPixels\twallOccludedPixels\troofOccludedPixels\tgameObjectOccludedPixels\twallObjectOccludedPixels\tminOccluderLegacyDrawOrder\tmaxOccluderLegacyDrawOrder\tminOccluderDepth\tmaxOccluderDepth\tdominantOccluderKind\tdominantOccluderFaceId\tdominantOccluderModelIndex\tdominantOccluderPixels\tdominantOccluderLegacyDrawOrder\tdominantOccluderDepth\tfullyOccluded\tvisiblePct\toccludedPct\tbounds",
                "0\t0\tSCENE\t20001\t25\t25\t20\t20\t0\t0\t99\t150\t400\t400\tstrict-id-bounds\t0\t25\t25\t20\t20\t0\t0\t0\t0\t400\t50\t350\t0\t0\t0\t350\t0\t0\t0\t180\t180\t360\t360\tWALL\t11\t1\t350\t180\t360\tfalse\t12.5\t87.5\t25,25-45,45",
                "",
            ]
        ),
    )


def add_owned_snapshot_fixture(capture_dir: Path) -> None:
    write(
        capture_dir / "world-sprite-snapshots.tsv",
        "\n".join(
            [
                "snapshotIndex\tanchorIndex\tlegacySpriteId\tlayerIndex\tsequence\tcharacterKind",
                "0\t0\t20001\t0\t0\tNPC",
                "",
            ]
        ),
    )
    write(
        capture_dir / "world-sprite-commands.tsv",
        "\n".join(
            [
                "index\tsequence\tphase\tlegacySpriteId\tsceneSpriteAnchorIndex"
                "\tsceneSpriteDrawOrder\trendererSnapshotOwned\tworldSpriteKind"
                "\tanchorMatchMode\tanchorMatchScore\tanchorFaceId"
                "\tanchorLegacyDrawOrder\tanchorAverageDepth\tanchorCameraZ\tdepthOwned"
                "\tx\ty\twidth\theight\ttopX16\tbottomX16\talpha\tsourceX\tsourceY"
                "\tsourceWidth\tsourceHeight\tspriteWidth\tspriteHeight"
                "\tsourceCropped\tmirrorX\tskewed",
                "0\t0\tSCENE\t20001\t0\t150\ttrue\tentity\trenderer-snapshot\t0"
                "\t99\t150\t400\t400\ttrue\t25\t25\t20\t20\t0\t0\t255\t0\t0"
                "\t20\t20\t20\t20\tfalse\tfalse\tfalse",
                "",
            ]
        ),
    )


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="renderer-v2-capture-analyzer-") as tmp:
        capture_dir = Path(tmp)
        make_capture_fixture(capture_dir)
        result = run_analyzer(capture_dir, "--top", "5", "--strict")
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        output = result.stdout
        for snippet in [
            "counts=worldFaces:2 spriteCommands:1 worldSpriteSnapshotRows:0 worldSpriteCommands:1 sceneCommands:2 staticWorldCommands:2 staticWorldOwnedFaces:2 staticWorldMaterialTriangles:4 residentMaterialRows:4 staticRangeCandidates:1 frontOccluderCandidates:1 spriteAnchors:1 spriteSubmissions:0 characters:1 entityDepthEvaluations:1",
            "worldKinds:",
            "  TERRAIN: 1",
            "  WALL: 1",
            "spritePhases:",
            "  SCENE: 1",
            "renderer2DCommandLimits:",
            "  sprite: limit=4096 attempted=1 accepted=1 dropped=0",
            "  rotated-sprite: limit=256 attempted=259 accepted=256 dropped=3",
            "worldSpriteCommands:",
            "total:1 anchored:1 missingAnchor:0 depthOwned:1 rendererSnapshotOwned:0 sourceCropped:0 mirrorX:0 skewed:0",
            "worldSpriteAnchorMatchModes:",
            "  strict-id-bounds: 1",
            "worldSpriteSnapshots:\n  none",
            "sceneCommandQueue:",
            "total:2 worldSprite:1 frontOccluderRange:1 frontOccluderFaces:1 staticWorldRange:0 sourceCropped:0 mirrorX:0 skewed:0",
            "sceneCommandAnchorMatchModes:",
            "  strict-id-bounds: 1",
            "staticWorldCommands:",
            "total:2 baseWorld:2 faceCount:2 triangleCount:4 ownedFaces:2 uniqueFaces:2 duplicateFaces:0 unownedWorldFaces:0 orphanOwnedFaces:0 unownedFrontOccluders:0",
            "staticWorldMaterialPasses:",
            "total:4 expected:4 uniqueTriangles:4 duplicateTriangles:0 missingTriangles:0 outOfRangeTriangles:0 unresolved:0 translucent:0",
            "CUTOUT: 2",
            "OPAQUE: 2",
            "staticWorldCommandKinds:",
            "  TERRAIN: 1",
            "  WALL: 1",
            "residentMaterialFamilies:",
            "total:4 expected:4 rows:4 uniqueRows:4 duplicateRows:0 missingTriangles:0 invalidRows:0 contradictions:0 waterNonTerrain:0 semanticNonObject:0 emissiveWithoutGlow:0 unclassified:0",
            "residentMaterialFamilyCounts:",
            "  FOLIAGE: 1",
            "  ORE: 1",
            "  TERRAIN: 1",
            "  WATER: 1",
            "staticRangeCandidates:",
            "total:1 finalRanges:1 worldSpriteCommandsAtOrders:1 staticFaces:1 overlapFaces:1 overlapWorldSpriteCommands:1 overlapTerrain:0 overlapWall:1 overlapRoof:0 overlapGameObject:0 overlapWallObject:0",
            "staticRangeOutliers:",
            "0\t150\t\t1\ttrue\t1\t1\t1\t0\t1\t0\t0\t0",
            "frontOccluderCandidates:",
            "total:1 expectedFromStaticOverlap:1 wall:1 gameObject:0 wallObject:0 overlapWorldSpriteCommands:1",
            "frontOccluderOutliers:",
            "0\t0\t150\t\tWALL\t1\t11\t180\t450\t1\t20,20-60,60",
            "characterDiagnoses:",
            "  restore-visible: 1",
            "entityVisibilityHealth:",
            "projectedWithBody:1 visible:1 expectedFullyOccluded:0 expectedOutOfFrame:0 notProjected:0 suspicious:0",
            "suspiciousVisibility:",
            "  none",
            "liveDepthEvaluations:",
            "total:1 fullyOccluded:0 sourcePixels:400 visiblePixels:50 occludedPixels:350 clippedPixels:0 outOfBoundsPixels:0 terrain:0 wall:350",
            "anchorMatchModes:",
            "  strict-id-bounds: 1",
            "anchorGeometryHealth:",
            "count:1 exactRect:1 maxAbsDeltaX:0 maxAbsDeltaY:0 maxAbsDeltaWidth:0 maxAbsDeltaHeight:0",
            "nonExact:0 sourceCrop:0 uncropped:0 mirrorX:0 skewed:0 missingCommand:0",
            "anchorGeometryOutliers:",
            "  none",
            "fullyOccludedCommands:",
            "  none",
            "fullyOccludedEntities:",
            "  none",
            "occlusionPressure:",
            "20001\t99\t150\t1\t400\tWALL:1\t25,25-45,45",
            "occlusionReplay:",
            "20001\t0\t99\t150\t1\t1\t400\t400\t100.0\tWALL:400\tcommand\t1\t50\t0\t400\t350\t25,25-45,45",
            "occlusionDisagreements:",
            "20001\t99\t100.0\t400\t400\t1\t50\t0\t400\t350\tWALL:400\trestore-visible\tdepth-mask-covered-less-than-replay",
        ]:
            if snippet not in output:
                raise AssertionError(f"missing {snippet!r} in:\n{output}")

        make_capture_fixture(capture_dir)
        add_owned_snapshot_fixture(capture_dir)
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        if (
            "worldSpriteCommands:\n"
            "  total:1 anchored:1 missingAnchor:0 depthOwned:1 rendererSnapshotOwned:1"
            not in result.stdout
        ):
            raise AssertionError(result.stdout)
        if (
            "worldSpriteSnapshots:\n"
            "  rows:1 groups:1 layers:1 characterGroups:1 groundItemGroups:0 "
            "invalidAnchorRows:0 sequenceOrderViolations:0"
            not in result.stdout
        ):
            raise AssertionError(result.stdout)

        snapshot_path = capture_dir / "world-sprite-snapshots.tsv"
        snapshot_path.write_text(
            snapshot_path.read_text(encoding="utf-8").replace(
                "0\t0\t20001\t0\t0\tNPC",
                "0\t1\t20001\t0\t0\tNPC",
            ),
            encoding="utf-8",
        )
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode == 0:
            raise AssertionError("analyzer should reject invalid snapshot anchor ownership")
        if "renderer snapshot rows contain invalid anchor ownership: 1" not in result.stderr:
            raise AssertionError(result.stderr)

        make_capture_fixture(capture_dir)
        (capture_dir / "renderer-2d-command-limits.tsv").unlink()
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode != 0:
            raise AssertionError("older captures without 2D limit data should remain valid:\n" + result.stderr)
        if "renderer2DCommandLimits:\n  none" not in result.stdout:
            raise AssertionError(result.stdout)

        make_capture_fixture(capture_dir)
        (capture_dir / "resident-material-families.tsv").unlink()
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode != 0:
            raise AssertionError(
                "older captures without material-family data should remain valid:\n" + result.stderr
            )
        if "residentMaterialFamilies:\n  none" not in result.stdout:
            raise AssertionError(result.stdout)

        make_capture_fixture(capture_dir)
        material_text = (capture_dir / "resident-material-families.tsv").read_text(
            encoding="utf-8"
        )
        material_text = material_text.replace("\tFOLIAGE\t6\t", "\tFOLIAGE\t4\t")
        (capture_dir / "resident-material-families.tsv").write_text(
            material_text, encoding="utf-8"
        )
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode == 0:
            raise AssertionError("analyzer should reject unstable material-family shader ids")
        if "material family 'FOLIAGE' has shader id 4, expected 6" not in result.stderr:
            raise AssertionError(result.stderr)

        make_capture_fixture(capture_dir)
        material_path = capture_dir / "resident-material-families.tsv"
        material_text = material_path.read_text(encoding="utf-8").replace(
            "\tGAME_OBJECT\tORE\t7\t4\t12345678\t0\t1",
            "\tGAME_OBJECT\tEMISSIVE\t8\t4\t12345678\t0\t1",
        )
        material_path.write_text(material_text, encoding="utf-8")
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode != 0:
            raise AssertionError("classification contradictions should remain advisory:\n" + result.stderr)
        if "contradictions:1 waterNonTerrain:0 semanticNonObject:0 emissiveWithoutGlow:1" not in result.stdout:
            raise AssertionError(result.stdout)

        make_capture_fixture(capture_dir)
        limit_text = (capture_dir / "renderer-2d-command-limits.tsv").read_text(encoding="utf-8")
        limit_text = limit_text.replace(
            "rotated-sprite\t256\t259\t256\t3",
            "rotated-sprite\t256\t259\t257\t2",
        )
        (capture_dir / "renderer-2d-command-limits.tsv").write_text(limit_text, encoding="utf-8")
        result = run_analyzer(capture_dir)
        if result.returncode == 0:
            raise AssertionError("analyzer should reject accepted 2D command counts above the cap")
        if "renderer 2D rotated-sprite accepted count exceeds limit: 257 > 256" not in result.stderr:
            raise AssertionError(result.stderr)

        make_capture_fixture(capture_dir)
        scene_commands = (capture_dir / "scene-commands.tsv").read_text(encoding="utf-8")
        scene_commands = scene_commands.replace(
            "1\tFRONT_OCCLUDER_RANGE\t150\t2147483647\t150\t\t1\t\t\t\t\tfalse\tfalse\tfalse",
            "1\tFRONT_OCCLUDER_RANGE\t150\t2147483647\t150\t\t0\t\t\t\t\tfalse\tfalse\tfalse",
        )
        (capture_dir / "scene-commands.tsv").write_text(scene_commands, encoding="utf-8")
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode == 0:
            raise AssertionError("analyzer should fail when front occluder face ownership is missing")
        if "front-occluder scene command face ownership does not match candidate rows" not in result.stderr:
            raise AssertionError(result.stderr)

        make_capture_fixture(capture_dir)
        ownership = (capture_dir / "static-world-face-ownership.tsv").read_text(encoding="utf-8")
        ownership = ownership.replace(
            "1\tBASE_WORLD\tWALL\t1\t11",
            "9\tBASE_WORLD\tWALL\t1\t11",
        )
        (capture_dir / "static-world-face-ownership.tsv").write_text(ownership, encoding="utf-8")
        result = run_analyzer(capture_dir, "--strict")
        if result.returncode == 0:
            raise AssertionError("analyzer should fail when static face ownership references a missing command")
        if "ownership row references missing command 9" not in result.stderr:
            raise AssertionError(result.stderr)

        make_capture_fixture(capture_dir)
        (capture_dir / "world-faces.tsv").unlink()
        result = run_analyzer(capture_dir)
        if result.returncode == 0:
            raise AssertionError("analyzer should reject captures missing world-faces.tsv")
        if "capture missing required files: world-faces.tsv" not in result.stderr:
            raise AssertionError(result.stderr)

        make_capture_fixture(capture_dir)
        character_text = (capture_dir / "character-sprites.tsv").read_text(encoding="utf-8")
        character_text = character_text.replace("restore-visible", "body-command-before-restore-stats")
        (capture_dir / "character-sprites.tsv").write_text(character_text, encoding="utf-8")
        restore_text = (capture_dir / "entity-restore-stats.tsv").read_text(encoding="utf-8")
        restore_text = restore_text.replace(
            "20001\t1\t100\t0\t0\t0\t0\t2\t50\t2\t400\t350",
            "",
        )
        (capture_dir / "entity-restore-stats.tsv").write_text(restore_text, encoding="utf-8")
        result = run_analyzer(capture_dir, "--fail-on-suspicious-visibility")
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        if (
            "entityVisibilityHealth:\n"
            "  projectedWithBody:1 visible:1 expectedFullyOccluded:0 expectedOutOfFrame:0 notProjected:0 suspicious:0"
            not in result.stdout
        ):
            raise AssertionError(result.stdout)

        make_capture_fixture(capture_dir)
        character_text = (capture_dir / "character-sprites.tsv").read_text(encoding="utf-8")
        character_text = character_text.replace("restore-visible", "body-command-not-restored")
        (capture_dir / "character-sprites.tsv").write_text(character_text, encoding="utf-8")
        result = run_analyzer(capture_dir, "--fail-on-suspicious-visibility")
        if result.returncode == 0:
            raise AssertionError("analyzer should fail when suspicious visibility rows are present")
        if "suspicious projected entity visibility rows: 1" not in result.stderr:
            raise AssertionError(result.stderr)

    print("PASS: renderer-v2 capture analyzer validates and summarizes captures")


if __name__ == "__main__":
    main()
