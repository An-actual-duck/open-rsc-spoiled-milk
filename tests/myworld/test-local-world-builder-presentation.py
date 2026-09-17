#!/usr/bin/env python3
"""Opt-in integration probes: real packaged classes, no app/server/network launch."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/local-world-builder-client"


def run(command, **kwargs):
    return subprocess.run(list(map(str, command)), text=True, capture_output=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("installation", type=Path)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--render", action="store_true", help="also test a hidden GPU context, without starting the client")
    args = parser.parse_args()
    app, bundle = args.installation.resolve(), args.bundle.resolve()
    java = app / "runtime/bin/java"
    client = app / "builder-runtime/Client_Base/Open_RSC_Client.jar"
    agent = bundle / "local-spoiled-milk-client/agent.jar"
    before = hashlib.sha256(client.read_bytes()).hexdigest()
    env = dict(os.environ)
    for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        env.pop(key, None)
    with tempfile.TemporaryDirectory(prefix="builder-presentation-probe-") as scratch:
        path = Path(scratch)
        compiled = run(["javac", "-source", "8", "-target", "8", "-cp", str(client) + os.pathsep + str(agent),
                        "-d", path, SOURCE / "PresentationProbe.java"], env=env)
        assert compiled.returncode == 0, compiled.stderr
        cp = os.pathsep.join(map(str, (path, client, agent)))
        probed = run([java, "-Djava.awt.headless=true", "-cp", cp, "orsc.PresentationProbe", client], cwd=path, env=env)
        assert probed.returncode == 0, probed.stdout + probed.stderr
        print(probed.stdout.strip())

        options = [java, "--dry-run", "-javaagent:" + str(agent), "-Dopenrsc.worldBuilderMode=true",
                   "-Dopenrsc.worldBuilderAdaptiveMode=true", "-Dopenrsc.worldBuilderHost=127.0.0.1"]
        dry = run(options + ["-jar", client], cwd=path, env=env)
        assert dry.returncode == 0 and "packaged protocol unchanged" in dry.stdout, dry.stderr
        print("PASS actual JVM agent startup with --dry-run (client main not executed)")
        altered = path / "Open_RSC_Client.jar"
        shutil.copyfile(client, altered)
        with altered.open("ab") as stream:
            stream.write(b"changed")
        # Direct invocation tests rejection without triggering the JVM fatal-agent abort/core dump.
        rejected = run([java, "-Djava.awt.headless=true", "-cp", cp, "orsc.PresentationProbe", altered], cwd=path, env=env)
        assert rejected.returncode != 0 and "Client changed" in rejected.stderr, rejected.stderr
        print("PASS changed project client rejected")
        if args.render:
            compiled_render = run(["javac", "-source", "8", "-target", "8", "-cp", client,
                                   "-d", path, SOURCE / "RenderProbe.java"], env=env)
            assert compiled_render.returncode == 0, compiled_render.stderr
            rendered = run([java, "-cp", cp, "orsc.RenderProbe"], cwd=path, env=env, timeout=30)
            assert rendered.returncode == 0, rendered.stdout + rendered.stderr
            print(rendered.stdout.strip())

        fixture = path / "installation with spaces"
        fixture.mkdir()
        shutil.copytree(bundle / "local-spoiled-milk-client", fixture / "local-spoiled-milk-client")
        launcher = fixture / "Start Spoiled Milk World Builder.sh"
        shutil.copyfile(bundle / launcher.name, launcher)
        standard = fixture / "Start World Builder.sh"
        standard.write_text('#!/bin/bash\n[[ "$WORLD_BUILDER_SKIP_UPDATE" == 1 ]] || exit 8\n'
                            '[[ "$JAVA_TOOL_OPTIONS" == -javaagent:* ]] || exit 9\nprintf "isolated-launch-ok\\n"\n')
        patch = fixture / "local-spoiled-milk-client"
        pins = patch / "application-pin.sha256"
        pins.write_text(hashlib.sha256(standard.read_bytes()).hexdigest() + "  Start World Builder.sh\n")
        (patch / "bundle.sha256").write_text("".join(hashlib.sha256((patch / name).read_bytes()).hexdigest()
                                                   + "  " + name + "\n" for name in ("agent.jar", pins.name)))
        success = run(["bash", launcher], env=env)
        assert success.returncode == 0 and "isolated-launch-ok" in success.stdout, success.stderr
        spaced_agent = patch / "agent.jar"
        injected = run([java, "--dry-run", "-Dopenrsc.worldBuilderMode=true",
                        "-Dopenrsc.worldBuilderAdaptiveMode=true", "-Dopenrsc.worldBuilderHost=127.0.0.1",
                        "-jar", client], cwd=path,
                       env={**env, "JAVA_TOOL_OPTIONS": '-javaagent:"' + str(spaced_agent) + '"'})
        assert injected.returncode == 0 and "packaged protocol unchanged" in injected.stdout, injected.stderr
        polluted = run(["bash", launcher], env={**env, "JAVA_TOOL_OPTIONS": "-Danything=true"})
        assert polluted.returncode != 0 and "Existing Java-wide" in polluted.stderr
        standard.write_text("changed after pin")
        drift = run(["bash", launcher], env=env)
        assert drift.returncode != 0 and "Application version changed" in drift.stderr
        print("PASS launcher spacing, updater isolation, environment guard and version pin")
    assert hashlib.sha256(client.read_bytes()).hexdigest() == before
    print("PASS installed client byte-identical after all probes")


if __name__ == "__main__":
    main()
