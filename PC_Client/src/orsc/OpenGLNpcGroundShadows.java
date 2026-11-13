package orsc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import orsc.graphics.three.Renderer3DFrame;

/** One untextured, depth-tested blended draw; no actor shadow maps or sprite assets. */
final class OpenGLNpcGroundShadows implements AutoCloseable {
    private final LwjglBindings gl;
    final NpcGroundShadowMesh mesh=new NpcGroundShadowMesh();
    private int vbo,ebo;
    OpenGLNpcGroundShadows(LwjglBindings gl) {this.gl=gl;}

    void draw(Renderer3DFrame frame,GeometryShadowRoofMask roofs,int offsetX,int offsetZ)throws Exception {
        mesh.clear();
        for(Renderer3DFrame.NpcGroundShadow shadow:frame.getNpcGroundShadows()) {
            float distance=(float)Math.hypot(shadow.x-frame.getCameraOffsetX(),shadow.z-frame.getCameraOffsetZ());
            float fade=Math.max(0,Math.min(1,(frame.getFogDistance()-distance)/Math.max(1,frame.getFogDistance()*.2f)));
            mesh.append(shadow.x,shadow.y,shadow.z,shadow.radius,shadow.slopeX,shadow.slopeZ,.28f*fade,roofs,offsetX,offsetZ);
        }
        if(mesh.vertexCount()==0)return;
        if(vbo==0) {
            vbo=gl.glGenBuffers();ebo=gl.glGenBuffers();
            IntBuffer indices=ByteBuffer.allocateDirect(NpcGroundShadowMesh.MAX_VERTICES*4).order(ByteOrder.nativeOrder()).asIntBuffer();
            for(int i=0;i<NpcGroundShadowMesh.MAX_VERTICES;i++)indices.put(i);
            indices.flip();gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);
            gl.glBufferData(gl.GL_ELEMENT_ARRAY_BUFFER,indices,gl.GL_STATIC_DRAW);
        }
        gl.glPushAttrib(gl.GL_ALL_ATTRIB_BITS);gl.glPushClientAttrib(gl.GL_CLIENT_ALL_ATTRIB_BITS);
        try {
            gl.glUseProgram(0);gl.glActiveTexture(gl.GL_TEXTURE0);
            gl.glDisable(gl.GL_TEXTURE_2D);gl.glDisable(gl.GL_ALPHA_TEST);gl.glDisable(gl.GL_CULL_FACE);gl.glDisable(gl.GL_FOG);
            gl.glEnable(gl.GL_BLEND);gl.glBlendFunc(gl.GL_SRC_ALPHA,gl.GL_ONE_MINUS_SRC_ALPHA);
            gl.glEnable(gl.GL_DEPTH_TEST);gl.glDepthMask(false);
            gl.glEnable(gl.GL_POLYGON_OFFSET_FILL);gl.glPolygonOffset(-1,-1);
            gl.glBindBuffer(gl.GL_ARRAY_BUFFER,vbo);gl.glBufferData(gl.GL_ARRAY_BUFFER,mesh.data(),gl.GL_STREAM_DRAW);
            gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,ebo);
            gl.glEnableClientState(gl.GL_VERTEX_ARRAY);gl.glVertexPointer(3,gl.GL_FLOAT,NpcGroundShadowMesh.STRIDE*4,0L);
            gl.glEnableClientState(gl.GL_COLOR_ARRAY);gl.glColorPointer(4,gl.GL_FLOAT,NpcGroundShadowMesh.STRIDE*4,12L);
            gl.glDrawElements(gl.GL_TRIANGLES,mesh.vertexCount(),gl.GL_UNSIGNED_INT,0L);
        } finally {
            gl.glPopClientAttrib();gl.glPopAttrib();
            gl.glBindBuffer(gl.GL_ARRAY_BUFFER,0);gl.glBindBuffer(gl.GL_ELEMENT_ARRAY_BUFFER,0);
        }
    }
    public void close()throws Exception {
        if(vbo!=0)gl.glDeleteBuffers(vbo);if(ebo!=0)gl.glDeleteBuffers(ebo);vbo=ebo=0;
    }
}
