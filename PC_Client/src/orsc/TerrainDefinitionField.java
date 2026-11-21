package orsc;

import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.TerrainOcclusion;
import orsc.graphics.three.World;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Assemble checked lighting cells in their stable world positions, including the currently resident overlap. */
    boolean updateResident(List<Renderer3DWorldChunkFrame.ChunkMesh> cells, int activePlane) {
        int size = World.SECTION_SIZE;
        int loX = Integer.MAX_VALUE, loZ = Integer.MAX_VALUE, hiX = Integer.MIN_VALUE, hiZ = Integer.MIN_VALUE;
        long signature = 0x4ce51d7c249f150aL ^ activePlane;
        for (Renderer3DWorldChunkFrame.ChunkMesh cell : cells) {
            if (!cell.hasSurfaceOcclusion() || cell.getPlane() != activePlane) return false;
            int x = originTile(cell.getOriginWorldX()), z = originTile(cell.getOriginWorldZ());
            loX = Math.min(loX, x);
            loZ = Math.min(loZ, z);
            hiX = Math.max(hiX, x + size);
            hiZ = Math.max(hiZ, z + size);
            signature = mix(mix(mix(signature, cell.getStorageSignature()), x), z);
        }
        if (cells.isEmpty() || hiX - loX > MAX_AXIS || hiZ - loZ > MAX_AXIS) return false;
        if (signature == sourceSignature) return true;
        minX = loX;
        minZ = loZ;
        width = hiX - loX;
        height = hiZ - loZ;
        plane = activePlane;
        rgba = new byte[width * height * 4];
        for (Renderer3DWorldChunkFrame.ChunkMesh cell : cells) {
            int x = originTile(cell.getOriginWorldX()) - minX, z = originTile(cell.getOriginWorldZ()) - minZ;
            for (int row = 0; row < size; row++) cell.copySurfaceOcclusionRow(row, rgba, ((z + row) * width + x) * 4);
        }
        sourceSignature = signature;
        revision++;
        rebuilds++;
        return true;
    }

    private static int originTile(int sourceOrigin) {
        // The fixed cell is the center sector of the exact three-sector footprint.
        return Math.floorDiv(sourceOrigin, TILE) + World.SECTION_SIZE;
    }
    static byte[] build(int[] heights, int width, int height) {
        return TerrainOcclusion.build(heights, width, height);
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
