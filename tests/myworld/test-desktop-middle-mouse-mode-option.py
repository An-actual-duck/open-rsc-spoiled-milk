#!/usr/bin/env python3
"""Validate the persisted desktop middle-mouse Graphics option."""

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "Client_Base/src/orsc/DesktopMiddleMouseSettings.java"
PANEL = ROOT / "Client_Base/src/orsc/RendererSettingsPanel.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
OPEN_RSC = ROOT / "PC_Client/src/orsc/OpenRSC.java"
ANDROID_INPUT = (
    ROOT
    / "legacy/clients/Android_Client/Open RSC Android Client/src/main/java"
    / "com/openrsc/android/render/InputImpl.java"
)


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"unexpected {description}: {needle}")


def run_settings_harness() -> None:
    fixture = textwrap.dedent(
        """
        package orsc;

        import java.util.Properties;

        public final class DesktopMiddleMouseSettingsFixture {
            private static void expect(boolean condition, String message) {
                if (!condition) {
                    throw new AssertionError(message);
                }
            }

            public static void main(String[] args) {
                DesktopMiddleMouseSettings.setMode(null);
                expect(DesktopMiddleMouseSettings.usesTilt(), "null/default must use tilt");

                DesktopMiddleMouseSettings.setMode(DesktopMiddleMouseSettings.Mode.CLASSIC);
                DesktopMiddleMouseSettings.loadFromClientSettings(new Properties());
                expect(DesktopMiddleMouseSettings.usesTilt(), "missing property must use tilt");

                Properties old = new Properties();
                old.setProperty("middle_mouse", "classic");
                DesktopMiddleMouseSettings.loadFromClientSettings(old);
                expect(DesktopMiddleMouseSettings.usesTilt(), "old property must use tilt");

                Properties invalid = new Properties();
                invalid.setProperty("middle_mouse_mode", "zoom-and-pan");
                DesktopMiddleMouseSettings.loadFromClientSettings(invalid);
                expect(DesktopMiddleMouseSettings.usesTilt(), "invalid property must use tilt");

                Properties classic = new Properties();
                classic.setProperty("middle_mouse_mode", "classic");
                DesktopMiddleMouseSettings.loadFromClientSettings(classic);
                expect(DesktopMiddleMouseSettings.getMode()
                    == DesktopMiddleMouseSettings.Mode.CLASSIC, "classic load");

                Properties saved = new Properties();
                saved.setProperty("unrelated", "preserved");
                DesktopMiddleMouseSettings.saveToClientSettings(saved);
                expect("classic".equals(saved.getProperty("middle_mouse_mode")), "classic save");
                expect("preserved".equals(saved.getProperty("unrelated")), "unrelated property");

                expect(DesktopMiddleMouseSettings.cycleMode()
                    == DesktopMiddleMouseSettings.Mode.WITH_TILT, "cycle to tilt");
                DesktopMiddleMouseSettings.saveToClientSettings(saved);
                expect("with-tilt".equals(saved.getProperty("middle_mouse_mode")), "tilt save");
                expect(DesktopMiddleMouseSettings.cycleMode()
                    == DesktopMiddleMouseSettings.Mode.CLASSIC, "cycle to classic");
                System.out.println("desktop-middle-mouse-settings-ok");
            }
        }
        """
    ).strip()

    with tempfile.TemporaryDirectory(prefix="desktop-middle-mouse-settings-") as directory:
        temp = Path(directory)
        fixture_path = temp / "DesktopMiddleMouseSettingsFixture.java"
        fixture_path.write_text(fixture + "\n", encoding="utf-8")
        result = subprocess.run(
            ["javac", "-d", str(temp), str(SETTINGS), str(fixture_path)],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            fail(f"settings fixture compilation failed:\n{result.stdout}{result.stderr}")
        result = subprocess.run(
            ["java", "-cp", str(temp), "orsc.DesktopMiddleMouseSettingsFixture"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            fail(f"settings fixture failed:\n{result.stdout}{result.stderr}")
        require(result.stdout, "desktop-middle-mouse-settings-ok", "settings fixture success")


def main() -> None:
    settings = SETTINGS.read_text(encoding="utf-8")
    panel = PANEL.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    applet = APPLET.read_text(encoding="utf-8")
    open_rsc = OPEN_RSC.read_text(encoding="utf-8")
    android_input = ANDROID_INPUT.read_text(encoding="utf-8")

    require(settings, 'PROPERTY_KEY = "middle_mouse_mode"', "client setting key")
    require(settings, 'WITH_TILT("with-tilt", "@gre@With tilt")', "With tilt mode and label")
    require(settings, 'CLASSIC("classic", "@yel@Classic")', "Classic mode and label")
    require(settings, "private static volatile Mode mode = Mode.WITH_TILT;", "new default")
    require(open_rsc, "DesktopMiddleMouseSettings.loadFromClientSettings(props);", "startup load")
    require(client, "DesktopMiddleMouseSettings.saveToClientSettings(properties);", "merged save")
    require(client, "case MIDDLE_MOUSE:", "Graphics action adapter")
    require(client, "this.cycleDesktopMiddleMouseMode();", "Graphics option cycling")

    require(panel, "static final int MIDDLE_MOUSE = 73;", "stable Graphics action ID")
    require(panel, '"@whi@Middle mouse - " + state.middleMouseLabel', "Graphics row label")
    require(panel, "return Action.MIDDLE_MOUSE;", "Graphics row dispatch")
    require(panel, "DesktopMiddleMouseSettings.getMode().label", "live mode label capture")

    require(
        applet,
        "if (mudclient.isInFirstPersonView() || DesktopMiddleMouseSettings.usesTilt())",
        "With tilt and first-person pitch branch",
    )
    require(applet, "mudclient.adjustCameraPitch(-deltaY);", "vertical tilt")
    require(applet, "mudclient.adjustCameraZoomSetting(direction * verticalDistance);", "Classic zoom")
    require(
        applet,
        "osConfig.C_SWIPE_TO_ZOOM_MODE == 2 ? -1 : 1",
        "Classic zoom inversion",
    )
    require(applet, "mudclient.adjustCameraZoomSetting(zoomAmount);", "wheel zoom in both modes")
    require(applet, "mudclient.currentMouseButtonDown = 0;", "middle gameplay isolation")
    require(applet, "var1.consume();", "middle event consumption")
    forbid(android_input, "DesktopMiddleMouseSettings", "desktop option in Android controls")

    run_settings_harness()
    print("PASS: desktop middle mouse mode defaults, persists, cycles, and selects tilt or Classic zoom")


if __name__ == "__main__":
    main()
