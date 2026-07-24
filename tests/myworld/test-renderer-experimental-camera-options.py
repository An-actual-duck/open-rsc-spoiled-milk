#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
OPENRSC = ROOT / "PC_Client/src/orsc/OpenRSC.java"
KEY_BINDINGS = ROOT / "PC_Client/src/orsc/OpenGLKeyBindings.java"
KEY_BINDING = ROOT / "PC_Client/src/orsc/KeyBinding.java"
SETTINGS = ROOT / "Client_Base/src/orsc/RendererExperimentalSettings.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"forbidden {description}: {needle}")


def main() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    applet = APPLET.read_text(encoding="utf-8")
    openrsc = OPENRSC.read_text(encoding="utf-8")
    key_bindings = KEY_BINDINGS.read_text(encoding="utf-8")
    key_bindings += "\n" + KEY_BINDING.read_text(encoding="utf-8")
    settings = SETTINGS.read_text(encoding="utf-8")

    require(
        settings,
        'private static final String CAMERA_TILT_PROPERTY = "spoiledmilk.experimentalCameraTilt";',
        "camera tilt runtime property",
    )
    require(
        settings,
        'private static final String CAMERA_TILT_ENV = "SPOILED_MILK_EXPERIMENTAL_CAMERA_TILT";',
        "camera tilt runtime environment override",
    )
    require(
        settings,
        'private static final String EXTRA_ZOOM_PROPERTY = "spoiledmilk.experimentalExtraZoom";',
        "extra zoom runtime property",
    )
    require(
        settings,
        'private static final String EXTRA_ZOOM_ENV = "SPOILED_MILK_EXPERIMENTAL_EXTRA_ZOOM";',
        "extra zoom runtime environment override",
    )
    require(
        settings,
        "readBoolean(CAMERA_TILT_PROPERTY, CAMERA_TILT_ENV, true);",
        "camera tilt defaults on",
    )
    require(
        settings,
        "readBoolean(EXTRA_ZOOM_PROPERTY, EXTRA_ZOOM_ENV, true);",
        "extra zoom defaults on",
    )
    require(
        settings,
        "These are no longer player-facing options. Defaults are always on",
        "baseline camera behavior ignores retired saved toggles",
    )
    forbid(
        settings,
        "toggleCameraTilt()",
        "camera tilt runtime toggle",
    )
    forbid(
        settings,
        "toggleExtraZoom()",
        "extra zoom runtime toggle",
    )
    require(
        openrsc,
        "RendererExperimentalSettings.loadFromClientSettings(props);",
        "experimental settings startup load",
    )
    forbid(
        client,
        "private static final int SETTINGS_ACTION_EXPERIMENTAL_CAMERA_TILT = 63;",
        "camera tilt settings action",
    )
    forbid(
        client,
        "private static final int SETTINGS_ACTION_EXPERIMENTAL_EXTRA_ZOOM = 64;",
        "extra zoom settings action",
    )
    forbid(
        client,
        'index = addSettingsSection(index, "Experimental");',
        "experimental options section",
    )
    forbid(
        client,
        '"@whi@Font - " + RendererFontSettings.getMode().label',
        "retired OpenGL UI font option",
    )
    forbid(
        client,
        '"@whi@Camera tilt - "',
        "camera tilt options row",
    )
    forbid(
        client,
        '"@whi@Extra zoom - "',
        "extra zoom options row",
    )
    forbid(
        client,
        "void toggleExperimentalCameraTilt()",
        "camera tilt click handler",
    )
    forbid(
        client,
        "void toggleExperimentalExtraZoom()",
        "extra zoom click handler",
    )
    require(
        client,
        "return RendererExperimentalSettings.isExtraZoomEnabled() ? EXTRA_CAMERA_ZOOM_MIN : NORMAL_CAMERA_ZOOM_MIN;",
        "extra zoom minimum bound",
    )
    require(
        client,
        "return RendererExperimentalSettings.isExtraZoomEnabled() ? EXTRA_CAMERA_ZOOM_MAX : NORMAL_CAMERA_ZOOM_MAX;",
        "extra zoom maximum bound",
    )
    require(
        client,
        "clientStream.bufferBits.putByte(persistableCameraZoom);",
        "saved zoom clamps to legacy byte range",
    )
    require(
        client,
        "this.isInFirstPersonView() || RendererExperimentalSettings.isCameraTiltEnabled()",
        "experimental tilt feeds camera pitch only when enabled",
    )
    require(
        client,
        "private static final int NORTH_CAMERA_ROTATION = ORSCharacterDirection.NORTH.rotation;",
        "north-facing camera rotation constant",
    )
    require(
        client,
        "private static final int NORTH_CAMERA_ANGLE = NORTH_CAMERA_ROTATION / 32;",
        "auto camera north angle constant",
    )
    require(
        client,
        "public void resetCameraNorth()",
        "Home camera reset method",
    )
    require(
        client,
        "this.cameraPitch = this.isInFirstPersonView() ? 0 : DEFAULT_CAMERA_PITCH;",
        "Home camera reset restores proper pitch",
    )
    require(
        client,
        "if (RendererExperimentalSettings.isCameraTiltEnabled()) {\n\t\t\t\t\t\t\tadjustCameraPitch(-4);",
        "Page Down camera tilt control",
    )
    require(
        client,
        "if (RendererExperimentalSettings.isCameraTiltEnabled()) {\n\t\t\t\t\t\t\tadjustCameraPitch(4);",
        "Page Up camera tilt control",
    )
    require(
        applet,
        "mudclient.adjustCameraPitch(-deltaY);",
        "middle-drag camera tilt uses the shared pitch limiter",
    )
    require(
        applet,
        "mudclient.adjustCameraZoomSetting(zoomAmount);",
        "wheel zoom uses extended bounds helper",
    )
    require(
        applet,
        "if (keyCode == KeyEvent.VK_F3) mudclient.setCameraZoomSetting(75);",
        "F3 zoom reset uses camera zoom helper",
    )
    require(
        applet,
        "if (keyCode == KeyEvent.VK_HOME) mudclient.resetCameraNorth();",
        "Home key resets camera north",
    )
    require(
        key_bindings,
        'key(gl, "GLFW_KEY_HOME", KeyEvent.VK_HOME, KeyEvent.CHAR_UNDEFINED, KeyEvent.CHAR_UNDEFINED),',
        "OpenGL Home key binding",
    )
    forbid(
        applet,
        "import static orsc.osConfig.C_LAST_ZOOM;",
        "direct applet C_LAST_ZOOM mutation",
    )
    forbid(
        applet,
        "newZoom >= 0 && newZoom <= 255",
        "hard-coded applet zoom bounds",
    )

    print("PASS: camera tilt and extra zoom are baseline-on with diagnostic overrides")


if __name__ == "__main__":
    main()
