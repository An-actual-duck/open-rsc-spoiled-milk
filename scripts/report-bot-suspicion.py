#!/usr/bin/env python3
"""Build read-only, uncalibrated bot-suspicion review leads."""

from __future__ import annotations

import argparse
import bisect
import gzip
import json
import math
import os
import re
import sqlite3
import statistics
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field, replace
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable
from urllib.parse import quote


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DB = ROOT / "server" / "inc" / "sqlite" / "spoiled_milk_alpha.db"
DEFAULT_LOG = ROOT / "server" / "logs" / "spoiled_milk_98.log"
DEFAULT_ROTATED_LOG_GLOB = "spoiled_milk_98.*.log.gz"
DEFAULT_LIVE_ROOT = Path("/tmp/spoiled-milk-live-main")
DEFAULT_LIVE_DB_ROOT = Path.home() / ".local" / "share" / "spoiled-milk" / "live"
LIVE_DB_NAME = "spoiled_milk_alpha.db"
TICK_SECONDS = 0.64
DEFAULT_SESSION_GAP_SECONDS = 30 * 60
AFK_BREAK_SECONDS = 2 * 60

PLUGIN_RE = re.compile(
    r"^(?P<date>\d{4}-\d\d-\d\d \d\d:\d\d:\d\d) "
    r".*?PluginHandler: - Tick (?P<tick>\d+) : "
    r"(?P<action>[A-Za-z0-9_$]+\.[A-Za-z0-9_$]+) : \[(?P<payload>.*)\]$"
)
PLAYER_RE = re.compile(
    r"\[Player:(?P<id>-?\d+):(?P<name>.+?) @ "
    r"\((?P<x>-?\d+), (?P<y>-?\d+)\)"
    r"(?:; world=(?P<world>[^;\]]+); level=(?P<level>-?\d+))?\]"
)
NPC_RE = re.compile(
    r"\[NPC:(?P<id>-?\d+):(?P<name>.+?) @ "
    r"\((?P<x>-?\d+), (?P<y>-?\d+)\)"
    r"(?:; world=(?P<world>[^;\]]+); level=(?P<level>-?\d+))?\]"
)
LOC_RE = re.compile(
    r"(?P<index>\d+):id = (?P<id>-?\d+); dir = (?P<dir>-?\d+); "
    r"location = \((?P<x>-?\d+), (?P<y>-?\d+)\)"
    r"(?:; world=(?P<world>[^;]+); level=(?P<level>-?\d+))?;"
)
ITEM_RE = re.compile(
    r"Item\((?P<id>-?\d+), (?P<amount>-?\d+)(?:, (?P<noted>true|false))?\)"
    r"(?: location = \((?P<x>-?\d+), (?P<y>-?\d+)\)"
    r"(?:; world=(?P<world>[^;]+); level=(?P<level>-?\d+))?)?"
)
GENERIC_LOCATION = (
    r"\((?P<x>-?\d+), (?P<y>-?\d+)\)"
    r"(?:; world=(?P<world>[^;]+); level=(?P<level>-?\d+))?"
)
GENERIC_LOG_PATTERNS = [
    (
        re.compile(
            rf"^(?P<player>.+?) picked up (?P<item>.+?) x(?P<amount>\d+) at {GENERIC_LOCATION}$"
        ),
        "picked_up",
    ),
    (
        re.compile(
            rf"^(?P<player>.+?) bought (?P<item>.+?) x(?P<amount>\d+) "
            rf"for (?P<coins>\d+)gp at {GENERIC_LOCATION}$"
        ),
        "bought",
    ),
    (
        re.compile(
            rf"^(?P<player>.+?) sold (?P<item>.+?) x(?P<amount>\d+) "
            rf"for (?P<coins>\d+)gp at {GENERIC_LOCATION}$"
        ),
        "sold",
    ),
    (
        re.compile(
            rf"^(?P<player>.+?) dropped (?P<item>.+?) x(?P<amount>\d+) at {GENERIC_LOCATION}$"
        ),
        "dropped",
    ),
    (
        re.compile(
            rf"^(?P<player>.+?) telegrabbed (?P<item>.+?) x(?P<amount>\d+) at {GENERIC_LOCATION}$"
        ),
        "telegrabbed",
    ),
    (
        re.compile(r"^(?P<player>.+?) guessed !_.*_! for filename:: (?P<filename>.+)$"),
        "sleep_guess",
    ),
    (
        re.compile(r"^(?P<player>.+?) has failed sleeping captcha (?P<count>\d+) times!$"),
        "sleep_failed",
    ),
]

LIFECYCLE_HOOKS = {"onPlayerLogin", "onPlayerLogout"}
BACKGROUND_HOOKS = {"onCatGrowth", "onCommand", "onStartup", "onTimedEvent", "onWineFerment"}
KILL_HOOKS = {"onKillNpc"}
COLOUR_TAG_RE = re.compile(r"@[A-Za-z0-9]{3}@")

# These are operational cohorts, not empirically calibrated player baselines.
# Keeping their minimum sample sizes separate prevents unrelated activities from
# being pooled into one timing or loop signal.
ACTIVITY_PROFILES: dict[str, dict[str, int]] = {
    "combat": {"minimum_timing_samples": 24, "minimum_loop_repetitions": 5},
    "mining": {"minimum_timing_samples": 20, "minimum_loop_repetitions": 4},
    "smithing": {"minimum_timing_samples": 20, "minimum_loop_repetitions": 4},
    "banking": {"minimum_timing_samples": 16, "minimum_loop_repetitions": 5},
    "shopping": {"minimum_timing_samples": 16, "minimum_loop_repetitions": 5},
    "agility": {"minimum_timing_samples": 24, "minimum_loop_repetitions": 12},
    "woodcutting": {"minimum_timing_samples": 20, "minimum_loop_repetitions": 4},
    "fishing": {"minimum_timing_samples": 20, "minimum_loop_repetitions": 4},
    "cooking": {"minimum_timing_samples": 20, "minimum_loop_repetitions": 4},
    "crafting": {"minimum_timing_samples": 20, "minimum_loop_repetitions": 4},
    "loot": {"minimum_timing_samples": 24, "minimum_loop_repetitions": 5},
    "other": {"minimum_timing_samples": 20, "minimum_loop_repetitions": 5},
}


class LiveSourceError(RuntimeError):
    """Raised when --live cannot prove that it found live-only sources."""


@dataclass(frozen=True)
class Location:
    x: int
    y: int
    world_space: str | None = None
    level: int | None = None

    @property
    def has_domain(self) -> bool:
        return self.world_space is not None and self.level is not None

    @property
    def signature(self) -> str:
        if self.has_domain:
            return f"{self.world_space}:L{self.level}:{self.x},{self.y}"
        return f"unknown-world:unknown-level:{self.x},{self.y}"

    @property
    def display(self) -> str:
        if self.has_domain:
            return f"{self.x},{self.y},{self.world_space},L{self.level}"
        return f"{self.x},{self.y},world/level unknown"


@dataclass(frozen=True)
class Event:
    source: str
    source_kind: str
    timestamp: int
    tick: int | None
    ordinal: int
    player: str
    action: str
    hook: str
    player_pos: Location | None
    target_type: str
    target_id: str
    target_name: str
    target_pos: Location | None
    command: str
    detail: str
    server_run: int = 1

    @property
    def signature(self) -> str:
        return "|".join(
            [
                self.action,
                self.target_type,
                self.target_id,
                self.target_name,
                format_location(self.target_pos),
                self.command,
            ]
        )

    @property
    def sequence_signature(self) -> str:
        return "|".join(
            [
                self.action,
                self.command,
                self.target_type,
                self.target_id,
                format_location(self.player_pos),
                format_location(self.target_pos),
            ]
        )

    @property
    def short_signature(self) -> str:
        target = self.target_name or self.target_id or self.target_type
        pieces = [self.action]
        if self.command:
            pieces.append(self.command)
        if target:
            pieces.append(target)
        if self.target_pos:
            pieces.append("@" + self.target_pos.display)
        return " ".join(pieces)

    @property
    def is_lifecycle(self) -> bool:
        return self.hook in LIFECYCLE_HOOKS

    @property
    def is_background(self) -> bool:
        return self.hook in BACKGROUND_HOOKS

    @property
    def is_kill(self) -> bool:
        return self.hook in KILL_HOOKS

    @property
    def score_eligible(self) -> bool:
        return not self.is_lifecycle and not self.is_background and not self.is_kill


@dataclass
class Coverage:
    log_files_requested: int = 0
    log_files_read: int = 0
    rotated_logs_read: int = 0
    log_lines_scanned: int = 0
    plugin_lines_matched: int = 0
    plugin_events_parsed: int = 0
    plugin_events_ignored: int = 0
    generic_rows_scanned: int = 0
    generic_events_parsed: int = 0
    explicit_domain_locations: int = 0
    xy_only_locations: int = 0
    missing_locations: int = 0
    lifecycle_events: int = 0
    kill_events: int = 0
    server_runs: int = 0
    tick_resets: int = 0
    source_details: list[dict[str, object]] = field(default_factory=list)

    def record_event(self, event: Event) -> None:
        if event.player_pos is None:
            self.missing_locations += 1
        elif event.player_pos.has_domain:
            self.explicit_domain_locations += 1
        else:
            self.xy_only_locations += 1
        if event.is_lifecycle:
            self.lifecycle_events += 1
        if event.is_kill:
            self.kill_events += 1

    def to_dict(self) -> dict[str, object]:
        if self.explicit_domain_locations and self.xy_only_locations:
            telemetry = "partial-world-space-and-level"
        elif self.explicit_domain_locations:
            telemetry = "world-space-and-level"
        elif self.xy_only_locations:
            telemetry = "xy-only"
        else:
            telemetry = "unavailable"
        return {
            "log_files_requested": self.log_files_requested,
            "log_files_read": self.log_files_read,
            "rotated_logs_read": self.rotated_logs_read,
            "log_lines_scanned": self.log_lines_scanned,
            "plugin_lines_matched": self.plugin_lines_matched,
            "plugin_events_parsed": self.plugin_events_parsed,
            "plugin_events_ignored": self.plugin_events_ignored,
            "generic_rows_scanned": self.generic_rows_scanned,
            "generic_events_parsed": self.generic_events_parsed,
            "explicit_domain_locations": self.explicit_domain_locations,
            "xy_only_locations": self.xy_only_locations,
            "missing_locations": self.missing_locations,
            "location_telemetry": telemetry,
            "lifecycle_events": self.lifecycle_events,
            "kill_events": self.kill_events,
            "server_runs": self.server_runs,
            "tick_resets": self.tick_resets,
            "sources": self.source_details,
        }


@dataclass
class Session:
    session_id: str
    start_reason: str
    events: list[Event] = field(default_factory=list)
    end_reason: str = "end_of_evidence"


@dataclass(frozen=True)
class LiveSources:
    root: Path
    db: Path
    logs: list[Path]
    commit: str


def format_location(location: Location | None) -> str:
    return "" if location is None else location.signature


def clean_player_name(name: str) -> str:
    cleaned = COLOUR_TAG_RE.sub("", name).replace("_", " ").replace(".", " ")
    return " ".join(cleaned.split())


def player_key(name: str) -> str:
    return clean_player_name(name).casefold()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Scan Spoiled Milk logs and SQLite generic_logs for uncalibrated "
            "behavioral suspicion signals worth human administrative review."
        )
    )
    parser.add_argument(
        "logs",
        nargs="*",
        type=Path,
        help=(
            "Log files or directories to scan. Defaults to the current checkout's "
            "spoiled_milk_98 current and rotated logs."
        ),
    )
    parser.add_argument("--db", type=Path, help="SQLite database to read generic_logs from.")
    parser.add_argument("--no-db", action="store_true", help="Do not read SQLite generic_logs.")
    parser.add_argument(
        "--live",
        action="store_true",
        help="Discover and validate the detached live checkout, external DB, and current/rotated logs.",
    )
    parser.add_argument(
        "--live-root",
        type=Path,
        default=Path(os.environ.get("BOT_REPORT_LIVE_ROOT", DEFAULT_LIVE_ROOT)),
        help=argparse.SUPPRESS,
    )
    parser.add_argument(
        "--live-db-root",
        type=Path,
        default=Path(os.environ.get("BOT_REPORT_LIVE_DB_ROOT", DEFAULT_LIVE_DB_ROOT)),
        help=argparse.SUPPRESS,
    )
    parser.add_argument(
        "--live-max-log-age-hours",
        type=float,
        default=36.0,
        help="Fail --live if the current log is older than this many hours (default: 36).",
    )
    parser.add_argument("--hours", type=float, help="Only include activity from the last N hours.")
    parser.add_argument(
        "--since",
        help="Only include activity after an epoch second or ISO/local datetime.",
    )
    parser.add_argument("--player", action="append", default=[], help="Limit to a player; repeatable.")
    parser.add_argument("--top", type=int, default=20, help="Number of ranked players to print.")
    parser.add_argument(
        "--min-score",
        type=float,
        default=15.0,
        help="Hide text results below this uncalibrated suspicion score.",
    )
    parser.add_argument(
        "--min-repeated-actions",
        type=int,
        default=20,
        help="Minimum same-signature sample count for the weak repetition component.",
    )
    parser.add_argument(
        "--session-gap-minutes",
        type=float,
        default=DEFAULT_SESSION_GAP_SECONDS / 60,
        help="Inactivity gap that starts a new session (default: 30 minutes).",
    )
    parser.add_argument(
        "--include-background",
        action="store_true",
        help="Include background hooks in evidence counts; they never contribute to scoring.",
    )
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON.")
    args = parser.parse_args(argv)
    if args.hours is not None and args.hours < 0:
        parser.error("--hours must not be negative")
    if args.top < 1:
        parser.error("--top must be at least 1")
    if args.min_repeated_actions < 2:
        parser.error("--min-repeated-actions must be at least 2")
    if args.session_gap_minutes <= 0:
        parser.error("--session-gap-minutes must be positive")
    if args.live_max_log_age_hours <= 0:
        parser.error("--live-max-log-age-hours must be positive")
    if args.live and args.logs:
        parser.error("--live discovers its own logs; do not pass positional log paths")
    if args.live and args.db is not None:
        parser.error("--live discovers the external database; do not pass --db")
    if args.live and args.no_db:
        parser.error("--live requires the external live database; do not pass --no-db")
    return args


def parse_since(args: argparse.Namespace) -> int | None:
    values: list[int] = []
    if args.hours is not None:
        values.append(int((datetime.now() - timedelta(hours=args.hours)).timestamp()))
    if args.since:
        values.append(parse_datetime_or_epoch(args.since))
    return max(values) if values else None


def parse_datetime_or_epoch(value: str) -> int:
    stripped = value.strip()
    if stripped.isdigit():
        return int(stripped)
    try:
        return int(datetime.fromisoformat(stripped.replace("Z", "+00:00")).timestamp())
    except ValueError as exc:
        raise SystemExit(
            f"Could not parse --since value {value!r}. Use epoch seconds or ISO datetime."
        ) from exc


def default_log_paths() -> list[Path]:
    paths: list[Path] = []
    if DEFAULT_LOG.exists():
        paths.append(DEFAULT_LOG)
    log_dir = ROOT / "server" / "logs"
    if log_dir.exists():
        paths.extend(sorted(log_dir.glob(DEFAULT_ROTATED_LOG_GLOB)))
    return dedupe_paths(paths)


def expand_log_paths(paths: Iterable[Path]) -> list[Path]:
    expanded: list[Path] = []
    for path in paths:
        if path.is_dir():
            expanded.extend(
                sorted(
                    candidate
                    for candidate in path.iterdir()
                    if candidate.name.endswith((".log", ".log.gz"))
                )
            )
        elif path.exists():
            expanded.append(path)
    return dedupe_paths(expanded)


def dedupe_paths(paths: Iterable[Path]) -> list[Path]:
    seen: set[Path] = set()
    result: list[Path] = []
    for path in paths:
        resolved = path.resolve()
        if resolved not in seen:
            result.append(path)
            seen.add(resolved)
    return result


def discover_live_sources(
    live_root: Path,
    live_db_root: Path,
    maximum_log_age_hours: float,
) -> LiveSources:
    root = live_root.expanduser().resolve()
    if not root.is_dir():
        raise LiveSourceError(f"live checkout is missing: {root}")
    if root == ROOT.resolve():
        raise LiveSourceError("live checkout resolved to this worker/manager checkout")

    inside = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--is-inside-work-tree"],
        capture_output=True,
        text=True,
        check=False,
    )
    if inside.returncode != 0 or inside.stdout.strip() != "true":
        raise LiveSourceError(f"live checkout is not a Git worktree: {root}")
    attached = subprocess.run(
        ["git", "-C", str(root), "symbolic-ref", "--quiet", "HEAD"],
        capture_output=True,
        text=True,
        check=False,
    )
    if attached.returncode == 0:
        raise LiveSourceError(f"live checkout is attached to a branch, not detached: {root}")
    commit_result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        capture_output=True,
        text=True,
        check=False,
    )
    if commit_result.returncode != 0:
        raise LiveSourceError(f"could not resolve the live checkout commit: {root}")

    log_dir = root / "server" / "logs"
    current_log = log_dir / "spoiled_milk_98.log"
    if not current_log.is_file():
        raise LiveSourceError(f"current live plugin log is missing: {current_log}")
    age_seconds = max(0.0, datetime.now().timestamp() - current_log.stat().st_mtime)
    maximum_age_seconds = maximum_log_age_hours * 3600
    if age_seconds > maximum_age_seconds:
        raise LiveSourceError(
            "current live plugin log is stale: "
            f"{current_log} was modified {age_seconds / 3600:.1f} hours ago "
            f"(limit {maximum_log_age_hours:g} hours)"
        )
    rotated_logs = sorted(log_dir.glob(DEFAULT_ROTATED_LOG_GLOB))

    external_db = (live_db_root.expanduser() / LIVE_DB_NAME).resolve()
    if not external_db.is_file():
        raise LiveSourceError(f"external live database is missing: {external_db}")
    checkout_db = root / "server" / "inc" / "sqlite" / LIVE_DB_NAME
    if not checkout_db.is_symlink():
        raise LiveSourceError(f"live checkout database path is not a symlink: {checkout_db}")
    try:
        same_database = checkout_db.resolve().samefile(external_db)
    except OSError as exc:
        raise LiveSourceError(f"could not validate live database link: {exc}") from exc
    if not same_database:
        raise LiveSourceError(
            f"live checkout database link does not target the external database: {checkout_db}"
        )
    return LiveSources(
        root=root,
        db=external_db,
        logs=[current_log, *rotated_logs],
        commit=commit_result.stdout.strip(),
    )


def open_text(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return path.open("r", encoding="utf-8", errors="replace")


def parse_plugin_logs(
    paths: Iterable[Path],
    since: int | None,
    include_background: bool,
    coverage: Coverage,
) -> tuple[list[Event], list[str], list[tuple[int, int]]]:
    events: list[Event] = []
    warnings: list[str] = []
    tick_samples: list[tuple[int, int]] = []
    ordinal = 0
    paths_list = list(paths)
    coverage.log_files_requested = len(paths_list)
    for path in paths_list:
        try:
            stat = path.stat()
            coverage.log_files_read += 1
            if path.name.endswith(".log.gz"):
                coverage.rotated_logs_read += 1
            coverage.source_details.append(
                {
                    "kind": "plugin_log",
                    "path": str(path),
                    "bytes": stat.st_size,
                    "modified": format_time(int(stat.st_mtime)),
                    "rotated": path.name.endswith(".log.gz"),
                }
            )
            with open_text(path) as handle:
                for line in handle:
                    coverage.log_lines_scanned += 1
                    stripped = line.rstrip("\n")
                    plugin_match = PLUGIN_RE.match(stripped)
                    if plugin_match is None:
                        continue
                    coverage.plugin_lines_matched += 1
                    timestamp = parse_log_timestamp(plugin_match.group("date"))
                    tick_samples.append((timestamp, int(plugin_match.group("tick"))))
                    if since is not None and timestamp < since:
                        continue
                    ordinal += 1
                    event = parse_plugin_match(plugin_match, path, ordinal, timestamp, stripped)
                    if event is None:
                        continue
                    if event.is_background and not include_background:
                        coverage.plugin_events_ignored += 1
                        continue
                    coverage.plugin_events_parsed += 1
                    coverage.record_event(event)
                    events.append(event)
        except OSError as exc:
            warnings.append(f"Could not read {path}: {exc}")
    return events, warnings, tick_samples


def parse_log_timestamp(value: str) -> int:
    return int(datetime.strptime(value, "%Y-%m-%d %H:%M:%S").timestamp())


def parse_plugin_match(
    match: re.Match[str],
    path: Path,
    ordinal: int,
    timestamp: int,
    detail: str,
) -> Event | None:
    action = match.group("action")
    hook = action.rsplit(".", 1)[-1]
    payload = match.group("payload")
    player_match = PLAYER_RE.search(payload)
    if player_match is None:
        return None

    remainder = payload[player_match.end() :]
    target_type = "unknown"
    target_id = ""
    target_name = ""
    target_pos: Location | None = None
    command = ""

    npc_match = NPC_RE.search(remainder)
    loc_match = LOC_RE.search(remainder)
    item_match = ITEM_RE.search(remainder)
    players = list(PLAYER_RE.finditer(payload))
    target_player_match = players[1] if len(players) > 1 else None

    if npc_match:
        target_type = "npc"
        target_id = npc_match.group("id")
        target_name = npc_match.group("name")
        target_pos = location_from_match(npc_match)
        command = trailing_command(remainder[npc_match.end() :])
    elif loc_match:
        target_type = "object"
        target_id = loc_match.group("id")
        target_name = "object"
        target_pos = location_from_match(loc_match)
        command = trailing_command(remainder[loc_match.end() :])
    elif item_match:
        target_type = "item"
        target_id = item_match.group("id")
        target_name = "item"
        target_pos = location_from_match(item_match) if item_match.group("x") else None
        command = trailing_command(remainder[item_match.end() :])
    elif target_player_match:
        target_type = "player"
        target_id = target_player_match.group("id")
        target_name = target_player_match.group("name")
        target_pos = location_from_match(target_player_match)
        command = trailing_command(payload[target_player_match.end() :])
    else:
        command = trailing_command(remainder)

    return Event(
        source=str(path),
        source_kind="plugin",
        timestamp=timestamp,
        tick=int(match.group("tick")),
        ordinal=ordinal,
        player=clean_player_name(player_match.group("name")),
        action=action,
        hook=hook,
        player_pos=location_from_match(player_match),
        target_type=target_type,
        target_id=target_id,
        target_name=target_name,
        target_pos=target_pos,
        command=command,
        detail=detail,
    )


def location_from_match(match: re.Match[str]) -> Location:
    groups = match.groupdict()
    world_space = groups.get("world")
    level = groups.get("level")
    return Location(
        x=int(groups["x"]),
        y=int(groups["y"]),
        world_space=world_space.strip() if world_space else None,
        level=int(level) if level is not None else None,
    )


def trailing_command(text: str) -> str:
    cleaned = text.strip()
    if cleaned.startswith(","):
        cleaned = cleaned[1:].strip()
    cleaned = cleaned.strip("[] ")
    if not cleaned:
        return ""
    if "," in cleaned:
        cleaned = cleaned.rsplit(",", 1)[-1].strip()
    return cleaned.strip("[] ")


def parse_sqlite_generic_logs(
    db_path: Path,
    since: int | None,
    coverage: Coverage,
    starting_ordinal: int,
) -> tuple[list[Event], list[str]]:
    if not db_path.exists():
        return [], [f"SQLite DB not found: {db_path}"]
    uri = "file:" + quote(str(db_path.resolve())) + "?mode=ro"
    events: list[Event] = []
    warnings: list[str] = []
    try:
        connection = sqlite3.connect(uri, uri=True)
    except sqlite3.Error as exc:
        return [], [f"Could not open SQLite DB {db_path} read-only: {exc}"]

    try:
        connection.execute("PRAGMA query_only=ON")
        query = "SELECT time, message FROM generic_logs"
        params: tuple[int, ...] = ()
        if since is not None:
            query += " WHERE time >= ?"
            params = (since,)
        query += " ORDER BY time, id"
        for index, (timestamp, message) in enumerate(connection.execute(query, params), 1):
            coverage.generic_rows_scanned += 1
            event = parse_generic_log_row(
                int(timestamp), str(message), db_path, starting_ordinal + index
            )
            if event is not None:
                coverage.generic_events_parsed += 1
                coverage.record_event(event)
                events.append(event)
    except sqlite3.Error as exc:
        warnings.append(f"Could not read generic_logs from {db_path}: {exc}")
    finally:
        connection.close()
    try:
        stat = db_path.stat()
        coverage.source_details.append(
            {
                "kind": "sqlite_generic_logs",
                "path": str(db_path),
                "bytes": stat.st_size,
                "modified": format_time(int(stat.st_mtime)),
                "read_only": True,
            }
        )
    except OSError:
        pass
    return events, warnings


def parse_generic_log_row(
    timestamp: int,
    message: str,
    db_path: Path,
    ordinal: int,
) -> Event | None:
    for pattern, verb in GENERIC_LOG_PATTERNS:
        match = pattern.match(message)
        if not match:
            continue
        groups = match.groupdict()
        position = location_from_match(match) if groups.get("x") else None
        item = groups.get("item") or groups.get("filename") or verb
        target_id = groups.get("filename") or item
        amount = groups.get("amount") or groups.get("count") or ""
        command = verb + (f" x{amount}" if amount else "")
        return Event(
            source=str(db_path),
            source_kind="generic_logs",
            timestamp=timestamp,
            tick=None,
            ordinal=ordinal,
            player=clean_player_name(groups["player"]),
            action=f"Generic.{verb}",
            hook=verb,
            player_pos=position,
            target_type="generic",
            target_id=target_id,
            target_name=item,
            target_pos=position,
            command=command,
            detail=message,
        )
    return None


def assign_server_runs(
    events: list[Event],
    tick_samples: list[tuple[int, int]],
    coverage: Coverage,
) -> list[Event]:
    ordered_samples = sorted(tick_samples)
    reset_timestamps: list[int] = []
    previous: tuple[int, int] | None = None
    for current in ordered_samples:
        if (
            previous is not None
            and current[0] > previous[0]
            and current[1] < previous[1]
        ):
            if not reset_timestamps or reset_timestamps[-1] != current[0]:
                reset_timestamps.append(current[0])
        previous = current
    coverage.tick_resets = len(reset_timestamps)
    coverage.server_runs = max(1, len(reset_timestamps) + 1) if events else 0
    return [
        replace(event, server_run=bisect.bisect_right(reset_timestamps, event.timestamp) + 1)
        for event in events
    ]


def activity_type(event: Event) -> str:
    text = " ".join(
        [event.action, event.hook, event.command, event.target_name, event.target_type]
    ).casefold()
    if event.is_kill or any(
        token in text
        for token in ("attacknpc", "playermeleenpc", "playerrangenpc", "castnpc", "combat")
    ):
        return "combat"
    if "mining" in text or re.search(r"\bmine\b", text):
        return "mining"
    if any(token in text for token in ("smith", "smelt", "anvil")):
        return "smithing"
    if "bank" in text:
        return "banking"
    if any(token in text for token in ("shop", "bought", "sold", "trade")):
        return "shopping"
    if "agility" in text:
        return "agility"
    if any(token in text for token in ("woodcut", "chop")):
        return "woodcutting"
    if "fish" in text:
        return "fishing"
    if "cook" in text:
        return "cooking"
    if "craft" in text:
        return "crafting"
    if event.hook in {"picked_up", "dropped", "telegrabbed"}:
        return "loot"
    return "other"


def sessionize(events: list[Event], session_gap_seconds: int) -> list[Session]:
    ordered = sorted(events, key=event_sort_key)
    sessions: list[Session] = []
    previous: Event | None = None
    current: Session | None = None
    for event in ordered:
        reason: str | None = None
        if previous is None:
            reason = "login" if event.hook == "onPlayerLogin" else "first_observation"
        elif event.server_run != previous.server_run:
            reason = "server_restart_or_tick_reset"
        elif previous.hook == "onPlayerLogout":
            reason = "after_logout"
        elif event.hook == "onPlayerLogin":
            reason = "login"
        elif event.tick is not None and previous.tick is not None and event.tick < previous.tick:
            reason = "player_tick_reset"
        elif event.timestamp - previous.timestamp > session_gap_seconds:
            reason = "inactivity_gap"

        if reason is not None:
            if current is not None:
                current.end_reason = reason
            current = Session(session_id=f"S{len(sessions) + 1}", start_reason=reason)
            sessions.append(current)
        assert current is not None
        current.events.append(event)
        previous = event
    if current and current.events[-1].hook == "onPlayerLogout":
        current.end_reason = "logout"
    return sessions


def event_sort_key(event: Event) -> tuple[int, int, int, int]:
    tick = event.tick if event.tick is not None else 2**31 - 1
    return event.timestamp, event.server_run, tick, event.ordinal


def analyze(
    events: list[Event],
    min_repeated_actions: int,
    session_gap_seconds: int = DEFAULT_SESSION_GAP_SECONDS,
) -> list[dict[str, object]]:
    by_player: dict[str, list[Event]] = defaultdict(list)
    display_names: dict[str, Counter[str]] = defaultdict(Counter)
    for event in events:
        key = player_key(event.player)
        if not key:
            continue
        by_player[key].append(event)
        display_names[key][clean_player_name(event.player)] += 1

    reports = [
        analyze_player(
            display_names[key].most_common(1)[0][0],
            player_events,
            min_repeated_actions,
            session_gap_seconds,
        )
        for key, player_events in by_player.items()
    ]
    reports.sort(
        key=lambda report: (
            -float(report["suspicion_score"]),
            -int(report["behavior_events"]),
            str(report["player"]).casefold(),
        )
    )
    return reports


def analyze_player(
    player: str,
    events: list[Event],
    min_repeated_actions: int,
    session_gap_seconds: int,
) -> dict[str, object]:
    ordered = sorted(events, key=event_sort_key)
    sessions = sessionize(ordered, session_gap_seconds)
    eligible = [event for event in ordered if event.score_eligible]
    kills = [event for event in ordered if event.is_kill]
    signatures = Counter(event.signature for event in eligible)
    activity_counts = Counter(activity_type(event) for event in eligible)

    top_event: Event | None = None
    top_count = 0
    if signatures:
        top_signature, top_count = signatures.most_common(1)[0]
        top_event = next(event for event in eligible if event.signature == top_signature)
    longest_event, longest_count = longest_session_run(sessions)
    timing = best_timing_signal(sessions, min_repeated_actions)
    loop = best_loop_signal(sessions)
    repetition_points = repetition_score(top_count, len(eligible), min_repeated_actions)
    timing_points = float(timing["score"]) if timing else 0.0
    loop_points = float(loop["score"]) if loop else 0.0
    components = {
        "timing_regularity": component_record(timing_points, timing, "timing_intervals"),
        "non_overlapping_loops": component_record(loop_points, loop, "repetitions"),
        "repetition_context": {
            "score": repetition_points,
            "evidence_samples": top_count,
            "qualifies_as_independent_signal": False,
            "note": "Raw repetition is deliberately weak and never independently raises classification.",
        },
    }
    independent_signals: list[str] = []
    if timing_points >= 15:
        independent_signals.append("timing_regularity")
        components["timing_regularity"]["qualifies_as_independent_signal"] = True
    if loop_points >= 12:
        independent_signals.append("non_overlapping_loops")
        components["non_overlapping_loops"]["qualifies_as_independent_signal"] = True

    signals_are_corroborated = bool(
        timing
        and loop
        and timing_points >= 15
        and loop_points >= 12
        and timing["session"] == loop["session"]
        and timing["activity"] == loop["activity"]
    )

    suspicion_score = round(min(100.0, timing_points + loop_points + repetition_points), 1)
    classification = score_classification(
        suspicion_score,
        len(independent_signals),
        len(eligible),
        signals_are_corroborated,
    )
    first_ts = ordered[0].timestamp
    last_ts = ordered[-1].timestamp
    active_seconds = sum(session_active_duration(session) for session in sessions)
    warnings = player_warnings(
        ordered,
        eligible,
        independent_signals,
        suspicion_score,
        signals_are_corroborated,
    )
    reasons: list[str] = []
    if timing_points:
        reasons.append(
            f"timing regularity contributed {timing_points:g} points from "
            f"{timing['timing_intervals']} intervals in {timing['activity']}"
        )
    if loop_points:
        reasons.append(
            f"non-overlapping {loop['length']}-step loop contributed {loop_points:g} points "
            f"from {loop['repetitions']} repetitions"
        )
    if repetition_points:
        reasons.append(
            f"raw repetition contributed only {repetition_points:g} contextual points"
        )

    representative_windows: list[dict[str, object]] = []
    if timing:
        representative_windows.append(
            {
                "component": "timing_regularity",
                "session": timing["session"],
                "activity": timing["activity"],
                "start": timing["window_start"],
                "end": timing["window_end"],
                "events": timing["event_count"],
                "signature": timing["signature"],
            }
        )
    if loop:
        representative_windows.append(
            {
                "component": "non_overlapping_loops",
                "session": loop["session"],
                "activity": loop["activity"],
                "start": loop["window_start"],
                "end": loop["window_end"],
                "events": loop["evidence_samples"],
                "sample": loop["sample"],
            }
        )

    top_action = {
        "count": top_count,
        "percent_of_behavior_events": round(top_count / len(eligible) * 100, 1)
        if eligible
        else 0.0,
        "signature": top_event.short_signature if top_event else "",
    }
    longest_run_report = {
        "count": longest_count,
        "signature": longest_event.short_signature if longest_event else "",
        "score_contribution": 0,
    }
    return {
        "player": player,
        "suspicion_score": suspicion_score,
        "classification": classification,
        "score_is_calibrated": False,
        "independent_signals": independent_signals,
        "signals_corroborated_in_same_activity_session": signals_are_corroborated,
        "component_scores": components,
        "total_events": len(ordered),
        "behavior_events": len(eligible),
        "kill_events": len(kills),
        "active_time": format_duration(active_seconds),
        "observed_range": format_duration(max(0, last_ts - first_ts)),
        "first_seen": format_time(first_ts),
        "last_seen": format_time(last_ts),
        "unique_signatures": len(signatures),
        "top_action": top_action,
        "longest_run": longest_run_report,
        "timing": serialize_timing(timing),
        "loop": serialize_loop(loop),
        "activity_breakdown": activity_breakdown(activity_counts),
        "kill_evidence": summarize_kills(kills),
        "sessions": [serialize_session(session) for session in sessions],
        "representative_windows": representative_windows,
        "evidence_quality": {
            "behavior_samples": len(eligible),
            "sessions": len(sessions),
            "sessions_with_behavior": sum(
                any(event.score_eligible for event in session.events) for session in sessions
            ),
            "explicit_domain_locations": sum(
                event.player_pos is not None and event.player_pos.has_domain for event in ordered
            ),
            "xy_only_locations": sum(
                event.player_pos is not None and not event.player_pos.has_domain for event in ordered
            ),
            "sufficient_for_classification": classification != "UNCLASSIFIED",
        },
        "reasons": reasons,
        "warnings": warnings,
    }


def component_record(
    score: float,
    signal: dict[str, object] | None,
    evidence_key: str,
) -> dict[str, object]:
    return {
        "score": round(score, 1),
        "evidence_samples": int(signal[evidence_key]) if signal else 0,
        "qualifies_as_independent_signal": False,
    }


def longest_session_run(sessions: list[Session]) -> tuple[Event | None, int]:
    best_event: Event | None = None
    best_count = 0
    for session in sessions:
        eligible = [event for event in session.events if event.score_eligible]
        current_event: Event | None = None
        current_count = 0
        for event in eligible:
            if current_event and event.signature == current_event.signature:
                current_count += 1
            else:
                current_event = event
                current_count = 1
            if current_count > best_count:
                best_event = current_event
                best_count = current_count
    return best_event, best_count


def repetition_score(count: int, total: int, minimum: int) -> float:
    if count < minimum or total <= 0:
        return 0.0
    share = count / total
    points = 1.0 + min(3.0, math.log2(max(1.0, count / minimum))) + min(2.0, share * 4)
    return round(min(6.0, points), 1)


def best_timing_signal(
    sessions: list[Session],
    min_repeated_actions: int,
) -> dict[str, object] | None:
    best: dict[str, object] | None = None
    for session in sessions:
        score_events = sorted(
            (event for event in session.events if event.score_eligible),
            key=event_sort_key,
        )
        bursts, afk_gaps, unmeasurable_gaps = score_bursts(score_events)
        for burst in bursts:
            grouped: dict[tuple[str, str], list[Event]] = defaultdict(list)
            for event in burst:
                grouped[(activity_type(event), event.signature)].append(event)
            for (activity, _signature), signature_events in grouped.items():
                profile = ACTIVITY_PROFILES[activity]
                minimum = max(
                    min_repeated_actions,
                    profile["minimum_timing_samples"],
                )
                ordered = sorted(signature_events, key=event_sort_key)
                if len(ordered) < minimum:
                    continue
                gaps = [
                    event_gap(previous, current)
                    for previous, current in zip(ordered, ordered[1:])
                ]
                measurable = [gap for gap in gaps if gap is not None and gap > 0]
                if len(measurable) < minimum - 1:
                    continue
                mean_gap = statistics.fmean(measurable)
                if mean_gap <= 0:
                    continue
                stdev = statistics.pstdev(measurable) if len(measurable) > 1 else 0.0
                cv = stdev / mean_gap
                rounded_gaps = Counter(round(gap, 2) for gap in measurable)
                same_gap_ratio = rounded_gaps.most_common(1)[0][1] / len(measurable)
                candidate: dict[str, object] = {
                    "activity": activity,
                    "session": session.session_id,
                    "event_count": len(ordered),
                    "timing_intervals": len(measurable),
                    "signature": ordered[0].short_signature,
                    "mean_gap": mean_gap,
                    "median_gap": statistics.median(measurable),
                    "stdev": stdev,
                    "cv": cv,
                    "same_gap_ratio": same_gap_ratio,
                    "minimum_samples": minimum,
                    "afk_break_count": len(afk_gaps),
                    "afk_break_seconds": round(sum(afk_gaps), 2),
                    "unmeasurable_gaps": unmeasurable_gaps,
                    "bursts_analyzed": len(bursts),
                    "window_start": format_time(ordered[0].timestamp),
                    "window_end": format_time(ordered[-1].timestamp),
                }
                candidate["score"] = timing_score(candidate)
                if best is None or float(candidate["score"]) > float(best["score"]):
                    best = candidate
    return best


def score_bursts(events: list[Event]) -> tuple[list[list[Event]], list[float], int]:
    if not events:
        return [], [], 0
    bursts: list[list[Event]] = [[events[0]]]
    afk_gaps: list[float] = []
    unmeasurable = 0
    for previous, current in zip(events, events[1:]):
        gap = event_gap(previous, current)
        if gap is None or gap <= 0:
            unmeasurable += 1
            bursts[-1].append(current)
        elif gap > AFK_BREAK_SECONDS:
            afk_gaps.append(gap)
            bursts.append([current])
        else:
            bursts[-1].append(current)
    return bursts, afk_gaps, unmeasurable


def event_gap(previous: Event, current: Event) -> float | None:
    if current.server_run != previous.server_run:
        return None
    if previous.tick is not None and current.tick is not None:
        tick_gap = current.tick - previous.tick
        if tick_gap > 0:
            return tick_gap * TICK_SECONDS
        return None
    seconds_gap = current.timestamp - previous.timestamp
    return float(seconds_gap) if seconds_gap > 0 else None


def timing_score(timing: dict[str, object]) -> float:
    count = int(timing["event_count"])
    cv = float(timing["cv"])
    same_gap_ratio = float(timing["same_gap_ratio"])
    if count < int(timing["minimum_samples"]):
        return 0.0
    regularity = max(0.0, min(1.0, (0.20 - cv) / 0.20))
    exactness = max(0.0, min(1.0, (same_gap_ratio - 0.55) / 0.45))
    sample_factor = min(1.0, count / 60.0)
    points = (28.0 * regularity + 12.0 * exactness) * (0.7 + 0.3 * sample_factor)
    return round(min(40.0, points), 1)


def best_loop_signal(sessions: list[Session]) -> dict[str, object] | None:
    best: dict[str, object] | None = None
    for session in sessions:
        score_events = sorted(
            (event for event in session.events if event.score_eligible),
            key=event_sort_key,
        )
        bursts, _afk_gaps, _unmeasurable = score_bursts(score_events)
        for burst in bursts:
            for activity, ordered in contiguous_activity_segments(burst):
                best = best_loop_in_segment(session, activity, ordered, best)
    return best


def contiguous_activity_segments(events: list[Event]) -> list[tuple[str, list[Event]]]:
    segments: list[tuple[str, list[Event]]] = []
    for event in events:
        activity = activity_type(event)
        if segments and segments[-1][0] == activity:
            segments[-1][1].append(event)
        else:
            segments.append((activity, [event]))
    return segments


def best_loop_in_segment(
    session: Session,
    activity: str,
    ordered: list[Event],
    best: dict[str, object] | None,
) -> dict[str, object] | None:
    sequence = [event.sequence_signature for event in ordered]
    for length in range(2, 9):
        if len(sequence) < length * 2:
            continue
        for offset in range(length):
            blocks: list[tuple[tuple[str, ...], int]] = []
            for start in range(offset, len(sequence) - length + 1, length):
                blocks.append((tuple(sequence[start : start + length]), start))
            run_pattern: tuple[str, ...] | None = None
            run_start = 0
            run_count = 0
            for pattern, start in [*blocks, ((), len(sequence))]:
                if pattern == run_pattern:
                    run_count += 1
                    continue
                candidate = build_loop_candidate(
                    session,
                    activity,
                    ordered,
                    run_pattern,
                    run_start,
                    length,
                    run_count,
                )
                if candidate and (best is None or loop_rank(candidate) > loop_rank(best)):
                    best = candidate
                run_pattern = pattern
                run_start = start
                run_count = 1
    return best


def build_loop_candidate(
    session: Session,
    activity: str,
    events: list[Event],
    pattern: tuple[str, ...] | None,
    start: int,
    length: int,
    repetitions: int,
) -> dict[str, object] | None:
    minimum = ACTIVITY_PROFILES[activity]["minimum_loop_repetitions"]
    if pattern is None or repetitions < minimum or len(set(pattern)) < 2:
        return None
    if max(Counter(pattern).values()) / length > 0.75:
        return None
    end_index = start + length * repetitions - 1
    if end_index >= len(events):
        return None
    score = loop_score(length, repetitions)
    return {
        "activity": activity,
        "session": session.session_id,
        "length": length,
        "repetitions": repetitions,
        "evidence_samples": length * repetitions,
        "score": score,
        "sample": [event.short_signature for event in events[start : start + length]],
        "window_start": format_time(events[start].timestamp),
        "window_end": format_time(events[end_index].timestamp),
        "non_overlapping": True,
        "session_bound": True,
    }


def loop_score(length: int, repetitions: int) -> float:
    return round(min(30.0, 6.0 + length * 2.0 + (repetitions - 3) * 2.0), 1)


def loop_rank(loop: dict[str, object]) -> tuple[float, int, int]:
    return float(loop["score"]), int(loop["repetitions"]), -int(loop["length"])


def score_classification(
    score: float,
    independent_signals: int,
    samples: int,
    signals_are_corroborated: bool,
) -> str:
    if samples < 20:
        return "UNCLASSIFIED"
    if independent_signals >= 2 and signals_are_corroborated and score >= 65:
        return "HIGH"
    if independent_signals >= 2 and signals_are_corroborated and score >= 40:
        return "MEDIUM"
    if score >= 20:
        return "LOW"
    return "WATCH"


def player_warnings(
    events: list[Event],
    eligible: list[Event],
    independent_signals: list[str],
    score: float,
    signals_are_corroborated: bool,
) -> list[str]:
    warnings: list[str] = []
    if len(eligible) < 20:
        warnings.append("Sparse behavioral evidence; no classification was assigned.")
    xy_only = sum(
        event.player_pos is not None and not event.player_pos.has_domain for event in events
    )
    if xy_only:
        warnings.append(
            f"{xy_only} events have X/Y only; world space and signed map level are unknown."
        )
    if score >= 40 and len(independent_signals) < 2:
        warnings.append(
            "Only one independent signal is present, so medium/high classification is blocked."
        )
    elif score >= 40 and not signals_are_corroborated:
        warnings.append(
            "Independent signals occur in different activities or sessions, so medium/high "
            "classification is blocked."
        )
    return warnings


def activity_breakdown(counts: Counter[str]) -> list[dict[str, object]]:
    return [
        {
            "activity": activity,
            "events": count,
            "profile": ACTIVITY_PROFILES[activity],
        }
        for activity, count in counts.most_common()
    ]


def summarize_kills(kills: list[Event]) -> dict[str, object]:
    if not kills:
        return {
            "count": 0,
            "top_targets": [],
            "first_seen": None,
            "last_seen": None,
            "score_contribution": 0,
        }
    targets = Counter(event.target_name or event.target_id or "unknown" for event in kills)
    return {
        "count": len(kills),
        "top_targets": [
            {"target": target, "count": count} for target, count in targets.most_common(5)
        ],
        "first_seen": format_time(min(event.timestamp for event in kills)),
        "last_seen": format_time(max(event.timestamp for event in kills)),
        "score_contribution": 0,
        "note": "Kill hooks are summarized context and never contribute directly to scoring.",
    }


def serialize_session(session: Session) -> dict[str, object]:
    events = sorted(session.events, key=event_sort_key)
    behavior = [event for event in events if event.score_eligible]
    breaks = session_breaks(behavior)
    return {
        "id": session.session_id,
        "start": format_time(events[0].timestamp),
        "end": format_time(events[-1].timestamp),
        "start_reason": session.start_reason,
        "end_reason": session.end_reason,
        "server_runs": sorted({event.server_run for event in events}),
        "events": len(events),
        "behavior_events": len(behavior),
        "kill_events": sum(event.is_kill for event in events),
        "lifecycle_events": sum(event.is_lifecycle for event in events),
        "background_events": sum(event.is_background for event in events),
        "activities": dict(Counter(activity_type(event) for event in behavior)),
        "afk_breaks": {
            "count": len(breaks),
            "total_seconds": round(sum(breaks), 2),
            "threshold_seconds": AFK_BREAK_SECONDS,
        },
    }


def session_breaks(events: list[Event]) -> list[float]:
    breaks: list[float] = []
    for previous, current in zip(events, events[1:]):
        gap = event_gap(previous, current)
        if gap is not None and gap > AFK_BREAK_SECONDS:
            breaks.append(gap)
    return breaks


def session_active_duration(session: Session) -> int:
    events = sorted(session.events, key=event_sort_key)
    total = 0.0
    for previous, current in zip(events, events[1:]):
        gap = event_gap(previous, current)
        if gap is not None and 0 < gap <= AFK_BREAK_SECONDS:
            total += gap
    return int(total)


def serialize_timing(timing: dict[str, object] | None) -> dict[str, object] | None:
    if timing is None:
        return None
    return {
        "activity": timing["activity"],
        "session": timing["session"],
        "event_count": timing["event_count"],
        "timing_intervals": timing["timing_intervals"],
        "signature": timing["signature"],
        "mean_gap_seconds": round(float(timing["mean_gap"]), 2),
        "median_gap_seconds": round(float(timing["median_gap"]), 2),
        "stdev_seconds": round(float(timing["stdev"]), 2),
        "coefficient_of_variation": round(float(timing["cv"]), 4),
        "same_gap_percent": round(float(timing["same_gap_ratio"]) * 100, 1),
        "afk_break_count": timing["afk_break_count"],
        "afk_break_seconds": timing["afk_break_seconds"],
        "unmeasurable_gaps": timing["unmeasurable_gaps"],
        "bursts_analyzed": timing["bursts_analyzed"],
        "score": timing["score"],
        "window_start": timing["window_start"],
        "window_end": timing["window_end"],
    }


def serialize_loop(loop: dict[str, object] | None) -> dict[str, object] | None:
    if loop is None:
        return None
    return dict(loop)


def format_duration(seconds: int) -> str:
    if seconds < 60:
        return f"{seconds}s"
    minutes, sec = divmod(seconds, 60)
    if minutes < 60:
        return f"{minutes}m {sec}s"
    hours, minutes = divmod(minutes, 60)
    return f"{hours}h {minutes}m"


def format_time(timestamp: int) -> str:
    return datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d %H:%M:%S")


def global_warnings(coverage: Coverage, live_sources: LiveSources | None) -> list[str]:
    warnings: list[str] = []
    if coverage.xy_only_locations:
        warnings.append(
            "Current telemetry includes X/Y without world space or signed map level; "
            "same-coordinate cross-layer activity cannot be distinguished for those events."
        )
    if coverage.explicit_domain_locations and coverage.xy_only_locations:
        warnings.append("Location-domain telemetry is only partially populated.")
    if coverage.lifecycle_events == 0:
        warnings.append(
            "No player login/logout events were available; session boundaries rely on restarts, "
            "tick resets, and inactivity gaps."
        )
    if live_sources is not None and coverage.rotated_logs_read == 0:
        warnings.append("No rotated live plugin logs were present; coverage begins with the current log.")
    return warnings


def print_text_report(
    payload: dict[str, object],
    min_score: float,
    top: int,
) -> None:
    print("Spoiled Milk Bot Suspicion Review Leads")
    print("Scores are uncalibrated administrative leads, never proof by themselves.")
    print("This tool does not punish, disconnect, restrict, or label players.")
    print()
    print(f"Events analyzed: {payload['eventsAnalyzed']}")
    print(f"Score-eligible behavior events: {payload['behaviorEventsAnalyzed']}")
    coverage = payload["dataSourceCoverage"]
    assert isinstance(coverage, dict)
    print(
        f"Plugin logs: {coverage['log_files_read']} "
        f"({coverage['rotated_logs_read']} rotated) | "
        f"generic_logs rows: {coverage['generic_rows_scanned']} | "
        f"server runs: {coverage['server_runs']}"
    )
    print(f"Location telemetry: {coverage['location_telemetry']}")
    warnings = payload["warnings"]
    assert isinstance(warnings, list)
    if warnings:
        print("Warnings:")
        for warning in warnings:
            print(f"  - {warning}")

    reports = payload["reports"]
    assert isinstance(reports, list)
    visible = [
        report
        for report in reports
        if isinstance(report, dict) and float(report["suspicion_score"]) >= min_score
    ][:top]
    print()
    if not visible:
        print(f"No players met the minimum uncalibrated suspicion score of {min_score:g}.")
        return

    print("Ranked review leads:")
    for index, report in enumerate(visible, start=1):
        print()
        print(
            f"{index}. {report['player']} - uncalibrated suspicion score "
            f"{report['suspicion_score']} ({report['classification']})"
        )
        print(
            f"   Behavior samples: {report['behavior_events']} | Kills summarized: "
            f"{report['kill_events']} | Sessions: {len(report['sessions'])} | "
            f"Active time: about {report['active_time']}"
        )
        components = report["component_scores"]
        assert isinstance(components, dict)
        print(
            "   Components: timing="
            f"{components['timing_regularity']['score']}, loops="
            f"{components['non_overlapping_loops']['score']}, repetition-context="
            f"{components['repetition_context']['score']}"
        )
        signals = report["independent_signals"]
        assert isinstance(signals, list)
        print(f"   Independent signals: {', '.join(signals) if signals else 'none'}")
        windows = report["representative_windows"]
        assert isinstance(windows, list)
        for window in windows[:2]:
            assert isinstance(window, dict)
            print(
                f"   - {window['component']} window: {window['start']} to {window['end']} "
                f"({window['events']} events, {window['activity']}, {window['session']})"
            )
        report_warnings = report["warnings"]
        assert isinstance(report_warnings, list)
        for warning in report_warnings[:3]:
            print(f"   ! {warning}")


def build_payload(
    events: list[Event],
    reports: list[dict[str, object]],
    log_paths: list[Path],
    db_path: Path | None,
    coverage: Coverage,
    warnings: list[str],
    since: int | None,
    session_gap_seconds: int,
    live_sources: LiveSources | None,
) -> dict[str, object]:
    return {
        "reportType": "uncalibrated-bot-suspicion-review-leads",
        "disclaimer": (
            "Administrative review leads only. This report is never proof and performs no "
            "automatic punishment, disconnection, restriction, or player labeling."
        ),
        "scoreCalibration": "none",
        "generatedAt": format_time(int(datetime.now().timestamp())),
        "liveMode": live_sources is not None,
        "liveCommit": live_sources.commit if live_sources else None,
        "since": format_time(since) if since is not None else None,
        "eventsAnalyzed": len(events),
        "behaviorEventsAnalyzed": sum(event.score_eligible for event in events),
        "logs": [str(path) for path in log_paths],
        "db": str(db_path) if db_path is not None else None,
        "dataSourceCoverage": coverage.to_dict(),
        "sessionPolicy": {
            "inactivity_gap_seconds": session_gap_seconds,
            "afk_break_seconds": AFK_BREAK_SECONDS,
            "boundaries": [
                "login",
                "logout",
                "server restart/tick reset",
                "inactivity gap",
            ],
        },
        "activityProfiles": ACTIVITY_PROFILES,
        "warnings": warnings,
        "reports": reports,
    }


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    since = parse_since(args)
    coverage = Coverage()
    live_sources: LiveSources | None = None
    try:
        if args.live:
            live_sources = discover_live_sources(
                args.live_root,
                args.live_db_root,
                args.live_max_log_age_hours,
            )
            log_paths = live_sources.logs
            db_path: Path | None = live_sources.db
        else:
            log_paths = expand_log_paths(args.logs) if args.logs else default_log_paths()
            db_path = None if args.no_db else (args.db or DEFAULT_DB)
    except LiveSourceError as exc:
        print(f"ERROR: --live source validation failed: {exc}", file=sys.stderr)
        return 2

    events, warnings, tick_samples = parse_plugin_logs(
        log_paths, since, args.include_background, coverage
    )
    if not log_paths:
        warnings.append("No plugin log sources were found.")
    if db_path is not None:
        db_events, db_warnings = parse_sqlite_generic_logs(
            db_path, since, coverage, len(events)
        )
        events.extend(db_events)
        warnings.extend(db_warnings)
    events = assign_server_runs(events, tick_samples, coverage)

    if args.player:
        wanted = {player_key(player) for player in args.player}
        events = [event for event in events if player_key(event.player) in wanted]

    session_gap_seconds = int(args.session_gap_minutes * 60)
    reports = (
        analyze(events, args.min_repeated_actions, session_gap_seconds) if events else []
    )
    warnings.extend(global_warnings(coverage, live_sources))
    payload = build_payload(
        events,
        reports,
        log_paths,
        db_path,
        coverage,
        warnings,
        since,
        session_gap_seconds,
        live_sources,
    )
    if args.json:
        print(json.dumps(payload, indent=2))
    else:
        print_text_report(payload, args.min_score, args.top)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BrokenPipeError:
        try:
            sys.stdout.close()
        finally:
            raise SystemExit(1)
