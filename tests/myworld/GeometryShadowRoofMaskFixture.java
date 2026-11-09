package orsc;
import java.util.*;
import orsc.graphics.three.*;

/** Positive roof metadata remains authoritative with or without roof triangles. */
public final class GeometryShadowRoofMaskFixture {
	static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
	static Renderer3DWorldChunkFrame.ChunkMesh chunk(int plane,int originX,int originZ,long bits,boolean visibleRoof,long signature){
		return new Renderer3DWorldChunkFrame.ChunkMesh(plane,10,9,originX,originZ,
			visibleRoof?new int[]{0,-128,0,128,-128,0,0,-128,128}:new int[0],
			new float[visibleRoof?3:0],new float[visibleRoof?3:0],new int[visibleRoof?3:0],
			new int[visibleRoof?3:0],new int[visibleRoof?3:0],visibleRoof?new int[]{0,1,2}:new int[0],
			visibleRoof?new int[]{-2}:new int[0],new int[visibleRoof?1:0],
			visibleRoof?new Renderer3DModelKind[]{Renderer3DModelKind.ROOF}:new Renderer3DModelKind[0],
			null,null,new long[]{bits},2,Long.bitCount(bits),0,0,visibleRoof?1:0,false,Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,signature);
	}
	static Renderer3DWorldChunkFrame frame(long bits,boolean visibleRoof,long signature){return Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(chunk(0,0,0,bits,visibleRoof,signature)));}
	public static void main(String[] args){
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
		System.out.println("PASS declared roof bits, hidden/visible roofs, outdoor/unknown, planes, metadata origins and cache lifecycle");
	}
}
