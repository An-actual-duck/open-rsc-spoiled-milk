package orsc;
import com.openrsc.client.model.Sector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import orsc.graphics.three.*;

/** Positive roof metadata remains authoritative with or without roof triangles. */
public final class GeometryShadowRoofMaskFixture {
	static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
	static Renderer3DWorldChunkFrame.ChunkMesh chunk(int plane,int originX,int originZ,long bits,boolean visibleRoof,long signature){
		return chunk(plane, originX, originZ, new long[]{bits}, 2, Long.bitCount(bits), visibleRoof, signature);
	}
	static Renderer3DWorldChunkFrame.ChunkMesh chunk(int plane,int originX,int originZ,long[] bits,int axis,int count,boolean visibleRoof,long signature){
		return new Renderer3DWorldChunkFrame.ChunkMesh(plane,10,9,originX,originZ,
			visibleRoof?new int[]{0,-128,0,128,-128,0,0,-128,128}:new int[0],
			new float[visibleRoof?3:0],new float[visibleRoof?3:0],new int[visibleRoof?3:0],
			new int[visibleRoof?3:0],new int[visibleRoof?3:0],visibleRoof?new int[]{0,1,2}:new int[0],
			visibleRoof?new int[]{-2}:new int[0],new int[visibleRoof?1:0],
			visibleRoof?new Renderer3DModelKind[]{Renderer3DModelKind.ROOF}:new Renderer3DModelKind[0],
			null,null,bits,axis,count,0,0,visibleRoof?1:0,false,Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,signature);
	}
	static Renderer3DWorldChunkFrame frame(long bits,boolean visibleRoof,long signature){return Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(chunk(0,0,0,bits,visibleRoof,signature)));}
	static Renderer3DWorldChunkFrame shapedFrame(Sector source) throws Exception {
		Class<?> input = Class.forName("orsc.graphics.three.World$TerrainModelInputSource");
		Constructor<?> constructor = input.getDeclaredConstructor(Sector[].class);
		constructor.setAccessible(true);
		Object tiles = constructor.newInstance((Object) new Sector[]{source});
		Method collect = World.class.getDeclaredMethod("collectRoofCoverageTiles", input);
		collect.setAccessible(true);
		Object product = collect.invoke(null, tiles);
		Field bits = product.getClass().getDeclaredField("bits");
		Field count = product.getClass().getDeclaredField("count");
		bits.setAccessible(true);
		count.setAccessible(true);
		return Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(
			chunk(0, 0, 0, (long[]) bits.get(product), World.LOCAL_FACE_TILE_COUNT,
				count.getInt(product), false, 235)));
	}

	static Renderer3DWorldChunkFrame halfTileFrame() throws Exception {
		Sector tiles = Sector.blankLoaded();
		tiles.getTile(1, 0).roofTexture = 1;
		tiles.getTile(1, 0).diagonalWalls = 1;
		tiles.getTile(0, 1).roofTexture = 1;
		return shapedFrame(tiles);
	}

	private static void checkDiagonals() throws Exception {
		Sector tiles = Sector.blankLoaded();
		for (int x : new int[]{3, 7, 11, 15, 19}) tiles.getTile(x, 3).roofTexture = 1;
		tiles.getTile(3, 3).diagonalWalls = 1;
		tiles.getTile(2, 4).roofTexture = 1;
		tiles.getTile(7, 3).diagonalWalls = 1;
		tiles.getTile(8, 2).roofTexture = 1;
		tiles.getTile(11, 3).diagonalWalls = 12001;
		tiles.getTile(10, 2).roofTexture = 1;
		tiles.getTile(15, 3).diagonalWalls = 12001;
		tiles.getTile(16, 4).roofTexture = 1;
		tiles.getTile(19, 3).diagonalWalls = 12001;
		tiles.getTile(18, 2).roofTexture = 1;
		tiles.getTile(20, 4).roofTexture = 1;

		Renderer3DWorldChunkFrame frame = shapedFrame(tiles);
		GeometryShadowRoofMask mask = new GeometryShadowRoofMask.Cache().get(frame, 0);
		for (int[] tile : new int[][]{{3,32,96,96,32},{7,96,32,32,96},{11,32,32,96,96},{15,96,96,32,32}}) {
			int x = tile[0]*128, z = 3*128;
			require(mask.covered(x + tile[1], z + tile[2]), "Diagonal lost roofed half at " + tile[0]);
			require(!mask.covered(x + tile[3], z + tile[4]), "Diagonal darkened outdoor half at " + tile[0]);
			Renderer3DWorldChunkFrame.ChunkMesh mesh = frame.getChunks().get(0);
			require(mesh.roofClassificationForWorldPoint(x + tile[1], z + tile[2]) == 1
				&& mesh.roofClassificationForWorldPoint(x + tile[3], z + tile[4]) == 0,
				"Chunk receiver classification disagrees with mask at " + tile[0]);
		}
		require(mask.covered(19*128 + 32, 3*128 + 32) && mask.covered(19*128 + 96, 3*128 + 96),
			"Interior partition with roofs either side lost coverage");
	}

	public static void main(String[] args) throws Exception {
		GeometryShadowRoofMask.Cache cache=new GeometryShadowRoofMask.Cache();
		Renderer3DWorldChunkFrame hidden=frame(1L<<2,false,1);
		GeometryShadowRoofMask mask=cache.get(hidden,0);
		require(mask.covered(180,40),"positive roof bit missed");require(!mask.covered(40,180),"roof bit transpose");
		require(!mask.covered(-1,40)&&!mask.covered(300,40),"roof coverage escaped source window");
		require(mask==cache.get(hidden,0),"unchanged metadata rebuilt mask");
		GeometryShadowRoofMask shown=cache.get(frame(1L<<2,true,2),0);
		require(shown.covered(180,40)&&shown.pixels().equals(mask.pixels()),"Hide Roofs changed declared coverage");
		require(cache.get(hidden,1).isEmpty(),"lower roof leaked onto upper plane");
		require(cache.get(frame(0,false,3),0).isEmpty(),"zero roof bits inferred an indoor area");
		Renderer3DWorldChunkFrame shifted=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(chunk(0,0,0,0,false,4),chunk(0,512,1024,1L<<2,false,5)));
		GeometryShadowRoofMask moved=cache.get(shifted,0);
		require(moved.covered(692,1064)&&!moved.covered(180,40),"source metadata origin replaced by presentation anchor");
		Renderer3DWorldChunkFrame.ChunkMesh unknown=new Renderer3DWorldChunkFrame.ChunkMesh(0,10,9,0,0,
			new int[]{0,0,0,128,0,0,0,-128,0},new float[3],new float[3],new int[3],new int[]{0,1,2},new int[]{-2},new int[]{8},new Renderer3DModelKind[]{Renderer3DModelKind.WALL},0,1,0,6);
		require(cache.get(Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(unknown)),0).isEmpty(),"unknown roof metadata inferred indoor from wall geometry");
		cache.clear();require(cache.get(hidden,0).covered(180,40),"mask cache did not recover after session clear");
		checkDiagonals();
		System.out.println("PASS declared and diagonal roof coverage, hidden/visible roofs, outdoor/unknown, planes, metadata origins and cache lifecycle");
	}
}
