package com.openrsc.layeredmaps;

/** Actionable refusal from read-only repository discovery. */
public final class PreflightException extends Exception {
	private static final long serialVersionUID = 1L;

	public PreflightException(String message) {
		super(message);
	}

	public PreflightException(String message, Throwable cause) {
		super(message, cause);
	}
}
