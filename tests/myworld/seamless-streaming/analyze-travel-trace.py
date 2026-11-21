#!/usr/bin/env python3
"""Correlate gaps between submitted world frames with private server positions."""

import argparse
from bisect import bisect_right
import json
import math
from pathlib import Path


def position_distance(a, b):
    return max(abs(a[0] - b[0]), abs(a[1] - b[1]))


def analyze(result, ticks):
    travel = result['travel']
    first_walk = next((e['milliseconds'] for e in result['events'] if e['message'].startswith('outer-coverage:walking:')), 0)
    started = result.get('timeOrigin', 0) + first_walk
    frames = [row for row in travel['rows'] if row['kind'] == 'frame' and row['submittedAt'] >= started]
    transitions = [row for row in travel['rows'] if row['kind'] == 'transition' and row['time'] >= started]
    transition_times = [row['time'] for row in transitions]
    frame_times = [row['submittedAt'] for row in frames]
    tick_times = [row['time'] for row in ticks]
    if len(frames) < 2 or not ticks or frame_times != sorted(frame_times) or tick_times != sorted(tick_times):
        raise ValueError('Expected chronological submitted world frames and server ticks')

    gap_rows = []
    for previous, frame in zip(frames, frames[1:]):
        start, end = previous['submittedAt'], frame['submittedAt']
        first, last = bisect_right(tick_times, start) - 1, bisect_right(tick_times, end)
        covered = ticks[max(0, first):last]
        positions = [[tick['x'], tick['y']] for tick in covered]
        stage_begin, stage_end = bisect_right(transition_times, start), bisect_right(transition_times, end)
        uploads = frame['uploadedBytes'] - previous['uploadedBytes']
        gap_rows.append({
            'time': end,
            'milliseconds': round(end - start, 1),
            'transition': stage_end != stage_begin or previous['epoch'] != frame['epoch'],
            'epoch': frame['epoch'],
            'receiptFrom': previous['receipt'],
            'receiptTo': frame['receipt'],
            'shownAdvanceTiles': round(position_distance(previous['shown'], frame['shown']) / 128, 2),
            'serverAdvanceTiles': sum(position_distance(a, b) for a, b in zip(positions, positions[1:])),
            'uploadBytes': uploads,
            'repacks': frame['repacks'] - previous['repacks'],
            'stagingMs': round(frame['stagingMs'], 1),
            'bridgeMs': round(frame['work'][0], 1),
            'javascriptMs': round(frame['js'], 1),
            'clientWorkMs': frame.get('clientWorkMs'),
        })

    def worst(rows):
        return max(rows, key=lambda row: row['milliseconds'], default=None)

    handoffs = []
    for transition in transitions:
        if not transition['walking']:
            continue
        index = bisect_right(frame_times, transition['time'])
        if not 0 < index < len(frames):
            continue
        first_frame = frames[index]
        after = [row for row in gap_rows[index:] if row['time'] <= first_frame['submittedAt'] + 5_000
                 and not row['transition'] and row['epoch'] == first_frame['epoch']]
        handoffs.append({'firstWorldFrameMs': round(first_frame['submittedAt'] - transition['time'], 1),
                         'gap': gap_rows[index - 1], 'worstFollowingFiveSeconds': worst(after)})

    ordinary = [row for row in gap_rows if not row['transition']]
    ordinary_durations = sorted(row['milliseconds'] for row in ordinary)
    p95 = ordinary_durations[math.ceil(len(ordinary_durations) * .95) - 1] if ordinary_durations else None
    staging_rows = sorted(ordinary, key=lambda row: row['stagingMs'], reverse=True)[:12]
    return {'submittedFrames': len(frames), 'discardedTraceRows': travel['dropped'], 'serverSamples': len(ticks),
            'largestOrdinaryGaps': sorted(ordinary, key=lambda row: row['milliseconds'], reverse=True)[:12],
            'largestEarlyUploads': staging_rows,
            'ordinaryFrameP95Ms': p95, 'walkingHandoffs': handoffs,
            'inputPauses': result.get('stream', []), 'finalServerPosition': result['serverStatus']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path, help='Private run directory containing Firefox and server receipts')
    args = parser.parse_args()
    result = json.loads((args.run / 'firefox-result.json').read_text())
    ticks = [json.loads(line) for line in (args.run / 'travel-server.ndjson').read_text().splitlines()]
    print(json.dumps(analyze(result, ticks), indent=2))


if __name__ == '__main__':
    main()
