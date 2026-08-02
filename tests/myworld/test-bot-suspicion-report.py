#!/usr/bin/env python3
"""Executable regression coverage for the offline bot-suspicion reporter."""

from __future__ import annotations

import gzip
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "report-bot-suspicion.py"
BASE_TIME = datetime(2026, 7, 5, 12, 0, 0)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def run_report(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(SCRIPT), *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )


def location_suffix(world: str | None, level: int | None) -> str:
    if world is None or level is None:
        return ""
    return f"; world={world}; level={level}"


def player_text(
    player: str,
    x: int = 10,
    y: int = 10,
    world: str | None = None,
    level: int | None = None,
) -> str:
    return f"[Player:1:{player} @ ({x}, {y}){location_suffix(world, level)}]"


def plugin_line(
    when: datetime,
    tick: int,
    player: str,
    *,
    action: str = "Mining.onOpLoc",
    target_id: int = 321,
    target_x: int = 11,
    target_y: int = 10,
    command: str = "mine",
    world: str | None = None,
    level: int | None = None,
) -> str:
    player_value = player_text(player, world=world, level=level)
    hook = action.rsplit(".", 1)[-1]
    if hook in {"onPlayerLogin", "onPlayerLogout"}:
        payload = f"[{player_value}]"
    elif hook == "onKillNpc":
        npc = (
            f"[NPC:{target_id}:Test target @ ({target_x}, {target_y})"
            f"{location_suffix(world, level)}]"
        )
        payload = f"[{player_value}, {npc}]"
    else:
        location = (
            f"0:id = {target_id}; dir = 0; location = ({target_x}, {target_y})"
            f"{location_suffix(world, level)};"
        )
        payload = f"[{player_value}, {location}, {command}]"
    return (
        f"{when:%Y-%m-%d %H:%M:%S} [Spoiled Milk : PluginThread-1] INFO  "
        f"PluginHandler: - Tick {tick} : {action} : [{payload}]\n"
    )


class LogBuilder:
    def __init__(self) -> None:
        self.when = BASE_TIME
        self.tick = 100
        self.lines: list[str] = []

    def advance(self, tick_gap: int) -> None:
        self.tick += tick_gap
        self.when += timedelta(seconds=tick_gap * 0.64)

    def emit(self, player: str, tick_gap: int = 1, **kwargs: object) -> None:
        self.advance(tick_gap)
        self.lines.append(plugin_line(self.when, self.tick, player, **kwargs))

    def restart(self, wall_gap_seconds: int = 60) -> None:
        self.when += timedelta(seconds=wall_gap_seconds)
        self.tick = 0


def create_db(path: Path, messages: list[tuple[str, int]]) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.execute(
            "CREATE TABLE generic_logs ("
            "id INTEGER PRIMARY KEY AUTOINCREMENT, message text NOT NULL, time int NOT NULL)"
        )
        connection.executemany(
            "INSERT INTO generic_logs(message, time) VALUES (?, ?)", messages
        )
        connection.commit()
    finally:
        connection.close()


def json_report(*args: str) -> dict[str, object]:
    result = run_report(*args, "--json", "--min-score", "0")
    require(result.returncode == 0, result.stderr or result.stdout)
    return json.loads(result.stdout)


def by_player(payload: dict[str, object]) -> dict[str, dict[str, object]]:
    reports = payload["reports"]
    require(isinstance(reports, list), "reports payload must be a list")
    return {str(report["player"]).casefold(): report for report in reports}


def initialize_detached_live_checkout(root: Path) -> None:
    root.mkdir(parents=True)
    subprocess.run(["git", "init", "-q", str(root)], check=True)
    marker = root / "README"
    marker.write_text("fixture\n", encoding="utf-8")
    subprocess.run(["git", "-C", str(root), "add", "README"], check=True)
    subprocess.run(
        [
            "git",
            "-C",
            str(root),
            "-c",
            "user.name=Bot Report Test",
            "-c",
            "user.email=bot-report@example.invalid",
            "commit",
            "-qm",
            "fixture",
        ],
        check=True,
    )
    subprocess.run(["git", "-C", str(root), "checkout", "-q", "--detach"], check=True)


with tempfile.TemporaryDirectory(prefix="bot-report-v2-test-") as tmp_dir_string:
    tmp_dir = Path(tmp_dir_string)
    current_log = tmp_dir / "spoiled_milk_98.log"
    rotated_log = tmp_dir / "spoiled_milk_98.20260704.log.gz"
    db_path = tmp_dir / "activity.db"
    builder = LogBuilder()

    # Fixed-timing actor: four distinct mining steps, 40 non-overlapping cycles.
    builder.emit("Scripted Actor", action="PlayerLogin.onPlayerLogin")
    scripted_steps = [(321, 11, 10), (322, 12, 10), (323, 12, 11), (324, 11, 11)]
    for _cycle in range(40):
        for target_id, target_x, target_y in scripted_steps:
            builder.emit(
                "Scripted Actor",
                tick_gap=5,
                target_id=target_id,
                target_x=target_x,
                target_y=target_y,
            )
    builder.emit("Scripted Actor", action="PlayerLogout.onPlayerLogout")

    # Ordinary repetitive skilling: one action, deliberately varied timing.
    builder.emit("Human Miner", action="PlayerLogin.onPlayerLogin")
    human_gaps = [3, 11, 5, 17, 4, 23, 8, 14, 6, 29, 9]
    for index in range(220):
        builder.emit("Human Miner", tick_gap=human_gaps[index % len(human_gaps)])
    builder.emit("Human Miner", action="PlayerLogout.onPlayerLogout")

    # Several hours of legitimate grinding, including visible AFK breaks.
    builder.emit("Long Grinder", action="PlayerLogin.onPlayerLogin")
    long_gaps = [10, 50, 17, 90, 24, 70, 12, 110]
    for index in range(600):
        if index and index % 150 == 0:
            builder.emit("Long Grinder", tick_gap=700)
        else:
            builder.emit("Long Grinder", tick_gap=long_gaps[index % len(long_gaps)])
    builder.emit("Long Grinder", action="PlayerLogout.onPlayerLogout")

    # AFK and explicit logout/login boundaries remain visible in session output.
    builder.emit("Interrupted Human", action="PlayerLogin.onPlayerLogin")
    for index in range(25):
        builder.emit("Interrupted Human", tick_gap=human_gaps[index % len(human_gaps)])
    builder.emit("Interrupted Human", tick_gap=1000)
    for index in range(25):
        builder.emit("Interrupted Human", tick_gap=human_gaps[(index + 3) % len(human_gaps)])
    builder.emit("Interrupted Human", action="PlayerLogout.onPlayerLogout")
    builder.advance(4000)
    builder.emit("Interrupted Human", action="PlayerLogin.onPlayerLogin")
    for index in range(25):
        builder.emit("Interrupted Human", tick_gap=human_gaps[(index + 5) % len(human_gaps)])

    # One intentional server restart/tick reset creates a global run boundary.
    builder.restart()
    builder.emit("Restart Witness", tick_gap=2)
    for index in range(25):
        builder.emit("Restart Witness", tick_gap=human_gaps[index % len(human_gaps)])

    # Overlap-only sequence versus a genuine contiguous, non-overlapping loop.
    for target_id in (401, 402, 401):
        builder.emit("Overlap Only", tick_gap=5, target_id=target_id)
    for _cycle in range(5):
        for target_id in (411, 412, 413):
            builder.emit("Genuine Loop", tick_gap=5, target_id=target_id)

    # Identical X/Y coordinates remain distinct when domain telemetry is present.
    builder.emit(
        "Layer Walker",
        tick_gap=5,
        world="global",
        level=-1,
        target_id=501,
    )
    builder.emit(
        "Layer Walker",
        tick_gap=5,
        world="instance:test",
        level=2,
        target_id=501,
    )

    # Formatting/capitalization variants must resolve to one player identity.
    name_variants = ["Mixed Name", "mixed_name", "MIXED.NAME"]
    for index in range(24):
        builder.emit(
            name_variants[index % len(name_variants)],
            tick_gap=human_gaps[index % len(human_gaps)],
            target_id=601,
        )

    for _index in range(3):
        builder.emit("Sparse Player", tick_gap=7, target_id=701)

    # High-frequency kill hooks are retained as summary evidence but never score.
    for _index in range(120):
        builder.emit(
            "Combat Grinder",
            tick_gap=4,
            action="Default.onKillNpc",
            target_id=42,
            target_x=15,
            target_y=15,
        )

    current_log.write_text("".join(builder.lines), encoding="utf-8")
    with gzip.open(rotated_log, "wt", encoding="utf-8") as handle:
        handle.write(plugin_line(BASE_TIME - timedelta(hours=1), 50, "Rotated Sparse"))

    generic_timestamp = int((builder.when + timedelta(seconds=1)).timestamp())
    create_db(
        db_path,
        [
            ("Shopper bought Bronze arrows x10 for 5gp at (10, 10)", generic_timestamp),
            ("shopper sold Bronze arrows x5 for 2gp at (10, 10)", generic_timestamp + 1),
            ("SHOPPER picked up Coins x1 at (10, 10)", generic_timestamp + 2),
        ],
    )
    db_before = db_path.read_bytes()

    payload = json_report(str(current_log), str(rotated_log), "--db", str(db_path))
    reports = by_player(payload)

    scripted = reports.get("scripted actor")
    require(scripted is not None, "fixed-timing scripted actor is missing")
    require(scripted["classification"] == "HIGH", f"scripted actor should be HIGH: {scripted}")
    require(
        set(scripted["independent_signals"])
        == {"timing_regularity", "non_overlapping_loops"},
        f"scripted actor needs two independent signals: {scripted['independent_signals']}",
    )
    require(scripted["score_is_calibrated"] is False, "score must be explicitly uncalibrated")
    require(scripted["loop"]["non_overlapping"] is True, "loop evidence must be non-overlapping")
    require(scripted["loop"]["session_bound"] is True, "loop evidence must be session-bound")

    human = reports.get("human miner")
    require(human is not None, "human miner is missing")
    require(human["classification"] == "WATCH", f"ordinary repetitive mining inflated: {human}")
    require(
        human["component_scores"]["repetition_context"]["score"] <= 6,
        "raw repetition must remain weak",
    )
    require(human["longest_run"]["score_contribution"] == 0, "same-action runs must not score")

    long_grinder = reports.get("long grinder")
    require(long_grinder is not None, "long human grinding fixture is missing")
    require(
        long_grinder["classification"] == "WATCH",
        f"long legitimate playtime must not be suspicious by itself: {long_grinder}",
    )
    require(
        sum(session["afk_breaks"]["count"] for session in long_grinder["sessions"]) >= 3,
        "long grinder AFK breaks were not reported",
    )

    interrupted = reports.get("interrupted human")
    require(interrupted is not None, "interrupted human is missing")
    require(len(interrupted["sessions"]) == 2, f"login/logout sessions not split: {interrupted['sessions']}")
    require(
        interrupted["sessions"][0]["afk_breaks"]["count"] >= 1,
        "within-session AFK break was not disclosed",
    )
    require(
        interrupted["sessions"][1]["start_reason"] in {"after_logout", "login"},
        f"second session boundary is unclear: {interrupted['sessions']}",
    )

    restart = reports.get("restart witness")
    require(restart is not None, "restart witness is missing")
    require(
        payload["dataSourceCoverage"]["tick_resets"] >= 1,
        "server tick reset was not detected",
    )
    require(
        payload["dataSourceCoverage"]["server_runs"] >= 2,
        "multiple server runs were not represented",
    )

    overlap = reports.get("overlap only")
    genuine = reports.get("genuine loop")
    require(overlap is not None and overlap["loop"] is None, f"overlapping window inflated: {overlap}")
    require(genuine is not None and genuine["loop"] is not None, "genuine loop was not detected")
    require(genuine["loop"]["repetitions"] == 5, f"genuine loop count is wrong: {genuine['loop']}")

    layer_walker = reports.get("layer walker")
    require(layer_walker is not None, "layer-aware fixture is missing")
    require(
        layer_walker["evidence_quality"]["explicit_domain_locations"] == 2,
        f"world-space/level telemetry was not retained: {layer_walker}",
    )
    require(
        layer_walker["unique_signatures"] == 2,
        "same X/Y on different world spaces/levels must not collapse",
    )

    mixed_reports = [key for key in reports if key == "mixed name"]
    require(len(mixed_reports) == 1, f"mixed name formatting was not normalized: {reports.keys()}")
    require(reports["mixed name"]["behavior_events"] == 24, "mixed name events did not merge")

    sparse = reports.get("sparse player")
    require(sparse is not None, "sparse fixture is missing")
    require(sparse["classification"] == "UNCLASSIFIED", f"sparse evidence classified: {sparse}")
    require(sparse["warnings"], "sparse report needs an evidence warning")

    combat = reports.get("combat grinder")
    require(combat is not None, "kill summary fixture is missing")
    require(combat["suspicion_score"] == 0, f"kill hook volume affected score: {combat}")
    require(combat["kill_evidence"]["count"] == 120, "kill events were not summarized")
    require(combat["kill_evidence"]["score_contribution"] == 0, "kill summary must not score")

    coverage = payload["dataSourceCoverage"]
    require(coverage["log_files_read"] == 2, f"current/rotated log coverage wrong: {coverage}")
    require(coverage["rotated_logs_read"] == 1, f"gzip rotated log not counted: {coverage}")
    require(coverage["generic_rows_scanned"] == 3, f"generic_logs coverage wrong: {coverage}")
    require(coverage["explicit_domain_locations"] >= 2, "explicit location coverage is missing")
    require(coverage["xy_only_locations"] > 0, "X/Y-only limitation should be visible")
    require(db_path.read_bytes() == db_before, "read-only report changed SQLite contents")
    require(not Path(str(db_path) + "-wal").exists(), "report created a SQLite WAL")

    filtered = json_report(
        str(current_log),
        "--no-db",
        "--player",
        "mixed name",
        "--since",
        f"{BASE_TIME:%Y-%m-%d %H:%M:%S}",
    )
    filtered_reports = by_player(filtered)
    require(list(filtered_reports) == ["mixed name"], f"player/time filter failed: {filtered_reports}")

    text_result = run_report(
        str(current_log),
        "--no-db",
        "--player",
        "Scripted Actor",
        "--min-score",
        "0",
    )
    require(text_result.returncode == 0, text_result.stderr or text_result.stdout)
    text_lower = text_result.stdout.casefold()
    require("uncalibrated suspicion score" in text_lower, "text output lacks score interpretation")
    require("probability" not in text_lower, "text output incorrectly claims probability")
    require("never proof" in text_lower, "text output lacks the administrative disclaimer")

    # --live must use a detached checkout, external DB link, current log, and rotated logs.
    missing_live = run_report(
        "--live",
        "--live-root",
        str(tmp_dir / "missing-live"),
        "--live-db-root",
        str(tmp_dir / "missing-live-db"),
        "--json",
    )
    require(missing_live.returncode == 2, "missing --live root should fail clearly")
    require("live checkout is missing" in missing_live.stderr, missing_live.stderr)

    live_root = tmp_dir / "live"
    live_db_root = tmp_dir / "external-live-db"
    initialize_detached_live_checkout(live_root)
    live_logs = live_root / "server" / "logs"
    live_logs.mkdir(parents=True)
    live_current = live_logs / "spoiled_milk_98.log"
    live_current.write_text(plugin_line(BASE_TIME, 1, "Live Fixture"), encoding="utf-8")
    live_rotated = live_logs / "spoiled_milk_98.20260704.log.gz"
    with gzip.open(live_rotated, "wt", encoding="utf-8") as handle:
        handle.write(plugin_line(BASE_TIME - timedelta(hours=1), 50, "Live Rotated"))
    live_db_root.mkdir(parents=True)
    external_db = live_db_root / "spoiled_milk_alpha.db"
    shutil.copyfile(db_path, external_db)
    checkout_db = live_root / "server" / "inc" / "sqlite" / "spoiled_milk_alpha.db"
    checkout_db.parent.mkdir(parents=True)
    checkout_db.symlink_to(external_db)

    live_payload = json_report(
        "--live",
        "--live-root",
        str(live_root),
        "--live-db-root",
        str(live_db_root),
    )
    require(live_payload["liveMode"] is True, "--live result is not labelled")
    require(live_payload["liveCommit"], "--live result lacks exact checkout commit")
    require(
        live_payload["dataSourceCoverage"]["log_files_read"] == 2,
        "--live did not discover current and rotated logs",
    )
    require(Path(live_payload["db"]).samefile(external_db), "--live used the wrong database")

    stale_time = (datetime.now() - timedelta(days=3)).timestamp()
    os.utime(live_current, (stale_time, stale_time))
    stale_live = run_report(
        "--live",
        "--live-root",
        str(live_root),
        "--live-db-root",
        str(live_db_root),
        "--live-max-log-age-hours",
        "24",
        "--json",
    )
    require(stale_live.returncode == 2, "stale --live current log should fail")
    require("current live plugin log is stale" in stale_live.stderr, stale_live.stderr)

print("PASS: bot suspicion report v2 regression fixtures")
