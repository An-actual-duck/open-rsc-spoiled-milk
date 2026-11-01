#!/usr/bin/env python3
"""Release profile/client pairing and production default/headroom; no server start."""
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER_CONTRACT = ROOT / "server/src/com/openrsc/server/net/rsc/NpcCapacityContract.java"
CLIENT_CONTRACT = ROOT / "Client_Base/src/orsc/NpcCapacityContract.java"
SERVER_SPELL_CONTRACT = ROOT / "server/src/com/openrsc/server/net/rsc/ClericBlessingContract.java"
CLIENT_SPELL_CONTRACT = ROOT / "Client_Base/src/orsc/ClericBlessingContract.java"
contract = json.loads((ROOT / "runtime-contract/npc-capacity-v1.json").read_text())
constants = contract["constants"]
first_expanded_version = constants["FIRST_EXPANDED_CLIENT_VERSION"]
# The NPC contract is a capability floor, not the release's current wire version.
spell_contract = (ROOT / "tools/protocol/ClericBlessingContract.java").read_text()
expected_version = int(re.search(r"CLIENT_VERSION\s*=\s*(\d+)\s*;", spell_contract).group(1))
expected_capacity = constants["TARGET_CAPACITY"]
expected_reserve = constants["TRANSIENT_HEADROOM"]


def value(source, key):
    matches = re.findall(r"^\s*" + re.escape(key) + r":\s*([^#\n]+)", source, re.M)
    if len(matches) > 1:
        raise AssertionError("Duplicate profile setting: " + key)
    return matches[0].strip() if matches else None


arguments = []
for name in ("myworld.conf", "myworld-host.conf"):
    source = (ROOT / "server" / name).read_text()
    version = int(value(source, "client_version"))
    if version != expected_version:
        raise AssertionError(name + " must use expanded client version " + str(expected_version))
    if value(source, "enforce_custom_client_version") != "true":
        raise AssertionError(name + " must enforce the paired client version")
    configured = value(source, contract["capacityConfigurationKey"])
    arguments.extend((name, str(version), configured or "default"))

client_config = (ROOT / "Client_Base/src/orsc/Config.java").read_text()
if not re.search(r"CLIENT_VERSION\s*=\s*ClericBlessingContract\.CLIENT_VERSION\s*;", client_config):
    raise AssertionError("Maintained client version must use the current paired spell contract")

HARNESS = r"""
import com.openrsc.server.net.rsc.NpcCapacityContract;
public final class NpcCapacityProfileFixture {
    public static void main(String[] args) {
        int expectedVersion=Integer.parseInt(args[0]);
        int expectedCapacity=Integer.parseInt(args[1]);
        int reserve=Integer.parseInt(args[2]);
        int firstExpanded=Integer.parseInt(args[3]);
        if(com.openrsc.server.net.rsc.ClericBlessingContract.CLIENT_VERSION != expectedVersion
                || orsc.ClericBlessingContract.CLIENT_VERSION != expectedVersion
                || NpcCapacityContract.FIRST_EXPANDED_CLIENT_VERSION != firstExpanded
                || orsc.NpcCapacityContract.FIRST_EXPANDED_CLIENT_VERSION != firstExpanded
                || NpcCapacityContract.TARGET_CAPACITY != expectedCapacity
                || NpcCapacityContract.TRANSIENT_HEADROOM != reserve)
            throw new AssertionError("Generated server/client release contracts differ");
        if (!NpcCapacityContract.expanded(firstExpanded)
                || NpcCapacityContract.expanded(firstExpanded-1)
                || NpcCapacityContract.supports(firstExpanded-1,expectedCapacity)
                || NpcCapacityContract.supportsWorld(firstExpanded-1,expectedVersion,expectedCapacity))
            throw new AssertionError("Expanded capacity floor must reject older incompatible clients");
        for(int i=4;i<args.length;i+=3) {
            String profile=args[i];int version=Integer.parseInt(args[i+1]);
            int capacity=args[i+2].equals("default")
                ? NpcCapacityContract.defaultCapacity(version) : Integer.parseInt(args[i+2]);
            if(!NpcCapacityContract.expanded(version) || capacity != expectedCapacity)
                throw new AssertionError(profile+" must resolve the expanded registry default");
            NpcCapacityContract.requireCapacity(version,capacity);
            NpcCapacityContract.requireStartupHeadroom(version,reserve);
            try {
                NpcCapacityContract.requireStartupHeadroom(version,reserve-1);
                throw new AssertionError(profile+" accepted less than required transient reserve");
            } catch(IllegalStateException expected) {}
            System.out.println("PASS "+profile+": paired version="+version+" capacity="+capacity+" reserve="+reserve);
        }
    }
}
"""
with tempfile.TemporaryDirectory(prefix="npc-capacity-profiles-") as directory:
    source = Path(directory) / "NpcCapacityProfileFixture.java"
    source.write_text(HARNESS)
    subprocess.run(["javac", "-d", directory, str(SERVER_CONTRACT), str(CLIENT_CONTRACT),
                    str(SERVER_SPELL_CONTRACT), str(CLIENT_SPELL_CONTRACT), str(source)], check=True)
    subprocess.run(["java", "-Xmx128m", "-cp", directory, "NpcCapacityProfileFixture",
                    str(expected_version), str(expected_capacity), str(expected_reserve),
                    str(first_expanded_version), *arguments], check=True)
