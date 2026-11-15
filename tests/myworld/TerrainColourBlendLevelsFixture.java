package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.TileDef;
import com.openrsc.client.model.Sector;
import com.openrsc.client.model.Tile;
import java.lang.reflect.*;
import java.util.List;
import orsc.graphics.three.World;
import orsc.util.GenUtil;

/** Actual terrain producers, with a synthetic palette and no map, server or GPU. */
public final class TerrainColourBlendLevelsFixture {
    static Field field(Class<?> owner,String name)throws Exception {
        Field result=owner.getDeclaredField(name);result.setAccessible(true);return result;
    }
    static Method method(String name,Class<?>... types)throws Exception {
        Method result=World.class.getDeclaredMethod(name,types);result.setAccessible(true);return result;
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Tile tile(Sector[] sectors,int x,int z)throws Exception {
        Sector sector=(Sector)method("sectorForLocalTile",Sector[].class,int.class,int.class).invoke(null,sectors,x,z);
        return sector.getTile(Math.floorMod(x,48),Math.floorMod(z,48));
    }
    static int number(Object value,String name)throws Exception{return field(value.getClass(),name).getInt(value);}
    static boolean flag(Object value,String name)throws Exception{return field(value.getClass(),name).getBoolean(value);}
    static Object blend(World world,Class<?> sourceType,Object source,int plane,int x,int z)throws Exception {
        return method("terrainVertexBlendInput",int.class,sourceType,int.class,int.class).invoke(world,plane,source,x,z);
    }
    static Object face(World world,Class<?> sourceType,Object source,int plane,int x,int z)throws Exception {
        Object[] faces=(Object[])method("collectTerrainTileFaceInputs",int.class,sourceType).invoke(world,plane,source);
        int side=field(World.class,"LOCAL_FACE_TILE_COUNT").getInt(null);return faces[x*side+z];
    }
    static void colours(Sector[] sectors,int x,int z)throws Exception {
        for(int dx=-1;dx<=0;dx++)for(int dz=-1;dz<=0;dz++){
            Tile tile=tile(sectors,x+dx,z+dz);tile.groundTexture=(byte)(dx<0?0:1);tile.groundOverlay=0;
        }
    }
    public static void main(String[] args)throws Exception {
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Object unsafe=field(unsafeType,"theUnsafe").get(null);
        World world=(World)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,World.class);
        int[] palette=new int[256];palette[0]=GenUtil.colorToResource(248,0,0);palette[1]=GenUtil.colorToResource(0,0,248);
        field(World.class,"colorToResource").set(world,palette);
        Sector[] sectors=new Sector[field(World.class,"ACTIVE_SECTION_COUNT").getInt(null)];
        for(int n=0;n<sectors.length;n++)sectors[n]=Sector.blankLoaded();
        Class<?> sourceType=Class.forName("orsc.graphics.three.World$TerrainModelInputSource");
        Constructor<?> ctor=sourceType.getDeclaredConstructor(Sector[].class);ctor.setAccessible(true);Object source=ctor.newInstance((Object)sectors);
        @SuppressWarnings("unchecked") List<TileDef> definitions=(List<TileDef>)field(EntityHandler.class,"tiles").get(null);
        definitions.clear();definitions.add(new TileDef(5,2,0));

        // Plane 3 is logical level -1; the other ground-style signed levels use plane 0.
        for(int x:new int[]{24,48}){
            colours(sectors,x,24);
            for(int plane:new int[]{0,3}){
                Object mixed=blend(world,sourceType,source,plane,x,24);
                check(number(mixed,"strength")==255,"Missing ground blend on plane "+plane);
                check(number(mixed,"color")==0x7c007c,"Wrong shared-corner average/seam on plane "+plane);
                check(flag(face(world,sourceType,source,plane,x,24),"terrainVariationEligible"),"Shader blend mask disabled on plane "+plane);
            }
            for(int plane:new int[]{1,2}){
                check(number(blend(world,sourceType,source,plane,x,24),"strength")==0,"Upper void acquired ground blending");
                check(!flag(face(world,sourceType,source,plane,x,24),"terrainVariationEligible"),"Upper void acquired shader mask");
            }
        }
        tile(sectors,24,24).groundOverlay=(byte)255;
        check(number(blend(world,sourceType,source,3,24,24),"color")==0x7c007c,"Blocked base ground excluded from blend");
        Object blocked=face(world,sourceType,source,3,24,24);
        check(flag(blocked,"terrainVariationEligible")&&flag(blocked,"collisionFullBlock"),"Blend changed blocking-ground semantics");

        tile(sectors,24,24).groundOverlay=1;
        check(!flag(face(world,sourceType,source,3,24,24),"terrainVariationEligible"),"Texture received ground blending");
        check(number(blend(world,sourceType,source,3,24,24),"color")==0xa50052,"Hidden base colour under a texture affected adjacent ground");
        for(int dx=-1;dx<=0;dx++)for(int dz=-1;dz<=0;dz++)tile(sectors,24+dx,24+dz).groundOverlay=1;
        check(number(blend(world,sourceType,source,3,24,24),"strength")==0,"Textures contributed to ground blend");
        for(int dx=-1;dx<=0;dx++)for(int dz=-1;dz<=0;dz++){
            Tile tile=tile(sectors,24+dx,24+dz);tile.groundOverlay=0;tile.groundTexture=0;
        }
        check(number(blend(world,sourceType,source,3,24,24),"strength")==0,"Uniform ground gained an unnecessary gradient");
        System.out.println("PASS actual terrain corner colours and face masks: overworld/underground parity, sector seam, upper voids, blocking ground, textured exclusions and uniform ground");
    }
}
