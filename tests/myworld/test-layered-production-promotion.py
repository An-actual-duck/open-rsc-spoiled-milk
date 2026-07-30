#!/usr/bin/env python3
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HOST_CONFIG = ROOT / "server" / "myworld-host.conf"
COMMON = ROOT / "scripts" / "lib" / "myworld-common.sh"
LAYERED_LIBRARY = ROOT / "scripts" / "lib" / "layered-world-package.sh"
DEPLOY = ROOT / "scripts" / "deploy-live-main.sh"
RUN_HOSTED = ROOT / "scripts" / "run-hosted-server.sh"
RUN_PRIVATE = ROOT / "scripts" / "run-server.sh"
LIVE_STATUS = ROOT / "scripts" / "live-status.sh"
MANAGER = ROOT / "scripts" / "ai-manager.sh"
PACKAGER = ROOT / "scripts" / "package-layered-world-release.sh"
RUNTIME_PROFILE = ROOT / (
    "server/src/com/openrsc/server/io/NativeLayeredWorldRuntimeProfile.java"
)
GENERATOR = ROOT / (
    "tools/layered-maps/src/com/openrsc/layeredmaps/"
    "PreservationTerrainPackageGenerator.java"
)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def config_value(contents: str, key: str) -> str | None:
    match = re.search(
        rf"^[ \t]*{re.escape(key)}:[ \t]*([^#\r\n]*?)[ \t]*(?:#.*)?$",
        contents,
        re.MULTILINE,
    )
    return match.group(1).strip() if match else None


def shell_constant(contents: str, name: str) -> str:
    match = re.search(rf'^{re.escape(name)}="([^"]+)"$', contents, re.MULTILINE)
    require(match is not None, f"missing shell constant {name}")
    return match.group(1)


def java_constant(contents: str, name: str) -> str:
    match = re.search(
        rf'{re.escape(name)}\s*=\s*\n?\s*"([^"]+)";',
        contents,
    )
    require(match is not None, f"missing Java constant {name}")
    return match.group(1)


def test_hosted_profile_is_explicit() -> None:
    config = read(HOST_CONFIG)
    expected = {
        "want_layered_player_location_authority": "true",
        "want_layered_spatial_runtime_authority": "true",
        "want_layered_protocol_client_authority": "true",
        "want_layered_synthetic_deep_fixture": "false",
        "want_layered_native_terrain_package": "true",
        "want_layered_native_terrain_residency": "true",
        "want_layered_native_terrain_readiness": "true",
        "want_layered_native_terrain_prediction": "true",
        "want_layered_native_terrain_symmetric_residency": "true",
        "want_layered_native_terrain_atomic_activation": "true",
        "want_sync_scene_baseline": "true",
        "layered_native_world_runtime_profile": "spoiled-milk-replacement",
    }
    for key, value in expected.items():
        require(
            config_value(config, key) == value,
            f"hosted config must set {key}: {value}",
        )


def test_package_identity_has_one_source_of_release_truth() -> None:
    library = read(LAYERED_LIBRARY)
    runtime = read(RUNTIME_PROFILE)
    require(
        shell_constant(library, "SPOILED_MILK_LAYERED_PACKAGE_ID")
        == java_constant(runtime, "SPOILED_MILK_PACKAGE_ID"),
        "shell and server package IDs disagree",
    )
    require(
        shell_constant(library, "SPOILED_MILK_LAYERED_PACKAGE_VERSION")
        == java_constant(runtime, "SPOILED_MILK_PACKAGE_VERSION"),
        "shell and server package versions disagree",
    )
    require(
        shell_constant(library, "SPOILED_MILK_LAYERED_MANIFEST_SHA256")
        == java_constant(runtime, "SPOILED_MILK_MANIFEST_SHA256"),
        "shell and server manifest pins disagree",
    )


def test_release_and_deployment_are_fail_closed() -> None:
    common = read(COMMON)
    deploy = read(DEPLOY)
    hosted = read(RUN_HOSTED)
    private = read(RUN_PRIVATE)
    live_status = read(LIVE_STATUS)
    manager = read(MANAGER)
    packager = read(PACKAGER)
    generator = read(GENERATOR)
    layered_library = read(LAYERED_LIBRARY)

    require(
        'source "$MYWORLD_DIR/layered-world-package.sh"' in common,
        "server launch common library does not load package validation",
    )
    require(
        "layered_world_install_live_package" in deploy,
        "live deployment does not install the reviewed package",
    )
    require(
        deploy.index('myworld_require_port_free "$MYWORLD_PUBLIC_PORT"')
        < deploy.index("layered_world_install_live_package")
        < deploy.index("switch --detach"),
        "live package installation must occur after shutdown verification and before checkout activation",
    )
    require(
        "layered_world_require_live_package" in hosted
        and "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH" in hosted,
        "hosted launch does not require and select the installed package",
    )
    require(
        "--legacy-map-rollback" in hosted
        and "layered_world_enable_legacy_rollback_profile" in hosted,
        "hosted launch has no explicit legacy-map rollback",
    )
    require(
        "marker_layered_manifest_sha256" in common
        and "marker_layered_runtime_mode" in common,
        "launch attestation omits the layered package or runtime mode",
    )
    require(
        "layered_world_manifest_sha256" in live_status
        and "installed_layered_manifest_sha256" in live_status,
        "live status trusts the launch marker without checking the installed manifest",
    )
    require(
        "--layered-production" in private
        and "layered_world_enable_private_production_profile" in private,
        "private final rehearsal cannot select the production profile",
    )
    require(
        "export OPENRSC_SYNC_SCENE_BASELINE=true" in layered_library
        and "export OPENRSC_SYNC_SCENE_BASELINE=false" in layered_library,
        "layered production and rollback profiles must explicitly select the "
        "atomic scene-baseline dependency",
    )
    require(
        "want_sync_scene_baseline true" in common,
        "hosted launch must fail closed when atomic activation lacks its "
        "scene-baseline dependency",
    )
    require(
        "package-layered-world-release.sh" in manager,
        "manager release omits the layered-world artifact",
    )
    require(
        "production-rehearsal-pending" in generator
        and 'document.put("runtimePromotionApproved", Boolean.FALSE)' in generator,
        "source must remain promotion-pending until the final owner rehearsal",
    )
    require(
        "layered_world_require_promotion_approved" in layered_library
        and "generation-report.json" in layered_library,
        "deployment omits the promotion approval gate",
    )
    require(
        "layered_world_require_promotion_approved" in packager,
        "release packaging omits the promotion approval gate",
    )
    for required in (
        "GENERATION-REPORT.json",
        "PACKAGE-VALIDATION.json",
        "SOURCE-COMMIT.txt",
        "MANIFEST-SHA256.txt",
        "PACKAGE-FINGERPRINT.txt",
        "SHA256SUMS.txt",
    ):
        require(required in packager, f"layered release omits {required}")


def test_generated_package_matches_runtime_pin() -> None:
    package = os.environ.get("SPOILED_MILK_LAYERED_PACKAGE", "")
    require(package != "", "test suite did not provide SPOILED_MILK_LAYERED_PACKAGE")
    with tempfile.TemporaryDirectory(prefix="layered-production-validation-") as temp:
        script = (
            f'source "{LAYERED_LIBRARY}"; '
            f'layered_world_validate_package "{ROOT}" "{package}" "{temp}"'
        )
        result = subprocess.run(
            ["bash", "-c", script],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
    require(
        result.returncode == 0,
        "generated package does not match the production pin:\n"
        + result.stdout
        + result.stderr,
    )

    generation_report = Path(package).parent / "generation-report.json"
    approval = subprocess.run(
        [
            "bash",
            "-c",
            f'source "{LAYERED_LIBRARY}"; '
            f'layered_world_require_promotion_approved "{generation_report}"',
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    require(
        approval.returncode != 0
        and "production-rehearsal-pending" in approval.stderr,
        "release/deployment promotion gate should remain closed before owner acceptance",
    )


def test_shell_syntax() -> None:
    result = subprocess.run(
        [
            "bash",
            "-n",
            LAYERED_LIBRARY,
            COMMON,
            DEPLOY,
            RUN_HOSTED,
            RUN_PRIVATE,
            LIVE_STATUS,
            MANAGER,
            PACKAGER,
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    require(
        result.returncode == 0,
        "layered production shell syntax failed:\n" + result.stdout + result.stderr,
    )


def main() -> None:
    test_hosted_profile_is_explicit()
    test_package_identity_has_one_source_of_release_truth()
    test_release_and_deployment_are_fail_closed()
    test_generated_package_matches_runtime_pin()
    test_shell_syntax()
    print("PASS: layered-world production promotion is explicit and fail-closed")


if __name__ == "__main__":
    main()
