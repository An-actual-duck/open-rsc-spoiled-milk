#!/usr/bin/env python3
import math
import random
import statistics
import sys
from dataclasses import dataclass


GAME_TICK_MS = 640
POISON_TICK_INTERVAL_TICKS = 8
POISON_DRAIN_PER_TICK = 3
DEFAULT_ATTACK_INTERVAL_TICKS = 3
DEFAULT_DURATION_SECONDS = 180
DEFAULT_TRIALS = 20000
WEAPON_START_PROC = 1.00
WEAPON_FIRST_SUCCESS_PROC = 0.50
WEAPON_FLOOR_PROC = 0.20
ARMOR_START_PROC = 0.50
ARMOR_FLOOR_PROC = 0.10
PROC_STEP = 0.10
PROC_RECHARGE_FAILURES = 5


@dataclass(frozen=True)
class Scenario:
    name: str
    weapon_tier: int
    weapon_proc_chance: float
    armor_max_power: int
    armor_proc_chance: float
    armor_applied_power: int

    @property
    def weapon_max_power(self) -> int:
        return self.weapon_tier * 10

    @property
    def weapon_applied_power(self) -> int:
        return self.weapon_tier * 4

    @property
    def total_max_power(self) -> int:
        return self.weapon_max_power + self.armor_max_power


@dataclass
class ProcTracker:
    start_chance: float
    floor_chance: float
    first_success_chance: float | None = None
    chance: float = 0.0
    failures: int = 0

    def __post_init__(self) -> None:
        self.chance = self.start_chance

    def roll(self, rng: random.Random) -> bool:
        success = rng.random() < self.chance
        if success:
            if self.first_success_chance is not None and self.chance >= self.start_chance:
                self.chance = self.first_success_chance
            else:
                self.chance = max(self.floor_chance, self.chance - PROC_STEP)
            self.failures = 0
            return True

        self.failures += 1
        if self.failures >= PROC_RECHARGE_FAILURES:
            if self.first_success_chance is not None:
                if self.chance >= self.first_success_chance:
                    self.chance = self.start_chance
                else:
                    self.chance = min(self.first_success_chance, self.chance + PROC_STEP)
            else:
                self.chance = min(self.start_chance, self.chance + PROC_STEP)
            self.failures = 0
        return False


SCENARIOS = (
    Scenario(
        name="tier1_weapon_only",
        weapon_tier=1,
        weapon_proc_chance=0.20,
        armor_max_power=0,
        armor_proc_chance=0.0,
        armor_applied_power=0,
    ),
    Scenario(
        name="tier6_plus_20max_armor",
        weapon_tier=6,
        weapon_proc_chance=0.20,
        armor_max_power=20,
        armor_proc_chance=0.20,
        armor_applied_power=10,
    ),
    Scenario(
        name="tier11_plus_40max_poison_armor",
        weapon_tier=11,
        weapon_proc_chance=0.20,
        armor_max_power=40,
        armor_proc_chance=0.60,
        armor_applied_power=20,
    ),
)


def poison_tick_damage(poison_power: int) -> int:
    return int(round(poison_power / 10.0))


def maybe_apply_poison(
    current_power: int,
    scenario: Scenario,
    rng: random.Random,
    weapon_tracker: ProcTracker | None,
    armor_tracker: ProcTracker | None,
) -> int:
    weapon_proc = weapon_tracker is not None and weapon_tracker.roll(rng)
    armor_proc = armor_tracker is not None and armor_tracker.roll(rng)
    if not weapon_proc and not armor_proc:
        return current_power

    # Per spec, source applications are not additive on a single attack.
    # If both sources proc together, use the stronger applied value.
    applied_power = 0
    if weapon_proc:
        applied_power = max(applied_power, scenario.weapon_applied_power)
    if armor_proc:
        applied_power = max(applied_power, scenario.armor_applied_power)
    return min(scenario.total_max_power, current_power + applied_power)


def run_trial(
    scenario: Scenario,
    duration_seconds: int,
    attack_interval_ticks: int,
    rng: random.Random,
) -> dict:
    total_ticks = int(duration_seconds * 1000 / GAME_TICK_MS)
    poison_power = 0
    total_damage = 0
    cap_tick = None
    weapon_tracker = (
        ProcTracker(WEAPON_START_PROC, WEAPON_FLOOR_PROC, WEAPON_FIRST_SUCCESS_PROC)
        if scenario.weapon_tier > 0
        else None
    )
    armor_tracker = (
        ProcTracker(ARMOR_START_PROC, ARMOR_FLOOR_PROC)
        if scenario.armor_max_power > 0
        else None
    )

    for tick in range(1, total_ticks + 1):
        if tick % attack_interval_ticks == 0:
            poison_power = maybe_apply_poison(poison_power, scenario, rng, weapon_tracker, armor_tracker)
            if cap_tick is None and poison_power >= scenario.total_max_power:
                cap_tick = tick

        if tick % POISON_TICK_INTERVAL_TICKS == 0 and poison_power >= 10:
            total_damage += poison_tick_damage(poison_power)
            poison_power = max(0, poison_power - POISON_DRAIN_PER_TICK)

    return {
        "cap_tick": cap_tick,
        "total_damage": total_damage,
        "ending_power": poison_power,
    }


def summarize_scenario(
    scenario: Scenario,
    duration_seconds: int,
    attack_interval_ticks: int,
    trials: int,
    seed: int,
) -> dict:
    rng = random.Random(seed)
    results = [
        run_trial(scenario, duration_seconds, attack_interval_ticks, rng)
        for _ in range(trials)
    ]

    total_ticks = int(duration_seconds * 1000 / GAME_TICK_MS)
    horizon_ticks = {
        60: int(60 * 1000 / GAME_TICK_MS),
        120: int(120 * 1000 / GAME_TICK_MS),
        180: total_ticks,
    }

    cap_ticks = [r["cap_tick"] for r in results if r["cap_tick"] is not None]
    cap_seconds = [tick * GAME_TICK_MS / 1000.0 for tick in cap_ticks]

    chance_by_horizon = {}
    for seconds, ticks in horizon_ticks.items():
        capped = sum(1 for r in results if r["cap_tick"] is not None and r["cap_tick"] <= ticks)
        chance_by_horizon[seconds] = capped / trials

    avg_damage = statistics.mean(r["total_damage"] for r in results)
    avg_dps = avg_damage / duration_seconds
    avg_end_power = statistics.mean(r["ending_power"] for r in results)

    summary = {
        "scenario": scenario,
        "trials": trials,
        "duration_seconds": duration_seconds,
        "attack_interval_ticks": attack_interval_ticks,
        "attack_interval_seconds": attack_interval_ticks * GAME_TICK_MS / 1000.0,
        "poison_tick_seconds": POISON_TICK_INTERVAL_TICKS * GAME_TICK_MS / 1000.0,
        "chance_reach_cap_60s": chance_by_horizon[60],
        "chance_reach_cap_120s": chance_by_horizon[120],
        "chance_reach_cap_180s": chance_by_horizon[180],
        "avg_damage": avg_damage,
        "avg_dps": avg_dps,
        "avg_end_power": avg_end_power,
        "cap_samples": len(cap_seconds),
    }
    if cap_seconds:
        summary["median_cap_seconds"] = statistics.median(cap_seconds)
        summary["p90_cap_seconds"] = statistics.quantiles(cap_seconds, n=10)[8]
    else:
        summary["median_cap_seconds"] = None
        summary["p90_cap_seconds"] = None
    return summary


def format_percent(value: float) -> str:
    return f"{value * 100:.1f}%"


def format_seconds(value: float | None) -> str:
    if value is None:
        return "never"
    return f"{value:.1f}s"


def print_summary(summary: dict) -> None:
    scenario: Scenario = summary["scenario"]
    print(f"SCENARIO {scenario.name}")
    print(
        f"  weapon: tier {scenario.weapon_tier} "
        f"(max {scenario.weapon_max_power}, applied {scenario.weapon_applied_power}, proc ramp 100% -> 50% -> 20% floor)"
    )
    print(
        f"  armor: max {scenario.armor_max_power}, applied {scenario.armor_applied_power}, proc ramp 50% -> 10% floor"
    )
    print(
        f"  combined max: {scenario.total_max_power}, attack interval: {summary['attack_interval_seconds']:.2f}s, "
        f"poison tick: {summary['poison_tick_seconds']:.2f}s"
    )
    print(
        f"  reach cap by 60s/120s/180s: "
        f"{format_percent(summary['chance_reach_cap_60s'])} / "
        f"{format_percent(summary['chance_reach_cap_120s'])} / "
        f"{format_percent(summary['chance_reach_cap_180s'])}"
    )
    print(
        f"  cap timing (when reached): median {format_seconds(summary['median_cap_seconds'])}, "
        f"p90 {format_seconds(summary['p90_cap_seconds'])}"
    )
    print(
        f"  avg poison damage over {summary['duration_seconds']}s: {summary['avg_damage']:.2f} "
        f"(avg dps {summary['avg_dps']:.3f})"
    )
    print(f"  avg ending poison power: {summary['avg_end_power']:.2f}")
    print()


def main() -> int:
    duration_seconds = DEFAULT_DURATION_SECONDS
    attack_interval_ticks = DEFAULT_ATTACK_INTERVAL_TICKS
    trials = DEFAULT_TRIALS

    print("Poison balance simulation")
    print("Assumptions:")
    print(f"- server tick = {GAME_TICK_MS}ms")
    print(f"- poison ticks every {POISON_TICK_INTERVAL_TICKS} server ticks ({POISON_TICK_INTERVAL_TICKS * GAME_TICK_MS / 1000.0:.2f}s)")
    print(f"- successful hit opportunity every {attack_interval_ticks} ticks ({attack_interval_ticks * GAME_TICK_MS / 1000.0:.2f}s)")
    print("- every attack is treated as a successful hit to isolate poison throughput")
    print("- weapon poison starts at 100%, drops to 50% after first success, then steps down to a 20% floor")
    print("- armor poison starts at 50% and steps down to a 10% floor")
    print("- every 5 failed proc attempts recharges a source by one step, with weapon 50% jumping back to 100%")
    print(f"- poison drains {POISON_DRAIN_PER_TICK} power per poison tick")
    print("- if weapon and armor both proc on one attack, only the stronger applied value is used")
    print()

    for index, scenario in enumerate(SCENARIOS, start=1):
        summary = summarize_scenario(
            scenario=scenario,
            duration_seconds=duration_seconds,
            attack_interval_ticks=attack_interval_ticks,
            trials=trials,
            seed=1000 + index,
        )
        print_summary(summary)

    return 0


if __name__ == "__main__":
    sys.exit(main())
