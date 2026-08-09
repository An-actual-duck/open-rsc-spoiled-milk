#!/usr/bin/env python3
"""Execute the real plugins.jar discovery path and guard its owned teardown."""
import subprocess
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE = SERVER / "core.jar"
HANDLER = SERVER / "src/com/openrsc/server/plugins/handler/PluginHandler.java"
CONTEXT = SERVER / "src/com/openrsc/server/extensions/ExtensionContext.java"
SOURCE = r'''
import com.openrsc.server.plugins.io.PluginJarLoader;
public final class LegacyPluginJarFixture {
 static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
 static boolean has(PluginJarLoader loader, String name) {
  for (Class<?> type : loader.getLoadedClasses()) if (type.getName().equals(name)) return true;
  return false;
 }
 public static void main(String[] args) throws Exception {
  PluginJarLoader loader = new PluginJarLoader();
  loader.loadJar();
  check(loader.getLoadedClasses().size() >= 492, "real plugins.jar class parity");
  check(has(loader, "com.openrsc.server.plugins.authentic.defaults.Default"), "default handler");
  check(has(loader, "com.openrsc.server.plugins.authentic.quests.free.ImpCatcher"), "quest");
  check(has(loader, "com.openrsc.server.plugins.authentic.minigames.gnomeball.GnomeBall"), "minigame");
  check(has(loader, "com.openrsc.server.plugins.custom.npcs.HorvikTheArmourerOpenPk"), "custom trigger");
  loader.clear();
  check(loader.getLoadedClasses().isEmpty(), "classloader cleanup");
  System.out.println("PASS");
 }
}
'''

with tempfile.TemporaryDirectory(prefix="legacy-plugin-adapter-") as directory:
    root = Path(directory)
    source = root / "LegacyPluginJarFixture.java"
    source.write_text(textwrap.dedent(SOURCE))
    result = subprocess.run(
        ["javac", "-source", "8", "-target", "8", "-cp", str(CORE), "-d", str(root), str(source)],
        text=True, capture_output=True,
    )
    if result.returncode:
        raise SystemExit(result.stderr)
    result = subprocess.run(
        ["java", "-cp", str(root) + ":" + str(CORE), "LegacyPluginJarFixture"],
        cwd=str(SERVER), text=True, capture_output=True,
    )
    if result.returncode:
        raise SystemExit(result.stderr)
    assert result.stdout.strip() == "PASS", result.stdout

handler = HANDLER.read_text(encoding="utf-8")
assert 'context.onDeactivate("legacy-plugin-runtime"' in handler
assert "restockEvent.stop();" in handler
assert "getGameEventHandler().remove(restockEvent);" in handler
assert "getExecutor().shutdown();" in handler
assert "awaitTermination(1, TimeUnit.MINUTES)" in handler
context = CONTEXT.read_text(encoding="utf-8")
assert "com.openrsc.server.Server" not in context
assert "getServer(" not in context
print("PASS: real legacy plugins.jar discovery and owned teardown contract validated")
