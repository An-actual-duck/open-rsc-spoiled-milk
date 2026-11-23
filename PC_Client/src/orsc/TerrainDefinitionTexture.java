package orsc;

import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame.ChunkMesh;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Small cached RG height-shading field on its own sampler; restores caller GL state. */
final class TerrainDefinitionTexture {
    private TerrainDefinitionField field = new TerrainDefinitionField();
    private ExecutorService preparation;
    private Future<TerrainDefinitionField> incoming;
    private List<ChunkMesh> incomingMeshes;
    private int incomingPlane;
    private int texture, previousBinding, previousActive;
    private long uploaded = Long.MIN_VALUE;
    private boolean bound;
    private Boolean supported;
    void bind(LwjglBindings gl, OpenGLShaderProgram shader, Renderer3DFrame frame) throws Exception {
        if (supported == null) supported = gl.glGetInteger(0x8872) >= 8; // GL_MAX_TEXTURE_IMAGE_UNITS
        RendererTerrainDefinitionSettings.Mode mode=RendererTerrainDefinitionSettings.getMode();
        boolean lighting = supported && mode.shaderId >= 2
            && RendererLightingSettings.getMode()==RendererLightingSettings.Mode.DIRECTIONAL;
        if (lighting && DesktopWorldStreamingPreview.ENABLED) {
            adopt(frame);
            prepare(frame);
        }
        boolean enabled = lighting && field.update(frame);
        shader.setTerrainDefinition(mode.shaderId, enabled ? field : null);
        if (!enabled) return;
        previousActive=gl.glGetInteger(0x84E0); // GL_ACTIVE_TEXTURE
        gl.glActiveTexture(gl.GL_TEXTURE0+7);
        previousBinding=gl.glGetInteger(0x8069); // GL_TEXTURE_BINDING_2D
        bound=true;
        try {
            if(texture==0)texture=gl.glGenTextures();
            gl.glBindTexture(gl.GL_TEXTURE_2D,texture);
            if(uploaded!=field.revision) {
                gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MIN_FILTER,gl.GL_LINEAR);
                gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_MAG_FILTER,gl.GL_LINEAR);
                gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_WRAP_S,gl.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(gl.GL_TEXTURE_2D,gl.GL_TEXTURE_WRAP_T,gl.GL_CLAMP_TO_EDGE);
                gl.glTexImage2D(gl.GL_TEXTURE_2D,0,gl.GL_RGBA,field.width,field.height,0,gl.GL_RGBA,gl.GL_UNSIGNED_BYTE,field.pixels());
                uploaded=field.revision;
            }
        } catch(Exception error) { unbind(gl);throw error; }
        finally { gl.glActiveTexture(previousActive); }
    }
    private void prepare(Renderer3DFrame frame) {
        List<ChunkMesh> meshes = frame.getPreparedWorldChunks();
        int plane = frame.getActivePlane();
        if (meshes.isEmpty() || incoming != null && (!incoming.isDone()
            || incomingPlane == plane && sameTerrain(incomingMeshes, meshes, plane))) return;
        if (preparation == null) {
            preparation = Executors.newSingleThreadExecutor(work -> {
                Thread thread = new Thread(work, "Incoming terrain lighting");
                thread.setDaemon(true);
                return thread;
            });
        }
        incomingMeshes = meshes;
        incomingPlane = plane;
        incoming = preparation.submit(() -> {
            TerrainDefinitionField prepared = new TerrainDefinitionField();
            return prepared.update(meshes, plane, 0L) ? prepared : null;
        });
    }

    private void adopt(Renderer3DFrame frame) {
        if (incoming == null || !incoming.isDone() || incomingPlane != frame.getActivePlane()
            || !sameTerrain(incomingMeshes, frame.getWorldChunkFrame().getChunks(), incomingPlane)) return;
        try {
            TerrainDefinitionField prepared = incoming.get();
            if (prepared != null) {
                prepared.adoptEpoch(frame);
                field = prepared;
                uploaded = Long.MIN_VALUE;
                if (DesktopWorldStreamingPreview.TRACE) {
                    System.out.printf("DESKTOP_TERRAIN_LIGHTING_ADOPTED tile=%d,%d%n", field.minX, field.minZ);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException failed) {
            ClientRuntimeLogger.logThrowable("Incoming terrain lighting preparation failed", failed.getCause());
        } finally {
            incoming = null;
            incomingMeshes = null;
        }
    }

    // Compare the same immutable geometry in world coordinates. The render
    // origin can move between prediction and installation.
    static boolean sameTerrain(List<ChunkMesh> left, List<ChunkMesh> right, int plane) {
        if (left.isEmpty() || right.isEmpty()) return false;
        int offsetX = left.get(0).getLogicalWorldOffsetX() - right.get(0).getLogicalWorldOffsetX();
        int offsetZ = left.get(0).getLogicalWorldOffsetZ() - right.get(0).getLogicalWorldOffsetZ();
        int next = 0;
        for (ChunkMesh mesh : left) {
            if (!TerrainDefinitionField.hasTerrain(mesh, plane)) continue;
            while (next < right.size() && !TerrainDefinitionField.hasTerrain(right.get(next), plane)) next++;
            if (next == right.size()) return false;
            ChunkMesh other = right.get(next++);
            if (mesh.getStorageSignature() != other.getStorageSignature()
                || offsetX + mesh.getVertexOffsetX() != other.getVertexOffsetX()
                || offsetZ + mesh.getVertexOffsetZ() != other.getVertexOffsetZ()) return false;
        }
        while (next < right.size() && !TerrainDefinitionField.hasTerrain(right.get(next), plane)) next++;
        return next == right.size();
    }

    void unbind(LwjglBindings gl) throws Exception {
        if(!bound)return;
        int active=gl.glGetInteger(0x84E0);
        try { gl.glActiveTexture(gl.GL_TEXTURE0+7);gl.glBindTexture(gl.GL_TEXTURE_2D,previousBinding); }
        finally { gl.glActiveTexture(active);bound=false; }
    }
    void clear(LwjglBindings gl) throws Exception {
        if (preparation != null) preparation.shutdownNow();
        preparation = null;
        incoming = null;
        incomingMeshes = null;
        unbind(gl);if(texture!=0)gl.glDeleteTextures(texture);texture=0;uploaded=Long.MIN_VALUE;field.clear();
    }
}
