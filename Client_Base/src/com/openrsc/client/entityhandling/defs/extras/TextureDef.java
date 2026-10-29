package com.openrsc.client.entityhandling.defs.extras;

public class TextureDef {
	private String dataName;
	private String animationName;
	private int nativeSpriteId;

	public TextureDef(String dataName, String animationName) {
		this(dataName, animationName, -1);
	}

	/** Explicit native archive slot for textures outside the original contiguous range. */
	public TextureDef(String dataName, String animationName, int nativeSpriteId) {
		if (nativeSpriteId < -1 || nativeSpriteId > 65535) throw new IllegalArgumentException("Native texture sprite outside archive domain");
		this.nativeSpriteId = nativeSpriteId;
		this.dataName = dataName;
		this.animationName = animationName;
	}

	public int getNativeSpriteId() { return nativeSpriteId; }

	public String getDataName() {
		return dataName;
	}

	public String getAnimationName() {
		return animationName;
	}
}
