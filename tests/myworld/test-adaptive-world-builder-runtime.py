#!/usr/bin/env python3
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "server/core.jar"
CLIENT = ROOT / "Client_Base/Open_RSC_Client.jar"
EVIDENCE_WRITER = ROOT / "scripts/write-adaptive-world-builder-runtime-evidence.py"


def digest_tree(root: Path) -> str:
    digest = hashlib.sha256()
    if not root.exists():
        return "missing"
    for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
        relative = path.relative_to(root).as_posix()
        digest.update(relative.encode("utf-8") + b"\0")
        if path.is_symlink():
            digest.update(b"link\0" + os.readlink(path).encode("utf-8"))
        elif path.is_file():
            digest.update(path.read_bytes())
        else:
            digest.update(b"directory")
    return digest.hexdigest()


def canonical_json(value) -> bytes:
    return (json.dumps(value, separators=(",", ":"), ensure_ascii=False) + "\n").encode()


def write_package(root: Path, *, empty: bool = False) -> None:
    root.mkdir(parents=True)
    levels = [0] if empty else [-3, 0]
    terrain = []
    placements = []
    for level in levels:
        token = f"m{-level}" if level < 0 else f"p{level}"
        terrain_path = f"terrain/global/l{token}/xp0-yp0.raw"
        tile = bytes((0, 1, 8, 0, 0, 0, 0, 0, 0, 0))
        payload = tile * (48 * 48)
        target = root / terrain_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
        terrain.append(
            {
                "worldSpace": "global",
                "level": level,
                "sectorX": 0,
                "sectorY": 0,
                "encoding": "raw-layered-sector-v1",
                "path": terrain_path,
                "sha256": hashlib.sha256(payload).hexdigest(),
            }
        )
        placement_path = f"placements/global/l{token}.json"
        document = {
            "schemaVersion": 3,
            "encoding": "layered-world-placements-v3",
            "worldSpace": "global",
            "level": level,
            "npcs": [],
            "groundItems": [],
            "scenery": [],
            "boundaries": [],
        }
        if level == 0 and not empty:
            document.update(
                {
                    "npcs": [
                        {
                            "placementId": "creator.fixture.npc",
                            "npcId": 1,
                            "start": {"x": 8, "y": 8},
                            "roamBounds": {
                                "minimum": {"x": 7, "y": 7},
                                "maximum": {"x": 9, "y": 9},
                            },
                        }
                    ],
                    "groundItems": [
                        {
                            "placementId": "creator.fixture.ground-item",
                            "itemId": 2,
                            "position": {"x": 10, "y": 10},
                            "amount": 3,
                            "respawnSeconds": 45,
                        }
                    ],
                    "scenery": [
                        {
                            "placementId": "creator.fixture.scenery",
                            "sceneryId": 4,
                            "position": {"x": 12, "y": 12},
                            "direction": 3,
                        }
                    ],
                    "boundaries": [
                        {
                            "placementId": "creator.fixture.boundary",
                            "boundaryId": 5,
                            "position": {"x": 14, "y": 14},
                            "direction": 1,
                        }
                    ],
                }
            )
        placement_bytes = canonical_json(document)
        target = root / placement_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(placement_bytes)
        placements.append(
            {
                "id": f"creator.global.l{token}",
                "worldSpace": "global",
                "level": level,
                "encoding": "layered-world-placements-v3",
                "path": placement_path,
                "sha256": hashlib.sha256(placement_bytes).hexdigest(),
            }
        )
    manifest = {
        "schemaVersion": 1,
        "packageType": "layered-world",
        "packageId": "creator.arbitrary-adopted-world" if not empty else "creator.empty-world",
        "packageVersion": "7.4.2-alpha.3" if not empty else "1.0.0",
        "coordinateModel": "signed-layered-v1",
        "storage": {"sectorSize": 48, "presentationChunkSize": 24},
        "worldSpaces": [{"id": "global", "kind": "static"}],
        "levels": [
            {
                "worldSpace": "global",
                "level": level,
                "name": "Creator level " + str(level),
                "role": f"creator-level-{'m' + str(-level) if level < 0 else 'p' + str(level)}",
            }
            for level in levels
        ],
        "terrainSectors": terrain,
        "placementSets": placements,
    }
    (root / "manifest.json").write_bytes(canonical_json(manifest))


HARNESS = r"""
import com.openrsc.server.content.worldedit.AdaptiveWorldBuilderPackagePublisher;
import com.openrsc.server.io.AdaptiveWorldBuilderPackageGuard;
import com.openrsc.server.io.NativeLayeredBoundaryPlacement;
import com.openrsc.server.io.NativeLayeredGroundItemPlacement;
import com.openrsc.server.io.NativeLayeredNpcPlacement;
import com.openrsc.server.io.NativeLayeredPlacementSet;
import com.openrsc.server.io.NativeLayeredSceneryPlacement;
import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class AdaptiveWorldBuilderRuntimeHarness {
    private static AdaptiveWorldBuilderPackagePublisher.Draft draft(
        NativeLayeredWorldPackage source) {
        List<AdaptiveWorldBuilderPackagePublisher.Level> levels = new ArrayList<>();
        for (NativeLayeredWorldPackage.LevelDeclaration level : source.getLevelDeclarations()) {
            levels.add(new AdaptiveWorldBuilderPackagePublisher.Level(
                level.getWorldSpace().getValue(), level.getLevel(),
                level.getName(), level.getRole()));
        }
        List<AdaptiveWorldBuilderPackagePublisher.Sector> sectors = new ArrayList<>();
        boolean changed = false;
        for (NativeLayeredTerrainSector sector : source.getTerrainSectors().values()) {
            byte[] bytes = sector.copyWireBytes();
            if (!changed) {
                bytes[0] = (byte)((bytes[0] + 1) & 255);
                changed = true;
            }
            sectors.add(new AdaptiveWorldBuilderPackagePublisher.Sector(
                sector.getIdentity(), bytes));
        }
        List<AdaptiveWorldBuilderPackagePublisher.Boundary> boundaries = new ArrayList<>();
        List<AdaptiveWorldBuilderPackagePublisher.Scenery> scenery = new ArrayList<>();
        List<AdaptiveWorldBuilderPackagePublisher.Npc> npcs = new ArrayList<>();
        List<AdaptiveWorldBuilderPackagePublisher.GroundItem> items = new ArrayList<>();
        for (NativeLayeredPlacementSet set : source.getPlacementSets().values()) {
            for (NativeLayeredBoundaryPlacement item : set.getBoundaries()) {
                boundaries.add(new AdaptiveWorldBuilderPackagePublisher.Boundary(
                    item.getPlacementId(), item.getBoundaryId(),
                    item.getLocation(), item.getDirection()));
            }
            for (NativeLayeredSceneryPlacement item : set.getScenery()) {
                scenery.add(new AdaptiveWorldBuilderPackagePublisher.Scenery(
                    item.getPlacementId(), item.getSceneryId(),
                    item.getLocation(), item.getDirection()));
            }
            for (NativeLayeredNpcPlacement item : set.getNpcs()) {
                npcs.add(new AdaptiveWorldBuilderPackagePublisher.Npc(
                    item.getPlacementId(), item.getNpcId(), item.getStart(),
                    item.getMinX(), item.getMinY(), item.getMaxX(), item.getMaxY()));
            }
            for (NativeLayeredGroundItemPlacement item : set.getGroundItems()) {
                items.add(new AdaptiveWorldBuilderPackagePublisher.GroundItem(
                    item.getPlacementId(), item.getItemId(), item.getLocation(),
                    item.getAmount(), item.getRespawnSeconds()));
            }
        }
        return new AdaptiveWorldBuilderPackagePublisher.Draft(
            source.getPackageId(), source.getPackageVersion(),
            source.getPresentationChunkSize(), source.getWorldSpaceKinds(),
            levels, sectors, boundaries, scenery, npcs, items);
    }

    private static Path first(Path root, String suffix) throws IOException {
        try (java.util.stream.Stream<Path> values = Files.walk(root)) {
            return values.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(suffix)).findFirst().get();
        }
    }

    private static Path firstIn(
        Path root, String directory, String suffix) throws IOException {
        try (java.util.stream.Stream<Path> values = Files.walk(root.resolve(directory))) {
            return values.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(suffix)).findFirst().get();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte item : digest) value.append(String.format("%02x", item & 255));
        return value.toString();
    }

    private static void replaceManifestHash(
        Path root, byte[] before, byte[] after) throws Exception {
        Path manifest = root.resolve("manifest.json");
        String document = new String(
            Files.readAllBytes(manifest), java.nio.charset.StandardCharsets.UTF_8);
        String oldHash = sha256(before);
        String newHash = sha256(after);
        String changed = document.replace(oldHash, newHash);
        if (changed.equals(document)) throw new IOException("fixture hash was not found");
        Files.write(manifest, changed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path working = Paths.get(args[1]);
        if ("guard".equals(mode)) {
            AdaptiveWorldBuilderPackageGuard.requireClosedPackage(working);
            System.out.println("accepted");
            return;
        }
        if ("recover".equals(mode)) {
            AdaptiveWorldBuilderPackagePublisher.recover(working);
            System.out.println("recovered");
            return;
        }
        Path baseline = Paths.get(args[2]);
        NativeLayeredWorldPackage source = NativeLayeredWorldPackage.load(working);
        String workingHash = AdaptiveWorldBuilderPackageGuard
            .requireClosedPackage(working).getFingerprint();
        String baselineHash = AdaptiveWorldBuilderPackageGuard
            .requireClosedPackage(baseline).getFingerprint();
        AdaptiveWorldBuilderPackagePublisher.Observer observer =
            AdaptiveWorldBuilderPackagePublisher.NO_OBSERVER;
        if (!"publish".equals(mode)) {
            observer = new AdaptiveWorldBuilderPackagePublisher.Observer() {
                @Override
                public void at(
                    AdaptiveWorldBuilderPackagePublisher.Stage stage, Path packageRoot)
                    throws IOException {
                    if ("fail-written".equals(mode)
                        && stage == AdaptiveWorldBuilderPackagePublisher.Stage.PACKAGE_WRITTEN) {
                        Path path = first(packageRoot, ".raw");
                        byte[] bytes = Files.readAllBytes(path);
                        bytes[17] ^= 1;
                        Files.write(path, bytes);
                    }
                    if ("fail-terrain-rehashed".equals(mode)
                        && stage == AdaptiveWorldBuilderPackagePublisher.Stage.PACKAGE_WRITTEN) {
                        Path path = firstIn(packageRoot, "terrain", ".raw");
                        byte[] before = Files.readAllBytes(path);
                        byte[] after = before.clone();
                        after[19] ^= 1;
                        Files.write(path, after);
                        try {
                            replaceManifestHash(packageRoot, before, after);
                        } catch (Exception failure) {
                            throw new IOException(failure);
                        }
                    }
                    if ("fail-placement-rehashed".equals(mode)
                        && stage == AdaptiveWorldBuilderPackagePublisher.Stage.PACKAGE_WRITTEN) {
                        Path path = firstIn(packageRoot, "placements", ".json");
                        byte[] before = Files.readAllBytes(path);
                        String document = new String(
                            before, java.nio.charset.StandardCharsets.UTF_8);
                        byte[] after = document.replace(
                            "creator.fixture.scenery", "creator.fixture.scenerx")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        if (java.util.Arrays.equals(before, after)) {
                            throw new IOException("fixture placement ID was not found");
                        }
                        Files.write(path, after);
                        try {
                            replaceManifestHash(packageRoot, before, after);
                        } catch (Exception failure) {
                            throw new IOException(failure);
                        }
                    }
                    if ("fail-validated".equals(mode)
                        && stage == AdaptiveWorldBuilderPackagePublisher.Stage.PACKAGE_VALIDATED) {
                        Path path = first(packageRoot, ".json");
                        byte[] bytes = Files.readAllBytes(path);
                        bytes[0] ^= 1;
                        Files.write(path, bytes);
                    }
                    if ("fail-moved".equals(mode)
                        && stage == AdaptiveWorldBuilderPackagePublisher.Stage.PREVIOUS_MOVED) {
                        throw new IOException("injected interrupted save");
                    }
                }
            };
        }
        AdaptiveWorldBuilderPackagePublisher.SaveResult result =
            AdaptiveWorldBuilderPackagePublisher.publish(
                working, baseline, workingHash, baselineHash, draft(source),
                new AdaptiveWorldBuilderPackagePublisher.PackageVerifier() {
                    @Override public void verify(NativeLayeredWorldPackage value) {}
                }, observer);
        System.out.println(result.manifestSha256 + " " + result.inventorySha256
            + " " + result.boundaryCount + " " + result.sceneryCount
            + " " + result.npcCount + " " + result.groundItemCount);
    }
}
"""


CLIENT_HARNESS = r"""
import orsc.AdaptiveWorldBuilderClientSession;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AdaptiveWorldBuilderClientBindingHarness {
    public static void main(String[] args) {
        Path binding = Paths.get(args[0]);
        AdaptiveWorldBuilderClientSession session =
            AdaptiveWorldBuilderClientSession.load(binding);
        session.requireEvidence(Paths.get(args[1]), Paths.get(args[2]));
        session.requirePackageIdentity(args[3], args[4], args[5]);
        System.out.println(session.token() + " " + session.packageId());
    }
}
"""


class AdaptiveWorldBuilderRuntimeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not CORE.is_file():
            raise RuntimeError("server/core.jar is required; run ./scripts/build-server.sh")
        if not CLIENT.is_file():
            raise RuntimeError(
                "Client_Base/Open_RSC_Client.jar is required; run ./scripts/build-client.sh"
            )
        cls.compiled = tempfile.TemporaryDirectory(prefix="adaptive-runtime-classes-")
        classes = Path(cls.compiled.name)
        source = classes / "AdaptiveWorldBuilderRuntimeHarness.java"
        source.write_text(textwrap.dedent(HARNESS), encoding="utf-8")
        subprocess.run(
            [
                "javac", "-source", "8", "-target", "8",
                "-cp", str(CORE), "-d", str(classes), str(source),
            ],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        client_source = classes / "AdaptiveWorldBuilderClientBindingHarness.java"
        client_source.write_text(textwrap.dedent(CLIENT_HARNESS), encoding="utf-8")
        subprocess.run(
            [
                "javac", "-source", "8", "-target", "8",
                "-cp", str(CLIENT), "-d", str(classes), str(client_source),
            ],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        cls.classpath = os.pathsep.join((str(classes), str(CORE)))
        cls.client_classpath = os.pathsep.join((str(classes), str(CLIENT)))

    @classmethod
    def tearDownClass(cls):
        cls.compiled.cleanup()

    def run_harness(self, mode: str, working: Path, baseline: Path | None = None):
        command = [
            "java", "-cp", self.classpath,
            "AdaptiveWorldBuilderRuntimeHarness", mode, str(working),
        ]
        if baseline is not None:
            command.append(str(baseline))
        return subprocess.run(command, cwd=ROOT, capture_output=True, text=True)

    def fixture(self, root: Path, *, empty: bool = False):
        working = root / "project/working/layered-world/package"
        baseline = root / "project/source/layered-baseline/package"
        target = root / "server-target"
        write_package(working, empty=empty)
        shutil.copytree(working, baseline)
        (target / "server/maps").mkdir(parents=True)
        (target / "server/maps/live.dat").write_bytes(b"target must not change\n")
        return working, baseline, target

    def client_binding_fixture(self, root: Path):
        control = root / "project/run/world-builder"
        evidence = root / "project/working/runtime/client/evidence"
        control.mkdir(parents=True)
        evidence.mkdir(parents=True)
        composition = control / "effective-static-composition.json"
        composition.write_bytes(b"{}\n")
        definitions = evidence / "definitions.bin"
        assets = evidence / "assets.bin"
        definitions.write_bytes(b"content-neutral definitions\n")
        assets.write_bytes(b"content-neutral assets\n")
        fields = {
            "assetContract": "world-builder-client-asset-binding-v1",
            "assetIdentity": "creator.assets.v1",
            "assetSha256": hashlib.sha256(assets.read_bytes()).hexdigest(),
            "authoring": "generic-signed-layered-authoring-v1",
            "capability": "adaptive-world-builder-runtime-capability-v1",
            "clientBuild": "core-framework-adaptive-builder-client-v1",
            "clientVersion": "10048",
            "coordinateModel": "signed-layered-v1",
            "definitionContract": "world-builder-definition-catalog-binding-v1",
            "definitionIdentity": "creator.definitions.v1",
            "definitionSha256": hashlib.sha256(definitions.read_bytes()).hexdigest(),
            "effectiveComposition": "world-builder-effective-static-composition-v1",
            "effectiveCompositionSha256": hashlib.sha256(
                composition.read_bytes()
            ).hexdigest(),
            "initialLevel": "0",
            "initialWorldSpace": "global",
            "initialX": "7",
            "initialY": "9",
            "loader": "generic-signed-layered-loader-v1",
            "levels": "-3,0",
            "manifestSha256": "1" * 64,
            "packageId": "creator.arbitrary-adopted-world",
            "packageInventorySha256": "2" * 64,
            "packageSchema": "layered-world-package-v1",
            "packageVersion": "7.4.2-alpha.3",
            "placementEncoding": "layered-world-placements-v3",
            "profile": "adaptive-world-builder",
            "projectOrigin": "target-layered",
            "protocol": "world-builder-native-layered-protocol-v1",
            "requiredBoundaryIds": "",
            "requiredItemIds": "",
            "requiredNpcIds": "",
            "requiredSceneryIds": "",
            "requiredTileIds": "",
            "serverBuild": "core-framework-adaptive-builder-server-v1",
            "sourceBaselineInventorySha256": "3" * 64,
        }
        binding = control / "runtime-binding.properties"
        binding.write_text(
            "adaptive-world-builder-session-v1\n"
            + "".join(f"{key}={fields[key]}\n" for key in sorted(fields)),
            encoding="ascii",
        )
        return binding, definitions, assets, fields

    def run_client_binding(
        self, binding: Path, definitions: Path, assets: Path, fields: dict
    ):
        return subprocess.run(
            [
                "java", "-cp", self.client_classpath,
                "AdaptiveWorldBuilderClientBindingHarness",
                str(binding), str(definitions), str(assets),
                fields["packageId"], fields["packageVersion"],
                fields["manifestSha256"],
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )

    def test_generic_package_publish_is_deterministic_and_preserves_all_families(self):
        outputs = []
        for name in ("short", "a-very-different-absolute-root-name"):
            with tempfile.TemporaryDirectory(prefix=f"adaptive-{name}-") as temp:
                working, baseline, target = self.fixture(Path(temp))
                baseline_before = digest_tree(baseline)
                target_before = digest_tree(target)
                result = self.run_harness("publish", working, baseline)
                self.assertEqual(0, result.returncode, result.stderr)
                fields = result.stdout.strip().split()
                self.assertEqual(["1", "1", "1", "1"], fields[2:])
                self.assertEqual(baseline_before, digest_tree(baseline))
                self.assertEqual(target_before, digest_tree(target))
                self.assertEqual(
                    "creator.arbitrary-adopted-world",
                    json.loads((working / "manifest.json").read_text())["packageId"],
                )
                outputs.append(
                    {
                        path.relative_to(working).as_posix(): path.read_bytes()
                        for path in working.rglob("*") if path.is_file()
                    }
                )
        self.assertEqual(outputs[0], outputs[1])

    def test_canonical_empty_package_is_accepted_without_fixed_world_identity(self):
        with tempfile.TemporaryDirectory(prefix="adaptive-empty-") as temp:
            working, baseline, target = self.fixture(Path(temp), empty=True)
            result = self.run_harness("guard", working)
            self.assertEqual(0, result.returncode, result.stderr)
            payload = next((working / "terrain").rglob("*.raw")).read_bytes()
            self.assertEqual(bytes((0, 1, 8, 0, 0, 0, 0, 0, 0, 0)), payload[:10])
            self.assertEqual(payload[:10] * (48 * 48), payload)
            self.assertEqual("creator.empty-world", json.loads(
                (working / "manifest.json").read_text())["packageId"])
            for path in working.rglob("*"):
                if path.is_file():
                    self.assertNotIn(b"spoiled-milk", path.read_bytes().lower())

    def test_injected_failures_leave_complete_working_baseline_and_target(self):
        for mode in (
            "fail-written", "fail-terrain-rehashed", "fail-placement-rehashed",
            "fail-validated", "fail-moved",
        ):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory(
                prefix=f"adaptive-{mode}-"
            ) as temp:
                working, baseline, target = self.fixture(Path(temp))
                before = (digest_tree(working), digest_tree(baseline), digest_tree(target))
                result = self.run_harness(mode, working, baseline)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(
                    before,
                    (digest_tree(working), digest_tree(baseline), digest_tree(target)),
                )
                parent = working.parent
                self.assertFalse((parent / "package.save-stage").exists())
                self.assertFalse((parent / "package.save-previous").exists())
                self.assertFalse((parent / "package.save-transaction").exists())
                self.assertEqual(0, self.run_harness("guard", working).returncode)

    def test_interrupted_swap_recovers_only_verified_previous_package(self):
        with tempfile.TemporaryDirectory(prefix="adaptive-recovery-") as temp:
            working, baseline, target = self.fixture(Path(temp))
            fingerprint = self.run_harness("guard", working)
            self.assertEqual(0, fingerprint.returncode, fingerprint.stderr)
            # Recovery accepts the marker hashes; use Java's guard fingerprint by
            # normalizing working first, then simulate the crash after old rename.
            normalized_working = working
            first = self.run_harness("publish", normalized_working, baseline)
            self.assertEqual(0, first.returncode, first.stderr)
            current_hash = first.stdout.split()[1]
            parent = normalized_working.parent
            previous = parent / "package.save-previous"
            stage = parent / "package.save-stage"
            transaction = parent / "package.save-transaction"
            shutil.copytree(normalized_working, stage)
            normalized_working.rename(previous)
            transaction.write_text(
                "adaptive-world-builder-save-transaction-v1\n"
                f"old={current_hash}\nnew={current_hash}\n",
                encoding="ascii",
            )
            target_before = digest_tree(target)
            baseline_before = digest_tree(baseline)
            recovered = self.run_harness("recover", normalized_working)
            self.assertEqual(0, recovered.returncode, recovered.stderr)
            self.assertTrue(normalized_working.is_dir())
            self.assertFalse(previous.exists())
            self.assertFalse(stage.exists())
            self.assertFalse(transaction.exists())
            self.assertEqual(target_before, digest_tree(target))
            self.assertEqual(baseline_before, digest_tree(baseline))

    def test_links_escapes_and_unbounded_inputs_fail_closed(self):
        with tempfile.TemporaryDirectory(prefix="adaptive-hostile-") as temp:
            root = Path(temp)
            for attack in ("symlink", "hardlink", "escape", "unbounded"):
                with self.subTest(attack=attack):
                    package = root / attack
                    write_package(package)
                    manifest = json.loads((package / "manifest.json").read_text())
                    terrain = package / manifest["terrainSectors"][0]["path"]
                    outside = root / f"{attack}-outside"
                    outside.write_bytes(terrain.read_bytes())
                    outside_before = hashlib.sha256(outside.read_bytes()).hexdigest()
                    if attack == "symlink":
                        terrain.unlink()
                        terrain.symlink_to(outside)
                    elif attack == "hardlink":
                        terrain.unlink()
                        os.link(outside, terrain)
                    elif attack == "escape":
                        manifest["terrainSectors"][0]["path"] = "../escape.raw"
                        (package / "manifest.json").write_bytes(canonical_json(manifest))
                    else:
                        (package / "too-large.bin").write_bytes(b"")
                        with (package / "too-large.bin").open("r+b") as handle:
                            handle.truncate(32 * 1024 * 1024 + 1)
                    result = self.run_harness("guard", package)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(
                        outside_before,
                        hashlib.sha256(outside.read_bytes()).hexdigest(),
                    )

    def test_working_and_immutable_baseline_cannot_alias(self):
        with tempfile.TemporaryDirectory(prefix="adaptive-baseline-alias-") as temp:
            package = Path(temp) / "package"
            write_package(package)
            before = digest_tree(package)
            result = self.run_harness("publish", package, package)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(before, digest_tree(package))
            self.assertFalse((package.parent / "package.save-stage").exists())

    def test_client_binding_rejects_package_definition_asset_and_path_mismatch(self):
        with tempfile.TemporaryDirectory(prefix="adaptive-client-binding-") as temp:
            root = Path(temp)
            binding, definitions, assets, fields = self.client_binding_fixture(root)
            accepted = self.run_client_binding(
                binding, definitions, assets, fields
            )
            self.assertEqual(0, accepted.returncode, accepted.stderr)
            self.assertIn(fields["packageId"], accepted.stdout)

            definitions.write_bytes(b"mismatched definitions\n")
            mismatch = self.run_client_binding(
                binding, definitions, assets, fields
            )
            self.assertNotEqual(0, mismatch.returncode)
            definitions.write_bytes(b"content-neutral definitions\n")

            assets.write_bytes(b"mismatched assets\n")
            mismatch = self.run_client_binding(
                binding, definitions, assets, fields
            )
            self.assertNotEqual(0, mismatch.returncode)
            assets.write_bytes(b"content-neutral assets\n")

            wrong_package = dict(fields)
            wrong_package["manifestSha256"] = "4" * 64
            mismatch = self.run_client_binding(
                binding, definitions, assets, wrong_package
            )
            self.assertNotEqual(0, mismatch.returncode)

            outside = root / "outside-assets.bin"
            outside.write_bytes(assets.read_bytes())
            mismatch = self.run_client_binding(
                binding, definitions, outside, fields
            )
            self.assertNotEqual(0, mismatch.returncode)

            hardlink = assets.with_name("assets-hardlink.bin")
            os.link(assets, hardlink)
            mismatch = self.run_client_binding(
                binding, definitions, assets, fields
            )
            self.assertNotEqual(0, mismatch.returncode)
            hardlink.unlink()

            real_binding = binding.with_name("binding-real.properties")
            binding.rename(real_binding)
            binding.symlink_to(real_binding)
            mismatch = self.run_client_binding(
                binding, definitions, assets, fields
            )
            self.assertNotEqual(0, mismatch.returncode)

    def test_discovery_evidence_is_strict_path_independent_and_version_bound(self):
        outputs = []
        for prefix in ("brief", "different-absolute-root"):
            with tempfile.TemporaryDirectory(prefix=f"adaptive-evidence-{prefix}-") as temp:
                catalog = Path(temp) / "working/evidence/catalog.bin"
                catalog.parent.mkdir(parents=True)
                catalog.write_bytes(b"content-neutral catalog\n")
                result = subprocess.run(
                    [
                        str(EVIDENCE_WRITER), "--side", "server",
                        "--definition-catalog", str(catalog),
                        "--definition-catalog-id", "creator.catalog.v1",
                    ],
                    cwd=ROOT, capture_output=True, check=True,
                )
                outputs.append(result.stdout)
        self.assertEqual(outputs[0], outputs[1])
        evidence = json.loads(outputs[0])
        self.assertEqual("world-builder-runtime-evidence", evidence["manifestType"])
        self.assertEqual("core-framework-adaptive-builder-server-v1", evidence["buildId"])
        self.assertEqual("generic-signed-layered-loader-v1", evidence["loaderId"])
        self.assertEqual("world-builder-native-layered-protocol-v1", evidence["protocolId"])
        self.assertEqual([1, 3], evidence["encodingVersions"])
        self.assertEqual(
            ["boundary", "ground-item", "npc", "scenery"],
            evidence["authoring"]["placementFamilies"],
        )

        capability = json.loads((
            ROOT / "server/conf/world-builder/adaptive-runtime-capability-v1.json"
        ).read_text())
        server_identity = (
            ROOT / "server/src/com/openrsc/server/content/worldedit/"
            "AdaptiveWorldBuilderRuntimeIdentity.java"
        ).read_text()
        client_identity = (
            ROOT / "Client_Base/src/orsc/AdaptiveWorldBuilderClientSession.java"
        ).read_text()
        for key in (
            "capabilityId", "serverBuildId", "clientBuildId", "loaderId",
            "authoringId", "definitionContractId", "assetContractId",
            "protocolId", "effectiveCompositionId", "packageSchemaId",
        ):
            self.assertIn(f'"{capability[key]}"', server_identity)
            self.assertIn(f'"{capability[key]}"', client_identity)
        self.assertEqual(
            [0, 1, 8, 0, 0, 0, 0, 0, 0, 0],
            capability["canonicalVoidTile"],
        )
        with tempfile.TemporaryDirectory(prefix="adaptive-evidence-link-") as temp:
            root = Path(temp)
            catalog = root / "catalog.bin"
            catalog.write_bytes(b"catalog\n")
            hardlink = root / "catalog-hardlink.bin"
            os.link(catalog, hardlink)
            refused = subprocess.run(
                [
                    str(EVIDENCE_WRITER), "--side", "client",
                    "--definition-catalog", str(catalog),
                    "--definition-catalog-id", "creator.catalog.v1",
                ],
                cwd=ROOT, capture_output=True,
            )
            self.assertNotEqual(0, refused.returncode)
            hardlink.unlink()
            linked = root / "catalog-linked.bin"
            linked.symlink_to(catalog)
            refused = subprocess.run(
                [
                    str(EVIDENCE_WRITER), "--side", "client",
                    "--definition-catalog", str(linked),
                    "--definition-catalog-id", "creator.catalog.v1",
                ],
                cwd=ROOT, capture_output=True,
            )
            self.assertNotEqual(0, refused.returncode)

    def test_fixed_profiles_and_content_neutral_policy_remain_explicit(self):
        profile = (ROOT / "server/src/com/openrsc/server/io/NativeLayeredWorldRuntimeProfile.java").read_text()
        self.assertIn('PRESERVATION_R64_REPLACEMENT("preservation-r64-replacement", true)', profile)
        self.assertIn('SPOILED_MILK_REPLACEMENT("spoiled-milk-replacement", true)', profile)
        self.assertIn('ADAPTIVE_WORLD_BUILDER("adaptive-world-builder", true)', profile)
        self.assertNotIn("ADAPTIVE_WORLD_BUILDER = SPOILED", profile)
        publisher = (ROOT / "server/src/com/openrsc/server/content/worldedit/AdaptiveWorldBuilderPackagePublisher.java").read_text()
        self.assertNotIn("rsc-remastered.spoiled-milk-layered-world", publisher)
        self.assertNotIn("SPOILED_MILK_PACKAGE", publisher)
        sessions = (ROOT / (
            "server/src/com/openrsc/server/content/worldedit/"
            "WorldEditorSessionManager.java"
        )).read_text()
        self.assertIn('"world-builder.authored."+family', sessions)
        self.assertIn("legacyNativeSceneryPlacementId", sessions)
        player_service = (
            ROOT / "server/src/com/openrsc/server/service/PlayerService.java"
        ).read_text()
        adaptive = player_service.index(
            "AdaptiveWorldBuilderRuntimeIdentity.isAdaptive(configuration)"
        )
        production_recovery = player_service.index(
            "LayeredPlayerLoginRecovery.resolve("
        )
        self.assertLess(adaptive, production_recovery)
        self.assertIn(
            "AdaptiveWorldBuilderRuntimeIdentity.PLAYER_LOCATION_ORIGIN",
            player_service,
        )


if __name__ == "__main__":
    unittest.main()
