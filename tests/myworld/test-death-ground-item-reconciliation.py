#!/usr/bin/env python3
"""Run production epoch and extracted, unchanged ground packet bodies in Java."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc"


def method(source, signature):
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def main():
    packet = (CLIENT / "PacketHandler.java").read_text()
    client = (CLIENT / "mudclient.java").read_text()
    begin = method(client, "public void beginAreaLoad()")
    assert "groundItemAreaEpoch.begin()" in begin
    assert "this.groundItemCount = this.groundItemAreaEpoch.cancel();" in client
    scope = method(client, "private void resetLayeredSceneIdentityCaches()")
    assert "beginAreaLoad();" in scope and "groundItemCount = 0;" in scope
    region = method(client, "public final boolean loadNextRegion(")
    assert region.index("deathScreenTimeout != 0") < region.index("this.loadingArea = var3")
    assert "resetGroundItemsForHardAreaLoad(baseDX, baseDZ)" in region
    assert region.index("reapplyCompleteSceneBaselineAfterRegionLoad();") < region.index("refreshGroundItemSupportHeights();")
    # All existing packet compaction paths operate on the sole epoch list.
    assert "mc.setGroundItemCount(newIndex);" in method(packet, "private void generateCounts(")
    methods = "\n".join(method(packet, sig) for sig in (
        "private void drawGroundItems(", "private void removeGroundItemsAt("))
    counts = method(packet, "private void generateCounts(")
    # Execute the exact ground portion; objects/walls are independent stores.
    methods += "\n" + counts[:counts.index("\n\t\t\t// Game Object Counts")] + "\n}}"
    heights = method(client, "private void refreshGroundItemSupportHeights()")
    heights = heights.replace("this.", "mc.").replace("EntityHandler", "com.openrsc.client.entityhandling.EntityHandler")
    for original, fixture in (("groundItemCount", "count"), ("groundItemHeight", "h"), ("groundItemX", "x"), ("groundItemZ", "z")):
        heights = heights.replace(original, fixture)
    methods += "\n" + heights
    harness = r'''
package orsc;
public final class DeathGroundTest {
 static final class Config { static boolean S_WANT_BANK_NOTES=true; }
 static final class Buffer {
  byte[] bytes; int packetEnd;
  void packet(int... values){bytes=new byte[values.length];for(int i=0;i<values.length;i++)bytes[i]=(byte)values[i];packetEnd=0;}
  int getUnsignedByte(){return bytes[packetEnd++]&255;}
  int getByte(){return bytes[packetEnd++];}
  int getShort(){return (getUnsignedByte()<<8)|getUnsignedByte();}
  int get16_V2(){int n=getShort();return n>32767?n-65536:n;}
 }
 static final class Client {
  int count, px, pz, objects, ox, oz; int[] x=new int[32],z=new int[32],id=new int[32],h=new int[32]; boolean[] note=new boolean[32];
  int getLocalPlayerX(){return px;} int getLocalPlayerZ(){return pz;}
  int getGroundItemCount(){return count;} void setGroundItemCount(int n){count=n;}
  int getGroundItemX(int i){return x[i];} void setGroundItemX(int i,int n){x[i]=n;}
  int getGroundItemZ(int i){return z[i];} void setGroundItemZ(int i,int n){z[i]=n;}
  int getGroundItemID(int i){return id[i];} void setGroundItemID(int i,int n){id[i]=n;}
  int getGroundItemHeight(int i){return h[i];} void setGroundItemHeight(int i,int n){h[i]=n;}
  boolean getGroundItemNoted(int i){return note[i];} void setGroundItemNoted(int i,boolean n){note[i]=n;}
  int getGameObjectInstanceCount(){return objects;} int getGameObjectInstanceX(int i){return ox;}
  int getGameObjectInstanceZ(int i){return oz;} int getGameObjectInstanceID(int i){return 0;}
 }
 final Client mc=new Client(); final Buffer packetsIncoming=new Buffer(); final GroundItemAreaEpoch epoch=new GroundItemAreaEpoch();
 void packet(int... b){packetsIncoming.packet(b);drawGroundItems(b.length);}
 void begin(){mc.count=epoch.begin();}
 void finish(int dx,int dz){mc.count=epoch.complete(mc.count,mc.x,mc.z,mc.id,mc.h,mc.note,dx,dz,96);}
 void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 void tests(){
  // Old area disappears immediately; repeated death-deferred player packets
  // leave destination updates in the OLD base's frame until terrain completes.
  mc.px=5;mc.pz=6;packet(0,7,0,0,0);begin();check(mc.count==0,"old set discarded");
  mc.px=205;mc.pz=-94;packet(0,14,0,0,1, 0,14,0,0,1);
  check(mc.count==2,"duplicates preserved before deferred load");
  packet(128,14,0,0,1);check(mc.count==1,"remove one duplicate");
  finish(200,-100);check(mc.count==1&&mc.x[0]==5&&mc.z[0]==6&&mc.note[0],"origin rebase and notes");
  mc.objects=1;mc.ox=5;mc.oz=6;refreshGroundItemSupportHeights();check(mc.h[0]==23,"destination baseline support");
  mc.objects=0;refreshGroundItemSupportHeights();check(mc.h[0]==0,"old support cleared");
  finish(0,0);check(mc.count==0,"epoch one shot; unannounced hard/plane load clears");
  // Normal ordering: completion first, subsequent packets append normally.
  begin();finish(0,0);mc.px=5;mc.pz=6;packet(0,14,0,0,0);check(mc.count==1,"normal ordering");
  // Same-region hard load must not retain the preceding set or collapse drops.
  begin();packet(0,14,0,0,0, 0,14,0,0,0);finish(0,0);check(mc.count==2,"same-region replacement");
  // Actual opcode99 chunk body removes all in chunk, leaves another chunk.
  begin();mc.px=205;mc.pz=-94;packet(0,14,0,0,0, 0,15,16,0,0);
  packet(255,0,0,0);finish(200,-100);check(mc.count==1&&mc.id[0]==15&&mc.x[0]==21,"chunk unload before rebase");
  begin();mc.px=205;mc.pz=-94;packet(0,14,0,0,0, 0,15,16,0,0);
  packetsIncoming.packet(0,0,0,0);generateCounts(5);finish(200,-100);
  check(mc.count==1&&mc.id[0]==15&&mc.x[0]==21,"opcode211 chunk before rebase");
  // Walk away/back: old epoch cannot be replayed; fresh announcements work.
  mc.px=21;mc.pz=6;packet(255,0,0,0);check(mc.count==0,"unload after load");
  packet(0,15,0,0,0);check(mc.count==1,"return announcement once");
  // Superseding area/plane/world transition discards earlier destination.
  begin();packet(0,17,0,0,0);begin();packet(0,18,0,0,0);finish(0,0);
  check(mc.count==1&&mc.id[0]==18,"superseded destination");
  begin();packet(0,19,0,0,0);mc.count=epoch.cancel();finish(0,0);check(mc.count==0,"logout/cancel cannot replay");
  // Clip only AFTER translation: destination may be outside the old window.
  begin();mc.px=1005;mc.pz=1006;packet(0,20,0,0,0, 0,21,100,0,0);finish(1000,1000);
  check(mc.count==1&&mc.x[0]==5&&mc.z[0]==6,"new origin bounds");
  System.out.println("PASS: Java production epoch + actual add/remove/chunk packet bodies");
 }
 public static void main(String[] args){new DeathGroundTest().tests();}
 METHODS
}
'''.replace("METHODS", methods)
    with tempfile.TemporaryDirectory(prefix="death-ground-test-") as temp:
        temp = Path(temp)
        fixture = temp / "DeathGroundTest.java"
        fixture.write_text(harness)
        stub = temp / "com/openrsc/client/entityhandling/EntityHandler.java"
        stub.parent.mkdir(parents=True)
        stub.write_text("package com.openrsc.client.entityhandling; public class EntityHandler { public static EntityHandler getObjectDef(int i){return new EntityHandler();} public int getGroundItemVar(){return 23;} }")
        env = dict(os.environ, JAVA_TOOL_OPTIONS="-Xmx768m")
        subprocess.run(["javac", "-source", "8", "-target", "8", "-d", str(temp), str(CLIENT / "GroundItemAreaEpoch.java"), str(fixture), str(stub)], check=True, env=env)
        subprocess.run(["java", "-Xmx768m", "-cp", str(temp), "orsc.DeathGroundTest"], check=True, env=env)


if __name__ == "__main__":
    main()
