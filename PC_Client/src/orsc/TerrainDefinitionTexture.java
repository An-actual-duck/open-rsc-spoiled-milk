package orsc;

import orsc.graphics.three.Renderer3DFrame;

/** Small cached RG height-shading field on its own sampler; restores caller GL state. */
final class TerrainDefinitionTexture {
    private final TerrainDefinitionField field = new TerrainDefinitionField();
    private int texture, previousBinding, previousActive;
    private long uploaded = Long.MIN_VALUE;
    private boolean bound;
    private Boolean supported;
    void bind(LwjglBindings gl, OpenGLShaderProgram shader, Renderer3DFrame frame) throws Exception {
        if (supported == null) supported = gl.glGetInteger(0x8872) >= 8; // GL_MAX_TEXTURE_IMAGE_UNITS
        RendererTerrainDefinitionSettings.Mode mode=RendererTerrainDefinitionSettings.getMode();
        boolean enabled = supported && mode.shaderId >= 2
            && RendererLightingSettings.getMode()==RendererLightingSettings.Mode.DIRECTIONAL && field.update(frame);
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
    void unbind(LwjglBindings gl) throws Exception {
        if(!bound)return;
        int active=gl.glGetInteger(0x84E0);
        try { gl.glActiveTexture(gl.GL_TEXTURE0+7);gl.glBindTexture(gl.GL_TEXTURE_2D,previousBinding); }
        finally { gl.glActiveTexture(active);bound=false; }
    }
    void clear(LwjglBindings gl) throws Exception {
        unbind(gl);if(texture!=0)gl.glDeleteTextures(texture);texture=0;uploaded=Long.MIN_VALUE;field.clear();
    }
}
