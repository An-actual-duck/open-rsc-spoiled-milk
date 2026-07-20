package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded whole-recipe topology evidence for authored reconstruction sources.
 *
 * <p>Each authored source is a node. A dependency coordinate containing
 * final-live authored content creates a directed owner-to-required-source
 * relationship; an empty coordinate remains external support. The analysis
 * compares an already completed forward cohort with the weak component that
 * can reach into or out of it, without interpreting graph membership as a
 * lifecycle unit.</p>
 *
 * <p>This value is detached diagnostic evidence only. It retains no entity,
 * Region, tile, archive, event, registry, cache, callback, claim, permit,
 * lease, transaction, commit, load, teardown, reconstruction, or rollback
 * authority.</p>
 */
public final class
	LayeredPackedRegionAuthoredReconstructionTopologyAnalysis {
	public static final int MAXIMUM_TOPOLOGY_SOURCES =
		LayeredPackedRegionAuthoredReconstructionRecipe.MAXIMUM_PACKED_SOURCES;
	public static final int MAXIMUM_RELATIONSHIPS =
		LayeredPackedRegionAuthoredReconstructionRecipe
			.MAXIMUM_AUTHORED_PLACEMENTS;

	private final long generation;
	private final long safetyObservedAtTick;
	private final int recipeSourceCount;
	private final List<SourceTopology> sources;
	private final List<KindTopology> kinds;
	private final List<ComponentTopology> weakComponents;
	private final List<ComponentTopology> strongComponents;
	private final int directedEdgeCount;
	private final int selfEdgeCount;
	private final int crossSourceDirectedEdgeCount;
	private final int authoredDependencyReferenceCount;
	private final int crossSourceAuthoredReferenceCount;
	private final int externalSupportSourceCount;
	private final int externalSupportEdgeCount;
	private final int externalSupportReferenceCount;
	private final int forwardCohortSourceCount;
	private final int forwardAuthoredSourceCount;
	private final int touchedWeakComponentCount;
	private final int conservativeConnectedSourceCount;
	private final int incomingOnlySourceCount;
	private final int directIncomingEdgeCount;
	private final int directIncomingReferenceCount;
	private final int conservativeConnectedEdgeCount;
	private final int conservativeConnectedReferenceCount;
	private final int largestWeakComponentSourceCount;
	private final int largestStrongComponentSourceCount;
	private final int cyclicStrongComponentCount;

	private LayeredPackedRegionAuthoredReconstructionTopologyAnalysis(
		final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
		final List<MutableNode> nodes,
		final List<MutableEdge> edges,
		final Set<Long> externalSupportSources,
		final Set<EdgeKey> externalSupportEdges,
		final MutableKindTopology[][] mutableKinds,
		final ComponentAssignment weak,
		final ComponentAssignment strong,
		final boolean[] forward,
		final boolean[] connected) {
		this.generation = recipe.getGeneration();
		this.safetyObservedAtTick = cohort.getSafetyObservedAtTick();
		this.recipeSourceCount = recipe.getSourceCount();
		this.forwardCohortSourceCount = cohort.getCohortSourceCount();
		int forwardSources = 0;
		int connectedSources = 0;
		int incomingSources = 0;
		for (int i = 0; i < nodes.size(); i++) {
			forwardSources += forward[i] ? 1 : 0;
			connectedSources += connected[i] ? 1 : 0;
			incomingSources += connected[i] && !forward[i] ? 1 : 0;
		}
		this.forwardAuthoredSourceCount = forwardSources;
		this.conservativeConnectedSourceCount = connectedSources;
		this.incomingOnlySourceCount = incomingSources;

		List<SourceTopology> immutableSources =
			new ArrayList<SourceTopology>(nodes.size());
		for (int i = 0; i < nodes.size(); i++) {
			immutableSources.add(new SourceTopology(
				nodes.get(i), weak.componentByNode[i],
				strong.componentByNode[i], forward[i], connected[i]));
		}
		this.sources = Collections.unmodifiableList(immutableSources);

		MutableComponentTopology[] weakSummaries =
			components(weak.componentCount, nodes, weak.componentByNode,
				forward, connected);
		MutableComponentTopology[] strongSummaries =
			components(strong.componentCount, nodes, strong.componentByNode,
				forward, connected);
		int edgesTotal = 0;
		int selfEdges = 0;
		int crossEdges = 0;
		int authoredReferences = 0;
		int crossReferences = 0;
		int incomingEdges = 0;
		int incomingReferences = 0;
		int connectedEdges = 0;
		int connectedReferences = 0;
		for (MutableEdge edge : edges) {
			edgesTotal = Math.incrementExact(edgesTotal);
			authoredReferences = Math.addExact(
				authoredReferences, edge.referenceCount);
			boolean self = edge.ownerIndex == edge.requiredIndex;
			if (self) {
				selfEdges = Math.incrementExact(selfEdges);
			} else {
				crossEdges = Math.incrementExact(crossEdges);
				crossReferences = Math.addExact(
					crossReferences, edge.referenceCount);
				MutableComponentTopology weakComponent =
					weakSummaries[weak.componentByNode[edge.ownerIndex]];
				weakComponent.edgeCount = Math.incrementExact(
					weakComponent.edgeCount);
				weakComponent.referenceCount = Math.addExact(
					weakComponent.referenceCount, edge.referenceCount);
				if (strong.componentByNode[edge.ownerIndex]
					== strong.componentByNode[edge.requiredIndex]) {
					MutableComponentTopology strongComponent =
						strongSummaries[
							strong.componentByNode[edge.ownerIndex]];
					strongComponent.edgeCount = Math.incrementExact(
						strongComponent.edgeCount);
					strongComponent.referenceCount = Math.addExact(
						strongComponent.referenceCount,
						edge.referenceCount);
				}
			}
			boolean directIncoming = !forward[edge.ownerIndex]
				&& forward[edge.requiredIndex];
			if (directIncoming) {
				incomingEdges = Math.incrementExact(incomingEdges);
				incomingReferences = Math.addExact(
					incomingReferences, edge.referenceCount);
			}
			if (forward[edge.ownerIndex] && !forward[edge.requiredIndex]) {
				throw new IllegalArgumentException(
					"Forward cohort omits an authored dependency source");
			}
			if (connected[edge.ownerIndex]) {
				if (!connected[edge.requiredIndex]) {
					throw new IllegalArgumentException(
						"Weak component assignment separates an edge");
				}
				connectedEdges = Math.incrementExact(connectedEdges);
				connectedReferences = Math.addExact(
					connectedReferences, edge.referenceCount);
			}
			for (ConstructionKind constructionKind
				: ConstructionKind.values()) {
				for (DependencyKind dependencyKind
					: DependencyKind.values()) {
					int references = edge.references[
						constructionKind.ordinal()][dependencyKind.ordinal()];
					if (references == 0) {
						continue;
					}
					MutableKindTopology kind = mutableKinds[
						constructionKind.ordinal()][dependencyKind.ordinal()];
					if (directIncoming) {
						kind.directIncomingReferenceCount = Math.addExact(
							kind.directIncomingReferenceCount, references);
					}
					if (connected[edge.ownerIndex]) {
						kind.conservativeConnectedReferenceCount =
							Math.addExact(
								kind.conservativeConnectedReferenceCount,
								references);
					}
				}
			}
		}
		this.directedEdgeCount = edgesTotal;
		this.selfEdgeCount = selfEdges;
		this.crossSourceDirectedEdgeCount = crossEdges;
		this.authoredDependencyReferenceCount = authoredReferences;
		this.crossSourceAuthoredReferenceCount = crossReferences;
		this.externalSupportSourceCount = externalSupportSources.size();
		this.externalSupportEdgeCount = externalSupportEdges.size();
		this.directIncomingEdgeCount = incomingEdges;
		this.directIncomingReferenceCount = incomingReferences;
		this.conservativeConnectedEdgeCount = connectedEdges;
		this.conservativeConnectedReferenceCount = connectedReferences;

		List<KindTopology> immutableKinds = new ArrayList<KindTopology>();
		int supportReferences = 0;
		for (ConstructionKind constructionKind : ConstructionKind.values()) {
			for (DependencyKind dependencyKind : DependencyKind.values()) {
				MutableKindTopology kind = mutableKinds[
					constructionKind.ordinal()][dependencyKind.ordinal()];
				if (kind != null) {
					immutableKinds.add(new KindTopology(kind));
					supportReferences = Math.addExact(supportReferences,
						kind.externalSupportReferenceCount);
				}
			}
		}
		this.kinds = Collections.unmodifiableList(immutableKinds);
		this.externalSupportReferenceCount = supportReferences;

		this.weakComponents = freezeComponents(weakSummaries);
		this.strongComponents = freezeComponents(strongSummaries);
		int touchedWeak = 0;
		int largestWeak = 0;
		for (ComponentTopology component : weakComponents) {
			touchedWeak += component.containsForwardCohort() ? 1 : 0;
			largestWeak = Math.max(largestWeak, component.getSourceCount());
		}
		this.touchedWeakComponentCount = touchedWeak;
		this.largestWeakComponentSourceCount = largestWeak;
		int largestStrong = 0;
		int cyclicStrong = 0;
		for (ComponentTopology component : strongComponents) {
			largestStrong = Math.max(largestStrong, component.getSourceCount());
			cyclicStrong += component.getSourceCount() > 1 ? 1 : 0;
		}
		this.largestStrongComponentSourceCount = largestStrong;
		this.cyclicStrongComponentCount = cyclicStrong;
	}

	/**
	 * Audits one complete detached recipe against one completed forward cohort.
	 * Relationship overflow is refused before an incomplete result is returned.
	 */
	public static LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
		analyze(
			final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
			final int maximumSources,
			final int maximumRelationships) {
		if (recipe == null) {
			throw new NullPointerException("recipe");
		}
		if (cohort == null) {
			throw new NullPointerException("cohort");
		}
		if (recipe.getGeneration() != cohort.getGeneration()) {
			throw new IllegalArgumentException(
				"Topology recipe and cohort generations differ");
		}
		if (maximumSources < 0 || maximumSources > MAXIMUM_TOPOLOGY_SOURCES
			|| maximumRelationships < 0
			|| maximumRelationships > MAXIMUM_RELATIONSHIPS) {
			throw new IllegalArgumentException(
				"Topology analysis budget is outside the supported range");
		}

		List<MutableNode> nodes = new ArrayList<MutableNode>();
		Map<Long, Integer> nodeIndexes = new LinkedHashMap<Long, Integer>();
		for (LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
			source : recipe.getSources()) {
			if (source.getReconstructionPlacementCount() == 0) {
				continue;
			}
			if (nodes.size() >= maximumSources) {
				throw new IllegalArgumentException(
					"Topology analysis exceeds its authored-source budget");
			}
			MutableNode node = new MutableNode(source);
			Long key = Long.valueOf(packedSourceKey(
				source.getPackedRegionX(), source.getPackedRegionY()));
			if (nodeIndexes.put(key, Integer.valueOf(nodes.size())) != null) {
				throw new IllegalArgumentException(
					"Topology recipe contains a duplicate authored source");
			}
			nodes.add(node);
		}

		boolean[] forward = new boolean[nodes.size()];
		int forwardAuthored = 0;
		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis source : cohort.getSources()) {
			LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
				sourceRecipe = recipe.findSource(
					source.getPackedRegionX(), source.getPackedRegionY());
			if ((sourceRecipe != null)
				!= source.isRecipeSourcePresent()
				|| (sourceRecipe == null ? 0
					: sourceRecipe.getReconstructionPlacementCount())
					!= source.getReconstructionPlacementCount()) {
				throw new IllegalArgumentException(
					"Topology cohort source differs from the recipe");
			}
			if (!source.hasAuthoredContent()) {
				continue;
			}
			Integer index = nodeIndexes.get(Long.valueOf(packedSourceKey(
				source.getPackedRegionX(), source.getPackedRegionY())));
			if (index == null || forward[index.intValue()]) {
				throw new IllegalArgumentException(
					"Topology cohort authored source is absent or duplicated");
			}
			forward[index.intValue()] = true;
			forwardAuthored = Math.incrementExact(forwardAuthored);
		}
		if (forwardAuthored != cohort.getAuthoredContentSourceCount()) {
			throw new IllegalArgumentException(
				"Topology cohort authored-source total differs");
		}

		MutableKindTopology[][] kinds = new MutableKindTopology[
			ConstructionKind.values().length][DependencyKind.values().length];
		Map<EdgeKey, MutableEdge> edges =
			new LinkedHashMap<EdgeKey, MutableEdge>();
		Set<EdgeKey> supportEdges = new LinkedHashSet<EdgeKey>();
		Set<Long> supportSources = new LinkedHashSet<Long>();
		for (int ownerIndex = 0; ownerIndex < nodes.size(); ownerIndex++) {
			LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
				source = nodes.get(ownerIndex).recipe;
			long ownerKey = packedSourceKey(
				source.getPackedRegionX(), source.getPackedRegionY());
			for (LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement : source.getPlacements()) {
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.PlacementDependency dependency = placement.getDependency();
				MutableKindTopology kind = kinds[placement.getKind().ordinal()]
					[dependency.getDependencyKind().ordinal()];
				if (kind == null) {
					kind = new MutableKindTopology(
						placement.getKind(), dependency.getDependencyKind());
					kinds[placement.getKind().ordinal()]
						[dependency.getDependencyKind().ordinal()] = kind;
				}
				for (int x = dependency.getMinimumPackedRegionX();
					x <= dependency.getMaximumPackedRegionX(); x++) {
					for (int y = dependency.getMinimumPackedRegionY();
						y <= dependency.getMaximumPackedRegionY(); y++) {
						long requiredKey = packedSourceKey(x, y);
						Integer requiredIndex = nodeIndexes.get(
							Long.valueOf(requiredKey));
						if (requiredIndex == null) {
							EdgeKey supportKey = new EdgeKey(
								ownerKey, requiredKey);
							if (!supportEdges.contains(supportKey)
								&& edges.size() + supportEdges.size()
									>= maximumRelationships) {
								throw new IllegalArgumentException(
									"Topology analysis exceeds its relationship budget");
							}
							supportEdges.add(supportKey);
							supportSources.add(Long.valueOf(requiredKey));
							kind.externalSupportReferenceCount = Math.addExact(
								kind.externalSupportReferenceCount, 1);
							continue;
						}
						EdgeKey edgeKey = new EdgeKey(ownerKey, requiredKey);
						MutableEdge edge = edges.get(edgeKey);
						if (edge == null) {
							if (edges.size() + supportEdges.size()
								>= maximumRelationships) {
								throw new IllegalArgumentException(
									"Topology analysis exceeds its relationship budget");
							}
							edge = new MutableEdge(
								ownerIndex, requiredIndex.intValue());
							edges.put(edgeKey, edge);
						}
						edge.record(placement.getKind(),
							dependency.getDependencyKind());
						kind.authoredDependencyReferenceCount = Math.addExact(
							kind.authoredDependencyReferenceCount, 1);
						if (ownerIndex != requiredIndex.intValue()) {
							kind.crossSourceAuthoredReferenceCount = Math.addExact(
								kind.crossSourceAuthoredReferenceCount, 1);
						}
					}
				}
			}
		}

		List<MutableEdge> orderedEdges =
			new ArrayList<MutableEdge>(edges.values());
		List<List<Integer>> outgoing = adjacency(nodes.size());
		List<List<Integer>> incoming = adjacency(nodes.size());
		List<List<Integer>> undirected = adjacency(nodes.size());
		for (MutableEdge edge : orderedEdges) {
			if (edge.ownerIndex == edge.requiredIndex) {
				continue;
			}
			outgoing.get(edge.ownerIndex).add(
				Integer.valueOf(edge.requiredIndex));
			incoming.get(edge.requiredIndex).add(
				Integer.valueOf(edge.ownerIndex));
			undirected.get(edge.ownerIndex).add(
				Integer.valueOf(edge.requiredIndex));
			undirected.get(edge.requiredIndex).add(
				Integer.valueOf(edge.ownerIndex));
		}
		for (int i = 0; i < nodes.size(); i++) {
			Collections.sort(outgoing.get(i));
			Collections.sort(incoming.get(i));
			Collections.sort(undirected.get(i));
		}
		ComponentAssignment weak = weakComponents(undirected);
		ComponentAssignment strong = strongComponents(outgoing, incoming);
		boolean[] touchedWeak = new boolean[weak.componentCount];
		for (int i = 0; i < nodes.size(); i++) {
			if (forward[i]) {
				touchedWeak[weak.componentByNode[i]] = true;
			}
		}
		boolean[] connected = new boolean[nodes.size()];
		for (int i = 0; i < nodes.size(); i++) {
			connected[i] = touchedWeak[weak.componentByNode[i]];
		}
		return new
			LayeredPackedRegionAuthoredReconstructionTopologyAnalysis(
				recipe, cohort, nodes, orderedEdges, supportSources,
				supportEdges, kinds, weak, strong, forward, connected);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public int getRecipeSourceCount() { return recipeSourceCount; }
	public List<SourceTopology> getSources() { return sources; }
	public int getAuthoredSourceCount() { return sources.size(); }
	public List<KindTopology> getKinds() { return kinds; }
	public int getKindCount() { return kinds.size(); }
	public List<ComponentTopology> getWeakComponents() {
		return weakComponents;
	}
	public int getWeakComponentCount() { return weakComponents.size(); }
	public List<ComponentTopology> getStrongComponents() {
		return strongComponents;
	}
	public int getStrongComponentCount() { return strongComponents.size(); }
	public int getDirectedEdgeCount() { return directedEdgeCount; }
	public int getSelfEdgeCount() { return selfEdgeCount; }
	public int getCrossSourceDirectedEdgeCount() {
		return crossSourceDirectedEdgeCount;
	}
	public int getAuthoredDependencyReferenceCount() {
		return authoredDependencyReferenceCount;
	}
	public int getCrossSourceAuthoredReferenceCount() {
		return crossSourceAuthoredReferenceCount;
	}
	public int getExternalSupportSourceCount() {
		return externalSupportSourceCount;
	}
	public int getExternalSupportEdgeCount() {
		return externalSupportEdgeCount;
	}
	public int getExternalSupportReferenceCount() {
		return externalSupportReferenceCount;
	}
	public int getForwardCohortSourceCount() {
		return forwardCohortSourceCount;
	}
	public int getForwardAuthoredSourceCount() {
		return forwardAuthoredSourceCount;
	}
	public int getTouchedWeakComponentCount() {
		return touchedWeakComponentCount;
	}
	public int getConservativeConnectedSourceCount() {
		return conservativeConnectedSourceCount;
	}
	public int getIncomingOnlySourceCount() { return incomingOnlySourceCount; }
	public int getDirectIncomingEdgeCount() { return directIncomingEdgeCount; }
	public int getDirectIncomingReferenceCount() {
		return directIncomingReferenceCount;
	}
	public int getConservativeConnectedEdgeCount() {
		return conservativeConnectedEdgeCount;
	}
	public int getConservativeConnectedReferenceCount() {
		return conservativeConnectedReferenceCount;
	}
	public int getLargestWeakComponentSourceCount() {
		return largestWeakComponentSourceCount;
	}
	public int getLargestStrongComponentSourceCount() {
		return largestStrongComponentSourceCount;
	}
	public int getCyclicStrongComponentCount() {
		return cyclicStrongComponentCount;
	}
	public boolean isForwardDependencyClosed() { return true; }
	public boolean isForwardCohortWeaklyClosed() {
		return incomingOnlySourceCount == 0;
	}
	public boolean isIdentityMetadataOnly() { return true; }
	public boolean isEntityRegistry() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** One authored source and its exact graph membership. */
	public static final class SourceTopology {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int reconstructionPlacementCount;
		private final int weakComponentOrdinal;
		private final int strongComponentOrdinal;
		private final boolean forwardCohortSource;
		private final boolean conservativeConnectedSource;

		private SourceTopology(
			final MutableNode node,
			final int weakComponentOrdinal,
			final int strongComponentOrdinal,
			final boolean forwardCohortSource,
			final boolean conservativeConnectedSource) {
			this.packedRegionX = node.recipe.getPackedRegionX();
			this.packedRegionY = node.recipe.getPackedRegionY();
			this.reconstructionPlacementCount =
				node.recipe.getReconstructionPlacementCount();
			this.weakComponentOrdinal = weakComponentOrdinal;
			this.strongComponentOrdinal = strongComponentOrdinal;
			this.forwardCohortSource = forwardCohortSource;
			this.conservativeConnectedSource = conservativeConnectedSource;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getReconstructionPlacementCount() {
			return reconstructionPlacementCount;
		}
		public int getWeakComponentOrdinal() {
			return weakComponentOrdinal;
		}
		public int getStrongComponentOrdinal() {
			return strongComponentOrdinal;
		}
		public boolean isForwardCohortSource() {
			return forwardCohortSource;
		}
		public boolean isConservativeConnectedSource() {
			return conservativeConnectedSource;
		}
		public boolean isIncomingOnlySource() {
			return conservativeConnectedSource && !forwardCohortSource;
		}
	}

	/** Reference totals for one construction/dependency-kind pair. */
	public static final class KindTopology {
		private final ConstructionKind constructionKind;
		private final DependencyKind dependencyKind;
		private final int authoredDependencyReferenceCount;
		private final int crossSourceAuthoredReferenceCount;
		private final int externalSupportReferenceCount;
		private final int directIncomingReferenceCount;
		private final int conservativeConnectedReferenceCount;

		private KindTopology(final MutableKindTopology source) {
			this.constructionKind = source.constructionKind;
			this.dependencyKind = source.dependencyKind;
			this.authoredDependencyReferenceCount =
				source.authoredDependencyReferenceCount;
			this.crossSourceAuthoredReferenceCount =
				source.crossSourceAuthoredReferenceCount;
			this.externalSupportReferenceCount =
				source.externalSupportReferenceCount;
			this.directIncomingReferenceCount =
				source.directIncomingReferenceCount;
			this.conservativeConnectedReferenceCount =
				source.conservativeConnectedReferenceCount;
		}

		public ConstructionKind getConstructionKind() {
			return constructionKind;
		}
		public DependencyKind getDependencyKind() { return dependencyKind; }
		public int getAuthoredDependencyReferenceCount() {
			return authoredDependencyReferenceCount;
		}
		public int getCrossSourceAuthoredReferenceCount() {
			return crossSourceAuthoredReferenceCount;
		}
		public int getExternalSupportReferenceCount() {
			return externalSupportReferenceCount;
		}
		public int getDirectIncomingReferenceCount() {
			return directIncomingReferenceCount;
		}
		public int getConservativeConnectedReferenceCount() {
			return conservativeConnectedReferenceCount;
		}
	}

	/** One weak or strong component, with cross-source internal edge totals. */
	public static final class ComponentTopology {
		private final int ordinal;
		private final int sourceCount;
		private final int edgeCount;
		private final int referenceCount;
		private final boolean forwardCohort;
		private final boolean conservativeConnected;

		private ComponentTopology(final MutableComponentTopology source) {
			this.ordinal = source.ordinal;
			this.sourceCount = source.sourceCount;
			this.edgeCount = source.edgeCount;
			this.referenceCount = source.referenceCount;
			this.forwardCohort = source.forwardCohort;
			this.conservativeConnected = source.conservativeConnected;
		}

		public int getOrdinal() { return ordinal; }
		public int getSourceCount() { return sourceCount; }
		public int getEdgeCount() { return edgeCount; }
		public int getReferenceCount() { return referenceCount; }
		public boolean containsForwardCohort() { return forwardCohort; }
		public boolean isConservativeConnected() {
			return conservativeConnected;
		}
	}

	private static List<List<Integer>> adjacency(final int size) {
		List<List<Integer>> result = new ArrayList<List<Integer>>(size);
		for (int i = 0; i < size; i++) {
			result.add(new ArrayList<Integer>());
		}
		return result;
	}

	private static ComponentAssignment weakComponents(
		final List<List<Integer>> adjacency) {
		int[] assignment = new int[adjacency.size()];
		Arrays.fill(assignment, -1);
		int component = 0;
		Deque<Integer> pending = new ArrayDeque<Integer>();
		for (int root = 0; root < adjacency.size(); root++) {
			if (assignment[root] >= 0) {
				continue;
			}
			assignment[root] = component;
			pending.addLast(Integer.valueOf(root));
			while (!pending.isEmpty()) {
				int node = pending.removeFirst().intValue();
				for (Integer neighborValue : adjacency.get(node)) {
					int neighbor = neighborValue.intValue();
					if (assignment[neighbor] < 0) {
						assignment[neighbor] = component;
						pending.addLast(Integer.valueOf(neighbor));
					}
				}
			}
			component = Math.incrementExact(component);
		}
		return new ComponentAssignment(assignment, component);
	}

	private static ComponentAssignment strongComponents(
		final List<List<Integer>> outgoing,
		final List<List<Integer>> incoming) {
		boolean[] visited = new boolean[outgoing.size()];
		List<Integer> finish = new ArrayList<Integer>(outgoing.size());
		Deque<DfsFrame> traversal = new ArrayDeque<DfsFrame>();
		for (int root = 0; root < outgoing.size(); root++) {
			if (visited[root]) {
				continue;
			}
			visited[root] = true;
			traversal.push(new DfsFrame(root));
			while (!traversal.isEmpty()) {
				DfsFrame frame = traversal.peek();
				List<Integer> targets = outgoing.get(frame.node);
				if (frame.nextTarget < targets.size()) {
					int target = targets.get(frame.nextTarget++).intValue();
					if (!visited[target]) {
						visited[target] = true;
						traversal.push(new DfsFrame(target));
					}
				} else {
					traversal.pop();
					finish.add(Integer.valueOf(frame.node));
				}
			}
		}
		int[] assignment = new int[outgoing.size()];
		Arrays.fill(assignment, -1);
		int component = 0;
		Deque<Integer> pending = new ArrayDeque<Integer>();
		for (int order = finish.size() - 1; order >= 0; order--) {
			int root = finish.get(order).intValue();
			if (assignment[root] >= 0) {
				continue;
			}
			assignment[root] = component;
			pending.push(Integer.valueOf(root));
			while (!pending.isEmpty()) {
				int node = pending.pop().intValue();
				for (Integer predecessorValue : incoming.get(node)) {
					int predecessor = predecessorValue.intValue();
					if (assignment[predecessor] < 0) {
						assignment[predecessor] = component;
						pending.push(Integer.valueOf(predecessor));
					}
				}
			}
			component = Math.incrementExact(component);
		}
		return new ComponentAssignment(assignment, component);
	}

	private static MutableComponentTopology[] components(
		final int count,
		final List<MutableNode> nodes,
		final int[] assignment,
		final boolean[] forward,
		final boolean[] connected) {
		MutableComponentTopology[] result =
			new MutableComponentTopology[count];
		for (int i = 0; i < count; i++) {
			result[i] = new MutableComponentTopology(i);
		}
		for (int i = 0; i < nodes.size(); i++) {
			MutableComponentTopology component = result[assignment[i]];
			component.sourceCount = Math.incrementExact(component.sourceCount);
			component.forwardCohort |= forward[i];
			component.conservativeConnected |= connected[i];
		}
		return result;
	}

	private static List<ComponentTopology> freezeComponents(
		final MutableComponentTopology[] sources) {
		List<ComponentTopology> result =
			new ArrayList<ComponentTopology>(sources.length);
		for (MutableComponentTopology source : sources) {
			result.add(new ComponentTopology(source));
		}
		return Collections.unmodifiableList(result);
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class MutableNode {
		private final LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe recipe;

		private MutableNode(
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe recipe) {
			this.recipe = recipe;
		}
	}

	private static final class MutableEdge {
		private final int ownerIndex;
		private final int requiredIndex;
		private final int[][] references = new int[
			ConstructionKind.values().length][DependencyKind.values().length];
		private int referenceCount;

		private MutableEdge(final int ownerIndex, final int requiredIndex) {
			this.ownerIndex = ownerIndex;
			this.requiredIndex = requiredIndex;
		}

		private void record(
			final ConstructionKind constructionKind,
			final DependencyKind dependencyKind) {
			references[constructionKind.ordinal()][dependencyKind.ordinal()] =
				Math.incrementExact(references[constructionKind.ordinal()]
					[dependencyKind.ordinal()]);
			referenceCount = Math.incrementExact(referenceCount);
		}
	}

	private static final class MutableKindTopology {
		private final ConstructionKind constructionKind;
		private final DependencyKind dependencyKind;
		private int authoredDependencyReferenceCount;
		private int crossSourceAuthoredReferenceCount;
		private int externalSupportReferenceCount;
		private int directIncomingReferenceCount;
		private int conservativeConnectedReferenceCount;

		private MutableKindTopology(
			final ConstructionKind constructionKind,
			final DependencyKind dependencyKind) {
			this.constructionKind = constructionKind;
			this.dependencyKind = dependencyKind;
		}
	}

	private static final class MutableComponentTopology {
		private final int ordinal;
		private int sourceCount;
		private int edgeCount;
		private int referenceCount;
		private boolean forwardCohort;
		private boolean conservativeConnected;

		private MutableComponentTopology(final int ordinal) {
			this.ordinal = ordinal;
		}
	}

	private static final class ComponentAssignment {
		private final int[] componentByNode;
		private final int componentCount;

		private ComponentAssignment(
			final int[] componentByNode,
			final int componentCount) {
			this.componentByNode = componentByNode;
			this.componentCount = componentCount;
		}
	}

	private static final class DfsFrame {
		private final int node;
		private int nextTarget;

		private DfsFrame(final int node) {
			this.node = node;
		}
	}

	private static final class EdgeKey {
		private final long owner;
		private final long required;

		private EdgeKey(final long owner, final long required) {
			this.owner = owner;
			this.required = required;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof EdgeKey)) {
				return false;
			}
			EdgeKey edge = (EdgeKey) other;
			return owner == edge.owner && required == edge.required;
		}

		@Override
		public int hashCode() {
			int result = (int) (owner ^ (owner >>> 32));
			result = 31 * result + (int) (required ^ (required >>> 32));
			return result;
		}
	}
}
