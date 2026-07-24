#!/usr/bin/env python3
"""Regression coverage for desktop middle-mouse camera orbit controls."""

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ORBIT = ROOT / "PC_Client/src/orsc/DesktopMiddleMouseOrbit.java"
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
OPENGL_INPUT = ROOT / "PC_Client/src/orsc/OpenGLInputBridge.java"
SCALED_WINDOW = ROOT / "PC_Client/src/orsc/ScaledWindow.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
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


def section(text: str, start: str, end: str) -> str:
    start_index = text.find(start)
    if start_index < 0:
        fail(f"missing section start: {start}")
    end_index = text.find(end, start_index + len(start))
    if end_index < 0:
        fail(f"missing section end after {start}: {end}")
    return text[start_index:end_index]


def run_orbit_state_harness() -> None:
    harness = textwrap.dedent(
        """
        package orsc;

        import java.awt.event.MouseEvent;

        public final class DesktopMiddleMouseOrbitHarness {
            private static void expect(boolean condition, String message) {
                if (!condition) {
                    throw new AssertionError(message);
                }
            }

            public static void main(String[] args) {
                DesktopMiddleMouseOrbit orbit = new DesktopMiddleMouseOrbit();

                expect(!orbit.begin(MouseEvent.BUTTON1, 10, 20), "left press activated orbit");
                expect(!orbit.begin(MouseEvent.BUTTON3, 10, 20), "right press activated orbit");
                expect(!orbit.update(30, 40), "non-middle drag updated orbit");

                expect(orbit.begin(MouseEvent.BUTTON2, 100, 200), "middle press did not activate orbit");
                expect(orbit.isActive(), "orbit is not active");
                expect(orbit.update(124, 200), "horizontal drag was ignored");
                expect(orbit.getDeltaX() == 24 && orbit.getDeltaY() == 0, "horizontal delta mismatch");

                expect(orbit.update(124, 181), "vertical drag was ignored");
                expect(orbit.getDeltaX() == 0 && orbit.getDeltaY() == -19, "vertical delta mismatch");

                expect(!orbit.end(MouseEvent.BUTTON1), "left release ended middle orbit");
                expect(orbit.isActive(), "left release cleared middle orbit");
                expect(orbit.end(MouseEvent.BUTTON2), "middle release was ignored");
                expect(!orbit.isActive(), "middle release did not end orbit");
                expect(!orbit.update(150, 150), "drag remained active after release");

                orbit.begin(MouseEvent.BUTTON2, 1, 1);
                orbit.cancel();
                expect(!orbit.isActive(), "cancel did not end orbit");
                System.out.println("desktop-middle-mouse-orbit-state-ok");
            }
        }
        """
    ).strip()

    with tempfile.TemporaryDirectory(prefix="desktop-middle-mouse-orbit-") as directory:
        temp = Path(directory)
        package_dir = temp / "orsc"
        package_dir.mkdir()
        harness_path = package_dir / "DesktopMiddleMouseOrbitHarness.java"
        harness_path.write_text(harness + "\n", encoding="utf-8")
        result = subprocess.run(
            ["javac", "-d", str(temp), str(ORBIT), str(harness_path)],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            fail(f"orbit state harness compilation failed:\n{result.stdout}{result.stderr}")
        result = subprocess.run(
            ["java", "-cp", str(temp), "orsc.DesktopMiddleMouseOrbitHarness"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            fail(f"orbit state harness failed:\n{result.stdout}{result.stderr}")
        require(result.stdout, "desktop-middle-mouse-orbit-state-ok", "orbit harness success")


def main() -> None:
    applet = APPLET.read_text(encoding="utf-8")
    opengl_input = OPENGL_INPUT.read_text(encoding="utf-8")
    scaled_window = SCALED_WINDOW.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    android_input = ANDROID_INPUT.read_text(encoding="utf-8")

    mouse_press = section(applet, "void mousePressed(MouseEvent var1)", "@Override")
    mouse_release = section(applet, "void mouseReleased(MouseEvent var1)", "@Override")
    mouse_exit = section(applet, "void mouseExited(MouseEvent var1)", "@Override")
    mouse_drag = section(applet, "void mouseDragged(MouseEvent var1)", "@Override")
    mouse_wheel = section(applet, "void mouseWheelMoved(MouseWheelEvent e)", "public class KeyHandler")

    require(mouse_press, "middleMouseOrbit.begin(", "middle-only orbit activation")
    require(mouse_press, "mudclient.currentMouseButtonDown = 0;", "middle press gameplay-button suppression")
    require(mouse_press, "var1.consume();", "middle press event consumption")
    require(mouse_release, "middleMouseOrbit.end(var1.getButton())", "middle release cleanup")
    require(mouse_release, "mudclient.currentMouseButtonDown = 0;", "middle release gameplay-button cleanup")
    require(mouse_exit, "middleMouseOrbit.isActive()", "middle orbit exit suppression")
    forbid(mouse_exit, "middleMouseOrbit.cancel()", "premature orbit cancellation on cursor exit")

    require(mouse_drag, "middleMouseOrbit.update(", "middle orbit drag gate")
    require(mouse_drag, "applyMiddleMouseVerticalDrag(deltaY);", "vertical drag mode dispatch")
    require(mouse_drag, "mudclient.adjustCameraPitch(-deltaY);", "With tilt vertical drag")
    require(
        mouse_drag,
        "mudclient.adjustCameraZoomSetting(direction * verticalDistance);",
        "Classic vertical drag zoom",
    )
    require(mouse_drag, "osConfig.C_SWIPE_TO_ROTATE_MODE == 2 ? -1 : 1", "horizontal inversion setting")
    require(mouse_drag, "mudclient.cameraRotation = 255 & mudclient.cameraRotation", "manual horizontal yaw")
    require(mouse_drag, "mudclient.keyLeft = true;", "automatic-camera left yaw")
    require(mouse_drag, "mudclient.keyRight = true;", "automatic-camera right yaw")
    require(mouse_drag, "mudclient.currentMouseButtonDown = 0;", "middle drag gameplay-button suppression")
    require(mouse_drag, "var1.consume();", "middle drag event consumption")
    forbid(mouse_drag, "runScroll(", "middle drag UI scrolling")

    require(mouse_wheel, "mudclient.adjustCameraZoomSetting(zoomAmount);", "wheel zoom")
    require(
        client,
        "if (this.isInFirstPersonView()) {\n\t\t\tthis.cameraPitch = (this.cameraPitch + amount) & 1023;",
        "preserved first-person pitch behavior",
    )
    require(
        client,
        "} else if (RendererExperimentalSettings.isCameraTiltEnabled()) {",
        "third-person camera-tilt setting",
    )
    require(client, "EXPERIMENTAL_CAMERA_PITCH_MIN", "third-person pitch minimum")
    require(client, "EXPERIMENTAL_CAMERA_PITCH_MAX", "third-person pitch maximum")

    require(opengl_input, "gl.GLFW_MOUSE_BUTTON_MIDDLE", "OpenGL middle-button polling")
    require(opengl_input, "MouseEvent.BUTTON2", "OpenGL AWT middle-button mapping")
    require(opengl_input, "modifiers |= InputEvent.BUTTON2_MASK;", "OpenGL middle-drag modifier")
    require(scaled_window, "int mouseEventButton = e.getButton();", "scaled middle-button preservation")
    require(scaled_window, "LegacySoftwareScalingSettings.unscaleCoordinate(e.getX())", "scaled orbit X mapping")
    require(scaled_window, "LegacySoftwareScalingSettings.unscaleCoordinate(e.getY())", "scaled orbit Y mapping")

    require(android_input, "osConfig.C_SWIPE_TO_ZOOM_MODE", "unchanged Android swipe zoom")
    require(android_input, "osConfig.C_SWIPE_TO_ROTATE_MODE", "unchanged Android swipe rotation")

    run_orbit_state_harness()
    print("PASS: desktop middle mouse provides isolated yaw plus configurable tilt/zoom")


if __name__ == "__main__":
    main()
