package orsc;

import java.nio.ByteBuffer;

final class OpenGLDynamicTextureAtlas implements AutoCloseable {
	private static final int PADDING = 1;

	private final LwjglBindings gl;
	private final int textureId;
	private final int width;
	private final int height;
	private int cursorX;
	private int cursorY;
	private int rowHeight;
	private boolean closed;
	private final OpenGLPixelWorkspace uploadWorkspace = new OpenGLPixelWorkspace();

	private OpenGLDynamicTextureAtlas(LwjglBindings gl, int textureId, int width, int height) {
		this.gl = gl;
		this.textureId = textureId;
		this.width = width;
		this.height = height;
	}

	static OpenGLDynamicTextureAtlas create(LwjglBindings gl, int width, int height) throws Exception {
		int textureId = gl.glGenTextures();
		gl.glBindTexture(gl.GL_TEXTURE_2D, textureId);
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MIN_FILTER, gl.GL_NEAREST);
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_MAG_FILTER, gl.GL_NEAREST);
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_S, gl.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(gl.GL_TEXTURE_2D, gl.GL_TEXTURE_WRAP_T, gl.GL_CLAMP_TO_EDGE);
		ByteBuffer emptyPixels = ByteBuffer.allocateDirect(width * height * 4);
		gl.glTexImage2D(
			gl.GL_TEXTURE_2D,
			0,
			gl.GL_RGBA,
			width,
			height,
			0,
			gl.GL_RGBA,
			gl.GL_UNSIGNED_BYTE,
			emptyPixels);
		return new OpenGLDynamicTextureAtlas(gl, textureId, width, height);
	}

	int getTextureId() {
		return textureId;
	}

	void beginFrame() {
		cursorX = 0;
		cursorY = 0;
		rowHeight = 0;
	}

	OpenGLTextureRegion upload(DynamicTextureData textureData) throws Exception {
		Placement placement = allocate(textureData.width, textureData.height);
		if (placement == null) {
			return null;
		}

		gl.glBindTexture(gl.GL_TEXTURE_2D, textureId);
		gl.glTexSubImage2D(
			gl.GL_TEXTURE_2D,
			0,
			placement.x - PADDING,
			placement.y - PADDING,
			textureData.width + PADDING * 2,
			textureData.height + PADDING * 2,
			gl.GL_RGBA,
			gl.GL_UNSIGNED_BYTE,
			OpenGLTexturePixels.buildPaddedRgbaPixels(
				textureData.pixels(),
				textureData.width,
				textureData.height,
				PADDING,
				uploadWorkspace));
		return new OpenGLTextureRegion(
			textureId,
			placement.x,
			placement.y,
			textureData.width,
			textureData.height,
			texelCenterU(placement.x),
			texelCenterV(placement.y),
			texelCenterU(placement.x + textureData.width - 1),
			texelCenterV(placement.y + textureData.height - 1),
			true);
	}

	private float texelCenterU(int x) {
		return (x + 0.5f) / width;
	}

	private float texelCenterV(int y) {
		return (y + 0.5f) / height;
	}

	private Placement allocate(int contentWidth, int contentHeight) {
		int allocationWidth = contentWidth + PADDING * 2;
		int allocationHeight = contentHeight + PADDING * 2;
		if (allocationWidth > width || allocationHeight > height) {
			return null;
		}

		if (cursorX + allocationWidth > width) {
			cursorX = 0;
			cursorY += rowHeight;
			rowHeight = 0;
		}
		if (cursorY + allocationHeight > height) {
			return null;
		}

		Placement placement = new Placement(cursorX + PADDING, cursorY + PADDING);
		cursorX += allocationWidth;
		rowHeight = Math.max(rowHeight, allocationHeight);
		return placement;
	}

	@Override
	public void close() throws Exception {
		if (closed) {
			return;
		}
		closed = true;
		uploadWorkspace.clear();
		gl.glDeleteTextures(textureId);
	}

	private static final class Placement {
		private final int x;
		private final int y;

		private Placement(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
}
