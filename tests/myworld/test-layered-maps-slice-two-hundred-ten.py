#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / (
    "server/src/com/openrsc/server/model/world/region/"
    "LayeredPackedRegionSchedulerBlockerFamilyInventory.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


INVENTORY_STUB = r"""
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

    public static final class EventTypeIdentity {
        private final String runtime;
        private final String family;
        private final String direct;
        private final boolean anonymous;
        private final boolean local;
        private final boolean synthetic;
        private final boolean captured;

        public EventTypeIdentity(
                String runtime, String family, String direct,
                boolean anonymous, boolean local, boolean synthetic,
                boolean captured) {
            this.runtime = runtime;
            this.family = family;
            this.direct = direct;
            this.anonymous = anonymous;
            this.local = local;
            this.synthetic = synthetic;
            this.captured = captured;
        }
        public String getRuntimeTypeName() { return runtime; }
        public String getFamilyTypeName() { return family; }
        public String getDirectSupertypeName() { return direct; }
        public boolean isAnonymousType() { return anonymous; }
        public boolean isLocalType() { return local; }
        public boolean isSyntheticType() { return synthetic; }
        public boolean isCaptured() { return captured; }
        public boolean isClassHandle() { return false; }
        public boolean isCallbackHandle() { return false; }
        public boolean isSchedulerHandle() { return false; }
        public boolean isLifecycleAuthority() { return false; }
    }

    public static final class EventRestorationState {
        private final RestorationKind kind;
        public EventRestorationState(RestorationKind kind) {
            this.kind = kind;
        }
        public RestorationKind getKind() { return kind; }
    }

    public static final class EventRecord {
        private final int ordinal;
        private final long registration;
        private final EventTypeIdentity type;
        private final OwnerKind owner;
        private final AttributionKind attribution;
        private final EventRestorationState restoration;
        private final boolean running;
        private final long ticks;
        private final int timesRan;
        private final List<Integer> candidates;

        public EventRecord(
                int ordinal, long registration, EventTypeIdentity type,
                OwnerKind owner, AttributionKind attribution,
                RestorationKind restoration, boolean running,
                long ticks, int timesRan, List<Integer> candidates) {
            this.ordinal = ordinal;
            this.registration = registration;
            this.type = type;
            this.owner = owner;
            this.attribution = attribution;
            this.restoration =
                new EventRestorationState(restoration);
            this.running = running;
            this.ticks = ticks;
            this.timesRan = timesRan;
            this.candidates = candidates;
        }
        public int getSnapshotOrdinal() { return ordinal; }
        public long getRegistrationSequence() { return registration; }
        public EventTypeIdentity getEventTypeIdentity() { return type; }
        public OwnerKind getOwnerKind() { return owner; }
        public AttributionKind getAttributionKind() {
            return attribution;
        }
        public EventRestorationState getRestorationState() {
            return restoration;
        }
        public boolean isRunning() { return running; }
        public long getTicksBeforeRun() { return ticks; }
        public int getTimesRan() { return timesRan; }
        public List<Integer> getCandidateSourceOrdinals() {
            return candidates;
        }
        public boolean isCandidateRelated() {
            return !candidates.isEmpty();
        }
    }

    private final long generation;
    private final long tick;
    private final String scheduler;
    private final List<EventRecord> events;

    public LayeredPackedRegionEventOwnershipInventory(
            long generation, long tick, String scheduler,
            List<EventRecord> events) {
        this.generation = generation;
        this.tick = tick;
        this.scheduler = scheduler;
        this.events = events;
    }
    public long getProposalGeneration() { return generation; }
    public long getObservedAtTick() { return tick; }
    public String getSchedulerInstanceIdentity() { return scheduler; }
    public List<EventRecord> getEvents() { return events; }
    public int getEventCount() { return events.size(); }
}
"""


CORRELATION_STUB = r"""
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import java.util.List;

public final class
        LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation {
    public enum EventOutcome {
        NPC_OWNER_FENCE(false),
        EXACT_AUTHORED_RESTORATION(false),
        CANDIDATE_NPC_OWNER_UNCORRELATED(true),
        CANDIDATE_NON_NPC_OWNER(true),
        CANDIDATE_EXACT_RESTORATION_INCOMPLETE(true),
        UNATTRIBUTED_BLOCKER(true);
        private final boolean blocker;
        EventOutcome(boolean blocker) { this.blocker = blocker; }
        public boolean isBlocker() { return blocker; }
    }

    public static final class EventCorrelation {
        private final int ordinal;
        private final long registration;
        private final OwnerKind owner;
        private final EventOutcome outcome;
        private final List<Integer> candidates;
        public EventCorrelation(
                int ordinal, long registration, OwnerKind owner,
                EventOutcome outcome, List<Integer> candidates) {
            this.ordinal = ordinal;
            this.registration = registration;
            this.owner = owner;
            this.outcome = outcome;
            this.candidates = candidates;
        }
        public int getSnapshotOrdinal() { return ordinal; }
        public long getRegistrationSequence() { return registration; }
        public OwnerKind getOwnerKind() { return owner; }
        public EventOutcome getOutcome() { return outcome; }
        public List<Integer> getCandidateSourceOrdinals() {
            return candidates;
        }
        public boolean isBlocker() { return outcome.isBlocker(); }
    }

    private final long generation;
    private final long tick;
    private final String scheduler;
    private final String fingerprint;
    private final int eventCount;
    private final int blockerCount;
    private final int npc;
    private final int nonNpc;
    private final int incomplete;
    private final int unattributed;
    private final List<EventCorrelation> retained;

    public LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation(
            long generation, long tick, String scheduler,
            String fingerprint, int eventCount, int blockerCount,
            int npc, int nonNpc, int incomplete, int unattributed,
            List<EventCorrelation> retained) {
        this.generation = generation;
        this.tick = tick;
        this.scheduler = scheduler;
        this.fingerprint = fingerprint;
        this.eventCount = eventCount;
        this.blockerCount = blockerCount;
        this.npc = npc;
        this.nonNpc = nonNpc;
        this.incomplete = incomplete;
        this.unattributed = unattributed;
        this.retained = retained;
    }
    public long getGeneration() { return generation; }
    public long getEventObservedAtTick() { return tick; }
    public String getSchedulerInstanceIdentity() { return scheduler; }
    public String getFingerprintSha256() { return fingerprint; }
    public int getEventCount() { return eventCount; }
    public int getBlockerEventCount() { return blockerCount; }
    public int getCandidateNpcOwnerUncorrelatedEventCount() {
        return npc;
    }
    public int getCandidateNonNpcOwnerEventCount() { return nonNpc; }
    public int getCandidateExactRestorationIncompleteEventCount() {
        return incomplete;
    }
    public int getUnattributedEventCount() { return unattributed; }
    public List<EventCorrelation> getRetainedEvents() { return retained; }
    public boolean areAllSchedulerEventsClassified() { return true; }
}
"""


FIXTURE = r"""
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.*;
import com.openrsc.server.model.world.region
    .LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SchedulerBlockerFamilyFixture {
    private static final String SCHEDULER =
        "00000000-0000-0000-0000-000000000210";

    public static void main(String[] args) {
        EventTypeIdentity npcType = type(
            "NpcPulse", "NpcPulse", "SingleTickEvent", false, true);
        EventTypeIdentity playerType = type(
            "PlayerPulse", "PlayerPulse", "SingleTickEvent", false, true);
        EventTypeIdentity objectType = type(
            "ObjectRemover", "ObjectRemover", "SingleTickEvent", false, true);
        EventTypeIdentity worldType = type(
            "World$1", "World", "SingleTickEvent", true, true);
        List<EventRecord> events = Arrays.asList(
            event(0, 11L, npcType, OwnerKind.NPC,
                AttributionKind.OWNER_POSITION_HINT, true, 4L, 1,
                Collections.singletonList(Integer.valueOf(0))),
            event(1, 12L, playerType, OwnerKind.PLAYER,
                AttributionKind.OWNER_POSITION_HINT, true, 2L, 3,
                Collections.singletonList(Integer.valueOf(0))),
            event(2, 13L, objectType, OwnerKind.NONE,
                AttributionKind.EXACT_SPATIAL, false, 8L, 0,
                Arrays.asList(Integer.valueOf(0), Integer.valueOf(1))),
            event(3, 14L, worldType, OwnerKind.NONE,
                AttributionKind.UNATTRIBUTED, false, -1L, 8,
                Collections.emptyList()),
            event(4, 15L, worldType, OwnerKind.NONE,
                AttributionKind.UNATTRIBUTED, false, 12L, 2,
                Collections.emptyList()));
        List<EventCorrelation> blockers = Arrays.asList(
            blocked(0, 11L, OwnerKind.NPC,
                EventOutcome.CANDIDATE_NPC_OWNER_UNCORRELATED,
                Collections.singletonList(Integer.valueOf(0))),
            blocked(1, 12L, OwnerKind.PLAYER,
                EventOutcome.CANDIDATE_NON_NPC_OWNER,
                Collections.singletonList(Integer.valueOf(0))),
            blocked(2, 13L, OwnerKind.NONE,
                EventOutcome.CANDIDATE_EXACT_RESTORATION_INCOMPLETE,
                Arrays.asList(Integer.valueOf(0), Integer.valueOf(1))),
            blocked(3, 14L, OwnerKind.NONE,
                EventOutcome.UNATTRIBUTED_BLOCKER,
                Collections.emptyList()),
            blocked(4, 15L, OwnerKind.NONE,
                EventOutcome.UNATTRIBUTED_BLOCKER,
                Collections.emptyList()));
        LayeredPackedRegionEventOwnershipInventory inventory =
            new LayeredPackedRegionEventOwnershipInventory(
                9L, 12L, SCHEDULER, events);
        LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation correlation =
            correlation(blockers, 5);
        LayeredPackedRegionSchedulerBlockerFamilyInventory reduced =
            LayeredPackedRegionSchedulerBlockerFamilyInventory.reduce(
                correlation, inventory, 4);
        check(reduced.getGeneration() == 9L
                && reduced.getEventObservedAtTick() == 12L
                && reduced.getSchedulerInstanceIdentity().equals(SCHEDULER)
                && reduced.getFamilyCount() == 4
                && reduced.getBlockerEventCount() == 5
                && reduced
                    .getCandidateNpcOwnerUncorrelatedEventCount() == 1
                && reduced.getCandidateNonNpcOwnerEventCount() == 1
                && reduced
                    .getCandidateExactRestorationIncompleteEventCount() == 1
                && reduced.getUnattributedEventCount() == 2
                && reduced.getRunningEventCount() == 2
                && reduced.getCandidateRelatedEventCount() == 3
                && reduced.getSelectedSourceReferenceCount() == 4
                && reduced.getFingerprintSha256().length() == 64,
            "blocker family aggregate drifted");
        LayeredPackedRegionSchedulerBlockerFamilyInventory.BlockerFamily world =
            reduced.getFamilies().get(3);
        check(world.getFamilyOrdinal() == 3
                && world.getOutcome()
                    == EventOutcome.UNATTRIBUTED_BLOCKER
                && world.getRuntimeTypeName().equals("World$1")
                && world.getFamilyTypeName().equals("World")
                && world.isAnonymousType()
                && world.getOwnerKind() == OwnerKind.NONE
                && world.getAttributionKind()
                    == AttributionKind.UNATTRIBUTED
                && world.getRestorationKind()
                    == RestorationKind.UNAVAILABLE
                && world.getEventCount() == 2
                && world.getRunningEventCount() == 0
                && world.getCandidateRelatedEventCount() == 0
                && world.getSelectedSourceReferenceCount() == 0
                && world.getFirstSnapshotOrdinal() == 3
                && world.getLastSnapshotOrdinal() == 4
                && world.getFirstRegistrationSequence() == 14L
                && world.getLastRegistrationSequence() == 15L
                && world.getMinimumTicksBeforeRun() == -1L
                && world.getMaximumTicksBeforeRun() == 12L
                && world.getMinimumTimesRan() == 2
                && world.getMaximumTimesRan() == 8,
            "unattributed family reduction drifted");
        check(reduced.areAllBlockersRetained()
                && reduced.isEventTypeIdentityComplete()
                && reduced.isPointInTimeOnly()
                && reduced.isDetachedSummaryOnly()
                && !reduced.isAttributionChanged()
                && !reduced.isRuntimeHandleRetained()
                && !reduced.isEventCancellation()
                && !reduced.isEventReschedule()
                && !reduced.isPreservationPerformed()
                && !reduced.isSourceAbsencePerformed()
                && !reduced.isSourceReconstructionPerformed()
                && !reduced.isRuntimeMutationAuthorized()
                && !reduced.isRuntimeMutationPerformed()
                && !reduced.isArrivalGate()
                && !reduced.isVisibilityReleased()
                && !reduced.isLifecycleAuthority(),
            "blocker family reduction crossed runtime authority");
        expectUnsupported(() -> reduced.getFamilies().clear());
        expectIllegal(() ->
            LayeredPackedRegionSchedulerBlockerFamilyInventory.reduce(
                correlation, inventory, 3));
        expectIllegal(() ->
            LayeredPackedRegionSchedulerBlockerFamilyInventory.reduce(
                correlation(blockers.subList(0, 4), 5), inventory, 4));

        EventRecord unknown = event(
            0, 11L, type("unknown", "unknown", "unknown", false, false),
            OwnerKind.NPC, AttributionKind.OWNER_POSITION_HINT,
            false, 0L, 0, Collections.singletonList(Integer.valueOf(0)));
        expectIllegal(() ->
            LayeredPackedRegionSchedulerBlockerFamilyInventory.reduce(
                correlation(
                    Collections.singletonList(blockers.get(0)), 1),
                new LayeredPackedRegionEventOwnershipInventory(
                    9L, 12L, SCHEDULER,
                    Collections.singletonList(unknown)),
                1));
        System.out.println("scheduler-blocker-family-reduction-ok");
    }

    private static EventTypeIdentity type(
            String runtime, String family, String direct,
            boolean anonymous, boolean captured) {
        return new EventTypeIdentity(
            runtime, family, direct, anonymous, false, false, captured);
    }

    private static EventRecord event(
            int ordinal, long registration, EventTypeIdentity type,
            OwnerKind owner, AttributionKind attribution, boolean running,
            long ticks, int timesRan, List<Integer> candidates) {
        return new EventRecord(
            ordinal, registration, type, owner, attribution,
            RestorationKind.UNAVAILABLE, running, ticks, timesRan,
            candidates);
    }

    private static EventCorrelation blocked(
            int ordinal, long registration, OwnerKind owner,
            EventOutcome outcome, List<Integer> candidates) {
        return new EventCorrelation(
            ordinal, registration, owner, outcome, candidates);
    }

    private static
        LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation correlation(
            List<EventCorrelation> retained, int blockers) {
        return new
            LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation(
                9L, 12L, SCHEDULER,
                repeat('a', 64), 5, blockers, 1, 1, 1, 2, retained);
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


class LayeredMapsSliceTwoHundredTenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-scheduler-blocker-families-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        sources = {
            (
                "com/openrsc/server/model/world/coordinate/"
                "LayeredPackedRegionEventOwnershipInventory.java"
            ): INVENTORY_STUB,
            (
                "com/openrsc/server/model/world/region/"
                "LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation.java"
            ): CORRELATION_STUB,
            (
                "com/openrsc/server/model/world/region/"
                "SchedulerBlockerFamilyFixture.java"
            ): FIXTURE,
        }
        paths = []
        for relative, content in sources.items():
            path = cls.temp / "src" / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(
                textwrap.dedent(content).lstrip(), encoding="utf-8"
            )
            paths.append(path)
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                *(str(path) for path in paths), str(SOURCE),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )
        cls.fixture_run = subprocess.run(
            [
                "java", "-cp", str(cls.classes),
                (
                    "com.openrsc.server.model.world.region."
                    "SchedulerBlockerFamilyFixture"
                ),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_all_blockers_reduce_to_bounded_stable_families(self):
        self.assertIn(
            "scheduler-blocker-family-reduction-ok",
            self.fixture_run.stdout,
        )

    def test_reduction_retains_no_runtime_types_or_authority(self):
        source = SOURCE.read_text(encoding="utf-8")
        fields = source.split(
            "LayeredPackedRegionSchedulerBlockerFamilyInventory {",
            1,
        )[1].split(
            "private LayeredPackedRegionSchedulerBlockerFamilyInventory(",
            1,
        )[0]
        for forbidden in (
            "GameTickEvent ", "Class<?>", "Region ", "Mob ",
            "Player ", "Npc ", "Scheduler ",
        ):
            self.assertNotIn(forbidden, fields)
        self.assertIn(
            "isAttributionChanged() { return false; }", source
        )
        self.assertIn(
            "isLifecycleAuthority() { return false; }", source
        )

    def test_plan_records_slice_210_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 210: Bounded scheduler-blocker family reduction",
            plan,
        )
        self.assertIn("uncaptured type identities", plan)
        self.assertIn("UNATTRIBUTED", plan)


if __name__ == "__main__":
    unittest.main()
