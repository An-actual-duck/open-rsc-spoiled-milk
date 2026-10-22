package com.openrsc.server.io;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.DoorDef;
import com.openrsc.client.model.Sector;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import orsc.graphics.three.World;
import orsc.graphics.three.RSModel;

/** Offline native geometry, structural support and source-definition parity. */
public final class StoneDoorSurroundTest {
	private static int checks;
	private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
	private static Field field(Class<?> type, String name) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); return f; }
	private static Method method(Class<?> type, String name, Class<?>... args) throws Exception { Method m=type.getDeclaredMethod(name,args); m.setAccessible(true); return m; }
	private static String value(Element row, String key) { NodeList list=row.getElementsByTagName(key); return list.getLength()==0 ? "0" : list.item(0).getTextContent(); }
	private static int number(Element row,String key) { return Integer.parseInt(value(row,key)); }
	public static void main(String[] args) throws Exception {
		EntityHandler.load(true);
		NodeList rows=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(args[0])).getDocumentElement().getElementsByTagName("DoorDef");
		check(rows.getLength()==EntityHandler.doorCount(),"Client/server definition counts");
		List<Integer> headers=new ArrayList<Integer>();
		for(int id=0;id<rows.getLength();id++) {
			Element row=(Element)rows.item(id); DoorDef client=EntityHandler.getDoorDef(id);
			check(client.getName().equals(value(row,"name"))&&client.getCommand1().equals(value(row,"command1"))
				&&client.getCommand2().equals(value(row,"command2")),"identity/action parity "+id);
			check(client.getWallObjectHeight()==number(row,"modelVar1"),"height parity "+id);
			check(client.getDoorOpeningHeight()==number(row,"doorOpeningHeight"),"opening parity "+id);
			check(client.getDoorSurroundFor()==number(row,"doorSurroundFor"),"association parity "+id);
			check(client.getDoorType()==number(row,"doorType")&&client.getUnknown()==number(row,"unknown"),"collision parity "+id);
			check(client.getModelVar2()==number(row,"modelVar2")&&client.getModelVar3()==number(row,"modelVar3"),"material parity "+id);
			if(client.getDoorOpeningHeight()>0) {
				headers.add(id); DoorDef source=EntityHandler.getDoorDef(client.getDoorSurroundFor());
				check(id+1<=255,"native byte carrier");
				check(client.getWallObjectHeight()==275&&client.getDoorOpeningHeight()==192,"bounded stone dimensions");
				check(source.getWallObjectHeight()==client.getWallObjectHeight()&&source.getModelVar2()==client.getModelVar2(),"source wall dimensions/material");
				check(client.getDoorType()==0&&client.getUnknown()==0,"passable visible header");
				check(client.getCommand1().equals("WalkTo")&&client.getCommand2().equals("Examine"),"noninteractive header");
			}
		}
		check(headers.size()==2,"two explicit source wall associations");
		for(int bad:new int[]{-1,275,276,Integer.MAX_VALUE}) {
			boolean refused=false;
			try{new DoorDef("test","","WalkTo","Examine",0,0,275,2,2,0,bad,7);}
			catch(IllegalArgumentException expected){refused=true;}
			check(refused,"malformed client opening refused "+bad);
		}
		com.openrsc.server.external.DoorDef[] server=new com.openrsc.server.external.DoorDef[rows.getLength()];
		for(int id=0;id<server.length;id++) {
			Element row=(Element)rows.item(id); com.openrsc.server.external.DoorDef d=new com.openrsc.server.external.DoorDef();
			d.name=value(row,"name"); d.command1=value(row,"command1"); d.command2=value(row,"command2");
			d.modelVar1=number(row,"modelVar1"); d.modelVar2=number(row,"modelVar2"); d.modelVar3=number(row,"modelVar3");
			d.doorType=number(row,"doorType"); d.unknown=number(row,"unknown");
			d.doorOpeningHeight=number(row,"doorOpeningHeight"); d.doorSurroundFor=number(row,"doorSurroundFor"); server[id]=d;
		}
		for(com.openrsc.server.external.DoorDef d:server)d.validateDoorSurround(server);
		for(int id:headers) {
			com.openrsc.server.external.DoorDef d=server[id];
			for(int bad:new int[]{-1,275,276,Integer.MAX_VALUE}) {
				d.doorOpeningHeight=bad; boolean refused=false;
				try{d.validateDoorSurround(server);}catch(IllegalArgumentException expected){refused=true;}
				check(refused,"malformed opening refused "+bad);
			}
			d.doorOpeningHeight=192; int sourceId=d.doorSurroundFor;
			d.doorSurroundFor=server.length; boolean refused=false;
			try{d.validateDoorSurround(server);}catch(IllegalArgumentException expected){refused=true;}
			check(refused,"missing source refused"); d.doorSurroundFor=sourceId;
			d.doorType=1; refused=false;
			try{d.validateDoorSurround(server);}catch(IllegalArgumentException expected){refused=true;}
			check(refused,"blocking header refused"); d.doorType=0;
			d.modelVar2=0; refused=false;
			try{d.validateDoorSurround(server);}catch(IllegalArgumentException expected){refused=true;}
			check(refused,"mismatched source material refused"); d.modelVar2=2;
		}
		// Allocate the pure geometry owner without opening landscapes, sockets or accounts.
		Class<?> unsafeType=Class.forName("sun.misc.Unsafe"); Object unsafe=field(unsafeType,"theUnsafe").get(null);
		World world=(World)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,World.class);
		Class<?> sourceType=Class.forName("orsc.graphics.three.World$TerrainModelInputSource");
		Constructor<?> sourceCtor=sourceType.getDeclaredConstructor(Sector[].class); sourceCtor.setAccessible(true);
		int side=200; int[][] base=new int[side][side]; for(int[] line:base)Arrays.fill(line,17);
		Sector[] sectors=new Sector[16]; for(int i=0;i<sectors.length;i++)sectors[i]=Sector.blankLoaded();
		Object source=sourceCtor.newInstance((Object)sectors);
		Method add=method(World.class,"addWallSegmentInput",List.class,sourceType,int[][].class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class);
		field(World.class,"modelLandscapeGrid").set(world,new RSModel[1024]);
		field(World.class,"tileElevationCache").set(world,base);
		field(World.class,"collisionFlags").set(world,new int[side][side]);
		Method insert=method(World.class,"insertWallIntoModel",int.class,RSModel.class,int.class,int.class,int.class,int.class,int.class);
		for(int id:headers) for(int kind=0;kind<4;kind++) {
			List<Object> segments=new ArrayList<Object>();
			add.invoke(world,segments,source,base,id+1,kind,2,2,3,2,2,3);
			int[] xyz=(int[])field(segments.get(0).getClass(),"vertexCoords").get(segments.get(0));
			check(xyz[1]==-209&&xyz[4]==-292&&xyz[7]==-292&&xyz[10]==-209,"static header opening/top kind "+kind);
			RSModel model=new RSModel(4,1); insert.invoke(world,id,model,3,2,2,-14584,3);
			int[] ys=(int[])field(RSModel.class,"vertY").get(model);
			check(ys[0]==-209&&ys[1]==-292&&ys[2]==-292&&ys[3]==-209,"dynamic header opening/top");
			world.applyWallToCollisionFlags(id,2,2,kind);
			check(((int[][])field(World.class,"collisionFlags").get(world))[2][2]==0,"header collision remains empty");
		}
		Class<?> roofType=Class.forName("orsc.graphics.three.World$RoofElevationWorkspace");
		Constructor<?> roofCtor=roofType.getDeclaredConstructor(int[][].class); roofCtor.setAccessible(true);
		Method support=method(World.class,"applyWallToElevationCache",roofType,int.class,int.class,int.class,int.class,int.class);
		Method clear=method(roofType,"clearRoofMarkers");
		for(int id:headers) {
			int[][] levels=new int[side][side]; Object roof=roofCtor.newInstance((Object)levels);
			support.invoke(world,roof,id,2,2,3,2); support.invoke(world,roof,id,3,2,4,2); clear.invoke(roof);
			check(levels[2][2]==275&&levels[3][2]==275&&levels[4][2]==275,"double opening center support neither missing nor doubled");
		}
		DoorDef ordinary=EntityHandler.getDoorDef(7); List<Object> ordinarySegments=new ArrayList<Object>();
		add.invoke(world,ordinarySegments,source,base,8,0,2,2,3,2,2,3);
		int[] unchanged=(int[])field(ordinarySegments.get(0).getClass(),"vertexCoords").get(ordinarySegments.get(0));
		check(ordinary.getDoorOpeningHeight()==0&&unchanged[1]==-17&&unchanged[10]==-17,"ordinary geometry unchanged");
		System.out.println("PASS "+checks+" stone surround native definition/geometry/support/collision checks");
	}
}
