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
    / "tools/layered-maps/workspace/spoiled-milk-package-v3/package"
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
        require(before.levels.size() == 5, "initial levels");
        require(before.terrainSectorCount == 1775, "initial terrain count");

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
        require(created.terrainSectorCount == 1784, "created terrain count");
        require(created.placementSetCount == 6, "created placement count");
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
            for (int tile = 0; tile < 48 * 48; tile++) {
                int offset = tile * 10;
                int localX = tile / 48;
                int localY = tile % 48;
                int worldX = record.sectorX * 48 + localX;
                int worldY = record.sectorY * 48 + localY;
                boolean anchorPad =
                    Math.abs(worldX - 140) <= 1
                        && Math.abs(worldY - 640) <= 1;
                require(bytes[offset] == 0, "starter elevation");
                require(bytes[offset + 1] == 1, "starter texture");
                require((bytes[offset + 2] & 255) == (anchorPad ? 0 : 8),
                    "starter void/pad overlay");
                for (int field = 3; field < 10; field++) {
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
        require(draft.terrainSectorCount == 1785, "grown terrain count");
        Path changed = working.resolve("terrain/global/lm3/xp2-yp13.raw");
        byte[] changedBytes = Files.readAllBytes(changed);
        int changedOffset = (Math.floorMod(140, 48) * 48
            + Math.floorMod(640, 48)) * 10;
        require((changedBytes[changedOffset] & 255) == 7,
            "committed elevation");
        require((changedBytes[changedOffset + 1] & 255) == 8,
            "committed floor color");
        Path grown =
            working.resolve("terrain/global/lm3/xp4-yp13.raw");
        require(Files.size(grown) == 48 * 48 * 10,
            "grown sector payload");
        byte[] grownBytes = Files.readAllBytes(grown);
        for (int offset = 0; offset < grownBytes.length; offset += 10) {
            require(grownBytes[offset] == 0, "grown void elevation");
            require(grownBytes[offset + 1] == 1, "grown void texture");
            require((grownBytes[offset + 2] & 255) == 8,
                "grown void overlay");
            for (int field = 3; field < 10; field++) {
                require(grownBytes[offset + field] == 0,
                    "grown void structure field");
            }
        }
        require(sourceManifestHash.equals(WorldBuilderHashes.sha256(sourceManifest)),
            "source changed during terrain commit");
        WorldBuilderSourceSnapshot.verify(workspace);
        WorldBuilderLayeredReview terrainRestart =
            WorldBuilderLayeredReview.readIfPresent(workspace);
        require(terrainCommit.manifestSha256.equals(
            terrainRestart.manifestSha256),
            "terrain manifest changed across reopen");

        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        String placementId =
            "spoiled-milk.builder.scenery.lm3.xp141.yp640";
        String combinedDraft =
            "world-builder-layered-draft-v2\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t1\n"
            + "sector-count\t0\n"
            + "scenery-count\t1\n"
            + "tile\t-3\t141\t641\t0\t1\t0\t0\t0\t0\t0\n"
            + "scenery\tupsert\t-3\t141\t640\t"
            + placementId + "\t3\t0\n";
        Files.write(journal, combinedDraft.getBytes(StandardCharsets.US_ASCII));
        WorldBuilderLayeredTerrainDraftJournal.CommitResult combinedCommit =
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        require(combinedCommit.tileCount == 1
                && combinedCommit.sceneryCount == 1,
            "combined terrain/scenery journal commit");
        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        draft.requireTerrainDraftDescendant(accepted);
        WorldBuilderLayeredPackage.PlacementRecord authored = null;
        for (WorldBuilderLayeredPackage.PlacementRecord record
            : draft.placementRecords) {
            if (record.level == -3) authored = record;
        }
        require(authored != null && authored.sceneryCount == 1
                && authored.npcCount == 0
                && authored.groundItemCount == 0
                && authored.boundaryCount == 0,
            "new-level scenery-only placement payload");
        Path authoredPayload = working.resolve(
            "placements/global/lm3.json");
        String authoredText = new String(
            Files.readAllBytes(authoredPayload), StandardCharsets.UTF_8);
        require(authoredText.contains("\"placementId\": \"" + placementId + "\"")
                && authoredText.contains("\"sceneryId\": 3"),
            "authored scenery payload");
        require(WorldBuilderLayeredReview.readIfPresent(workspace)
                .manifestSha256.equals(combinedCommit.manifestSha256),
            "combined commit changed across reopen");

        String npcPlacementId =
            "spoiled-milk.builder.npc.lm3.xp142.yp641.s0";
        String npcDraft =
            "world-builder-layered-draft-v3\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t0\n"
            + "sector-count\t0\n"
            + "scenery-count\t0\n"
            + "npc-count\t1\n"
            + "npc\tupsert\t-3\t142\t641\t" + npcPlacementId
            + "\t0\t141\t640\t143\t642\n";
        Files.write(journal, npcDraft.getBytes(StandardCharsets.US_ASCII));
        WorldBuilderLayeredTerrainDraftJournal.CommitResult npcCommit =
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        require(npcCommit.npcCount == 1 && npcCommit.sceneryCount == 0,
            "NPC journal commit");
        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        draft.requireTerrainDraftDescendant(accepted);
        authored = null;
        for (WorldBuilderLayeredPackage.PlacementRecord record
            : draft.placementRecords) {
            if (record.level == -3) authored = record;
        }
        require(authored != null && authored.npcCount == 1
                && authored.sceneryCount == 1,
            "new-level NPC/scenery placement payload");
        authoredText = new String(
            Files.readAllBytes(authoredPayload), StandardCharsets.UTF_8);
        require(authoredText.contains("\"placementId\": \""
                    + npcPlacementId + "\"")
                && authoredText.contains("\"npcId\": 0")
                && authoredText.contains("\"roamBounds\""),
            "authored NPC payload");

        String rotateDraft =
            "world-builder-layered-draft-v2\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t0\n"
            + "sector-count\t0\n"
            + "scenery-count\t1\n"
            + "scenery\tupsert\t-3\t141\t640\t"
            + placementId + "\t3\t1\n";
        Files.write(journal, rotateDraft.getBytes(StandardCharsets.US_ASCII));
        WorldBuilderLayeredTerrainDraftJournal.CommitResult rotateCommit =
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        require(rotateCommit.sceneryCount == 1,
            "scenery rotation journal commit");
        authoredText = new String(
            Files.readAllBytes(authoredPayload), StandardCharsets.UTF_8);
        require(authoredText.contains("\"direction\": 1")
                && authoredText.contains("\"placementId\": \"" + placementId + "\""),
            "rotation retained scenery identity");

        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        String staleRemoveDraft =
            "world-builder-layered-draft-v2\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t0\n"
            + "sector-count\t0\n"
            + "scenery-count\t1\n"
            + "scenery\tremove\t-3\t141\t640\t"
            + placementId + "\t3\t0\n";
        Files.write(
            journal, staleRemoveDraft.getBytes(StandardCharsets.US_ASCII));
        boolean staleRemovalRefused = false;
        try {
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        } catch (WorldBuilderDiscoveryException expected) {
            staleRemovalRefused = expected.getMessage().contains(
                "no longer matches");
        }
        require(staleRemovalRefused && Files.exists(journal),
            "stale scenery removal refusal");
        require(draft.manifestSha256.equals(
            WorldBuilderLayeredPackage.discoverDraft(working).manifestSha256),
            "stale scenery removal changed working package");

        String removeDraft =
            "world-builder-layered-draft-v2\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t0\n"
            + "sector-count\t0\n"
            + "scenery-count\t1\n"
            + "scenery\tremove\t-3\t141\t640\t"
            + placementId + "\t3\t1\n";
        Files.write(journal, removeDraft.getBytes(StandardCharsets.US_ASCII));
        WorldBuilderLayeredTerrainDraftJournal.CommitResult removeCommit =
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        require(removeCommit.sceneryCount == 1,
            "scenery removal journal commit");
        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        authored = null;
        for (WorldBuilderLayeredPackage.PlacementRecord record
            : draft.placementRecords) {
            if (record.level == -3) authored = record;
        }
        require(authored != null && authored.sceneryCount == 0
                && authored.npcCount == 1,
            "scenery removal retained NPC placement");

        String removeNpcDraft =
            "world-builder-layered-draft-v3\n"
            + "base-manifest-sha256\t" + draft.manifestSha256 + "\n"
            + "tile-count\t0\n"
            + "sector-count\t0\n"
            + "scenery-count\t0\n"
            + "npc-count\t1\n"
            + "npc\tremove\t-3\t142\t641\t" + npcPlacementId
            + "\t0\t141\t640\t143\t642\n";
        Files.write(
            journal, removeNpcDraft.getBytes(StandardCharsets.US_ASCII));
        WorldBuilderLayeredTerrainDraftJournal.CommitResult removeNpcCommit =
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        require(removeNpcCommit.npcCount == 1,
            "NPC removal journal commit");
        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        authored = null;
        for (WorldBuilderLayeredPackage.PlacementRecord record
            : draft.placementRecords) {
            if (record.level == -3) authored = record;
        }
        require(authored != null && authored.placementCount == 0,
            "NPC removal restored empty placement payload");

        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        int priorTerrainCount = draft.terrainSectorCount;
        int priorPlacementCount = draft.placementSetCount;
        String v4SceneryId =
            "spoiled-milk.builder.scenery.lm4.xp500.yp700";
        StringBuilder allocationDraft = new StringBuilder()
            .append("world-builder-layered-draft-v4\n")
            .append("base-manifest-sha256\t")
            .append(draft.manifestSha256).append('\n')
            .append("level-count\t1\n")
            .append("tile-count\t2\n")
            .append("sector-count\t18\n")
            .append("scenery-count\t1\n")
            .append("npc-count\t0\n")
            .append("level\t-4\t500\t700\tUnderground level 4\t")
            .append("underground-level-4\n");
        int levelCenterX = Math.floorDiv(500, 48);
        int levelCenterY = Math.floorDiv(700, 48);
        for (int sectorX = levelCenterX - 1;
            sectorX <= levelCenterX + 1; sectorX++) {
            for (int sectorY = levelCenterY - 1;
                sectorY <= levelCenterY + 1; sectorY++) {
                allocationDraft.append("sector\t-4\t")
                    .append(sectorX).append('\t')
                    .append(sectorY).append('\n');
            }
        }
        for (int sectorX = 19; sectorX <= 21; sectorX++) {
            for (int sectorY = 19; sectorY <= 21; sectorY++) {
                allocationDraft.append("sector\t-4\t")
                    .append(sectorX).append('\t')
                    .append(sectorY).append('\n');
            }
        }
        allocationDraft
            .append("tile\t-4\t500\t700\t0\t1\t0\t0\t0\t0\t0\n")
            .append("tile\t-4\t960\t960\t0\t1\t0\t0\t0\t0\t0\n")
            .append("scenery\tupsert\t-4\t500\t700\t")
            .append(v4SceneryId).append("\t3\t0\n");
        Files.write(
            journal,
            allocationDraft.toString().getBytes(StandardCharsets.US_ASCII));
        WorldBuilderLayeredTerrainDraftJournal.CommitResult allocationCommit =
            new WorldBuilderLayeredTerrainDraftJournal()
                .commitIfPresentLocked(workspace);
        require(allocationCommit.levelCount == 1
                && allocationCommit.sectorCount == 18
                && allocationCommit.tileCount == 2
                && allocationCommit.sceneryCount == 1,
            "v4 level/sparse allocation commit");
        draft = WorldBuilderLayeredPackage.discoverDraft(working);
        draft.requireTerrainDraftDescendant(accepted);
        require(draft.levels.contains(Integer.valueOf(-4)),
            "v4 level declaration");
        require(draft.terrainSectorCount == priorTerrainCount + 18,
            "v4 terrain count");
        require(draft.placementSetCount == priorPlacementCount + 1,
            "v4 placement-set count");
        require(Files.isRegularFile(
                working.resolve("terrain/global/lm4/xp20-yp20.raw")),
            "detached v4 canvas");
        Path v4Payload = working.resolve("placements/global/lm4.json");
        String v4Text = new String(
            Files.readAllBytes(v4Payload), StandardCharsets.UTF_8);
        require(v4Text.contains("\"placementId\": \""
                + v4SceneryId + "\""),
            "v4 same-transaction scenery");
        require(WorldBuilderLayeredReview.readIfPresent(workspace)
                .levels.contains(Integer.valueOf(-4)),
            "v4 level survives reopen");

        String beforeSourceRefusal = draft.manifestSha256;
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
        require(beforeSourceRefusal.equals(
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
