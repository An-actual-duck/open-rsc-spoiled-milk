#!/usr/bin/env python3
"""Check early network preparation without launching UI or loading LWJGL."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
source = (ROOT / "PC_Client/src/orsc/OpenRSC.java").read_text()
main = source[source.index("public static void main(String[] args)"):source.index("public static void createAndShowGUI()")]
assert main.index("ClientSocketRuntime.prepare();") < main.index("scaledWindow = ScaledWindow.getInstance();")
assert main.index("ClientSocketRuntime.prepare();") < main.index("SwingUtilities.invokeLater(OpenRSC::createAndShowGUI);")
with tempfile.TemporaryDirectory(prefix="client-socket-runtime-") as temp:
    subprocess.run(["javac", "-source", "8", "-target", "8", "-cp", str(JAR), "-d", temp,
                    str(ROOT / "tests/myworld/ClientSocketRuntimeFixture.java")], check=True, cwd=ROOT)
    for extra in ([], ["--real"]):
        subprocess.run(["java", "-cp", temp+":"+str(JAR), "orsc.ClientSocketRuntimeFixture", *extra], check=True, cwd=ROOT)
print("PASS network preparation precedes native graphics/window initialization")
