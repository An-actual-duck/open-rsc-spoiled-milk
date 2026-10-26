#!/usr/bin/env python3
"""Compile the production shared registry against minimal entity identity fixtures."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HARNESS = r"""
import com.openrsc.server.util.EntityList;
import com.openrsc.server.util.PlayerList;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.Entity;
import java.util.*;
public class EntityListSafety {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run(String name, Runnable test) {
        try { test.run(); System.out.println("PASS " + name); }
        catch (Throwable error) { failures++; System.out.println("FAIL " + name + ": " + error); }
    }
    static int failures;
    public static void main(String[] args) {
        run("repeated removal and full recovery", () -> {
            EntityList<Entity> list = new EntityList<>(3);
            Entity first = new Entity(); list.add(first); list.remove(0); list.remove(0);
            for (int i=0;i<3;i++) { Entity e=new Entity(); list.add(e); check(e.getIndex()==i,"duplicate free index"); }
            check(list.size()==3 && list.getAvailableCapacity()==0,"pool/map/array agreement");
            try { list.add(new Entity()); throw new AssertionError("overfull accepted"); } catch (IllegalStateException expected) {}
        });
        run("stale object cannot remove replacement", () -> {
            EntityList<Entity> list = new EntityList<>(2);
            Entity old=new Entity(); list.add(old); int index=old.getIndex(); list.remove(old);
            Entity replacement=new Entity(); list.add(replacement); old.setIndex(index); list.remove(old);
            check(list.get(index)==replacement && list.size()==1,"replacement erased");
        });
        run("bounded lookup/removal", () -> {
            EntityList<Entity> list = new EntityList<>(2);
            check(list.get(-1)==null && list.get(2)==null && list.get(Integer.MAX_VALUE)==null,"lookup bounds");
            check(list.remove(-1)==null && list.remove(2)==null,"removal bounds");
            check(list.getAvailableCapacity()==2,"invalid index returned to pool");
        });
        run("duplicate/null add is nonmutating", () -> {
            EntityList<Entity> list = new EntityList<>(2); Entity e=new Entity(); list.add(e);
            check(!list.add(e) && list.size()==1 && e.getIndex()==0,"duplicate registration");
            try { list.add(null); throw new AssertionError("null accepted"); } catch (NullPointerException expected) {}
            check(list.size()==1 && list.getAvailableCapacity()==1,"failed add consumed slot");
        });
        run("snapshot order and live mutation", () -> {
            EntityList<Entity> list=new EntityList<>(4); Entity a=new Entity(), b=new Entity(), c=new Entity();
            list.add(a); list.add(b); Iterator<Entity> snapshot=list.iterator(); list.remove(b); list.add(c);
            check(snapshot.next()==a && snapshot.next()==b && !snapshot.hasNext(),"snapshot semantics");
            List<Entity> seen=new ArrayList<>(); Entity d=new Entity();
            list.forEachLive(e -> { seen.add(e); if(e==a) {list.remove(c); list.add(d);} });
            check(seen.equals(Arrays.asList(a,d)),"ascending live higher-slot mutation visibility");
        });
        run("clear resets entity handles", () -> {
            EntityList<Entity> list=new EntityList<>(2); Entity e=new Entity(); list.add(e); list.clear();
            check(e.getIndex()==-1 && list.isEmpty() && list.getAvailableCapacity()==2,"clear ownership");
        });
        run("shared player hash ownership", () -> {
            PlayerList list=new PlayerList(2); Player old=new Player(7), duplicate=new Player(7);
            check(list.add(old) && !list.add(old),"duplicate object accepted");
            list.remove(old); Player replacement=new Player(7); list.add(replacement);
            old.setIndex(replacement.getIndex()); list.remove(old);
            check(list.getPlayerByHash(7)==replacement && list.contains(replacement),"stale player erased replacement");
            Player equal=new Player(7); list.add(equal); list.remove(replacement);
            check(list.getPlayerByHash(7)==equal,"equal stale hash owner erased replacement");
            check(list.removePlayerByHash(7)==equal && !list.contains(equal),"hash removal left registry occupied");
            list.add(replacement);
            list.clear(); check(list.getPlayerByHash(7)==null && replacement.getIndex()==-1,"player clear disagreement");
        });
        if(failures!=0) throw new AssertionError(failures+" registry scenarios failed");
    }
}
"""
with tempfile.TemporaryDirectory(prefix="npc-registry-") as temp:
    tmp = Path(temp)
    stub = tmp / "com/openrsc/server/model/entity/Entity.java"
    stub.parent.mkdir(parents=True)
    stub.write_text("package com.openrsc.server.model.entity; public class Entity { private int index=-1; public int getIndex(){return index;} public void setIndex(int value){index=value;} }")
    player = tmp / "com/openrsc/server/model/entity/player/Player.java"
    player.parent.mkdir(parents=True)
    player.write_text("package com.openrsc.server.model.entity.player; import com.openrsc.server.model.entity.Entity; public class Player extends Entity { private final long hash; public Player(long value){hash=value;} public long getUsernameHash(){return hash;} @Override public boolean equals(Object o){return o instanceof Player && ((Player)o).hash==hash;} }")
    harness = tmp / "EntityListSafety.java"
    harness.write_text(HARNESS)
    cp = str(ROOT / "server/lib/guava-30.1.1-jre.jar")
    subprocess.run(["javac", "-cp", cp, "-d", temp, str(stub), str(player), str(ROOT / "server/src/com/openrsc/server/util/EntityList.java"), str(ROOT / "server/src/com/openrsc/server/util/PlayerList.java"), str(harness)], check=True)
    subprocess.run(["java", "-cp", temp + ":" + cp, "EntityListSafety"], check=True)
