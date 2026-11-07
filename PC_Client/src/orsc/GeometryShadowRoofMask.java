package orsc;

import java.nio.ByteBuffer;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Declared roof coverage only; an enclosed courtyard without roof bits is outdoor. */
final class GeometryShadowRoofMask {
	static final GeometryShadowRoofMask EMPTY = new GeometryShadowRoofMask(0, 0, 0, 0, 0, 0, null);
	final int plane, minX, minZ, width, height;
	final long signature;
	private final ByteBuffer rgba;
	private GeometryShadowRoofMask(int plane, int minX, int minZ, int width, int height, long signature, ByteBuffer rgba) {
		this.plane=plane; this.minX=minX; this.minZ=minZ; this.width=width; this.height=height; this.signature=signature; this.rgba=rgba;
	}
	boolean isEmpty() { return rgba == null; }
	ByteBuffer pixels() { return rgba == null ? null : rgba.asReadOnlyBuffer(); }
	boolean covered(int worldX, int worldZ) {
		if (isEmpty()) return false;
		int x=Math.floorDiv(worldX-minX,128), z=Math.floorDiv(worldZ-minZ,128);
		return x>=0&&z>=0&&x<width&&z<height&&(rgba.get((z*width+x)*4)&255)!=0;
	}

	static final class Cache {
		private boolean known;
		private long key;
		private GeometryShadowRoofMask mask=EMPTY;
		void clear() { known=false; mask=EMPTY; }
		GeometryShadowRoofMask get(Renderer3DWorldChunkFrame frame, int plane) {
			long next=1469598103934665603L ^ plane;
			int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
			for(Renderer3DWorldChunkFrame.ChunkMesh chunk:frame.getChunks()) {
				if(chunk.isObjectChunk()||chunk.getPlane()!=plane||!chunk.hasRoofCoverageData()) continue;
				int axis=chunk.getRoofCoverageAxis();
				next=(next^chunk.getStorageSignature())*1099511628211L;
				next=(next^chunk.getOriginWorldX())*1099511628211L; next=(next^chunk.getOriginWorldZ())*1099511628211L;
				next=(next^axis)*1099511628211L;
				// Roof bits retain their own source-window origin. Halo vertex
				// presentation offsets must not be applied to this metadata.
				int x=Math.floorDiv(chunk.getOriginWorldX(),128), z=Math.floorDiv(chunk.getOriginWorldZ(),128);
				minX=Math.min(minX,x); minZ=Math.min(minZ,z);
				maxX=Math.max(maxX,Math.addExact(x,axis)); maxZ=Math.max(maxZ,Math.addExact(z,axis));
			}
			if(known&&next==key) return mask;
			GeometryShadowRoofMask result=EMPTY;
			if(minX!=Integer.MAX_VALUE) {
				int width=Math.subtractExact(maxX,minX), height=Math.subtractExact(maxZ,minZ);
				if(width>1024||height>1024) throw new IllegalArgumentException("Roof receiver coverage exceeds native scene bounds");
				ByteBuffer pixels=ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width,height),4));
				boolean any=false;
				for(Renderer3DWorldChunkFrame.ChunkMesh chunk:frame.getChunks()) {
					if(chunk.isObjectChunk()||chunk.getPlane()!=plane||!chunk.hasRoofCoverageData()) continue;
					int ox=Math.floorDiv(chunk.getOriginWorldX(),128)-minX, oz=Math.floorDiv(chunk.getOriginWorldZ(),128)-minZ;
					for(int x=0;x<chunk.getRoofCoverageAxis();x++)for(int z=0;z<chunk.getRoofCoverageAxis();z++) {
						if(!chunk.isRoofCoveredTile(x,z)) continue;
						int at=((oz+z)*width+ox+x)*4;
						pixels.put(at,(byte)255);pixels.put(at+1,(byte)255);pixels.put(at+2,(byte)255);pixels.put(at+3,(byte)255);any=true;
					}
				}
				if(any) result=new GeometryShadowRoofMask(plane,minX*128,minZ*128,width,height,next,pixels);
			}
			key=next; mask=result; known=true; return result;
		}
	}
}
