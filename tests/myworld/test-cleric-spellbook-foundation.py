#!/usr/bin/env python3
"""Compile and validate the stable Cleric definition foundation."""

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "server/src/com/openrsc/server/content/cleric"
LEGACY_SPELLS = ROOT / "server/src/com/openrsc/server/constants/Spells.java"
IMPLEMENTATION_PLAN = ROOT / "docs/myworld/completed-work-plans/cleric-spellbook-implementation-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_source_boundaries() -> None:
    java_files = sorted(JAVA_ROOT.glob("*.java"))
    required_foundation = {
        "ClericAlignment.java",
        "ClericSigilCost.java",
        "ClericSigilMaterial.java",
        "ClericSpellCatalog.java",
        "ClericSpellDefinition.java",
        "ClericSpellId.java",
        "ClericSpellPresentation.java",
    }
    actual = {source.name for source in java_files}
    require(required_foundation <= actual,
            "Cleric C01 source missing: " + ", ".join(sorted(required_foundation - actual)))
    for source in java_files:
        text = source.read_text(encoding="utf-8")
        require(".ordinal()" not in text, f"{source.name} must not persist or transport enum ordinals")

    legacy = LEGACY_SPELLS.read_text(encoding="utf-8")
    require("CLERIC_MEND" not in legacy and "CLERIC_SPELL" not in legacy,
            "Cleric identities leaked into the legacy Magic enum")

    references = []
    for base in (ROOT / "server/src", ROOT / "server/plugins"):
        for source in base.rglob("*.java"):
            if source.parent == JAVA_ROOT:
                continue
            text = source.read_text(encoding="utf-8")
            if any(name in text for name in (
                "ClericSpellCatalog", "ClericSpellDefinition", "ClericSpellId"
            )):
                references.append(str(source.relative_to(ROOT)))
    expected_authorized_references = {
        "server/src/com/openrsc/server/content/cleric/effect/ClericEffectCatalog.java",
        "server/src/com/openrsc/server/content/cleric/effect/ClericEffectRankDefinition.java",
        "server/src/com/openrsc/server/content/cleric/effect/ClericEffectRegistry.java",
        "server/src/com/openrsc/server/content/status/ClericActiveStatusCollector.java",
        "server/src/com/openrsc/server/content/cleric/runtime/ClericSupportCasting.java",
        "server/src/com/openrsc/server/content/cleric/runtime/ClericTimedEffectRuntime.java",
        "server/src/com/openrsc/server/net/rsc/ActionSender.java",
        "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java",
        "server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java",
        "server/src/com/openrsc/server/net/rsc/struct/outgoing/ClericSpellbookStruct.java",
    }
    require(set(references) == expected_authorized_references,
            "Cleric catalog exposure drift: " + ", ".join(references))

    plan = IMPLEMENTATION_PLAN.read_text(encoding="utf-8")
    for settled_c07_rule in (
        "Spend one full cast vector exactly once",
        "if every recipient is\nineffective, spend nothing",
        "queued walking",
        "natural regeneration clock",
    ):
        require(settled_c07_rule in plan,
                f"settled C07 contract missing: {settled_c07_rule}")
    require("C07 remains blocked" not in plan,
            "C07 must not remain blocked after its runtime rules were settled")


FIXTURE = r"""
package com.openrsc.server.content.cleric;
import java.util.*;
import com.openrsc.server.content.cleric.effect.*;
public final class ClericSpellbookFoundationFixture {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  check(ClericSpellCatalog.SCHEMA_VERSION==3,"schema3");check(ClericSpellCatalog.getAll().size()==11,"active book eleven");
  Set<Integer> ids=new HashSet<Integer>();Set<String> keys=new HashSet<String>();
  for(ClericSpellDefinition d:ClericSpellCatalog.getAll()){
   check(ids.add(d.getStableCode())&&keys.add(d.getStableKey()),"unique stable identities");check(ClericSpellCatalog.fromKey(d.getStableKey())==d&&ClericSpellCatalog.fromCode(d.getStableCode())==d,"identity lookup");
   check(!d.affectsCaster(),"caster exclusion");
   if(d.getId().isBlessing()){
    int tier=d.getSpellTier();check(d.getWorshipLevel()==(tier==1?1:tier==2?20:40)&&d.getRadius()==tier+1,"tier unlock and radius");
    check(d.getEffectRankCount()==tier*2&&d.resolveEffectRank(163)==tier*2&&d.resolveEffectRank(0)==1,"cap raises maximum not minimum");
    for(int power=1;power<=tier*2;power++){
     ClericBlessingMagnitude m=(ClericBlessingMagnitude)ClericEffectCatalog.get(d.getId(),power).getMagnitude();
     if(d.getAlignment()==ClericAlignment.SARADOMIN)check(m.regenerationPercent()==200+50*(power-1)&&m.healingPerPulse()==(tier>=2?power:0),"Sara incremental values");
     if(d.getAlignment()==ClericAlignment.GUTHIX)check(m.poisonCleanse()==power+1&&m.restoreIntervalSeconds()==11-power&&m.defensePercent()==(tier>=2?3+2*power:0)&&m.reflectPercent()==(tier>=3?power+4:0),"Guthix incremental values");
     if(d.getAlignment()==ClericAlignment.ZAMORAK)check(m.maxDamagePercent()==3+2*power&&m.dealtDamagePercent()==(tier>=2?power+2:0)&&m.lifestealPercent()==(tier>=3?power+4:0),"Zam incremental values");
     check(ClericEffectCatalog.get(d.getId(),power).getDuration().toMilliseconds(640)==60000,"shared60s");
    }
    check(d.getResourceCosts().size()==2,"mixed god+Grace");for(ClericResourceCost cost:d.getResourceCosts())check(cost.getCount()==(tier==1?1:2),"reviewable initial private costs");
   }else check(d.getId()==ClericSpellId.UNIFY||d.getId()==ClericSpellId.GREATER_UNIFY,"only generic utilities");
  }
  for(int code:new int[]{0,2,3,4,5,6,7,8,9,10,11,-1,65535}){try{ClericSpellCatalog.fromCode(code);throw new AssertionError("retired code accepted");}catch(IllegalArgumentException expected){checks++;}}
  check(ClericSpellId.MEND.getCode()==0&&ClericSpellId.UNIFY.getCode()==1&&ClericSpellId.RESPITE.getCode()==11&&ClericSpellId.GREATER_UNIFY.getCode()==21,"old codes reserved, new code appended");
  check(ClericSigilItemId.get(ClericSigilMaterial.STONE,ClericAlignment.NEUTRAL,true).getItemId()==3300,"Grace alias stable");
  try{ClericSpellCatalog.getAll().clear();throw new AssertionError("mutable catalog");}catch(UnsupportedOperationException expected){checks++;}
  System.out.println("PASS "+checks+" composite Cleric stable catalog/magnitude/retirement checks");
 }
}
"""


def run_compiled_fixture() -> None:
    sources = sorted(str(path) for path in JAVA_ROOT.glob("*.java") if path.name != "ClericWellInteraction.java")
    sources.append(str(ROOT / "server/src/com/openrsc/server/content/PoisonPowerReduction.java"))
    with tempfile.TemporaryDirectory(prefix="cleric-foundation-") as temporary:
        temp = Path(temporary)
        fixture = temp / "com/openrsc/server/content/cleric/ClericSpellbookFoundationFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-cp", str(ROOT / "server/core.jar") + ":" + str(ROOT / "server/lib/*"), "-d", str(classes), *sources, str(fixture)], check=True)
        subprocess.run(
            ["java", "-cp", str(classes) + ":" + str(ROOT / "server/core.jar") + ":" + str(ROOT / "server/lib/*"),
             "com.openrsc.server.content.cleric.ClericSpellbookFoundationFixture"],
            check=True,
        )


def main() -> None:
    validate_source_boundaries()
    run_compiled_fixture()
    print("Cleric spellbook definition foundation checks passed")


if __name__ == "__main__":
    main()
