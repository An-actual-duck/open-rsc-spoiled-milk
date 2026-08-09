package com.openrsc.server.extensions;

/** Whether a package can truthfully be reactivated without a process restart. */
public enum ExtensionReloadPolicy {
	HOT_RELOAD_SUPPORTED,
	RESTART_REQUIRED
}
