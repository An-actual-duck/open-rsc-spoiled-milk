package orsc;

import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame;
import orsc.graphics.three.Renderer3DModelKind;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Render-only bounded height-field probes. No camera/sun dependence or world authority. */
final class TerrainDefinitionField {
    static final int TILE = 128, MAX_AXIS = 512;
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private long sourceSignature = Long.MIN_VALUE;
    int minX, minZ, width, height, plane;
    long revision;
    private byte[] rgba = new byte[0];
    int rebuilds;
    private final LinkedHashMap<Long,Long> terrainStorageHeights = new LinkedHashMap<Long,Long>(32,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<Long,Long> eldest) { return size()>256; }
    };

    boolean update(Renderer3DFrame frame) {
        Renderer3DWorldChunkFrame world = frame == null ? null : frame.getWorldChunkFrame();
        if (world == null || world.getChunkCount() == 0) { clear(); return false; }
        int active = frame.getActivePlane();
        long signature = frame.getShadowContextEpoch() * 31 + active;
        int offX = GeometryShadowCoordinates.worldOffsetX(world), offZ = GeometryShadowCoordinates.worldOffsetZ(world);
        int loX = Integer.MAX_VALUE, loZ = loX, hiX = Integer.MIN_VALUE, hiZ = hiX;
        for (Renderer3DWorldChunkFrame.ChunkMesh chunk : world.getChunks()) {
            if (chunk.getPlane() != active || chunk.getTerrainTriangles() <= 0) continue;
            signature = mix(signature, terrainHeightSignature(chunk));
            signature = mix(signature, (long)offX + chunk.getVertexOffsetX());
            signature = mix(signature, (long)offZ + chunk.getVertexOffsetZ());
            loX = Math.min(loX, Math.floorDiv(chunk.getMinVertexX() + offX, TILE));
            loZ = Math.min(loZ, Math.floorDiv(chunk.getMinVertexZ() + offZ, TILE));
            hiX = Math.max(hiX, Math.floorDiv(chunk.getMaxVertexX() + offX, TILE));
            hiZ = Math.max(hiZ, Math.floorDiv(chunk.getMaxVertexZ() + offZ, TILE));
        }
        if (signature == sourceSignature) return width > 0;
        if (loX > hiX || loZ > hiZ || hiX - loX + 1 > MAX_AXIS || hiZ - loZ + 1 > MAX_AXIS) { clear(); return false; }
        minX = loX; minZ = loZ; width = hiX - loX + 1; height = hiZ - loZ + 1; plane = active;
        int[] heights = new int[width * height]; Arrays.fill(heights, UNKNOWN);
        for (Renderer3DWorldChunkFrame.ChunkMesh chunk : world.getChunks()) {
            if (chunk.getPlane() != active || chunk.getTerrainTriangles() <= 0) continue;
            for (int triangle = 0; triangle < chunk.getTriangleCount(); triangle++) {
                if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) continue;
                for (int corner = 0; corner < 3; corner++) {
                    int coord = chunk.getIndex(triangle * 3 + corner) * 3;
                    int x = chunk.getVertexCoord(coord) + offX, z = chunk.getVertexCoord(coord + 2) + offZ;
                    if (Math.floorMod(x, TILE) != 0 || Math.floorMod(z, TILE) != 0) continue;
                    int at = (Math.floorDiv(z, TILE) - minZ) * width + Math.floorDiv(x, TILE) - minX;
                    int y = chunk.getVertexCoord(coord + 1);
                    if (at >= 0 && at < heights.length) heights[at] = heights[at] == UNKNOWN ? y : Math.min(heights[at], y);
                }
            }
        }
        rgba = build(heights, width, height); sourceSignature = signature; revision++; rebuilds++;
        return true;
    }
    static byte[] build(int[] heights, int width, int height) {
        byte[] result = new byte[width * height * 4];
        int[][] directions = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {
            int at = z * width + x, y = heights[at];
            double occlusion = 0, bend = 0; int count = 0;
            if (y != UNKNOWN) for (int[] d : directions) {
                // Symmetric pairs cancel a planar slope: only terrain above its local tangent
                // or signed local bending contributes, rather than painting every slope dark.
                int nx = x+d[0], nz=z+d[1], px=x-d[0], pz=z-d[1];
                if (nx < 0 || nx >= width || nz < 0 || nz >= height || px < 0 || px >= width || pz < 0 || pz >= height) continue;
                int n=heights[nz*width+nx], p=heights[pz*width+px];
                if (n == UNKNOWN || p == UNKNOWN) continue;
                double length = Math.hypot(d[0],d[1]) * TILE;
                double curve = ((double)y - (n+(double)p)*.5) / length; // positive = concave in Y-down
                double tangent = (p - (double)n) / (2 * length);
                double horizon = 0;
                for (int radius=1;radius<=4;radius++) {
                    int sx=x+d[0]*radius, sz=z+d[1]*radius;
                    if (sx<0 || sx>=width || sz<0 || sz>=height) break;
                    int sample=heights[sz*width+sx]; if(sample==UNKNOWN) break;
                    double aboveTangent=(y-(double)sample)/(length*radius)-tangent;
                    horizon=Math.max(horizon,aboveTangent);
                }
                occlusion += horizon; bend += curve; count++;
            }
            float ao=count==0?0:(float)Math.min(1,occlusion/8*1.6);
            float curve=count==0?0:(float)Math.max(-1,Math.min(1,bend/8*3));
            result[at*4]=(byte)Math.round(ao*255);
            result[at*4+1]=(byte)Math.round((curve*.5f+.5f)*255);
            result[at*4+2]=0; result[at*4+3]=(byte)(y==UNKNOWN?0:255);
        }
        return result;
    }
    private long terrainHeightSignature(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        Long key=chunk.getStorageSignature(), cached=terrainStorageHeights.get(key);
        if(cached!=null)return cached;
        long hash=1469598103934665603L;
        // The immutable storage signature only memoizes this rare scan. Material/light
        // changes can miss that memo, but their identical height hash cannot invalidate
        // the field. Rebased wrappers retain storage and only change placement.
        for(int triangle=0;triangle<chunk.getTriangleCount();triangle++) {
            if(chunk.getTriangleModelKind(triangle)!=Renderer3DModelKind.TERRAIN)continue;
            for(int corner=0;corner<3;corner++) {
                int coord=chunk.getIndex(triangle*3+corner)*3;
                hash=mix(hash,chunk.getVertexCoord(coord)-chunk.getVertexOffsetX());
                hash=mix(hash,chunk.getVertexCoord(coord+1));
                hash=mix(hash,chunk.getVertexCoord(coord+2)-chunk.getVertexOffsetZ());
            }
        }
        terrainStorageHeights.put(key,hash);return hash;
    }
    ByteBuffer pixels() { ByteBuffer out=ByteBuffer.allocateDirect(rgba.length);out.put(rgba).flip();return out; }
    void clear() { sourceSignature=Long.MIN_VALUE;width=height=0;rgba=new byte[0];terrainStorageHeights.clear();revision++; }
    private static long mix(long h,long value) { return (h^value)*1099511628211L; }
}
