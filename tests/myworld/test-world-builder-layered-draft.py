#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / "tools/world-builder/src"
PACKAGE_ROOT = (
    ROOT
    / "tools/layered-maps/workspace/spoiled-milk-package-v2/package"
)


HARNESS = r"""
package com.openrsc.worldbuilder;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class WorldBuilderLayeredDraftHarness {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void copyTree(final Path source, final Path destination)
        throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(
                Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(
                Path file, BasicFileAttributes attributes) throws IOException {
                Path target = destination.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void prepare(Path workspace, Path packageRoot) throws Exception {
        WorldBuilderLayeredPackage accepted =
            WorldBuilderLayeredPackage.discover(
                packageRoot, WorldBuilderLayeredPackage.PROFILE_ID);
        Path source = workspace.resolve(
            WorldBuilderRuntimePreparer.LAYERED_SOURCE_PACKAGE);
        Path working = workspace.resolve(
            WorldBuilderRuntimePreparer.LAYERED_WORKING_PACKAGE);
        copyTree(packageRoot, source);
        copyTree(packageRoot, working);
        byte[] metadata =
            accepted.toMetadataJson().getBytes(StandardCharsets.UTF_8);
        Files.write(
            workspace.resolve(WorldBuilderRuntimePreparer.LAYERED_REVIEW_METADATA),
            metadata);
        Files.write(
            workspace.resolve("source").resolve(
                WorldBuilderRuntimePreparer.LAYERED_REVIEW_METADATA),
            metadata);
        StringBuilder inventory = new StringBuilder("world-builder-source-v1\n");
        for (WorldBuilderLayeredPackage.FileRecord file : accepted.files) {
            inventory.append(file.sha256).append('\t')
                .append("layered-world/package/")
                .append(file.relativePath).append('\n');
        }
        inventory.append(WorldBuilderHashes.sha256(metadata)).append('\t')
            .append(WorldBuilderRuntimePreparer.LAYERED_REVIEW_METADATA)
            .append('\n');
        Files.write(
            workspace.resolve(WorldBuilderRuntimePreparer.SOURCE_INVENTORY),
            inventory.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        Path packageRoot = Paths.get(args[0]);
        Path workspace = Paths.get(args[1]);
        Files.createDirectories(workspace);
        prepare(workspace, packageRoot);
        WorldBuilderSourceSnapshot.verify(workspace);
        Path sourceManifest = workspace.resolve(
            "source/layered-world/package/manifest.json");
        String sourceManifestHash = WorldBuilderHashes.sha256(sourceManifest);
        WorldBuilderLayeredReview before =
            WorldBuilderLayeredReview.readIfPresent(workspace);
        require(before.levels.size() == 4, "initial levels");
        require(before.terrainSectorCount == 1771, "initial terrain count");

        WorldBuilderLayeredDraftWriter writer =
            new WorldBuilderLayeredDraftWriter();
        WorldBuilderLayeredDraftWriter.CreateLevelResult created =
            writer.createLevel(
                workspace,
                -3,
                140,
                640,
                WorldBuilderLayeredDraftWriter.defaultName(-3),
                WorldBuilderLayeredDraftWriter.defaultRole(-3));
        require(created.level == -3, "created level");
        require(created.minimumSectorX == 1 && created.maximumSectorX == 3,
            "starter sector X bounds");
        require(created.minimumSectorY == 12 && created.maximumSectorY == 14,
            "starter sector Y bounds");
        require(created.terrainSectorCount == 1780, "created terrain count");
        require(created.placementSetCount == 5, "created placement count");
        require(sourceManifestHash.equals(WorldBuilderHashes.sha256(sourceManifest)),
            "source manifest changed");
        WorldBuilderSourceSnapshot.verify(workspace);

        WorldBuilderLayeredReview firstRestart =
            WorldBuilderLayeredReview.readIfPresent(workspace);
        WorldBuilderLayeredReview secondRestart =
            WorldBuilderLayeredReview.readIfPresent(workspace);
        require(firstRestart.levels.contains(Integer.valueOf(-3)),
            "new level missing after first reopen");
        require(secondRestart.levels.contains(Integer.valueOf(-3)),
            "new level missing after second reopen");
        require(created.manifestSha256.equals(secondRestart.manifestSha256),
            "manifest hash changed across reopen");
        require(created.packageFingerprintSha256.equals(
            secondRestart.packageFingerprintSha256),
            "package fingerprint changed across reopen");

        Path working = workspace.resolve(
            WorldBuilderRuntimePreparer.LAYERED_WORKING_PACKAGE);
        WorldBuilderLayeredPackage draft =
            WorldBuilderLayeredPackage.discoverDraft(working);
        WorldBuilderLayeredPackage accepted =
            WorldBuilderLayeredPackage.discover(
                workspace.resolve(WorldBuilderRuntimePreparer.LAYERED_SOURCE_PACKAGE),
                WorldBuilderLayeredPackage.PROFILE_ID);
        draft.requireFirstDraftDescendant(accepted);
        int newTerrain = 0;
        for (WorldBuilderLayeredPackage.TerrainRecord record
            : draft.terrainRecords) {
            if (record.level != -3) continue;
            newTerrain++;
            byte[] bytes = Files.readAllBytes(working.resolve(record.path));
            require(bytes.length == 48 * 48 * 10, "starter payload size");
            for (int offset = 0; offset < bytes.length; offset += 10) {
                require(bytes[offset] == 0, "starter elevation");
                require(bytes[offset + 1] == 1, "starter texture");
                for (int field = 2; field < 10; field++) {
                    require(bytes[offset + field] == 0,
                        "starter collision/structure field");
                }
            }
        }
        require(newTerrain == 9, "starter terrain count");
        WorldBuilderLayeredPackage.PlacementRecord empty = null;
        for (WorldBuilderLayeredPackage.PlacementRecord record
            : draft.placementRecords) {
            if (record.level == -3) empty = record;
        }
        require(empty != null && empty.placementCount == 0,
            "empty v3 placement set");

        Path journal = workspace.resolve(
            WorldBuilderLayeredTerrainDraftJournal.RELATIVE_PATH);
        String journalText =
            "world-builder-layered-terrain-draft-v1\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t1\n"
            + "sector-count\t1\n"
            + "sector\t-3\t4\t13\n"
            + "tile\t-3\t140\t640\t7\t8\t0\t0\t0\t0\t0\n";
        Files.write(journal, journalText.getBytes(StandardCharsets.US_ASCII));
        WorldBuilderLayeredTerrainDraftJournal.CommitResult terrainCommit =
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        require(terrainCommit != null && terrainCommit.tileCount == 1,
            "terrain tile journal commit");
        require(terrainCommit.sectorCount == 1,
            "terrain sector-growth journal commit");
        require(!Files.exists(journal), "committed journal retained");
        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        draft.requireTerrainDraftDescendant(accepted);
        require(draft.terrainSectorCount == 1781, "grown terrain count");
        Path changed = working.resolve("terrain/global/lm3/xp2-yp13.raw");
        byte[] changedBytes = Files.readAllBytes(changed);
        int changedOffset = (Math.floorMod(140, 48) * 48
            + Math.floorMod(640, 48)) * 10;
        require((changedBytes[changedOffset] & 255) == 7,
            "committed elevation");
        require((changedBytes[changedOffset + 1] & 255) == 8,
            "committed floor color");
        require(Files.size(
            working.resolve("terrain/global/lm3/xp4-yp13.raw"))
                == 48 * 48 * 10,
            "grown sector payload");
        require(sourceManifestHash.equals(WorldBuilderHashes.sha256(sourceManifest)),
            "source changed during terrain commit");
        WorldBuilderSourceSnapshot.verify(workspace);
        WorldBuilderLayeredReview terrainRestart =
            WorldBuilderLayeredReview.readIfPresent(workspace);
        require(terrainCommit.manifestSha256.equals(
            terrainRestart.manifestSha256),
            "terrain manifest changed across reopen");

        String refusedSourceEdit =
            "world-builder-layered-terrain-draft-v1\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t1\n"
            + "sector-count\t0\n"
            + "tile\t0\t120\t648\t7\t8\t0\t0\t0\t0\t0\n";
        Files.write(
            journal,
            refusedSourceEdit.getBytes(StandardCharsets.US_ASCII));
        boolean sourceEditRefused = false;
        try {
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        } catch (WorldBuilderDiscoveryException expected) {
            sourceEditRefused = expected.getMessage().contains(
                "restricted to a Builder-created level");
        }
        require(sourceEditRefused, "accepted source-level edit refusal");
        require(Files.exists(journal), "refused journal was discarded");
        require(terrainCommit.manifestSha256.equals(
            WorldBuilderLayeredPackage.discoverDraft(working).manifestSha256),
            "source-level refusal changed the working draft");
        Files.delete(journal);

        String stableFingerprint = draft.packageFingerprintSha256;
        boolean duplicateRefused = false;
        try {
            writer.createLevel(
                workspace, -3, 140, 640,
                "Duplicate", "underground-level-3");
        } catch (WorldBuilderDiscoveryException expected) {
            duplicateRefused = expected.getMessage().contains(
                "already declared");
        }
        require(duplicateRefused, "duplicate level refusal");
        require(stableFingerprint.equals(
            WorldBuilderLayeredPackage.discoverDraft(working)
                .packageFingerprintSha256),
            "duplicate refusal changed draft");

        Path lockPath = workspace.getParent().resolve(
            "." + workspace.getFileName() + ".world-builder.lock");
        boolean runningRefused = false;
        try (FileChannel channel = FileChannel.open(
                lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            try {
                writer.createLevel(
                    workspace, 3, 140, 640,
                    "Upper level 3", "upper-level-3");
            } catch (WorldBuilderDiscoveryException expected) {
                runningRefused = expected.getMessage().contains(
                    "Close the World Builder");
            }
        }
        require(runningRefused, "running workspace refusal");
        require(stableFingerprint.equals(
            WorldBuilderLayeredPackage.discoverDraft(working)
                .packageFingerprintSha256),
            "running refusal changed draft");
        require(sourceManifestHash.equals(WorldBuilderHashes.sha256(sourceManifest)),
            "source changed after refusals");
        System.out.println("world-builder-layered-draft-ok");
    }
}
"""


class WorldBuilderLayeredDraftTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="world-builder-layered-draft-classes-"
        )
        cls.classes = Path(cls.compile_temp.name)
        harness = (
            cls.classes
            / "com/openrsc/worldbuilder/WorldBuilderLayeredDraftHarness.java"
        )
        harness.parent.mkdir(parents=True)
        harness.write_text(textwrap.dedent(HARNESS), encoding="utf-8")
        sources = [str(path) for path in SOURCE_ROOT.rglob("*.java")]
        result = subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", str(cls.classes)]
            + sources
            + [str(harness)],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_transactional_create_level_is_isolated_and_restart_safe(self):
        with tempfile.TemporaryDirectory(
            prefix="world-builder-layered-draft-workspace-"
        ) as temp:
            workspace = Path(temp) / "draft"
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(self.classes),
                    "com.openrsc.worldbuilder.WorldBuilderLayeredDraftHarness",
                    str(PACKAGE_ROOT),
                    str(workspace),
                ],
                cwd=ROOT,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(
                "world-builder-layered-draft-ok\n", result.stdout
            )


if __name__ == "__main__":
    unittest.main()
