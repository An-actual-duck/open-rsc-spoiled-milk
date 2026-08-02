# Offline Bot-Suspicion Review Leads

`scripts/report-bot-suspicion.py` is a read-only administrative triage tool. It
finds behavioral patterns that may deserve human review. Its suspicion score is
uncalibrated: it is not a probability, a percentage, a diagnosis, or proof. The
tool never punishes, disconnects, restricts, or labels a player.

## Safe usage

Analyze the detached live checkout, its external SQLite database, the current
plugin log, and all available rotated plugin logs:

```bash
python3 scripts/report-bot-suspicion.py --live --hours 24
```

Produce JSON for offline review:

```bash
python3 scripts/report-bot-suspicion.py --live --since "2026-08-01 00:00:00" --json > /tmp/bot-review.json
```

Filter case-insensitively to one player without changing the underlying data:

```bash
python3 scripts/report-bot-suspicion.py --live --hours 72 --player "Example Name"
```

For a private fixture or archived copy, pass explicit log files/directories and
an explicit database instead of `--live`:

```bash
python3 scripts/report-bot-suspicion.py server/logs --db /path/to/copy.db --hours 24
```

SQLite is opened with `mode=ro` and `PRAGMA query_only=ON`. `--live` also proves
that:

- the configured live checkout exists and has detached `HEAD`;
- the current `spoiled_milk_98.log` exists and is not stale;
- rotated `spoiled_milk_98.*.log.gz` files are discovered;
- the checkout database path is a symlink to the expected external live DB;
- the external DB exists.

Missing, stale, attached, or mismatched live sources produce an error. `--live`
does not fall back to the manager or worker checkout. It does not stop, restart,
or otherwise interact with the public server process.

## Interpreting the report

The report separates score-eligible behavior from lifecycle, background, and
kill-hook evidence. It exposes component scores, sample counts, activity
profiles, session boundaries, representative time windows, source coverage,
and evidence-quality warnings.

Classifications mean only:

- `UNCLASSIFIED`: fewer than 20 score-eligible samples; evidence is too sparse.
- `WATCH`: no strong combined behavioral signal.
- `LOW`: one independent signal or a higher score without corroboration.
- `MEDIUM`: at least two independent signals corroborated in the same activity
  and session, with at least 40 uncalibrated score points.
- `HIGH`: the same corroboration requirement with at least 65 points.

Raw repetition contributes at most six contextual points. Consecutive identical
actions, total event volume, long playtime, and kill volume contribute no score.
Loops must be session-bound, contiguous, non-overlapping, contain at least two
different steps, and meet the applicable activity profile. Timing is evaluated
inside activity-specific, within-session bursts. Pauses over two minutes are
reported as AFK breaks instead of silently removed; login/logout, a server tick
reset/restart, or 30 minutes of inactivity creates a new session.

Combat, mining, smithing, banking, shopping, agility, woodcutting, fishing,
cooking, crafting, loot, and other activity are kept in separate operational
profiles. These thresholds reduce obvious false positives, but they are not
statistical population baselines. A reviewer should inspect the underlying
activity, game mechanics, access patterns, and representative windows before
drawing any conclusion.

## Location-telemetry limitation

As of 2026-08-02, `Player.toString()`, `Npc.toString()`, scenery/item strings,
and messages stored in `generic_logs` provide X/Y coordinates without a world
space or signed map level. Consequently, the report cannot distinguish two
current events at the same X/Y on different layers or world spaces. It reports
this loss of coverage explicitly rather than assuming level zero.

The parser already accepts an append-only form such as:

```text
[Player:12:Example Name @ (484, 449); world=global; level=-1]
```

The smallest safe future server change is a plugin-log-only formatter in
`PluginHandler` that renders `Player`/`Npc` arguments with their authoritative
`WorldLocation`. That avoids changing the broadly used model `toString()`
contract. Generic pickup/shop/drop/telegrab messages can adopt the same suffix
separately. Such telemetry work should have its own compatibility tests before
deployment; this reporting branch does not change server logging.

## Live-data development comparison

The implementation was evaluated read-only against the anonymized window from
2026-07-30 00:00:00 through the 2026-08-02 development run. No player was
identified or characterized as a bot.

| Metric | Previous report | Revised report |
| --- | ---: | ---: |
| Score-eligible behavior events | 16,617 | 16,617 |
| Additional summarized lifecycle/kill evidence | Not retained | 9,316 |
| Players represented | 4 | 4 |
| High | 2 | 0 |
| Medium | 0 | 0 |
| Low | Not defined separately | 2 |
| Sparse/unclassified | 0 | 2 |
| Maximum uncalibrated score | 95.0 | 45.9 |
| Median uncalibrated score | 35.95 | 20.8 |

The previous high results were driven largely by activity volume, raw
repetition, overlapping loops, and signals pooled across unrelated sessions.
The revised output retains them only as anonymized review context and blocks
medium/high classification without same-session, same-activity corroboration.

## Future calibration

Before any threshold is treated as measured risk, build a versioned, labelled,
privacy-reviewed dataset containing consenting normal-play samples, controlled
script fixtures, known game-mechanic loops, and administrator-reviewed cases.
Evaluate false-positive rate, precision/recall, activity-specific sample sizes,
and out-of-time performance without using report scores as labels. Record the
tool version and telemetry coverage with every label. Until then, the score must
remain explicitly uncalibrated and advisory.
