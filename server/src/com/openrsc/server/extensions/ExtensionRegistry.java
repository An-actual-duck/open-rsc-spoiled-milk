package com.openrsc.server.extensions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic dependency resolver with transactional activation and reverse cleanup. */
public final class ExtensionRegistry {
	private final Map<String, ServerExtension> discovered = new LinkedHashMap<String, ServerExtension>();
	private final List<ServerExtension> active = new ArrayList<ServerExtension>();

	public void discover(final Collection<? extends ServerExtension> extensions) {
		if (!active.isEmpty()) throw new IllegalStateException("cannot discover while active");
		for (ServerExtension extension : extensions) {
			if (extension == null) throw new IllegalArgumentException("extension must not be null");
			final String id = extension.descriptor().getId();
			if (discovered.put(id, extension) != null) throw new IllegalArgumentException("duplicate extension id: " + id);
		}
	}
	public List<ServerExtension> resolve() {
		final List<ServerExtension> ordered = new ArrayList<ServerExtension>();
		final Set<String> visiting = new LinkedHashSet<String>(), visited = new LinkedHashSet<String>();
		final List<String> ids = new ArrayList<String>(discovered.keySet()); Collections.sort(ids);
		for (String id : ids) visit(id, visiting, visited, ordered);
		return Collections.unmodifiableList(ordered);
	}
	private void visit(final String id, final Set<String> visiting, final Set<String> visited, final List<ServerExtension> ordered) {
		if (visited.contains(id)) return;
		if (!visiting.add(id)) throw new IllegalStateException("extension dependency cycle: " + visiting);
		final ServerExtension extension = discovered.get(id);
		if (extension == null) throw new IllegalStateException("missing extension dependency: " + id);
		final List<String> dependencies = new ArrayList<String>(extension.descriptor().getDependencies()); Collections.sort(dependencies);
		for (String dependency : dependencies) visit(dependency, visiting, visited, ordered);
		visiting.remove(id); visited.add(id); ordered.add(extension);
	}
	public void activate(final ExtensionContext context) throws Exception {
		if (!active.isEmpty()) throw new IllegalStateException("extensions already active");
		try { for (ServerExtension extension : resolve()) { extension.activate(context); active.add(extension); } }
		catch (Exception failure) { deactivate(); throw failure; }
	}
	public void deactivate() {
		for (int index = active.size() - 1; index >= 0; index--) try {
			active.get(index).deactivate();
		} catch (Exception ignored) {
			// Continue cleanup: one extension must not retain another's resources.
			continue;
		}
		active.clear();
	}
	/** Clears discovery only after all active extensions have been deactivated. */
	public void reset() {
		if (!active.isEmpty()) throw new IllegalStateException("cannot reset active extensions");
		discovered.clear();
	}
	public List<String> activeIds() { final List<String> ids = new ArrayList<String>(); for (ServerExtension e : active) ids.add(e.descriptor().getId()); return Collections.unmodifiableList(ids); }
}
