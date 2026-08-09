package com.openrsc.server.config;

/** Immutable tool/editor startup gate snapshot. */
public final class ToolsConfiguration {
	private final String configDirectory;
	private final boolean allowInGameWorldEditor, worldBuilderMode, worldBuilderLayeredReviewMode;
	public ToolsConfiguration(final String configDirectory, final boolean allowInGameWorldEditor,
			final boolean worldBuilderMode, final boolean worldBuilderLayeredReviewMode) {
		this.configDirectory = configDirectory == null ? "" : configDirectory;
		this.allowInGameWorldEditor = allowInGameWorldEditor; this.worldBuilderMode = worldBuilderMode;
		this.worldBuilderLayeredReviewMode = worldBuilderLayeredReviewMode;
	}
	public String getConfigDirectory() { return configDirectory; }
	public boolean isAllowInGameWorldEditor() { return allowInGameWorldEditor; }
	public boolean isWorldBuilderMode() { return worldBuilderMode; }
	public boolean isWorldBuilderLayeredReviewMode() { return worldBuilderLayeredReviewMode; }
}
