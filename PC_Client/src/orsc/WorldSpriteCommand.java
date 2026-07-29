package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;

final class WorldSpriteCommand {
	final Renderer2DFrame.SpriteCommand command;
	final Renderer3DFrame.SpriteAnchor anchor;
	final WorldSpriteAnchorMatch anchorMatch;
	final int legacyDrawOrder;
	final boolean sourceCropped;
	final boolean skewed;

	WorldSpriteCommand(
		Renderer2DFrame.SpriteCommand command,
		Renderer3DFrame.SpriteAnchor anchor,
		WorldSpriteAnchorMatch anchorMatch) {
		this.command = command;
		this.anchor = anchor;
		this.anchorMatch = anchorMatch == null ? WorldSpriteAnchorMatch.unmatched() : anchorMatch;
		this.legacyDrawOrder = anchor != null
			? anchor.getLegacyDrawOrder()
			: command != null && command.getSceneSpriteDrawOrder() >= 0
				? command.getSceneSpriteDrawOrder()
				: Integer.MAX_VALUE;
		Sprite sprite = command == null ? null : command.getSprite();
		this.sourceCropped = command != null
			&& sprite != null
			&& (command.getSourceX() != 0
				|| command.getSourceY() != 0
				|| command.getSourceWidth() != sprite.getWidth()
				|| command.getSourceHeight() != sprite.getHeight());
		this.skewed = command != null && command.getTopX16() != command.getBottomX16();
	}

	boolean hasAnchor() {
		return anchor != null;
	}
}
