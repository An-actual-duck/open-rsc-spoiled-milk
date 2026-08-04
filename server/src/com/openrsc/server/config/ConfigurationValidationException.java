package com.openrsc.server.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Raised when a typed configuration view cannot safely be consumed. */
public final class ConfigurationValidationException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	private final List<String> errors;

	public ConfigurationValidationException(final String viewName, final List<String> errors) {
		super(viewName + " configuration is invalid: " + String.join("; ", errors));
		this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
	}

	public List<String> getErrors() {
		return errors;
	}
}
