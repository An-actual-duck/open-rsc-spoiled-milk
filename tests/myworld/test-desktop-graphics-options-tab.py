#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
RENDERER_SETTINGS_PANEL = ROOT / "Client_Base/src/orsc/RendererSettingsPanel.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"unexpected {description}: {needle}")


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        fail(f"missing method: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        fail(f"missing method body: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    fail(f"unterminated method: {signature}")


def main() -> None:
    source = MUDCLIENT.read_text(encoding="utf-8")
    panel = RENDERER_SETTINGS_PANEL.read_text(encoding="utf-8")
    desktop_box = method_body(source, "private void drawCustomSettingsBox(")
    android_box = method_body(source, "private void drawAndroidSettingsBox(")
    options = method_body(source, "private void drawUiTabOptions(")
    general = method_body(source, "private void drawGeneralSettingsOptions(")
    graphics = method_body(panel, "static List<Row> rows(")
    clicks = method_body(panel, "boolean handleSelectedAction(")
    action_map = method_body(panel, "private static Action actionFor(")
    adapter = method_body(source, "private void applyRendererSettingsAction(")
    authentic = method_body(source, "private void drawAuthenticSettingsOptions(")

    for label in ('"Social"', '"General"', '"Graphics"'):
        require(desktop_box, label, f"desktop {label} tab label")
    forbid(desktop_box, '"Android"', "Android label on desktop tab bar")
    require(desktop_box, "int firstDivider = var5 / 3;", "desktop first tab divider")
    require(desktop_box, "int secondDivider = 2 * (var5 / 3) + 1;", "desktop second tab divider")
    for selected in range(3):
        require(desktop_box, f"this.settingTab == {selected} ? chosenColor : unchosenColor",
                f"desktop selected color for tab {selected}")

    for label in ('"Social"', '"General"', '"Android"'):
        require(android_box, label, f"unchanged Android {label} tab label")
    forbid(android_box, '"Graphics"', "Graphics label on Android tab bar")

    # Both desktop and Android use the established inclusive boundaries:
    # 0..65 Social, 66..131 General, and 132..195 platform-specific third tab.
    if options.count("var3 < 66") != 2:
        fail("Social click boundary must exist once for Android and once for desktop")
    if options.count("var3 >= 66 && var3 <= 131") != 2:
        fail("General click boundary must exist once for Android and once for desktop")
    if options.count("var3 > 131") != 2:
        fail("third-tab click boundary must exist once for Android and once for desktop")
    require(options, "this.settingTab = 2; // Android Settings Tab", "Android third-tab routing")
    require(options, "this.settingTab = 2; // Graphics Settings Tab", "desktop third-tab routing")
    require(options, "if (isAndroid()) {\n\t\t\t\t\t\tthis.drawAndroidSettingsOptions",
            "Android third-tab renderer")
    require(options, "this.drawGraphicsSettingsOptions(var3, var5, var6, var7);",
            "desktop Graphics renderer")

    graphics_rows = (
        ("Sprites:", "REMASTERED_SPRITES"),
        ("Scaling - ", "ACTION_SCALING_ROW"),
        ("Scaling type - ", "ACTION_SCALING_TYPE"),
        ("Preset - ", "ACTION_RENDERER_PROFILE"),
        ("Aspect Ratio - ", "ACTION_RENDER_SURFACE"),
        ("Borderless - ", "ACTION_WINDOW_MODE"),
        ("Lighting - ", "ACTION_LIGHTING"),
        ("Geometry - ", "ACTION_GEOMETRY"),
        ("Terrain Variation - ", "ACTION_TERRAIN_VARIATION"),
        ("Fog - ", "ACTION_FOG"),
        ("Terrain shading", "TERRAIN_RELIEF_SLIDER"),
        ("Object shading", "OBJECT_RELIEF_SLIDER"),
        ("Brightness / dimness", "DIMNESS_SLIDER"),
        ("Contrast", "CONTRAST_SLIDER"),
        ("Gamma", "GAMMA_SLIDER"),
        ("Saturation", "SATURATION_SLIDER"),
        ("Hide Roofs", "ACTION_HIDE_ROOFS"),
        ("Hide Underground Flicker", "ACTION_HIDE_UNDERGROUND_FLICKER"),
    )
    for label, action in graphics_rows:
        require(graphics, label, f"Graphics placement for {label}")
        require(graphics, action, f"preserved action for {label}")

    for visual_owner in (
        "RendererProfileSettings", "RenderSurfaceSettings", "OpenGLWindowSettings",
        "RendererLightingSettings", "RendererGeometrySettings", "RendererTerrainVariationSettings",
        "RendererFogSettings", "SETTINGS_TERRAIN_RELIEF_SLIDER", "SETTINGS_OBJECT_RELIEF_SLIDER",
        "SETTINGS_DIMNESS_SLIDER", "SETTINGS_CONTRAST_SLIDER", "SETTINGS_GAMMA_SLIDER",
        "SETTINGS_SATURATION_SLIDER", '"@whi@Scaling - "', '"@whi@Scaling type - "',
    ):
        forbid(general, visual_owner, "desktop visual setting in General")

    # Android keeps the visual rows it already exposed through General.
    require(general, "if (isAndroid() && !ScaledWindow.isOpenGLPrimaryWindowEnabled())",
            "Android sprite compatibility guard")
    require(general, "if (isAndroid() && S_SHOW_ROOF_TOGGLE)", "Android roof compatibility guard")
    require(general, "if (isAndroid() && S_SHOW_UNDERGROUND_FLICKER_TOGGLE)",
            "Android underground-flicker compatibility guard")

    for label in (
        "Camera angle mode", "Sound effects", "Mouse buttons", "Coordinates",
        "Spellbook layout", "Batch Progress Bar", "Tool Focus Menu", "Hits XP Focus Menu",
        "Summon Health Bars", "Experience Drops",
    ):
        require(general, label, f"General placement for {label}")

    for stable_action, semantic_action, handler in (
        ("ACTION_RENDER_SURFACE", "RENDER_SURFACE", "cycleRenderSurfaceMode();"),
        ("ACTION_RENDERER_PROFILE", "RENDERER_PROFILE", "cycleOpenGLRendererProfileMode();"),
        ("ACTION_FOG", "FOG", "cycleOpenGLFogMode();"),
        ("ACTION_LIGHTING", "LIGHTING", "cycleOpenGLLightingMode();"),
        ("ACTION_GEOMETRY", "GEOMETRY", "cycleOpenGLGeometryMode();"),
        ("ACTION_WINDOW_MODE", "WINDOW_MODE", "cycleOpenGLWindowMode();"),
        ("ACTION_TERRAIN_VARIATION", "TERRAIN_VARIATION", "cycleOpenGLTerrainVariationMode();"),
    ):
        require(action_map, f"case {stable_action}:", f"{stable_action} dispatch")
        require(action_map, f"return Action.{semantic_action};", f"{semantic_action} semantic mapping")
        require(adapter, handler, f"{semantic_action} behavior")
    require(source, "private void toggleRoofVisibilitySetting()", "roof packet adapter")
    require(source, "this.reloadCurrentRegionForRoofVisibility();", "roof visibility reload")
    require(source, "private void toggleUndergroundFlickerSetting()", "underground packet adapter")
    require(source, "bufferBits.putByte(42);", "underground flicker packet key")
    require(clicks, "!state.openGLPrimary", "software scaling limited to fallback Graphics")
    require(source, "!isAndroid() && this.settingTab == 2", "desktop Graphics click route")
    require(options, "if (this.authenticSettings)\n\t\t\t\tthis.drawAuthenticSettingsOptions",
            "authentic settings route")
    require(authentic, 'drawString("Game options - click to toggle"', "unchanged authentic options UI")

    print("PASS: desktop Graphics options tab placement and boundaries are guarded")


if __name__ == "__main__":
    main()
