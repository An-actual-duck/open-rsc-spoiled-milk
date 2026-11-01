#!/usr/bin/env python3
import os
import hashlib
import importlib.util
import json
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
INSTALLED_CLIENT_PROFILE = ROOT / (
    "Client_Base/world-builder-configs/installed-client.json"
)
GENERATOR = ROOT / (
    "tools/layered-maps/src/com/openrsc/layeredmaps/"
    "PreservationTerrainPackageGenerator.java"
)



spec = importlib.util.spec_from_file_location("installed_selection", ROOT / "scripts/installed-map-selection.py")
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)
# This immutable historical product remains the deliberate no-selector fallback.
# It is not the identity of whichever valid map PRIMARY currently selects.
PINNED_FALLBACK = {
    "SPOILED_MILK_LAYERED_PACKAGE_ID": "rsc-remastered.spoiled-milk-layered-world",
    "SPOILED_MILK_LAYERED_PACKAGE_VERSION": "0.5.0",
    "SPOILED_MILK_LAYERED_MANIFEST_SHA256": "f3489503d2c323e669ab3004345271c6137a58698cc8c0bf0b0fb2587eb52b29",
    "SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT": "f699e5a37709a0f127d676d91f07f64e256825bef6d65a7a4ccd13c8310f3b75",
    "SPOILED_MILK_LAYERED_TERRAIN_SECTORS": "1782",
    "SPOILED_MILK_LAYERED_PLACEMENTS": "33545",
}


def run_library(command: str, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["bash", "-euc", 'source "$1"; shift; ' + command,
                           "test", str(LAYERED_LIBRARY), *map(str, arguments)],
                          cwd=ROOT, text=True, capture_output=True)


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
        "layered_native_world_runtime_profile": "world-builder-installed",
    }
    for key, value in expected.items():
        require(
            config_value(config, key) == value,
            f"hosted config must set {key}: {value}",
        )


def test_selected_pair_and_pinned_fallback_have_distinct_authorities() -> None:
    selected = selection.select(ROOT)
    require(config_value(read(HOST_CONFIG), "layered_native_terrain_manifest_sha256")
            == selected["manifestSha256"], "hosted manifest disagrees with exact selected source pair")
    profile = json.loads(read(INSTALLED_CLIENT_PROFILE))
    for key in ("packageId", "packageVersion", "manifestSha256", "packageFingerprintSha256"):
        require(profile[key] == selected[key], "installed-client selection disagrees for " + key)
    library = read(LAYERED_LIBRARY)
    for key, value in PINNED_FALLBACK.items():
        require(shell_constant(library, key) == value, "historical fallback pin changed: " + key)
    fallback = ROOT / "server/world-builder/packages" / PINNED_FALLBACK["SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT"] / "package"
    _, fingerprint = selection.inventory(fallback)
    require(fingerprint == PINNED_FALLBACK["SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT"],
            "historical fallback inventory changed")
    require(hashlib.sha256((fallback / "manifest.json").read_bytes()).hexdigest()
            == PINNED_FALLBACK["SPOILED_MILK_LAYERED_MANIFEST_SHA256"], "historical fallback manifest changed")
    with tempfile.TemporaryDirectory(prefix="layered-fallback-validation-") as temp:
        result = run_library('layered_world_validate_package "$1" "$2" "$3"', ROOT, fallback, temp)
        require(result.returncode == 0, "strict fallback validation failed:\n" + result.stdout + result.stderr)
        # A selected edit must not silently replace the immutable fallback pin.
        if selected["packageFingerprintSha256"] != fingerprint:
            result = run_library('layered_world_validate_package "$1" "$2" "$3"',
                                 ROOT, selected["serverPackage"], temp)
            require(result.returncode != 0, "historical pin accepted a different selected package")


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
        "layered_world_enable_hosted_installed_profile" in hosted
        and "export OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH" not in hosted,
        "hosted launch must validate the installed pair without overriding PRIMARY",
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
        'mktemp -d "$ROOT_DIR/tools/layered-maps/workspace/private-production.XXXXXX"'
        in private
        and "refusing to launch with a stale package" in private,
        "private layered launch must generate into a fresh workspace and fail closed",
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
        "production-approved" in generator
        and "Boolean.valueOf(target == ContentTarget.SPOILED_MILK)" in generator,
        "the accepted Spoiled Milk target is not production-approved independently "
        "of the pending Preservation target",
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


def test_promoted_package_matches_selected_source_pair() -> None:
    package = os.environ.get("SPOILED_MILK_LAYERED_PACKAGE", "")
    require(package != "", "test suite did not provide SPOILED_MILK_LAYERED_PACKAGE")
    selected = selection.select(ROOT)
    _, fingerprint = selection.inventory(Path(package))
    require(fingerprint == selected["packageFingerprintSha256"], "promoted package differs from selected source inventory")
    require(hashlib.sha256((Path(package) / "manifest.json").read_bytes()).hexdigest()
            == selected["manifestSha256"], "promoted package manifest differs from selected source")
    with tempfile.TemporaryDirectory(prefix="layered-selected-validation-") as temp:
        result = run_library('layered_world_selected_pair "$1" "$2"', ROOT, temp)
        require(result.returncode == 0, "selected package runtime-format validation failed:\n" + result.stdout + result.stderr)
        require(json.loads(result.stdout) == selected, "selected source changed during validation")
    generation_report = Path(package).parent / "generation-report.json"
    report = json.loads(read(generation_report))
    require(report.get("reportType") == "source-selected-package-promotion",
            "source-selected package lost its source-review promotion receipt")
    for key in ("root", "packageId", "packageVersion", "manifestSha256", "packageFingerprintSha256"):
        require(report.get(key) == selected[key], "promotion receipt selection mismatch: " + key)
    approval = run_library('layered_world_require_promotion_approved "$1"', generation_report)
    require(approval.returncode == 0, "source-reviewed promotion refused:\n" + approval.stdout + approval.stderr)


def test_missing_malformed_and_unreviewed_promotion_refuse() -> None:
    with tempfile.TemporaryDirectory(prefix="layered-promotion-guards-") as temp:
        temp = Path(temp)
        path = temp / "generation-report.json"
        def approval(accepted):
            result = run_library('layered_world_require_promotion_approved "$1"', path)
            require((result.returncode == 0) == accepted, "promotion guard result differs:\n" + result.stdout + result.stderr)
        approval(False)
        path.write_text("{malformed")
        approval(False)
        approved = dict(contentTarget="spoiled-milk", reviewState="production-approved", runtimePromotionApproved=True)
        path.write_text(json.dumps(approved)); approval(True)
        for field, value in (("contentTarget", "preservation"), ("reviewState", "pending"),
                             ("runtimePromotionApproved", False)):
            path.write_text(json.dumps(dict(approved, **{field: value}))); approval(False)
        root = temp / "source"; root.mkdir()
        (root / "source.txt").write_text("reviewed source\n")
        subprocess.run(["git", "init", "-q", str(root)], check=True)
        subprocess.run(["git", "-C", str(root), "add", "."], check=True)
        subprocess.run(["git", "-C", str(root), "-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                        "commit", "-qm", "reviewed fixture"], check=True)
        head = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
        selected = dict(reportType="source-selected-package-promotion", root=str(root), sourceCommit=head)
        path.write_text(json.dumps(selected)); approval(True)
        for field in ("root", "sourceCommit"):
            malformed = dict(selected); del malformed[field]
            path.write_text(json.dumps(malformed)); approval(False)
        path.write_text(json.dumps(dict(selected, sourceCommit="0" * 40))); approval(False)
        path.write_text(json.dumps(selected))
        (root / "source.txt").write_text("unreviewed edit\n"); approval(False)
        (root / "source.txt").write_text("reviewed source\n")
        (root / "untracked.txt").write_text("unreviewed addition\n"); approval(False)


def test_private_profile_uses_validated_package_identity() -> None:
    selected = selection.select(ROOT)
    fallback = ROOT / "server/world-builder/packages" / PINNED_FALLBACK["SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT"] / "package"
    for package in (Path(selected["serverPackage"]), fallback):
        manifest = hashlib.sha256((package / "manifest.json").read_bytes()).hexdigest()
        result = run_library('layered_world_enable_private_production_profile "$1"; '
                             'printf "%s\\n%s\\n" "$OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH" '
                             '"$OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256"', package)
        require(result.returncode == 0, "private profile refused validated package:\n" + result.stdout + result.stderr)
        require(result.stdout.splitlines() == [str(package), manifest], "private profile pinned another map: " + result.stdout)
    with tempfile.TemporaryDirectory(prefix="layered-profile-missing-") as temp:
        result = run_library('layered_world_enable_private_production_profile "$1"', temp)
        require(result.returncode != 0, "private profile accepted a missing manifest")
    require('"$OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256"' in read(RUN_PRIVATE),
            "private banner prints a historical manifest instead of the selected export")


def test_missing_pinned_input_never_reuses_a_stale_package() -> None:
    with tempfile.TemporaryDirectory(prefix="layered-stale-generation-") as temp:
        root = Path(temp) / "repository"
        workspace = Path(temp) / "workspace"
        (workspace / "package").mkdir(parents=True)
        (workspace / "package/manifest.json").write_text("{}\n", encoding="utf-8")
        stale_result = subprocess.run(
            [
                "bash",
                "-c",
                f'source "{LAYERED_LIBRARY}"; '
                f'layered_world_generate_package "{root}" "{workspace}"',
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        fresh_workspace = Path(temp) / "fresh-workspace"
        fresh_result = subprocess.run(
            [
                "bash",
                "-c",
                f'source "{LAYERED_LIBRARY}"; '
                f'layered_world_generate_package "{root}" "{fresh_workspace}"',
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
    require(stale_result.returncode != 0, "stale package workspace was accepted")
    require(
        "requires a fresh workspace" in stale_result.stderr,
        "existing package did not trigger an explicit stale-workspace refusal",
    )
    require(fresh_result.returncode != 0, "missing reviewed fallback input unexpectedly succeeded")
    require(
        "refusing all package output" in fresh_result.stderr,
        "missing reviewed fallback input did not explicitly refuse package output",
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
    test_selected_pair_and_pinned_fallback_have_distinct_authorities()
    test_release_and_deployment_are_fail_closed()
    test_promoted_package_matches_selected_source_pair()
    test_missing_malformed_and_unreviewed_promotion_refuse()
    test_private_profile_uses_validated_package_identity()
    test_missing_pinned_input_never_reuses_a_stale_package()
    test_shell_syntax()
    print("PASS: layered-world production promotion is explicit and fail-closed")


if __name__ == "__main__":
    main()
