package orsc;
import orsc.graphics.three.*;
import java.util.Arrays;

/** Actual object mesh construction includes cell metadata but retains local model vertices. */
public final class GeometryShadowCoordinatesFixture {
	static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
	static Renderer3DWorldChunkFrame.ChunkMesh anchor(int plane) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(plane,10,9,432*128,384*128,
			new int[]{0,0,0,128,0,0,0,0,128},new float[]{0,1,0},new float[]{0,0,1},new int[]{0,0,0},
			new int[]{0,1,2},new int[]{-2},new int[]{8},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},1,0,0,12L);
	}
	public static void main(String[] args){
		for(int level:new int[]{-1,0,2})for(int role:new int[]{Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS}) {
			Renderer3DWorldChunkFrame.ChunkMesh world=anchor(level);
			RSModel model=new RSModel(3,1);model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
			int a=model.insertVertex(3000,-128,2000),b=model.insertVertex(3128,-128,2000),c=model.insertVertex(3000,-256,2000);
			model.insertFace(3,new int[]{a,b,c},-2,-2,false);
			Renderer3DWorldChunkFrame.ChunkMesh object=RSModel.buildRenderer3DObjectChunkMesh(level,10,9,world.getOriginWorldX()+24*128,world.getOriginWorldZ()-8*128,new RSModel[]{model},1,role);
			require(object.getVertexCoord(0)==3000&&object.getVertexCoord(2)==2000,"builder no longer retains presentation coordinates");
			Renderer3DWorldChunkFrame frame=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(world,object));
			int requiredX=3000+world.getLogicalWorldOffsetX(),requiredZ=2000+world.getLogicalWorldOffsetZ();
			require(object.getVertexCoord(0)+GeometryShadowCoordinates.worldOffsetX(frame,object)==requiredX,"object cell was counted twice in shadow X");
			require(object.getVertexCoord(2)+GeometryShadowCoordinates.worldOffsetZ(frame,object)==requiredZ,"object cell was counted twice in shadow Z");
			Renderer3DWorldChunkFrame.ChunkMesh movedWorld=world.rebasePresentation(-6144,6144),movedObject;
			if(role==Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS) {
				movedObject=object.rebaseStaticObjectPresentation(11,10,-6144,6144);
			} else {
				model.translate2(-6144,0,6144);
				movedObject=RSModel.buildRenderer3DObjectChunkMesh(level,11,10,object.getOriginWorldX(),object.getOriginWorldZ(),new RSModel[]{model},1,role);
			}
			Renderer3DWorldChunkFrame moved=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(movedWorld,movedObject));
			require(movedObject.getVertexCoord(0)+GeometryShadowCoordinates.worldOffsetX(moved,movedObject)==requiredX,"object shadow X moved on rebase");
			require(movedObject.getVertexCoord(2)+GeometryShadowCoordinates.worldOffsetZ(moved,movedObject)==requiredZ,"object shadow Z moved on rebase");
			require(GeometryShadowCoordinates.worldOffsetX(moved,movedWorld)==movedWorld.getLogicalWorldOffsetX(),"terrain world anchor changed");
		}
		System.out.println("PASS actual object-cell mesh shadow coordinates, static/animated and signed-level rebases");
	}
}
