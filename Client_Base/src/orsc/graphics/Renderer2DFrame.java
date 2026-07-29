package orsc.graphics;

import com.openrsc.client.model.Sprite;

import java.util.List;

public final class Renderer2DFrame {
	public static final int SPRITE_COMMAND_LIMIT = 4096;
	public static final int TEXT_COMMAND_LIMIT = 4096;
	public static final int PRIMITIVE_COMMAND_LIMIT = 4096;
	public static final int ROTATED_SPRITE_COMMAND_LIMIT = 256;
	public static final int CIRCLE_COMMAND_LIMIT = 512;

	private static final SpriteCommand[] EMPTY_COMMANDS = new SpriteCommand[0];
	private static final TextCommand[] EMPTY_TEXT_COMMANDS = new TextCommand[0];
	private static final PrimitiveCommand[] EMPTY_PRIMITIVE_COMMANDS = new PrimitiveCommand[0];
	private static final RotatedSpriteCommand[] EMPTY_ROTATED_SPRITE_COMMANDS = new RotatedSpriteCommand[0];
	private static final CircleCommand[] EMPTY_CIRCLE_COMMANDS = new CircleCommand[0];

	public static final Renderer2DFrame EMPTY =
		new Renderer2DFrame(
			0,
			0,
			EMPTY_COMMANDS,
			EMPTY_TEXT_COMMANDS,
			EMPTY_PRIMITIVE_COMMANDS,
			EMPTY_ROTATED_SPRITE_COMMANDS,
			EMPTY_CIRCLE_COMMANDS,
			CaptureStats.EMPTY);

	public enum Phase {
		SCENE("scene"),
		WORLD_OVERLAY("world"),
		UI_OVERLAY("ui"),
		UNKNOWN("unknown");

		private final String id;

		Phase(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}
	}

	private final int width;
	private final int height;
	private final SpriteCommand[] spriteCommands;
	private final TextCommand[] textCommands;
	private final PrimitiveCommand[] primitiveCommands;
	private final RotatedSpriteCommand[] rotatedSpriteCommands;
	private final CircleCommand[] circleCommands;
	private final CaptureStats captureStats;

	private Renderer2DFrame(
		int width,
		int height,
		SpriteCommand[] spriteCommands,
		TextCommand[] textCommands,
		PrimitiveCommand[] primitiveCommands,
		RotatedSpriteCommand[] rotatedSpriteCommands,
		CircleCommand[] circleCommands,
		CaptureStats captureStats) {
		this.width = width;
		this.height = height;
		this.spriteCommands = spriteCommands == null ? EMPTY_COMMANDS : spriteCommands;
		this.textCommands = textCommands == null ? EMPTY_TEXT_COMMANDS : textCommands;
		this.primitiveCommands = primitiveCommands == null ? EMPTY_PRIMITIVE_COMMANDS : primitiveCommands;
		this.rotatedSpriteCommands =
			rotatedSpriteCommands == null ? EMPTY_ROTATED_SPRITE_COMMANDS : rotatedSpriteCommands;
		this.circleCommands = circleCommands == null ? EMPTY_CIRCLE_COMMANDS : circleCommands;
		this.captureStats = captureStats == null ? CaptureStats.EMPTY : captureStats;
	}

	public static Renderer2DFrame empty(int width, int height) {
		return new Renderer2DFrame(
			width,
			height,
			EMPTY_COMMANDS,
			EMPTY_TEXT_COMMANDS,
			EMPTY_PRIMITIVE_COMMANDS,
			EMPTY_ROTATED_SPRITE_COMMANDS,
			EMPTY_CIRCLE_COMMANDS,
			CaptureStats.EMPTY);
	}

	public static Renderer2DFrame snapshot(int width, int height, List<SpriteCommand> spriteCommands) {
		return snapshot(width, height, spriteCommands, CaptureStats.EMPTY);
	}

	public static Renderer2DFrame snapshot(
		int width,
		int height,
		List<SpriteCommand> spriteCommands,
		CaptureStats captureStats) {
		return snapshot(width, height, spriteCommands, null, captureStats);
	}

	public static Renderer2DFrame snapshot(
		int width,
		int height,
		List<SpriteCommand> spriteCommands,
		List<TextCommand> textCommands,
		List<PrimitiveCommand> primitiveCommands,
		List<RotatedSpriteCommand> rotatedSpriteCommands,
		CaptureStats captureStats) {
		return snapshot(width, height, spriteCommands, textCommands, primitiveCommands, rotatedSpriteCommands, null, captureStats);
	}

	public static Renderer2DFrame snapshot(
		int width,
		int height,
		List<SpriteCommand> spriteCommands,
		List<TextCommand> textCommands,
		List<PrimitiveCommand> primitiveCommands,
		List<RotatedSpriteCommand> rotatedSpriteCommands,
		List<CircleCommand> circleCommands,
		CaptureStats captureStats) {
		boolean hasSprites = spriteCommands != null && !spriteCommands.isEmpty();
		boolean hasText = textCommands != null && !textCommands.isEmpty();
		boolean hasPrimitives = primitiveCommands != null && !primitiveCommands.isEmpty();
		boolean hasRotatedSprites = rotatedSpriteCommands != null && !rotatedSpriteCommands.isEmpty();
		boolean hasCircles = circleCommands != null && !circleCommands.isEmpty();
		if (!hasSprites && !hasText && !hasPrimitives && !hasRotatedSprites && !hasCircles) {
			if (captureStats == null || captureStats.isEmpty()) {
				return empty(width, height);
			}
			return new Renderer2DFrame(
				width,
				height,
				EMPTY_COMMANDS,
				EMPTY_TEXT_COMMANDS,
				EMPTY_PRIMITIVE_COMMANDS,
				EMPTY_ROTATED_SPRITE_COMMANDS,
				EMPTY_CIRCLE_COMMANDS,
				captureStats);
		}
		return new Renderer2DFrame(
			width,
			height,
			hasSprites ? spriteCommands.toArray(new SpriteCommand[spriteCommands.size()]) : EMPTY_COMMANDS,
			hasText ? textCommands.toArray(new TextCommand[textCommands.size()]) : EMPTY_TEXT_COMMANDS,
			hasPrimitives ? primitiveCommands.toArray(new PrimitiveCommand[primitiveCommands.size()]) : EMPTY_PRIMITIVE_COMMANDS,
			hasRotatedSprites
				? rotatedSpriteCommands.toArray(new RotatedSpriteCommand[rotatedSpriteCommands.size()])
				: EMPTY_ROTATED_SPRITE_COMMANDS,
			hasCircles ? circleCommands.toArray(new CircleCommand[circleCommands.size()]) : EMPTY_CIRCLE_COMMANDS,
			captureStats);
	}

	public static Renderer2DFrame snapshot(
		int width,
		int height,
		List<SpriteCommand> spriteCommands,
		List<TextCommand> textCommands,
		List<PrimitiveCommand> primitiveCommands,
		CaptureStats captureStats) {
		return snapshot(width, height, spriteCommands, textCommands, primitiveCommands, null, captureStats);
	}

	public static Renderer2DFrame snapshot(
		int width,
		int height,
		List<SpriteCommand> spriteCommands,
		List<TextCommand> textCommands,
		CaptureStats captureStats) {
		return snapshot(width, height, spriteCommands, textCommands, null, null, captureStats);
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public SpriteCommand[] getSpriteCommands() {
		return spriteCommands;
	}

	public TextCommand[] getTextCommands() {
		return textCommands;
	}

	public PrimitiveCommand[] getPrimitiveCommands() {
		return primitiveCommands;
	}

	public RotatedSpriteCommand[] getRotatedSpriteCommands() {
		return rotatedSpriteCommands;
	}

	public CircleCommand[] getCircleCommands() {
		return circleCommands;
	}

	public CaptureStats getCaptureStats() {
		return captureStats;
	}

	public boolean isEmpty() {
		return spriteCommands.length == 0
			&& textCommands.length == 0
			&& primitiveCommands.length == 0
			&& rotatedSpriteCommands.length == 0
			&& circleCommands.length == 0;
	}

	public static final class CaptureStats {
		public static final CaptureStats EMPTY =
			new CaptureStats(
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0);

		private final int attempts;
		private final int captured;
		private final int replacedUi;
		private final int uiBaseCaptured;
		private final int skippedAlpha;
		private final int skippedBounds;
		private final int skippedSource;
		private final int skippedTransform;
		private final int skippedInterlace;
		private final int skippedOverflow;
		private final int skippedInvalid;
		private final int textDraws;
		private final int textGlyphs;
		private final int textReplacedUi;
		private final int textScene;
		private final int textWorld;
		private final int textUi;
		private final int textUnknown;
		private final int primitiveDraws;
		private final int primitiveReplacedUi;
		private final int rotatedSpriteDraws;
		private final int rotatedSpriteCapturedUi;
		private final int circleDraws;
		private final int circleCapturedUi;
		private final int nativeUiBlockSprite;
		private final int nativeUiBlockText;
		private final int nativeUiBlockPrimitive;
		private final int nativeUiBlockMinimap;
		private final int nativeUiBlockGradient;
		private final int nativeUiBlockClear;
		private final int nativeUiBlockCircle;
		private final int nativeUiBlockPixel;
		private final int nativeUiBaseEligible;
		private final int textCommandsCaptured;
		private final int textCommandsSkippedOverflow;
		private final int primitiveCommandsCaptured;
		private final int primitiveCommandsSkippedOverflow;
		private final int rotatedSpriteCommandsSkippedOverflow;
		private final int circleCommandsSkippedOverflow;

		public CaptureStats(
			int attempts,
			int captured,
			int replacedUi,
			int uiBaseCaptured,
			int skippedAlpha,
			int skippedBounds,
			int skippedSource,
			int skippedTransform,
			int skippedInterlace,
			int skippedOverflow,
			int skippedInvalid,
			int textDraws,
			int textGlyphs,
			int textReplacedUi,
			int textScene,
			int textWorld,
			int textUi,
			int textUnknown,
			int primitiveDraws,
			int primitiveReplacedUi,
			int rotatedSpriteDraws,
			int rotatedSpriteCapturedUi,
			int circleDraws,
			int circleCapturedUi,
			int nativeUiBlockSprite,
			int nativeUiBlockText,
			int nativeUiBlockPrimitive,
			int nativeUiBlockMinimap,
			int nativeUiBlockGradient,
			int nativeUiBlockClear,
			int nativeUiBlockCircle,
			int nativeUiBlockPixel,
			int nativeUiBaseEligible,
			int textCommandsCaptured,
			int textCommandsSkippedOverflow,
			int primitiveCommandsCaptured,
			int primitiveCommandsSkippedOverflow,
			int rotatedSpriteCommandsSkippedOverflow,
			int circleCommandsSkippedOverflow) {
			this.attempts = Math.max(0, attempts);
			this.captured = Math.max(0, captured);
			this.replacedUi = Math.max(0, replacedUi);
			this.uiBaseCaptured = Math.max(0, uiBaseCaptured);
			this.skippedAlpha = Math.max(0, skippedAlpha);
			this.skippedBounds = Math.max(0, skippedBounds);
			this.skippedSource = Math.max(0, skippedSource);
			this.skippedTransform = Math.max(0, skippedTransform);
			this.skippedInterlace = Math.max(0, skippedInterlace);
			this.skippedOverflow = Math.max(0, skippedOverflow);
			this.skippedInvalid = Math.max(0, skippedInvalid);
			this.textDraws = Math.max(0, textDraws);
			this.textGlyphs = Math.max(0, textGlyphs);
			this.textReplacedUi = Math.max(0, textReplacedUi);
			this.textScene = Math.max(0, textScene);
			this.textWorld = Math.max(0, textWorld);
			this.textUi = Math.max(0, textUi);
			this.textUnknown = Math.max(0, textUnknown);
			this.primitiveDraws = Math.max(0, primitiveDraws);
			this.primitiveReplacedUi = Math.max(0, primitiveReplacedUi);
			this.rotatedSpriteDraws = Math.max(0, rotatedSpriteDraws);
			this.rotatedSpriteCapturedUi = Math.max(0, rotatedSpriteCapturedUi);
			this.circleDraws = Math.max(0, circleDraws);
			this.circleCapturedUi = Math.max(0, circleCapturedUi);
			this.nativeUiBlockSprite = Math.max(0, nativeUiBlockSprite);
			this.nativeUiBlockText = Math.max(0, nativeUiBlockText);
			this.nativeUiBlockPrimitive = Math.max(0, nativeUiBlockPrimitive);
			this.nativeUiBlockMinimap = Math.max(0, nativeUiBlockMinimap);
			this.nativeUiBlockGradient = Math.max(0, nativeUiBlockGradient);
			this.nativeUiBlockClear = Math.max(0, nativeUiBlockClear);
			this.nativeUiBlockCircle = Math.max(0, nativeUiBlockCircle);
			this.nativeUiBlockPixel = Math.max(0, nativeUiBlockPixel);
			this.nativeUiBaseEligible = nativeUiBaseEligible > 0 ? 1 : 0;
			this.textCommandsCaptured = Math.max(0, textCommandsCaptured);
			this.textCommandsSkippedOverflow = Math.max(0, textCommandsSkippedOverflow);
			this.primitiveCommandsCaptured = Math.max(0, primitiveCommandsCaptured);
			this.primitiveCommandsSkippedOverflow = Math.max(0, primitiveCommandsSkippedOverflow);
			this.rotatedSpriteCommandsSkippedOverflow = Math.max(0, rotatedSpriteCommandsSkippedOverflow);
			this.circleCommandsSkippedOverflow = Math.max(0, circleCommandsSkippedOverflow);
		}

		public boolean isEmpty() {
			return attempts == 0
				&& captured == 0
				&& replacedUi == 0
				&& uiBaseCaptured == 0
				&& skippedAlpha == 0
				&& skippedBounds == 0
				&& skippedSource == 0
				&& skippedTransform == 0
				&& skippedInterlace == 0
				&& skippedOverflow == 0
				&& skippedInvalid == 0
				&& textDraws == 0
				&& textGlyphs == 0
				&& textReplacedUi == 0
				&& textScene == 0
				&& textWorld == 0
				&& textUi == 0
				&& textUnknown == 0
				&& primitiveDraws == 0
				&& primitiveReplacedUi == 0
				&& rotatedSpriteDraws == 0
				&& rotatedSpriteCapturedUi == 0
				&& circleDraws == 0
				&& circleCapturedUi == 0
				&& nativeUiBlockSprite == 0
				&& nativeUiBlockText == 0
				&& nativeUiBlockPrimitive == 0
				&& nativeUiBlockMinimap == 0
				&& nativeUiBlockGradient == 0
				&& nativeUiBlockClear == 0
				&& nativeUiBlockCircle == 0
				&& nativeUiBlockPixel == 0
				&& nativeUiBaseEligible == 0
				&& textCommandsCaptured == 0
				&& textCommandsSkippedOverflow == 0
				&& primitiveCommandsCaptured == 0
				&& primitiveCommandsSkippedOverflow == 0
				&& rotatedSpriteCommandsSkippedOverflow == 0
				&& circleCommandsSkippedOverflow == 0;
		}

		public int getAttempts() {
			return attempts;
		}

		public int getCaptured() {
			return captured;
		}

		public int getReplacedUi() {
			return replacedUi;
		}

		public int getUiBaseCaptured() {
			return uiBaseCaptured;
		}

		public int getSkippedAlpha() {
			return skippedAlpha;
		}

		public int getSkippedBounds() {
			return skippedBounds;
		}

		public int getSkippedSource() {
			return skippedSource;
		}

		public int getSkippedTransform() {
			return skippedTransform;
		}

		public int getSkippedInterlace() {
			return skippedInterlace;
		}

		public int getSkippedOverflow() {
			return skippedOverflow;
		}

		public int getSkippedInvalid() {
			return skippedInvalid;
		}

		public int getTextDraws() {
			return textDraws;
		}

		public int getTextGlyphs() {
			return textGlyphs;
		}

		public int getTextReplacedUi() {
			return textReplacedUi;
		}

		public int getTextScene() {
			return textScene;
		}

		public int getTextWorld() {
			return textWorld;
		}

		public int getTextUi() {
			return textUi;
		}

		public int getTextUnknown() {
			return textUnknown;
		}

		public int getPrimitiveDraws() {
			return primitiveDraws;
		}

		public int getPrimitiveReplacedUi() {
			return primitiveReplacedUi;
		}

		public int getRotatedSpriteDraws() {
			return rotatedSpriteDraws;
		}

		public int getRotatedSpriteCapturedUi() {
			return rotatedSpriteCapturedUi;
		}

		public int getCircleDraws() {
			return circleDraws;
		}

		public int getCircleCapturedUi() {
			return circleCapturedUi;
		}

		public int getNativeUiBlockSprite() {
			return nativeUiBlockSprite;
		}

		public int getNativeUiBlockText() {
			return nativeUiBlockText;
		}

		public int getNativeUiBlockPrimitive() {
			return nativeUiBlockPrimitive;
		}

		public int getNativeUiBlockMinimap() {
			return nativeUiBlockMinimap;
		}

		public int getNativeUiBlockGradient() {
			return nativeUiBlockGradient;
		}

		public int getNativeUiBlockClear() {
			return nativeUiBlockClear;
		}

		public int getNativeUiBlockCircle() {
			return nativeUiBlockCircle;
		}

		public int getNativeUiBlockPixel() {
			return nativeUiBlockPixel;
		}

		public boolean isNativeUiBaseEligible() {
			return nativeUiBaseEligible != 0;
		}

		public int getSpriteCommandLimit() {
			return SPRITE_COMMAND_LIMIT;
		}

		public int getSpriteCommandAttempts() {
			return captured + skippedOverflow;
		}

		public int getSpriteCommandsCaptured() {
			return captured;
		}

		public int getSpriteCommandsSkippedOverflow() {
			return skippedOverflow;
		}

		public int getTextCommandLimit() {
			return TEXT_COMMAND_LIMIT;
		}

		public int getTextCommandAttempts() {
			return textCommandsCaptured + textCommandsSkippedOverflow;
		}

		public int getTextCommandsCaptured() {
			return textCommandsCaptured;
		}

		public int getTextCommandsSkippedOverflow() {
			return textCommandsSkippedOverflow;
		}

		public int getPrimitiveCommandLimit() {
			return PRIMITIVE_COMMAND_LIMIT;
		}

		public int getPrimitiveCommandAttempts() {
			return primitiveCommandsCaptured + primitiveCommandsSkippedOverflow;
		}

		public int getPrimitiveCommandsCaptured() {
			return primitiveCommandsCaptured;
		}

		public int getPrimitiveCommandsSkippedOverflow() {
			return primitiveCommandsSkippedOverflow;
		}

		public int getRotatedSpriteCommandLimit() {
			return ROTATED_SPRITE_COMMAND_LIMIT;
		}

		public int getRotatedSpriteCommandAttempts() {
			return rotatedSpriteCapturedUi + rotatedSpriteCommandsSkippedOverflow;
		}

		public int getRotatedSpriteCommandsCaptured() {
			return rotatedSpriteCapturedUi;
		}

		public int getRotatedSpriteCommandsSkippedOverflow() {
			return rotatedSpriteCommandsSkippedOverflow;
		}

		public int getCircleCommandLimit() {
			return CIRCLE_COMMAND_LIMIT;
		}

		public int getCircleCommandAttempts() {
			return circleCapturedUi + circleCommandsSkippedOverflow;
		}

		public int getCircleCommandsCaptured() {
			return circleCapturedUi;
		}

		public int getCircleCommandsSkippedOverflow() {
			return circleCommandsSkippedOverflow;
		}
	}

	public static final class SpriteCommand {
		public static final int FULL_ALPHA = 255;

		private final Sprite sprite;
		private final int x;
		private final int y;
		private final int width;
		private final int height;
		private final int sourceX;
		private final int sourceY;
		private final int sourceWidth;
		private final int sourceHeight;
		private final int sourceStartX16;
		private final int sourceStartY16;
		private final int sourceScaleX16;
		private final int sourceScaleY16;
		private final int alpha;
		private final RendererSpriteTransform transform;
		private final int topX16;
		private final int bottomX16;
		private final boolean mirrorX;
		private final boolean requiresOrderedReplay;
		private final int legacySpriteId;
		private final int sceneSpriteAnchorIndex;
		private final int sceneSpriteDrawOrder;
		private final Phase phase;
		private final int sequence;

		public SpriteCommand(Sprite sprite, int x, int y, int width, int height, int alpha) {
			this(sprite, x, y, width, height, 0, 0, spriteWidth(sprite), spriteHeight(sprite), alpha);
		}

		public SpriteCommand(
			Sprite sprite,
			int x,
			int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int alpha) {
			this(sprite, x, y, width, height, sourceX, sourceY, sourceWidth, sourceHeight, alpha, RendererSpriteTransform.IDENTITY);
		}

		public SpriteCommand(
			Sprite sprite,
			int x,
			int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int alpha,
			RendererSpriteTransform transform) {
			this(
				sprite,
				x,
				y,
				width,
				height,
				sourceX,
				sourceY,
				sourceWidth,
				sourceHeight,
				sourceX << 16,
				sourceY << 16,
				(sourceWidth << 16) / width,
				(sourceHeight << 16) / height,
				alpha,
				transform,
				x << 16,
				x << 16,
				false,
				false);
		}

		public SpriteCommand(
			Sprite sprite,
			int x,
			int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int alpha,
			RendererSpriteTransform transform,
			int topX16,
			int bottomX16,
			boolean mirrorX) {
			this(
				sprite,
				x,
				y,
				width,
				height,
				sourceX,
				sourceY,
				sourceWidth,
				sourceHeight,
				sourceX << 16,
				sourceY << 16,
				(sourceWidth << 16) / width,
				(sourceHeight << 16) / height,
				alpha,
				transform,
				topX16,
				bottomX16,
				mirrorX,
				false);
		}

		public SpriteCommand(
			Sprite sprite,
			int x,
			int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int sourceStartX16,
			int sourceStartY16,
			int sourceScaleX16,
			int sourceScaleY16,
			int alpha,
			RendererSpriteTransform transform,
			int topX16,
			int bottomX16,
			boolean mirrorX,
			boolean requiresOrderedReplay) {
			this(
				sprite,
				x,
				y,
				width,
				height,
				sourceX,
				sourceY,
				sourceWidth,
				sourceHeight,
				sourceStartX16,
				sourceStartY16,
				sourceScaleX16,
				sourceScaleY16,
				alpha,
				transform,
				topX16,
				bottomX16,
				mirrorX,
				requiresOrderedReplay,
				Phase.UI_OVERLAY,
				0);
		}

		public SpriteCommand(
			Sprite sprite,
			int x,
			int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int sourceStartX16,
			int sourceStartY16,
			int sourceScaleX16,
			int sourceScaleY16,
			int alpha,
			RendererSpriteTransform transform,
			int topX16,
			int bottomX16,
			boolean mirrorX,
			boolean requiresOrderedReplay,
			Phase phase,
			int sequence) {
			this(
				sprite,
				x,
				y,
				width,
				height,
				sourceX,
				sourceY,
				sourceWidth,
				sourceHeight,
				sourceStartX16,
				sourceStartY16,
				sourceScaleX16,
				sourceScaleY16,
				alpha,
				transform,
				topX16,
				bottomX16,
				mirrorX,
				requiresOrderedReplay,
				-1,
				-1,
				-1,
				phase,
				sequence);
		}

		public SpriteCommand(
			Sprite sprite,
			int x,
			int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int sourceStartX16,
			int sourceStartY16,
			int sourceScaleX16,
			int sourceScaleY16,
			int alpha,
			RendererSpriteTransform transform,
			int topX16,
			int bottomX16,
			boolean mirrorX,
			boolean requiresOrderedReplay,
			int legacySpriteId,
			Phase phase,
			int sequence) {
			this(
				sprite,
				x,
				y,
				width,
				height,
				sourceX,
				sourceY,
				sourceWidth,
				sourceHeight,
				sourceStartX16,
				sourceStartY16,
				sourceScaleX16,
				sourceScaleY16,
				alpha,
				transform,
				topX16,
				bottomX16,
				mirrorX,
				requiresOrderedReplay,
				legacySpriteId,
				-1,
				-1,
				phase,
				sequence);
		}

		public SpriteCommand(
			Sprite sprite,
			int x,
			int y,
			int width,
			int height,
			int sourceX,
			int sourceY,
			int sourceWidth,
			int sourceHeight,
			int sourceStartX16,
			int sourceStartY16,
			int sourceScaleX16,
			int sourceScaleY16,
			int alpha,
			RendererSpriteTransform transform,
			int topX16,
			int bottomX16,
			boolean mirrorX,
			boolean requiresOrderedReplay,
			int legacySpriteId,
			int sceneSpriteAnchorIndex,
			int sceneSpriteDrawOrder,
			Phase phase,
			int sequence) {
			if (sprite == null) {
				throw new IllegalArgumentException("sprite must not be null");
			}
			if (width <= 0 || height <= 0) {
				throw new IllegalArgumentException("destination dimensions must be positive");
			}
			if (sourceX < 0
				|| sourceY < 0
				|| sourceWidth <= 0
				|| sourceHeight <= 0
				|| sourceX + sourceWidth > sprite.getWidth()
				|| sourceY + sourceHeight > sprite.getHeight()) {
				throw new IllegalArgumentException("sprite source rectangle is outside sprite bounds");
			}
			this.sprite = sprite;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.sourceX = sourceX;
			this.sourceY = sourceY;
			this.sourceWidth = sourceWidth;
			this.sourceHeight = sourceHeight;
			this.sourceStartX16 = sourceStartX16;
			this.sourceStartY16 = sourceStartY16;
			this.sourceScaleX16 = sourceScaleX16;
			this.sourceScaleY16 = sourceScaleY16;
			this.alpha = Math.max(0, Math.min(FULL_ALPHA, alpha));
			this.transform = transform == null ? RendererSpriteTransform.IDENTITY : transform;
			this.topX16 = topX16;
			this.bottomX16 = bottomX16;
			this.mirrorX = mirrorX;
			this.requiresOrderedReplay = requiresOrderedReplay;
			this.legacySpriteId = legacySpriteId;
			this.sceneSpriteAnchorIndex = sceneSpriteAnchorIndex;
			this.sceneSpriteDrawOrder = sceneSpriteDrawOrder;
			this.phase = phase == null ? Phase.UNKNOWN : phase;
			this.sequence = sequence;
		}

		public Sprite getSprite() {
			return sprite;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}

		public int getSourceX() {
			return sourceX;
		}

		public int getSourceY() {
			return sourceY;
		}

		public int getSourceWidth() {
			return sourceWidth;
		}

		public int getSourceHeight() {
			return sourceHeight;
		}

		public int getSourceStartX16() {
			return sourceStartX16;
		}

		public int getSourceStartY16() {
			return sourceStartY16;
		}

		public int getSourceScaleX16() {
			return sourceScaleX16;
		}

		public int getSourceScaleY16() {
			return sourceScaleY16;
		}

		public int getAlpha() {
			return alpha;
		}

		public RendererSpriteTransform getTransform() {
			return transform;
		}

		public int getTopX16() {
			return topX16;
		}

		public int getBottomX16() {
			return bottomX16;
		}

		public boolean isMirrorX() {
			return mirrorX;
		}

		public boolean requiresOrderedReplay() {
			return requiresOrderedReplay;
		}

		public int getLegacySpriteId() {
			return legacySpriteId;
		}

		public int getSceneSpriteAnchorIndex() {
			return sceneSpriteAnchorIndex;
		}

		public int getSceneSpriteDrawOrder() {
			return sceneSpriteDrawOrder;
		}

		public Phase getPhase() {
			return phase;
		}

		public int getSequence() {
			return sequence;
		}

		private static int spriteWidth(Sprite sprite) {
			if (sprite == null) {
				throw new IllegalArgumentException("sprite must not be null");
			}
			return sprite.getWidth();
		}

		private static int spriteHeight(Sprite sprite) {
			if (sprite == null) {
				throw new IllegalArgumentException("sprite must not be null");
			}
			return sprite.getHeight();
		}
	}

	public static final class TextCommand {
		private static final GlyphCommand[] EMPTY_GLYPHS = new GlyphCommand[0];

		private final GlyphCommand[] glyphs;
		private final Phase phase;
		private final int sequence;

		public TextCommand(List<GlyphCommand> glyphs, Phase phase, int sequence) {
			this(
				glyphs == null || glyphs.isEmpty()
					? EMPTY_GLYPHS
					: glyphs.toArray(new GlyphCommand[glyphs.size()]),
				phase,
				sequence);
		}

		public TextCommand(GlyphCommand[] glyphs, Phase phase, int sequence) {
			if (glyphs == null || glyphs.length == 0) {
				throw new IllegalArgumentException("text command must contain at least one glyph");
			}
			for (GlyphCommand glyph : glyphs) {
				if (glyph == null) {
					throw new IllegalArgumentException("text command glyphs must not be null");
				}
			}
			this.glyphs = glyphs;
			this.phase = phase == null ? Phase.UNKNOWN : phase;
			this.sequence = sequence;
		}

		public GlyphCommand[] getGlyphs() {
			return glyphs;
		}

		public Phase getPhase() {
			return phase;
		}

		public int getSequence() {
			return sequence;
		}

		public static final class GlyphCommand {
			private final byte[] fontData;
			private final int dataAddress;
			private final int x;
			private final int y;
			private final int width;
			private final int height;
			private final int color;
			private final boolean antiAliased;

			public GlyphCommand(
				byte[] fontData,
				int dataAddress,
				int x,
				int y,
				int width,
				int height,
				int color,
				boolean antiAliased) {
				if (fontData == null) {
					throw new IllegalArgumentException("glyph font data must not be null");
				}
				if (dataAddress < 0 || width <= 0 || height <= 0 || dataAddress + width * height > fontData.length) {
					throw new IllegalArgumentException("glyph source rectangle is outside font data");
				}
				this.fontData = fontData;
				this.dataAddress = dataAddress;
				this.x = x;
				this.y = y;
				this.width = width;
				this.height = height;
				this.color = color & RendererTransparency.RGB_MASK;
				this.antiAliased = antiAliased;
			}

			public byte[] getFontData() {
				return fontData;
			}

			public int getDataAddress() {
				return dataAddress;
			}

			public int getX() {
				return x;
			}

			public int getY() {
				return y;
			}

			public int getWidth() {
				return width;
			}

			public int getHeight() {
				return height;
			}

			public int getColor() {
				return color;
			}

			public boolean isAntiAliased() {
				return antiAliased;
			}
		}
	}

	public static final class PrimitiveCommand {
		private final int x;
		private final int y;
		private final int width;
		private final int height;
		private final int color;
		private final int alpha;
		private final Phase phase;
		private final int sequence;

		public PrimitiveCommand(
			int x,
			int y,
			int width,
			int height,
			int color,
			int alpha,
			Phase phase,
			int sequence) {
			if (width <= 0 || height <= 0) {
				throw new IllegalArgumentException("primitive dimensions must be positive");
			}
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.color = color & RendererTransparency.RGB_MASK;
			this.alpha = Math.max(0, Math.min(256, alpha));
			this.phase = phase == null ? Phase.UNKNOWN : phase;
			this.sequence = sequence;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}

		public int getColor() {
			return color;
		}

		public int getAlpha() {
			return alpha;
		}

		public Phase getPhase() {
			return phase;
		}

		public int getSequence() {
			return sequence;
		}
	}

	public static final class CircleCommand {
		private final int x;
		private final int y;
		private final int radius;
		private final int color;
		private final int alpha;
		private final Phase phase;
		private final int sequence;

		public CircleCommand(
			int x,
			int y,
			int radius,
			int color,
			int alpha,
			Phase phase,
			int sequence) {
			if (radius <= 0) {
				throw new IllegalArgumentException("circle radius must be positive");
			}
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.color = color & RendererTransparency.RGB_MASK;
			this.alpha = Math.max(0, Math.min(256, alpha));
			this.phase = phase == null ? Phase.UNKNOWN : phase;
			this.sequence = sequence;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getRadius() {
			return radius;
		}

		public int getColor() {
			return color;
		}

		public int getAlpha() {
			return alpha;
		}

		public Phase getPhase() {
			return phase;
		}

		public int getSequence() {
			return sequence;
		}
	}

	public static final class RotatedSpriteCommand {
		private final Sprite sprite;
		private final int x0;
		private final int y0;
		private final int x1;
		private final int y1;
		private final int x2;
		private final int y2;
		private final int x3;
		private final int y3;
		private final int clipLeft;
		private final int clipTop;
		private final int clipRight;
		private final int clipBottom;
		private final boolean transparentMask;
		private final Phase phase;
		private final int sequence;

		public RotatedSpriteCommand(
			Sprite sprite,
			int x0,
			int y0,
			int x1,
			int y1,
			int x2,
			int y2,
			int x3,
			int y3,
			int clipLeft,
			int clipTop,
			int clipRight,
			int clipBottom,
			boolean transparentMask,
			Phase phase,
			int sequence) {
			if (sprite == null) {
				throw new IllegalArgumentException("sprite must not be null");
			}
			if (sprite.getWidth() <= 0 || sprite.getHeight() <= 0 || sprite.getPixels() == null) {
				throw new IllegalArgumentException("sprite texture must be available");
			}
			if (clipRight <= clipLeft || clipBottom <= clipTop) {
				throw new IllegalArgumentException("rotated sprite clip rectangle must be positive");
			}
			this.sprite = sprite;
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.x3 = x3;
			this.y3 = y3;
			this.clipLeft = clipLeft;
			this.clipTop = clipTop;
			this.clipRight = clipRight;
			this.clipBottom = clipBottom;
			this.transparentMask = transparentMask;
			this.phase = phase == null ? Phase.UNKNOWN : phase;
			this.sequence = sequence;
		}

		public Sprite getSprite() {
			return sprite;
		}

		public int getX0() {
			return x0;
		}

		public int getY0() {
			return y0;
		}

		public int getX1() {
			return x1;
		}

		public int getY1() {
			return y1;
		}

		public int getX2() {
			return x2;
		}

		public int getY2() {
			return y2;
		}

		public int getX3() {
			return x3;
		}

		public int getY3() {
			return y3;
		}

		public int getClipLeft() {
			return clipLeft;
		}

		public int getClipTop() {
			return clipTop;
		}

		public int getClipRight() {
			return clipRight;
		}

		public int getClipBottom() {
			return clipBottom;
		}

		public boolean isTransparentMask() {
			return transparentMask;
		}

		public Phase getPhase() {
			return phase;
		}

		public int getSequence() {
			return sequence;
		}
	}
}
