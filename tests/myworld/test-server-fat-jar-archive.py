#!/usr/bin/env python3

import subprocess
import stat
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
BUILD_XML = ROOT / "server/build.xml"
LIB_DIR = ROOT / "server/lib"
CORE_JAR = ROOT / "server/core.jar"
PLUGINS_JAR = ROOT / "server/plugins.jar"
REQUIRED_CORE_CLASSES = {
    "com/openrsc/server/Server.class",
    "com/openrsc/server/io/AdaptiveWorldBuilderPackageGuard.class",
    "com/openrsc/server/io/NativeLayeredWorldRuntimeProfile.class",
    "com/openrsc/server/content/worldedit/AdaptiveWorldBuilderRuntimeIdentity.class",
    "com/openrsc/server/content/worldedit/AdaptiveWorldBuilderRuntimeSession.class",
    "com/openrsc/server/content/worldedit/AdaptiveWorldBuilderPackagePublisher.class",
    "com/openrsc/server/content/worldedit/AdaptiveWorldBuilderDefinitionInventory.class",
    "com/openrsc/server/content/worldedit/WorldEditorSessionManager.class",
}
REQUIRED_SERVICE_PROVIDERS = {
    "java.sql.Driver": {
        "com.mysql.cj.jdbc.Driver",
        "org.sqlite.JDBC",
    },
    "org.slf4j.spi.SLF4JServiceProvider": {
        "org.apache.logging.slf4j.SLF4JServiceProvider",
        "org.slf4j.nop.NOPServiceProvider",
    },
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def provider_names(content: bytes) -> set[str]:
    providers = set()
    for raw_line in content.decode("utf-8").splitlines():
        provider = raw_line.split("#", 1)[0].strip()
        if provider:
            providers.add(provider)
    return providers


def casefold_collisions(names: list[str]) -> list[list[str]]:
    folded: dict[str, set[str]] = {}
    for name in names:
        folded.setdefault(name.casefold(), set()).add(name)
    return sorted(sorted(paths) for paths in folded.values() if len(paths) > 1)


def assert_unique_archive(path: Path) -> None:
    with ZipFile(path) as archive:
        infos = archive.infolist()
    names = [info.filename for info in infos]
    duplicate_paths = sorted(name for name, count in Counter(names).items() if count > 1)
    if duplicate_paths:
        fail(f"{path.name} contains exact duplicate paths: {duplicate_paths[:20]}")
    folded = casefold_collisions(names)
    if folded:
        fail(f"{path.name} contains case-insensitive path collisions: {folded[:20]}")
    special_bits = stat.S_ISUID | stat.S_ISGID | stat.S_ISVTX
    unsafe_modes = []
    for info in infos:
        mode = (info.external_attr >> 16) & 0xFFFF
        allowed_types = {0, stat.S_IFDIR} if info.is_dir() else {0, stat.S_IFREG}
        if stat.S_IFMT(mode) not in allowed_types or mode & special_bits:
            unsafe_modes.append((info.filename, f"0{mode:o}"))
    if unsafe_modes:
        fail(f"{path.name} contains link/special Unix archive modes: {unsafe_modes[:20]}")


def dependency_inventory() -> tuple[dict[str, list[tuple[Path, bytes]]], dict[str, set[str]]]:
    class_sources: dict[str, list[tuple[Path, bytes]]] = {}
    service_providers: dict[str, set[str]] = {}
    for library in sorted(LIB_DIR.glob("*.jar"), key=lambda path: path.name):
        with ZipFile(library) as archive:
            for name in archive.namelist():
                if name.endswith(".class"):
                    class_sources.setdefault(name, []).append((library, archive.read(name)))
                elif name.startswith("META-INF/services/") and not name.endswith("/"):
                    service = name.removeprefix("META-INF/services/")
                    service_providers.setdefault(service, set()).update(
                        provider_names(archive.read(name))
                    )
    return class_sources, service_providers


def run_service_loader_probe() -> None:
    source = """
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;

public final class ServerFatJarServiceProbe {
    private static Set<String> providers(String serviceName) throws Exception {
        Class<?> service = Class.forName(serviceName);
        Set<String> names = new LinkedHashSet<>();
        for (Object provider : ServiceLoader.load(service)) {
            names.add(provider.getClass().getName());
        }
        return names;
    }

    private static void requireProvider(String service, String provider) throws Exception {
        if (!providers(service).contains(provider)) {
            throw new IllegalStateException(service + " did not discover " + provider);
        }
    }

    public static void main(String[] args) throws Exception {
        requireProvider("java.sql.Driver", "com.mysql.cj.jdbc.Driver");
        requireProvider("java.sql.Driver", "org.sqlite.JDBC");
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="server-fat-jar-probe-") as temp_dir:
        temp = Path(temp_dir)
        source_path = temp / "ServerFatJarServiceProbe.java"
        source_path.write_text(source, encoding="utf-8")
        compiled = subprocess.run(
            ["javac", "-cp", str(CORE_JAR), source_path.name],
            cwd=temp,
            capture_output=True,
            text=True,
        )
        if compiled.returncode != 0:
            fail(f"ServiceLoader probe compilation failed:\n{compiled.stdout}{compiled.stderr}")
        executed = subprocess.run(
            ["java", "-cp", f"{CORE_JAR}:{temp}", "ServerFatJarServiceProbe"],
            cwd=temp,
            capture_output=True,
            text=True,
        )
        if executed.returncode != 0:
            fail(f"ServiceLoader probe failed:\n{executed.stdout}{executed.stderr}")


def main() -> int:
    build = ET.parse(BUILD_XML).getroot()
    compile_target = build.find("target[@name='compile_core']")
    jar_task = None if compile_target is None else compile_target.find("jar")
    if jar_task is None:
        fail("Server Ant compile_core target is missing its jar task")
    if jar_task.get("duplicate") != "preserve":
        fail("Server fat JAR must preserve the first archive entry on dependency collisions")
    if jar_task.get("filesonly") != "true":
        fail("Server fat JAR must omit inherited directory records with unsafe ZIP modes")
    zipgroup = jar_task.find("zipgroupfileset")
    if zipgroup is None or zipgroup.get("includes") != "*.jar":
        fail("Server fat JAR no longer merges all shipped server libraries")

    built = subprocess.run(
        [str(ROOT / "scripts/build-server.sh")],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if built.returncode != 0 or built.stdout.count("BUILD SUCCESSFUL") < 2:
        fail(f"Authoritative core.jar/plugins.jar build failed:\n{built.stdout}{built.stderr}")

    assert_unique_archive(CORE_JAR)
    assert_unique_archive(PLUGINS_JAR)

    class_sources, expected_services = dependency_inventory()
    duplicate_classes = {
        name: sources for name, sources in class_sources.items() if len(sources) > 1
    }
    if not duplicate_classes:
        fail("Server dependency fixture no longer exercises duplicate class precedence")

    with ZipFile(CORE_JAR) as core:
        names = set(core.namelist())
        missing_classes = sorted(REQUIRED_CORE_CLASSES - names)
        if missing_classes:
            fail(f"core.jar is missing adaptive World Builder classes: {missing_classes}")
        manifest = core.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
        if "Main-Class: com.openrsc.server.Server" not in manifest:
            fail("core.jar lost its server entry point")

        wrong_precedence = []
        for name, sources in duplicate_classes.items():
            if core.read(name) != sources[0][1]:
                wrong_precedence.append(
                    f"{name} (expected first input {sources[0][0].name})"
                )
        if wrong_precedence:
            fail(
                "core.jar does not preserve deterministic first-library class precedence: "
                f"{wrong_precedence[:20]}"
            )

        for service, expected in sorted(expected_services.items()):
            descriptor = f"META-INF/services/{service}"
            if descriptor not in names:
                fail(f"core.jar is missing service descriptor {descriptor}")
            actual = provider_names(core.read(descriptor))
            if actual != expected:
                fail(
                    f"core.jar service providers differ for {service}: "
                    f"expected {sorted(expected)}, found {sorted(actual)}"
                )

    with ZipFile(PLUGINS_JAR) as plugins:
        if not any(name.endswith(".class") for name in plugins.namelist()):
            fail("plugins.jar contains no plugin classes")

    for service, required in REQUIRED_SERVICE_PROVIDERS.items():
        missing = required - expected_services.get(service, set())
        if missing:
            fail(f"Dependency inventory lost required {service} providers: {sorted(missing)}")
    run_service_loader_probe()

    print(
        "PASS: server fat JARs have unique, regular archive entries; adaptive classes, "
        f"{len(duplicate_classes)} deterministic duplicate-class resolutions, and "
        f"{len(expected_services)} complete service descriptors remain; JDBC provider "
        "discovery is runtime-valid"
    )
    return 0


if __name__ == "__main__":
    main()
