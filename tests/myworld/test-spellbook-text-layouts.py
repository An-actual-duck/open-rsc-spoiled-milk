#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "Client_Base/src/orsc/SpellbookLayoutSettings.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"{label} missing expected snippet: {needle!r}")


def run_settings_fixture() -> None:
    fixture = textwrap.dedent(
        """
        package orsc;

        import java.util.Properties;

        public final class SpellbookLayoutSettingsFixture {
            private static void expect(boolean condition, String label) {
                if (!condition) {
                    throw new AssertionError(label);
                }
            }

            public static void main(String[] args) {
                SpellbookLayoutSettings.setMode(null);
                expect(SpellbookLayoutSettings.getMode() == SpellbookLayoutSettings.Mode.ICONS,
                    "icons default");
                expect(!SpellbookLayoutSettings.usesTextLayout(), "icons predicate");

                SpellbookLayoutSettings.cycleMode();
                expect(SpellbookLayoutSettings.usesTextLayout(), "cycle to text");
                Properties saved = new Properties();
                SpellbookLayoutSettings.saveToClientSettings(saved);
                expect("text".equals(saved.getProperty("spellbook_layout")), "saved text id");

                SpellbookLayoutSettings.setMode(SpellbookLayoutSettings.Mode.ICONS);
                SpellbookLayoutSettings.loadFromClientSettings(saved);
                expect(SpellbookLayoutSettings.usesTextLayout(), "load text id");

                Properties alias = new Properties();
                alias.setProperty("spellbook_layout", "list");
                SpellbookLayoutSettings.loadFromClientSettings(alias);
                expect(SpellbookLayoutSettings.usesTextLayout(), "load list alias");

                alias.setProperty("spellbook_layout", "unknown");
                SpellbookLayoutSettings.loadFromClientSettings(alias);
                expect(SpellbookLayoutSettings.getMode() == SpellbookLayoutSettings.Mode.ICONS,
                    "unknown falls back to icons");
            }
        }
        """
    )
    with tempfile.TemporaryDirectory(prefix="spellbook-layout-test-") as temp_dir:
        temp = Path(temp_dir)
        fixture_path = temp / "SpellbookLayoutSettingsFixture.java"
        fixture_path.write_text(fixture, encoding="utf-8")
        subprocess.run(
            ["javac", "-d", str(temp), str(SETTINGS), str(fixture_path)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run(
            ["java", "-cp", str(temp), "orsc.SpellbookLayoutSettingsFixture"],
            check=True,
            cwd=ROOT,
        )


def main() -> None:
    settings = SETTINGS.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")

    require(settings, 'PROPERTY_KEY = "spellbook_layout"', "local preference key")
    require(settings, 'ICONS("icons", "@gre@Icons")', "icon default mode")
    require(settings, 'TEXT("text", "@yel@Text")', "text mode")
    require(mudclient, "Properties clientSettings = loadClientSettings();",
            "shared client preference load")
    require(mudclient, "SpellbookLayoutSettings.loadFromClientSettings(clientSettings);",
            "spellbook client preference load")
    require(mudclient, "SpellbookLayoutSettings.saveToClientSettings(props);",
            "client preference save")
    require(mudclient, '"@whi@Spellbook layout - " + SpellbookLayoutSettings.getMode().label, 65',
            "general settings layout row")
    require(mudclient, "if (settingIndex == 65 && this.mouseButtonClick == 1)",
            "general settings layout action")
    require(mudclient, "private int drawMagicTextList()", "magic text list")
    require(mudclient, "private int drawClericTextList()", "Cleric text list")
    require(mudclient, "private int drawPrayerTextList()", "prayer text list")
    require(mudclient, "0xFFFFFF, 0, 1, prayerTooltipY + 4);",
            "prayer point summary clears its divider")
    require(mudclient, "private int drawSummoningTextList()", "summoning text list")
    require(mudclient, "this.getMagicMenuSpellIndex(selectedRow)",
            "hidden-spell canonical index mapping")
    require(mudclient, "this.activateMagicMenuSpell(spellIndex);", "shared magic action")
    require(mudclient, "this.activateClericSpell(clericSpellCode);", "immediate Cleric action")
    require(mudclient, "this.togglePrayerMenuPrayer(spellIndex);", "shared prayer action")
    require(mudclient, "this.castSummon(summonIndex);", "shared summoning action")
    require(mudclient, "this.panelMagic.scrollMethodList(this.controlMagicPanel, x);",
            "text list mouse-wheel scrolling")
    require(mudclient, "this.saveSpellbookTextScrollPosition(this.magicOrPrayerList);",
            "per-tab text scroll preservation")
    require(mudclient, "this.clericTextScrollPosition", "Cleric subtab scroll preservation")
    require(mudclient, "if (lastSelectedSpell != -1 && isAndroid())",
            "Android last-spell control retained")

    run_settings_fixture()
    print("PASS: optional Magic, Prayer, and Summoning text layouts are shared and persisted")


if __name__ == "__main__":
    main()
