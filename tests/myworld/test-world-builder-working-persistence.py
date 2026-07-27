#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JSON_JAR = ROOT / "server/lib/json-20190722.jar"
SOURCES = (
    ROOT / "server/src/com/openrsc/server/io/WorldEditorTerrainSaveFiles.java",
    ROOT / "server/src/com/openrsc/server/content/worldedit/WorldEditorLayeredTerrainJournal.java",
    ROOT / "server/src/com/openrsc/server/util/WorldSceneryEditFiles.java",
    ROOT / "server/src/com/openrsc/server/util/WorldNpcEditFiles.java",
)


HARNESS = r"""
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.content.worldedit.WorldEditorLayeredTerrainJournal;
import com.openrsc.server.io.WorldEditorTerrainSaveFiles;
import com.openrsc.server.util.WorldNpcEditFiles;
import com.openrsc.server.util.WorldSceneryEditFiles;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class WorldBuilderWorkingPersistenceHarness {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void createTerrain(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        byte[] sector = new byte[48 * 48 * 10];
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            ZipEntry entry = new ZipEntry("h0x48y37");
            entry.setTime(1700000000000L);
            zip.putNextEntry(entry);
            zip.write(sector);
            zip.closeEntry();
        }
    }

    private static byte[] readSector(Path path) throws Exception {
        try (ZipFile zip = new ZipFile(path.toFile());
             InputStream input = zip.getInputStream(zip.getEntry("h0x48y37"))) {
            byte[] bytes = new byte[48 * 48 * 10];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            require(offset == bytes.length, "terrain sector length");
            return bytes;
        }
    }

    public static void main(String[] args) throws Exception {
        Path project = Paths.get(args[0]);
        Path source = project.resolve("source");
        Path working = project.resolve("working");
        Path target = project.resolve("target-fixture");
        Path serverTerrain = working.resolve("server/conf/server/data/Custom_Landscape.orsc");
        Path clientTerrain = working.resolve("Client_Base/Cache/video/Custom_Landscape.orsc");
        Path sourceTerrain = source.resolve("server/conf/server/data/Custom_Landscape.orsc");
        Path targetTerrain = target.resolve("server/conf/server/data/Custom_Landscape.orsc");
        Path config = working.resolve("server/conf/server");
        createTerrain(serverTerrain);
        Files.createDirectories(clientTerrain.getParent());
        Files.copy(serverTerrain, clientTerrain, StandardCopyOption.REPLACE_EXISTING);
        Files.createDirectories(sourceTerrain.getParent());
        Files.copy(serverTerrain, sourceTerrain, StandardCopyOption.REPLACE_EXISTING);
        Files.createDirectories(targetTerrain.getParent());
        Files.copy(serverTerrain, targetTerrain, StandardCopyOption.REPLACE_EXISTING);
        String sourceHash = WorldEditorTerrainSaveFiles.sha256(sourceTerrain);
        String targetHash = WorldEditorTerrainSaveFiles.sha256(targetTerrain);

        String firstBase = WorldEditorTerrainSaveFiles.sha256(serverTerrain);
        WorldEditorTerrainSaveFiles.SaveResult first = WorldEditorTerrainSaveFiles.save(
            serverTerrain, clientTerrain, project.resolve("backups/terrain"), firstBase,
            Arrays.asList(WorldEditorTerrainSaveFiles.TileRecord.of(0, 0, 0, 7, 8, 9, 0, 0, 0, 0)));
        WorldSceneryEditFiles.save(config, Arrays.asList(
            WorldSceneryEditFiles.Edit.upsert(10, 100, 200, 2, 0)));
        WorldNpcEditFiles.save(config, Arrays.asList(
            WorldNpcEditFiles.Edit.upsert(new NPCLoc(20, 110, 210, 108, 112, 208, 212))));

        // A second save models reopening the Builder and editing the files loaded from working/.
        WorldEditorTerrainSaveFiles.SaveResult second = WorldEditorTerrainSaveFiles.save(
            serverTerrain, clientTerrain, project.resolve("backups/terrain"), first.resultSha256,
            Arrays.asList(WorldEditorTerrainSaveFiles.TileRecord.of(1, 1, 0, 17, 18, 19, 0, 0, 0, 0)));
        WorldSceneryEditFiles.save(config, Arrays.asList(
            WorldSceneryEditFiles.Edit.upsert(11, 101, 201, 4, 0)));
        WorldNpcEditFiles.save(config, Arrays.asList(
            WorldNpcEditFiles.Edit.upsert(new NPCLoc(21, 111, 211, 111, 111, 211, 211))));

        require(second.resultSha256.equals(WorldEditorTerrainSaveFiles.sha256(clientTerrain)),
            "working client/server terrain agreement");
        byte[] sector = readSector(serverTerrain);
        require((sector[0] & 255) == 7, "first terrain edit did not survive restart");
        int secondOffset = (1 * 48 + 1) * 10;
        require((sector[secondOffset] & 255) == 17, "second terrain edit missing");
        String scenery = new String(Files.readAllBytes(
            WorldSceneryEditFiles.sceneryLocsPath(config)), "UTF-8");
        require(scenery.contains("\"id\": 10") && scenery.contains("\"id\": 11"),
            "scenery edits did not accumulate across restart");
        String npcs = new String(Files.readAllBytes(WorldNpcEditFiles.npcLocsPath(config)), "UTF-8");
        require(npcs.contains("\"id\": 20") && npcs.contains("\"id\": 21"),
            "NPC edits did not accumulate across restart");
        Set<String> emptySceneryRemovals = WorldSceneryEditFiles.readSceneryRemovalKeys(
            WorldSceneryEditFiles.sceneryRemovalsPath(config));
        Set<String> emptyNpcRemovals = WorldNpcEditFiles.readNpcRemovalKeys(
            WorldNpcEditFiles.npcRemovalsPath(config));
        require(emptySceneryRemovals.isEmpty() && emptyNpcRemovals.isEmpty(), "unexpected removals");
        require(sourceHash.equals(WorldEditorTerrainSaveFiles.sha256(sourceTerrain)),
            "source snapshot changed");
        require(targetHash.equals(WorldEditorTerrainSaveFiles.sha256(targetTerrain)),
            "target terrain changed");
        require(Files.list(project.resolve("backups/terrain")).count() == 2,
            "terrain backups were not workspace-owned");

        Path layeredJournal =
            working.resolve("layered-world/terrain-draft-v1.tsv");
        WorldEditorLayeredTerrainJournal.SaveResult layered =
            WorldEditorLayeredTerrainJournal.save(
                layeredJournal,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                Arrays.asList(
                    new WorldEditorLayeredTerrainJournal.SectorGrowth(-3, 4, 13)),
                Arrays.asList(
                    new WorldEditorLayeredTerrainJournal.TileEdit(
                        -3, 141, 640, 9, 10, 0, 0, 0, 0, 0),
                    new WorldEditorLayeredTerrainJournal.TileEdit(
                        -3, 140, 640, 7, 8, 0, 0, 0, 0, 0)));
        require(layered.tileCount == 2 && layered.sectorCount == 1,
            "layered journal counts");
        String journalText = new String(
            Files.readAllBytes(layeredJournal), "US-ASCII");
        require(journalText.indexOf("tile\t-3\t140\t640")
                < journalText.indexOf("tile\t-3\t141\t640"),
            "layered journal tile order is not deterministic");
        require(!Files.exists(
            layeredJournal.resolveSibling("terrain-draft-v1.tsv.tmp")),
            "layered journal staging file retained");
        layered = WorldEditorLayeredTerrainJournal.save(
            layeredJournal,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            Arrays.asList(
                new WorldEditorLayeredTerrainJournal.SectorGrowth(-3, 4, 13)),
            Arrays.asList(
                new WorldEditorLayeredTerrainJournal.TileEdit(
                    -3, 140, 640, 7, 8, 0, 0, 0, 0, 0)),
            Arrays.asList(
                new WorldEditorLayeredTerrainJournal.SceneryEdit(
                    false, -3, 142, 640,
                    "spoiled-milk.builder.scenery.lm3.xp142.yp640",
                    4, 1),
                new WorldEditorLayeredTerrainJournal.SceneryEdit(
                    false, -3, 141, 640,
                    "spoiled-milk.builder.scenery.lm3.xp141.yp640",
                    3, 0)),
            Arrays.asList(
                new WorldEditorLayeredTerrainJournal.NpcEdit(
                    false, -3, 143, 640,
                    "spoiled-milk.builder.npc.lm3.xp143.yp640.s0",
                    0, 142, 639, 144, 641),
                new WorldEditorLayeredTerrainJournal.NpcEdit(
                    false, -3, 141, 640,
                    "spoiled-milk.builder.npc.lm3.xp141.yp640.s0",
                    1, 141, 640, 141, 640)));
        require(layered.tileCount == 1 && layered.sectorCount == 1
                && layered.sceneryCount == 2 && layered.npcCount == 2,
            "combined layered journal counts");
        journalText = new String(
            Files.readAllBytes(layeredJournal), "US-ASCII");
        require(journalText.startsWith("world-builder-layered-draft-v3\n")
                && journalText.contains("scenery-count\t2\n")
                && journalText.contains("npc-count\t2\n")
                && journalText.indexOf("scenery\tupsert\t-3\t141\t640")
                    < journalText.indexOf("scenery\tupsert\t-3\t142\t640")
                && journalText.indexOf("npc\tupsert\t-3\t141\t640")
                    < journalText.indexOf("npc\tupsert\t-3\t143\t640"),
            "combined layered journal is not stable and deterministic");
        layered = WorldEditorLayeredTerrainJournal.save(
            layeredJournal,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            java.util.Collections.<WorldEditorLayeredTerrainJournal.SectorGrowth>emptyList(),
            java.util.Collections.<WorldEditorLayeredTerrainJournal.TileEdit>emptyList(),
            java.util.Collections.<WorldEditorLayeredTerrainJournal.SceneryEdit>emptyList(),
            java.util.Collections.<WorldEditorLayeredTerrainJournal.NpcEdit>emptyList());
        require(layered.tileCount == 0 && layered.sectorCount == 0
                && layered.sceneryCount == 0 && layered.npcCount == 0
                && !Files.exists(layeredJournal),
            "fully reverted layered draft retained a stale journal");
        System.out.println("working-persistence-ok");
    }
}
"""


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="world-builder-persistence-") as temp:
        root = Path(temp)
        npc_loc = root / "com/openrsc/server/external/NPCLoc.java"
        npc_loc.parent.mkdir(parents=True)
        npc_loc.write_text(
            """
package com.openrsc.server.external;
public class NPCLoc {
    public int id,startX,startY,minX,maxX,minY,maxY;
    public NPCLoc() {}
    public NPCLoc(int id,int startX,int startY,int minX,int maxX,int minY,int maxY) {
        this.id=id;this.startX=startX;this.startY=startY;
        this.minX=minX;this.maxX=maxX;this.minY=minY;this.maxY=maxY;
    }
}
""".strip() + "\n",
            encoding="utf-8",
        )
        harness = root / "WorldBuilderWorkingPersistenceHarness.java"
        harness.write_text(textwrap.dedent(HARNESS), encoding="utf-8")
        classes = root / "classes"
        classes.mkdir()
        subprocess.run(
            [
                "javac", "-source", "8", "-target", "8",
                "-cp", str(JSON_JAR), "-d", str(classes),
                str(npc_loc), *map(str, SOURCES), str(harness),
            ],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        result = subprocess.run(
            [
                "java", "-cp", f"{classes}:{JSON_JAR}",
                "WorldBuilderWorkingPersistenceHarness", str(root / "project"),
            ],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        assert result.stdout == "working-persistence-ok\n", result.stdout + result.stderr


if __name__ == "__main__":
    main()
