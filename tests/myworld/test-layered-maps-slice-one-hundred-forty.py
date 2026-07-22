#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationOneShotConsumptionContract.java"
)
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationOneShotConsumptionContract.Decision;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationOneShotConsumptionContract.Postcondition;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationOneShotConsumptionContract.Precondition;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationOneShotConsumptionContract.RegionCommitOutcome;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationOneShotConsumptionContract.RequiredAction;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationOneShotConsumptionContract.Verification;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationOneShotConsumptionContract.VerificationReason;

public final class RestorationOneShotConsumptionFixture {
    public static void main(String[] args) {
        refusedCommitRetainsExactEventUnchanged();
        appliedAndNoOpRequireTerminalConsumption();
        invalidFenceAndOutcomeRefuse();
        invalidPostconditionsRefuse();
    }

    private static void refusedCommitRetainsExactEventUnchanged() {
        Decision decision = assess(RegionCommitOutcome.REFUSED, false, false);
        check(!decision.isRefused()
                && decision.getRequiredAction()
                    == RequiredAction.RETAIN_SCHEDULED,
            "Region refusal requires exact event retention");
        Verification verification =
            GameTickEventRestorationOneShotConsumptionContract
                .verifyPostcondition(decision,
                    Postcondition.declare(
                        true, true, false, true, 0, 12L,
                        false, false, false));
        check(verification.isSatisfied()
                && verification.getReason()
                    == VerificationReason.REFUSAL_RETAINED_UNCHANGED,
            "refusal postcondition preserves registration and lifecycle");
    }

    private static void appliedAndNoOpRequireTerminalConsumption() {
        for (RegionCommitOutcome outcome : new RegionCommitOutcome[] {
                RegionCommitOutcome.APPLIED, RegionCommitOutcome.NO_OP}) {
            Decision decision = assess(
                outcome, outcome == RegionCommitOutcome.APPLIED, true);
            check(!decision.isRefused()
                    && decision.getRequiredAction()
                        == RequiredAction.TERMINALLY_CONSUME,
                "satisfied desired state requires terminal consumption");
            Verification verification =
                GameTickEventRestorationOneShotConsumptionContract
                    .verifyPostcondition(decision,
                        Postcondition.declare(
                            false, false, true, false, 0, 13L,
                            false, false, true));
            check(verification.isSatisfied()
                    && verification.getReason()
                        == VerificationReason.EVENT_TERMINALLY_CONSUMED,
                "terminal postcondition removes exact registration");
        }
    }

    private static void invalidFenceAndOutcomeRefuse() {
        Precondition storeHeld = Precondition.declare(
            RegionCommitOutcome.APPLIED,
            true, true, true, true, true, true, true, true,
            true, 0, 12L, true, true, false);
        check(GameTickEventRestorationOneShotConsumptionContract
                .assess(storeHeld).isRefused(),
            "Store boundary across Region work refuses");
        Precondition inconsistent = Precondition.declare(
            RegionCommitOutcome.NO_OP,
            true, true, true, true, false, true, true, true,
            true, 0, 12L, true, true, false);
        check(GameTickEventRestorationOneShotConsumptionContract
                .assess(inconsistent).isRefused(),
            "no-op cannot claim a Region mutation");
        Precondition callbackRan = Precondition.declare(
            RegionCommitOutcome.APPLIED,
            true, true, true, true, false, true, true, true,
            true, 0, 12L, true, true, true);
        check(GameTickEventRestorationOneShotConsumptionContract
                .assess(callbackRan).isRefused(),
            "already-invoked callback refuses early restoration");
    }

    private static void invalidPostconditionsRefuse() {
        Decision consume = assess(RegionCommitOutcome.APPLIED, true, true);
        check(!GameTickEventRestorationOneShotConsumptionContract
                .verifyPostcondition(consume,
                    Postcondition.declare(
                        true, true, false, true, 0, 12L,
                        false, false, false))
                .isSatisfied(),
            "satisfied desired state cannot retain scheduled callback");
        check(!GameTickEventRestorationOneShotConsumptionContract
                .verifyPostcondition(consume,
                    Postcondition.declare(
                        false, false, true, false, 1, 13L,
                        true, false, true))
                .isSatisfied(),
            "callback execution cannot masquerade as consumption");
        Decision retain = assess(RegionCommitOutcome.REFUSED, false, false);
        check(!GameTickEventRestorationOneShotConsumptionContract
                .verifyPostcondition(retain,
                    Postcondition.declare(
                        false, false, true, false, 0, 13L,
                        false, false, true))
                .isSatisfied(),
            "Region refusal cannot consume the scheduled event");
    }

    private static Decision assess(
            RegionCommitOutcome outcome,
            boolean mutation,
            boolean desiredSatisfied) {
        return GameTickEventRestorationOneShotConsumptionContract.assess(
            Precondition.declare(
                outcome, true, true, true, true, false, true,
                true, true, true, 0, 12L, mutation,
                desiredSatisfied, false));
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredFortyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-restoration-one-shot-consumption-"
        )
        cls.classes = Path(cls.temp_dir.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.temp_dir.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationOneShotConsumptionFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(CONTRACT), str(fixture),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp_dir.cleanup()

    def test_consumption_contract_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationOneShotConsumptionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_contract_has_no_runtime_or_mutation_capability(self):
        source = CONTRACT.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.event.rsc.handler",
            "GameTickEvent event", "World world", "Region region",
            "GameObject object", "synchronized (", ".stop()", ".run()",
            "unregisterAccepted", "registerGameObject",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "RequiredAction.RETAIN_SCHEDULED",
            "RequiredAction.TERMINALLY_CONSUME",
            "isRuntimeHandleRetained() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_region_runtime_consumer_remains_disconnected(self):
        name = "GameTickEventRestorationOneShotConsumptionContract"
        self.assertIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, REGION_MANAGER.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_forty(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 140: One-shot restoration consumption contract",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn("refusal retains the exact event unchanged", normalized)
        self.assertIn("no runtime consumer", normalized)


if __name__ == "__main__":
    unittest.main()
