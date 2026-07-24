#!/usr/bin/env python3
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_SOURCE = ROOT / "server/src"
CORRELATION = SERVER_SOURCE / (
    "com/openrsc/server/model/world/region/"
    "LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


EVENT_INVENTORY_STUB = r"""
package com.openrsc.server.model.world.coordinate;

import java.util.List;

public final class LayeredPackedRegionEventOwnershipInventory {
    public static final int MAXIMUM_EVENTS = 65536;

    public enum OwnerKind { NONE, PLAYER, NPC }
    public enum AttributionKind {
        EXACT_SPATIAL, OWNER_POSITION_HINT, NON_SPATIAL_GLOBAL, UNATTRIBUTED
    }
    public enum RestorationKind {
        UNAVAILABLE, SCENERY_SPAWN, SCENERY_REMOVE
    }
    public enum AuthoredConstructionKind {
        SCENERY, BOUNDARY, NPC_SPAWN, GROUND_ITEM_SPAWN,
        HARVESTING_SCENERY
    }

    public static final class NpcOwnerIdentity {
        private final long generation;
        private final int x;
        private final int y;
        private final int sourceOrdinal;
        private final int runtimeNpcId;

        public NpcOwnerIdentity(
                long generation, int x, int y, int sourceOrdinal,
                int runtimeNpcId) {
            this.generation = generation;
            this.x = x;
            this.y = y;
            this.sourceOrdinal = sourceOrdinal;
            this.runtimeNpcId = runtimeNpcId;
        }

        public long getGeneration() { return generation; }
        public int getPackedRegionX() { return x; }
        public int getPackedRegionY() { return y; }
        public int getSourceOrdinal() { return sourceOrdinal; }
        public int getRuntimeNpcId() { return runtimeNpcId; }
    }

    public static final class AuthoredPlacementRestorationState {
        private final long generation;
        private final int x;
        private final int y;
        private final int sourceOrdinal;
        private final AuthoredConstructionKind kind;

        public AuthoredPlacementRestorationState(
                long generation, int x, int y, int sourceOrdinal,
                AuthoredConstructionKind kind) {
            this.generation = generation;
            this.x = x;
            this.y = y;
            this.sourceOrdinal = sourceOrdinal;
            this.kind = kind;
        }

        public long getGeneration() { return generation; }
        public int getPackedRegionX() { return x; }
        public int getPackedRegionY() { return y; }
        public int getSourceOrdinal() { return sourceOrdinal; }
        public AuthoredConstructionKind getConstructionKind() { return kind; }
    }

    public static final class SceneryRestorationState {
        private final int objectId;
        private final int permanentObjectId;
        private final int x;
        private final int y;
        private final int direction;
        private final int type;
        private final String owner;
        private final AuthoredPlacementRestorationState authored;

        public SceneryRestorationState(
                int objectId, int permanentObjectId, int x, int y,
                int direction, int type, String owner,
                AuthoredPlacementRestorationState authored) {
            this.objectId = objectId;
            this.permanentObjectId = permanentObjectId;
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.type = type;
            this.owner = owner;
            this.authored = authored;
        }

        public int getObjectId() { return objectId; }
        public int getPermanentObjectId() { return permanentObjectId; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getDirection() { return direction; }
        public int getType() { return type; }
        public String getOwner() { return owner; }
        public AuthoredPlacementRestorationState getAuthoredPlacement() {
            return authored;
        }
    }

    public static final class EventRestorationState {
        private final RestorationKind kind;
        private final SceneryRestorationState scenery;
        private final boolean complete;

        public EventRestorationState(
                RestorationKind kind, SceneryRestorationState scenery,
                boolean complete) {
            this.kind = kind;
            this.scenery = scenery;
            this.complete = complete;
        }

        public RestorationKind getKind() { return kind; }
        public SceneryRestorationState getScenery() { return scenery; }
        public boolean isDetachedCallbackPayloadComplete() {
            return complete;
        }
        public boolean isExecutionSemanticsCaptured() { return complete; }
        public boolean isTargetBindingRequirementCaptured() {
            return complete;
        }
        public boolean isTargetBindingComplete() { return complete; }
        public boolean isArrivalOrderingCaptured() { return complete; }
        public boolean isGenerationBindingRequirementCaptured() {
            return complete;
        }
        public boolean isGenerationBindingComplete(long generation) {
            return complete && scenery != null
                && scenery.getAuthoredPlacement() != null
                && scenery.getAuthoredPlacement().getGeneration()
                    == generation;
        }
        public boolean isIdempotencyRequirementCaptured() {
            return complete;
        }
    }

    public static final class EventRecord {
        private final int snapshotOrdinal;
        private final long registrationSequence;
        private final OwnerKind ownerKind;
        private final NpcOwnerIdentity npcOwner;
        private final AttributionKind attribution;
        private final boolean atomicTiming;
        private final List<Integer> candidateSources;
        private final EventRestorationState restoration;

        public EventRecord(
                int snapshotOrdinal, long registrationSequence,
                OwnerKind ownerKind, NpcOwnerIdentity npcOwner,
                AttributionKind attribution, boolean atomicTiming,
                List<Integer> candidateSources,
                EventRestorationState restoration) {
            this.snapshotOrdinal = snapshotOrdinal;
            this.registrationSequence = registrationSequence;
            this.ownerKind = ownerKind;
            this.npcOwner = npcOwner;
            this.attribution = attribution;
            this.atomicTiming = atomicTiming;
            this.candidateSources = candidateSources;
            this.restoration = restoration;
        }

        public int getSnapshotOrdinal() { return snapshotOrdinal; }
        public long getRegistrationSequence() {
            return registrationSequence;
        }
        public OwnerKind getOwnerKind() { return ownerKind; }
        public NpcOwnerIdentity getNpcOwnerIdentity() { return npcOwner; }
        public AttributionKind getAttributionKind() { return attribution; }
        public boolean isAtomicTimingCaptured() { return atomicTiming; }
        public List<Integer> getCandidateSourceOrdinals() {
            return candidateSources;
        }
        public boolean isCandidateRelated() {
            return !candidateSources.isEmpty();
        }
        public EventRestorationState getRestorationState() {
            return restoration;
        }
    }

    public static final class SourceRecord {
        private final int x;
        private final int y;

        public SourceRecord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getPackedRegionX() { return x; }
        public int getPackedRegionY() { return y; }
    }

    private final long generation;
    private final long observedAtTick;
    private final String schedulerIdentity;
    private final List<SourceRecord> sources;
    private final List<EventRecord> events;
    private final int candidateRelated;
    private final int unattributed;
    private final int nonSpatialGlobal;

    public LayeredPackedRegionEventOwnershipInventory(
            long generation, long observedAtTick, String schedulerIdentity,
            List<SourceRecord> sources, List<EventRecord> events,
            int candidateRelated, int unattributed, int nonSpatialGlobal) {
        this.generation = generation;
        this.observedAtTick = observedAtTick;
        this.schedulerIdentity = schedulerIdentity;
        this.sources = sources;
        this.events = events;
        this.candidateRelated = candidateRelated;
        this.unattributed = unattributed;
        this.nonSpatialGlobal = nonSpatialGlobal;
    }

    public long getProposalGeneration() { return generation; }
    public long getObservedAtTick() { return observedAtTick; }
    public String getSchedulerInstanceIdentity() {
        return schedulerIdentity;
    }
    public List<SourceRecord> getSources() { return sources; }
    public int getSourceCount() { return sources.size(); }
    public List<EventRecord> getEvents() { return events; }
    public int getEventCount() { return events.size(); }
    public int getCandidateRelatedEventCount() {
        return candidateRelated;
    }
    public int getUnattributedEventCount() { return unattributed; }
    public int getNonSpatialGlobalEventCount() {
        return nonSpatialGlobal;
    }
}
"""


REQUIREMENTS_STUB = r"""
package com.openrsc.server.model.world.coordinate;

import java.util.List;

public final class LayeredPackedRegionNpcOwnerPreservationRequirements {
    public static final class SelectedSource {
        private final int x;
        private final int y;

        public SelectedSource(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getPackedRegionX() { return x; }
        public int getPackedRegionY() { return y; }
    }

    public static final class OwnerRequirement {
        private final long generation;
        private final int x;
        private final int y;
        private final int sourceOrdinal;
        private final int runtimeNpcId;
        private final List<Long> registrations;

        public OwnerRequirement(
                long generation, int x, int y, int sourceOrdinal,
                int runtimeNpcId, List<Long> registrations) {
            this.generation = generation;
            this.x = x;
            this.y = y;
            this.sourceOrdinal = sourceOrdinal;
            this.runtimeNpcId = runtimeNpcId;
            this.registrations = registrations;
        }

        public long getGeneration() { return generation; }
        public int getPackedRegionX() { return x; }
        public int getPackedRegionY() { return y; }
        public int getSourceOrdinal() { return sourceOrdinal; }
        public int getRuntimeNpcId() { return runtimeNpcId; }
        public List<Long> getEventRegistrationSequences() {
            return registrations;
        }
    }

    private final long generation;
    private final long observedAtTick;
    private final String schedulerIdentity;
    private final List<SelectedSource> sources;
    private final List<OwnerRequirement> owners;
    private final int proposalRelated;
    private final int eventLinks;
    private final int relatedLinks;
    private final int supportingLinks;
    private final int npcEvents;
    private final int npcBlockers;
    private final int nonNpcEvents;

    public LayeredPackedRegionNpcOwnerPreservationRequirements(
            long generation, long observedAtTick, String schedulerIdentity,
            List<SelectedSource> sources, List<OwnerRequirement> owners,
            int proposalRelated, int eventLinks, int relatedLinks,
            int supportingLinks, int npcEvents, int npcBlockers,
            int nonNpcEvents) {
        this.generation = generation;
        this.observedAtTick = observedAtTick;
        this.schedulerIdentity = schedulerIdentity;
        this.sources = sources;
        this.owners = owners;
        this.proposalRelated = proposalRelated;
        this.eventLinks = eventLinks;
        this.relatedLinks = relatedLinks;
        this.supportingLinks = supportingLinks;
        this.npcEvents = npcEvents;
        this.npcBlockers = npcBlockers;
        this.nonNpcEvents = nonNpcEvents;
    }

    public long getGeneration() { return generation; }
    public long getEventObservedAtTick() { return observedAtTick; }
    public String getSchedulerInstanceIdentity() {
        return schedulerIdentity;
    }
    public int getSelectedSourceCount() { return sources.size(); }
    public List<SelectedSource> getSelectedSources() { return sources; }
    public List<OwnerRequirement> getOwners() { return owners; }
    public int getProposalRelatedEventCount() { return proposalRelated; }
    public int getEventLinkCount() { return eventLinks; }
    public int getRelatedEventLinkCount() { return relatedLinks; }
    public int getSupportingEventLinkCount() { return supportingLinks; }
    public int getNpcOwnerEventCount() { return npcEvents; }
    public int getNpcHardBlockerEventCount() { return npcBlockers; }
    public int getSeparateNonNpcOwnerEventCount() { return nonNpcEvents; }
}
"""


DETACHMENT_STUB = r"""
package com.openrsc.server.model.world.region;

import java.util.List;

public final class LayeredPackedRegionAuthoredObjectDetachmentPlan {
    public enum ConstructionKind { SCENERY, BOUNDARY, HARVESTING_SCENERY }

    public static final class ObjectDetachment {
        private final long generation;
        private final int sourceX;
        private final int sourceY;
        private final int sourceOrdinal;
        private final ConstructionKind kind;
        private final int objectId;
        private final int permanentObjectId;
        private final int x;
        private final int y;
        private final int direction;
        private final int type;
        private final String owner;

        public ObjectDetachment(
                long generation, int sourceX, int sourceY,
                int sourceOrdinal, ConstructionKind kind, int objectId,
                int permanentObjectId, int x, int y, int direction,
                int type, String owner) {
            this.generation = generation;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceOrdinal = sourceOrdinal;
            this.kind = kind;
            this.objectId = objectId;
            this.permanentObjectId = permanentObjectId;
            this.x = x;
            this.y = y;
            this.direction = direction;
            this.type = type;
            this.owner = owner;
        }

        public long getAuthoredGeneration() { return generation; }
        public int getSourcePackedRegionX() { return sourceX; }
        public int getSourcePackedRegionY() { return sourceY; }
        public int getAuthoredSourceOrdinal() { return sourceOrdinal; }
        public ConstructionKind getConstructionKind() { return kind; }
        public int getObjectId() { return objectId; }
        public int getPermanentObjectId() { return permanentObjectId; }
        public int getPackedX() { return x; }
        public int getPackedY() { return y; }
        public int getDirection() { return direction; }
        public int getObjectType() { return type; }
        public String getObjectOwner() { return owner; }
    }

    public static final class SourcePlan {
        private final int ordinal;
        private final int x;
        private final int y;
        private final List<ObjectDetachment> objects;

        public SourcePlan(
                int ordinal, int x, int y,
                List<ObjectDetachment> objects) {
            this.ordinal = ordinal;
            this.x = x;
            this.y = y;
            this.objects = objects;
        }

        public int getSelectedSourceOrdinal() { return ordinal; }
        public int getPackedRegionX() { return x; }
        public int getPackedRegionY() { return y; }
        public List<ObjectDetachment> getObjects() { return objects; }
    }

    private final long generation;
    private final long runtimeObservedAtTick;
    private final long authoredGeneration;
    private final String fingerprint;
    private final List<SourcePlan> sources;

    public LayeredPackedRegionAuthoredObjectDetachmentPlan(
            long generation, long runtimeObservedAtTick,
            long authoredGeneration, String fingerprint,
            List<SourcePlan> sources) {
        this.generation = generation;
        this.runtimeObservedAtTick = runtimeObservedAtTick;
        this.authoredGeneration = authoredGeneration;
        this.fingerprint = fingerprint;
        this.sources = sources;
    }

    public long getGeneration() { return generation; }
    public long getRuntimeObservedAtTick() { return runtimeObservedAtTick; }
    public long getAuthoredGeneration() { return authoredGeneration; }
    public String getFingerprintSha256() { return fingerprint; }
    public List<SourcePlan> getSources() { return sources; }
    public int getSourceCount() { return sources.size(); }
}
"""


FIXTURE = r"""
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.*;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SchedulerCorrelationFixture {
    private static final String SCHEDULER =
        "00000000-0000-0000-0000-000000000206";

    public static void main(String[] args) {
        LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment object =
            new LayeredPackedRegionAuthoredObjectDetachmentPlan
                .ObjectDetachment(
                    9L, 4, 0, 7,
                    LayeredPackedRegionAuthoredObjectDetachmentPlan
                        .ConstructionKind.SCENERY,
                    100, 100, 193, 10, 0, 0, null);
        LayeredPackedRegionAuthoredObjectDetachmentPlan plan =
            new LayeredPackedRegionAuthoredObjectDetachmentPlan(
                9L, 15L, 9L, repeat('a', 64),
                Collections.singletonList(
                    new LayeredPackedRegionAuthoredObjectDetachmentPlan
                        .SourcePlan(
                            0, 4, 0,
                            Collections.singletonList(object))));
        NpcOwnerIdentity npc =
            new NpcOwnerIdentity(9L, 4, 0, 30, 10);
        EventRestorationState unavailable =
            new EventRestorationState(
                RestorationKind.UNAVAILABLE, null, false);
        AuthoredPlacementRestorationState authored =
            new AuthoredPlacementRestorationState(
                9L, 4, 0, 7,
                AuthoredConstructionKind.SCENERY);
        EventRestorationState exact =
            new EventRestorationState(
                RestorationKind.SCENERY_SPAWN,
                new SceneryRestorationState(
                    100, 100, 193, 10, 0, 0, null, authored),
                true);
        List<EventRecord> events = Arrays.asList(
            event(
                0, 11L, OwnerKind.NPC, npc,
                AttributionKind.OWNER_POSITION_HINT, false,
                Collections.singletonList(Integer.valueOf(0)),
                unavailable),
            event(
                1, 12L, OwnerKind.NPC, npc,
                AttributionKind.NON_SPATIAL_GLOBAL, false,
                Collections.emptyList(), unavailable),
            event(
                2, 13L, OwnerKind.PLAYER, null,
                AttributionKind.OWNER_POSITION_HINT, false,
                Collections.singletonList(Integer.valueOf(0)),
                unavailable),
            event(
                3, 14L, OwnerKind.NONE, null,
                AttributionKind.UNATTRIBUTED, false,
                Collections.emptyList(), unavailable),
            event(
                4, 15L, OwnerKind.NPC, null,
                AttributionKind.OWNER_POSITION_HINT, false,
                Collections.emptyList(), unavailable),
            event(
                5, 16L, OwnerKind.NONE, null,
                AttributionKind.NON_SPATIAL_GLOBAL, false,
                Collections.emptyList(), unavailable),
            event(
                6, 17L, OwnerKind.NONE, null,
                AttributionKind.EXACT_SPATIAL, true,
                Collections.singletonList(Integer.valueOf(0)), exact),
            event(
                7, 18L, OwnerKind.NONE, null,
                AttributionKind.EXACT_SPATIAL, false,
                Collections.singletonList(Integer.valueOf(0)),
                unavailable));
        LayeredPackedRegionEventOwnershipInventory inventory =
            new LayeredPackedRegionEventOwnershipInventory(
                9L, 12L, SCHEDULER,
                Collections.singletonList(new SourceRecord(4, 0)),
                events, 4, 1, 2);
        OwnerRequirement owner = new OwnerRequirement(
            9L, 4, 0, 30, 10,
            Arrays.asList(Long.valueOf(11L), Long.valueOf(12L)));
        LayeredPackedRegionNpcOwnerPreservationRequirements requirements =
            new LayeredPackedRegionNpcOwnerPreservationRequirements(
                9L, 12L, SCHEDULER,
                Collections.singletonList(new SelectedSource(4, 0)),
                Collections.singletonList(owner),
                4, 2, 1, 1, 1, 0, 1);

        LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation correlation =
            LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
                .correlate(
                    plan, inventory, requirements,
                    LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
                        .MAXIMUM_RETAINED_EVENTS);
        check(correlation.getGeneration() == 9L
                && correlation.getEventObservedAtTick() == 12L
                && correlation.getDetachmentRuntimeObservedAtTick() == 15L
                && correlation.getSchedulerInstanceIdentity().equals(
                    SCHEDULER)
                && correlation.getDetachmentPlanFingerprintSha256().equals(
                    repeat('a', 64))
                && correlation.getSourceCount() == 1
                && correlation.getEventCount() == 8
                && correlation.getRetainedEventCount() == 6
                && correlation.getNpcOwnerFenceEventCount() == 2
                && correlation.getRelatedNpcOwnerFenceEventCount() == 1
                && correlation.getSupportingNpcOwnerFenceEventCount() == 1
                && correlation.getExactAuthoredRestorationEventCount() == 1
                && correlation
                    .getCandidateNpcOwnerUncorrelatedEventCount() == 0
                && correlation.getCandidateNonNpcOwnerEventCount() == 1
                && correlation
                    .getCandidateExactRestorationIncompleteEventCount() == 1
                && correlation.getUnattributedEventCount() == 1
                && correlation.getOutsideSelectionOwnerHintEventCount() == 1
                && correlation
                    .getOutsideSelectionExactSpatialEventCount() == 0
                && correlation.getNonSpatialGlobalEventCount() == 2
                && correlation.getBlockerEventCount() == 3
                && correlation.getFingerprintSha256().length() == 64,
            "detached scheduler classifications drifted");
        LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
            .SourceCorrelation source = correlation.getSources().get(0);
        check(source.getSelectedSourceOrdinal() == 0
                && source.getPackedRegionX() == 4
                && source.getPackedRegionY() == 0
                && source.getNpcOwnerFenceEventCount() == 1
                && source.getExactAuthoredRestorationEventCount() == 1
                && source.getNpcOwnerUncorrelatedEventCount() == 0
                && source.getNonNpcOwnerEventCount() == 1
                && source.getExactRestorationIncompleteEventCount() == 1
                && source.getBlockerEventReferenceCount() == 2,
            "source scheduler totals drifted");
        check(correlation.getRetainedEvents().get(0).getOutcome()
                    == LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
                        .EventOutcome.NPC_OWNER_FENCE
                && correlation.getRetainedEvents().get(4).getOutcome()
                    == LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
                        .EventOutcome.EXACT_AUTHORED_RESTORATION
                && correlation.getRetainedEvents().get(4)
                    .getMatchedSelectedSourceOrdinal() == 0
                && correlation.getRetainedEvents().get(4)
                    .getMatchedAuthoredSourceOrdinal() == 7
                && !correlation.getRetainedEvents().get(4).isBlocker(),
            "exact authored callback identity was not retained");
        expectUnsupported(() -> correlation.getSources().clear());
        expectUnsupported(() -> correlation.getRetainedEvents().clear());
        expectIllegal(() ->
            LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
                .correlate(plan, inventory, requirements, 5));
        check(correlation.areAllSchedulerEventsClassified()
                && !correlation.isDetachedSchedulerCorrelationComplete()
                && correlation.isSchedulerCorrelationPerformed()
                && correlation.isPointInTimeOnly()
                && correlation.isDetachedSummaryOnly()
                && !correlation.isRuntimeDetachmentReady()
                && !correlation.isSchedulerBoundaryEntered()
                && !correlation.isSchedulerIdentityRetained()
                && !correlation.isCallbackRetained()
                && !correlation.isRuntimeHandleRetained()
                && !correlation.isEventCancellation()
                && !correlation.isEventReschedule()
                && !correlation.isPreservationPerformed()
                && !correlation.isSourceAbsencePerformed()
                && !correlation.isSourceReconstructionPerformed()
                && !correlation.isRuntimeMutationAuthorized()
                && !correlation.isRuntimeMutationPerformed()
                && !correlation.isRegionRegistryMutated()
                && !correlation.isResidencyMirrorMutated()
                && !correlation.isVisibilityCacheMutated()
                && !correlation.isArrivalGate()
                && !correlation.isVisibilityReleased()
                && !correlation.isLifecycleAuthority(),
            "detached scheduler correlation crossed runtime authority");
        System.out.println("authored-detachment-scheduler-correlation-ok");
    }

    private static EventRecord event(
            int ordinal, long registration, OwnerKind ownerKind,
            NpcOwnerIdentity owner, AttributionKind attribution,
            boolean atomic, List<Integer> candidates,
            EventRestorationState restoration) {
        return new EventRecord(
            ordinal, registration, ownerKind, owner, attribution,
            atomic, candidates, restoration);
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String repeat(char value, int count) {
        char[] values = new char[count];
        Arrays.fill(values, value);
        return new String(values);
    }
}
"""


class LayeredMapsSliceTwoHundredSixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-authored-detachment-scheduler-correlation-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        sources = {
            (
                "com/openrsc/server/model/world/coordinate/"
                "LayeredPackedRegionEventOwnershipInventory.java"
            ): EVENT_INVENTORY_STUB,
            (
                "com/openrsc/server/model/world/coordinate/"
                "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
            ): REQUIREMENTS_STUB,
            (
                "com/openrsc/server/model/world/region/"
                "LayeredPackedRegionAuthoredObjectDetachmentPlan.java"
            ): DETACHMENT_STUB,
            (
                "com/openrsc/server/model/world/region/"
                "SchedulerCorrelationFixture.java"
            ): FIXTURE,
        }
        source_paths = []
        for relative, content in sources.items():
            path = cls.temp / "src" / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(
                textwrap.dedent(content).lstrip(), encoding="utf-8"
            )
            source_paths.append(path)
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                *(str(path) for path in source_paths),
                str(CORRELATION),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )
        cls.fixture_run = subprocess.run(
            [
                "java", "-cp", str(cls.classes),
                (
                    "com.openrsc.server.model.world.region."
                    "SchedulerCorrelationFixture"
                ),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_scheduler_events_reduce_to_fences_restorations_and_blockers(self):
        self.assertIn(
            "authored-detachment-scheduler-correlation-ok",
            self.fixture_run.stdout,
        )

    def test_correlation_retains_no_runtime_types_or_authority(self):
        source = CORRELATION.read_text(encoding="utf-8")
        fields = source.split(
            "LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation {",
            1,
        )[1].split(
            "private\n"
            "\t\tLayeredPackedRegionAuthoredDetachmentSchedulerCorrelation(",
            1,
        )[0]
        self.assertNotIn("GameTickEvent", fields)
        self.assertNotIn("Region ", fields)
        self.assertNotIn("GameObject", fields)
        self.assertIn(
            "isSchedulerCorrelationPerformed() { return true; }", source
        )
        self.assertIn(
            "isRuntimeDetachmentReady() { return false; }", source
        )
        self.assertIn(
            "isLifecycleAuthority() { return false; }", source
        )

    def test_plan_records_slice_206_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 206: Detached scheduler correlation", plan
        )
        self.assertIn("player-owned", plan)
        self.assertIn("unattributed", plan)
        self.assertIn("fresh atomic", plan)


if __name__ == "__main__":
    unittest.main()
