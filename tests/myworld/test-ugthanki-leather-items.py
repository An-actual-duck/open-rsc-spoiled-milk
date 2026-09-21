#!/usr/bin/env python3
"""Check the seven Ugthanki item definitions against the built client, including notes."""
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def main():
    entries = json.loads((ROOT / "server/conf/server/defs/ItemDefsCustom.json").read_text())["items"]
    defs = {entry["id"]: entry for entry in entries}
    assert len(entries) == len(defs), "Duplicate custom item IDs"
    constants = (ROOT / "server/src/com/openrsc/server/constants/custom/MyWorldItemId.java").read_text()
    assertions = []
    sprites = [69, 69, 5, 17, 223, 590, 7]
    for offset, item_id in enumerate(range(3392, 3399)):
        entry = defs[item_id]
        assert len(re.findall(rf"= {item_id};", constants)) == 1
        assert entry["isUntradable"] == 0 and entry["isStackable"] == 0 and entry["isNoteable"] == 1
        assert entry["requiredLevel"] == 0 and entry["requiredSkillID"] == -1
        assert entry["isWearable"] == int(offset >= 2)
        if offset >= 2:
            assert entry["wearSlot"] == [5,8,9,7,6][offset-2]
            assert [entry[k] for k in ["meleeDefense","rangedDefense","magicDefense"]] == [
                [4,0,0],[7,1,0],[7,1,0],[9,1,1],[13,1,1]][offset-2]
        assertions.append('check(%d, %s, %s, %d, %d, %d, %d, %s);' % (
            item_id, json.dumps(entry["name"]), json.dumps(entry["description"]),
            entry["basePrice"], sprites[offset], 0xB99A70 if offset == 0 else 0x947654,
            entry["wearableID"], "true" if offset >= 2 else "false"))
    source = '''
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
public final class UgthankiItemFixture {
  public static void main(String[] args) {
    orsc.Config.S_WANT_BANK_NOTES = true;
    orsc.Config.S_WANT_CERT_AS_NOTES = true;
    EntityHandler.load(true);
    ASSERTIONS
  }
  private static void check(int id, String name, String description, int price,
      int sprite, int mask, int wearable, boolean wieldable) {
    ItemDef d = EntityHandler.getItemDef(id);
    if (d == null || d.id != id || !name.equals(d.getName())
        || !description.equals(d.getDescription()) || d.basePrice != price
        || d.spriteID != sprite || !("items:" + sprite).equals(d.spriteLocation)
        || d.getPictureMask() != mask || d.wieldable != wieldable || d.wearableID != wearable
        || d.stackable || d.untradeable || d.membersItem || !d.noteable)
      throw new AssertionError("Ugthanki client/server mismatch: " + id);
    ItemDef note = EntityHandler.getItemDef(id, true);
    if (note == null || !note.stackable || !name.equals(note.getName()))
      throw new AssertionError("Missing note: " + id);
  }
}
'''.replace("ASSERTIONS", "\n".join(assertions))
    jar = ROOT / "Client_Base/Open_RSC_Client.jar"
    assert jar.exists(), "Build the client first"
    with tempfile.TemporaryDirectory(prefix="ugthanki-items-") as directory:
        java = Path(directory) / "UgthankiItemFixture.java"
        java.write_text(source)
        subprocess.run(["javac", "-cp", str(jar), "-d", directory, str(java)], check=True)
        subprocess.run(["java", "-cp", f"{directory}:{jar}", "UgthankiItemFixture"],
                       cwd=ROOT / "Client_Base", check=True)
    print("PASS: Ugthanki server/client identities, tiers, icons, flags and note forms")

if __name__ == "__main__":
    main()
