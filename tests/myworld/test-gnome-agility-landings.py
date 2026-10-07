#!/usr/bin/env python3
"""Execute the course plugin and check its landings against authored terrain.

Optional --native-package audits the same routes against a deployed package,
read-only. Empty walkable upper-layer tiles are deliberately rejected.
"""
import argparse
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / 'server/plugins/com/openrsc/server/plugins/authentic/skills/agility/GnomeAgilityCourse.java'
STUBS = {
 'com/openrsc/server/model/Point.java': '''package com.openrsc.server.model;
public class Point {public int x,y; private Point(int x,int y){this.x=x;this.y=y;}
public static Point location(int x,int y){return new Point(x,y);}}''',
 'com/openrsc/server/model/entity/GameObject.java': '''package com.openrsc.server.model.entity;
public class GameObject {private int id; public GameObject(int id){this.id=id;} public int getID(){return id;}}''',
 'com/openrsc/server/model/entity/npc/Npc.java': 'package com.openrsc.server.model.entity.npc; public class Npc {}',
 'com/openrsc/server/constants/NpcId.java': '''package com.openrsc.server.constants;
public enum NpcId {GNOME_TRAINER_STARTINGNET,GNOME_TRAINER_PLATFORM,GNOME_TRAINER_ENDINGNET,GNOME_TRAINER_ENTRANCE;
public int id(){return ordinal();}}''',
 'com/openrsc/server/constants/ItemId.java': '''package com.openrsc.server.constants;
public enum ItemId {TIER_1_AGILITY_POUCH;public int id(){return 2328;}}''',
 'com/openrsc/server/constants/Skill.java': '''package com.openrsc.server.constants;
public enum Skill {AGILITY; public int id(){return 16;}}''',
 'com/openrsc/server/plugins/triggers/OpLocTrigger.java': '''package com.openrsc.server.plugins.triggers;
import com.openrsc.server.model.entity.GameObject;import com.openrsc.server.model.entity.player.Player;
public interface OpLocTrigger {boolean blockOpLoc(Player p,GameObject o,String c);void onOpLoc(Player p,GameObject o,String c);}''',
 'com/openrsc/server/model/entity/player/Player.java': '''package com.openrsc.server.model.entity.player;
import com.openrsc.server.model.Point;
public class Player {public int x,y,level,id,exp,completed;public boolean nativeMode;public double MAX_FATIGUE=100;
public Player(int id,int level,boolean nativeMode){this.id=id;this.level=level;this.nativeMode=nativeMode;}
public double getFatigue(){return 0;} public void message(String s){} public void incExp(int skill,int amount,boolean flag){exp+=amount;}
public void teleport(int x,int y,boolean bubble){if(nativeMode){move(x,y,level);}else{move(x,y%944,y/944);}}
public void teleport(int x,int y,int level,boolean bubble){move(x,y,level);}
public void setLocation(Point p){if(nativeMode){move(p.x,p.y,level);}else{move(p.x,p.y%944,p.y/944);}}
private void move(int x,int y,int level){this.x=x;this.y=y;this.level=level;
System.out.println("MOVE "+nativeMode+" "+id+" "+x+" "+y+" "+level);}}
''',
 'com/openrsc/server/plugins/Functions.java': '''package com.openrsc.server.plugins;
import com.openrsc.server.model.Point;import com.openrsc.server.model.entity.npc.Npc;import com.openrsc.server.model.entity.player.Player;
public class Functions {public static class Config {public boolean WANT_FATIGUE=false;public int STOP_SKILLING_FATIGUED=0;}
public static Config config(){return new Config();}public static boolean inArray(int n,int... a){for(int i:a)if(i==n)return true;return false;}
public static void delay(int... a){} public static void teleport(Player p,int x,int y){p.teleport(x,y,false);}
public static void boundaryTeleport(Player p,Point t){p.setLocation(t);}
public static Npc ifnearvisnpc(Player p,int id,int range){return null;}
public static void npcsay(Player p,Npc n,String... a){}public static void say(Player p,Npc n,String s){}public static void mes(String s){}}
''',
 'com/openrsc/server/plugins/authentic/skills/agility/AgilityUtils.java': '''package com.openrsc.server.plugins.authentic.skills.agility;
import java.util.Set;import com.openrsc.server.model.entity.player.Player;
public class AgilityUtils {public static boolean hasDoneObstacle(Player p,int id,Set<Integer> s){return false;}
public static void completedObstacle(Player p,int id,Set<Integer> s,Integer last,int bonus,int reward){p.completed++;}}
''',
 'CourseFixture.java': '''import com.openrsc.server.plugins.authentic.skills.agility.GnomeAgilityCourse;
import com.openrsc.server.model.entity.GameObject;import com.openrsc.server.model.entity.player.Player;
public class CourseFixture {public static void main(String[] args){
int[] ids={655,647,648,650,649,653,654};int[] start={0,0,1,2,2,0,0};
for(boolean nativeMode:new boolean[]{false,true})for(int i=0;i<ids.length;i++){
Player p=new Player(ids[i],start[i],nativeMode);GameObject o=new GameObject(ids[i]);GnomeAgilityCourse c=new GnomeAgilityCourse();
if(!c.blockOpLoc(p,o,"use"))throw new AssertionError("Missing obstacle");c.onOpLoc(p,o,"use");
if(p.exp!=30||p.completed!=1)throw new AssertionError("Reward changed");
System.out.println("END "+nativeMode+" "+ids[i]+" "+p.x+" "+p.y+" "+p.level);}}}
'''
}


def execute_plugin():
    with tempfile.TemporaryDirectory(prefix='gnome-agility-test-') as temp:
        folder = Path(temp)
        sources = []
        for relative, text in STUBS.items():
            path = folder / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            sources.append(str(path))
        subprocess.run(['javac', '-d', str(folder), *sources, str(PLUGIN)], check=True)
        return subprocess.check_output(['java', '-cp', str(folder), 'CourseFixture'], text=True).splitlines()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-package', type=Path)
    args = parser.parse_args()
    archive = zipfile.ZipFile(ROOT / 'server/conf/server/defs/locs/Custom_Landscape.orsc')
    sectors = {}
    if args.native_package:
        manifest = json.loads((args.native_package / 'manifest.json').read_text())
        sectors = {(s['level'], s['sectorX'], s['sectorY']): s for s in manifest['terrainSectors'] if s['worldSpace'] == 'global'}

    def tile(x, y, level):
        if args.native_package:
            sector = sectors[(level, x // 48, y // 48)]
            assert sector['encoding'] == 'raw-layered-sector-v1', 'Review changed terrain encoding'
            raw = (args.native_package / sector['path']).read_bytes()
        else:
            raw = archive.read(f'h{level}x{x // 48 + 48}y{y // 48 + 37}')
        return struct.unpack_from('>6Bi', raw, ((x % 48) * 48 + y % 48) * 10)

    expected = {655: (692,499,0),647: (692,504,1),648: (691,507,2),650: (685,508,2),
                649: (683,506,0),653: (683,501,0),654: (683,494,0)}
    lines = execute_plugin()
    counts = {'false':0,'true':0}
    for line in lines:
        kind, mode, obstacle, x, y, level = line.split()
        obstacle,x,y,level = map(int,(obstacle,x,y,level))
        if kind == 'END':
            assert (x,y,level) == expected[obstacle], line
            counts[mode] += 1
        else:
            assert 681 <= x <= 695 and 494 <= y <= 508 and level in (0,1,2), 'Outside actual course: ' + line
            t = tile(x,y,level)
            if level:
                assert t[2] == 3, 'Must land on visible wooden platform, not walkable void: ' + line
            else:
                assert t[1] != 0, 'Missing ground surface: ' + line
    assert counts == {'false':7,'true':7}, counts
    # Negative control proves this audit rejects the old tree-climb landing.
    assert tile(693,506,2)[2] != 3, 'Revisit platform audit if authored course changes'
    print('PASS all seven obstacles in legacy and native modes; every intermediate/final landing has actual course terrain.')
    print('PASS old tree-climb invisible tile rejected; explicit level transitions and 30 XP/completion behavior preserved.')


if __name__ == '__main__':
    main()
