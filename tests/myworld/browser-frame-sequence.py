#!/usr/bin/env python3
"""Exact overlap deduplication for steady-world interval rings; not scanout or holds."""
import math

def summarize(rows):
    if len(rows)<2:raise ValueError("At least two snapshots required")
    # holdWorld can leave lastWorld intact: a resumed interval may contain the
    # full hold. Reject nonsteady context here; never trim such gaps into FPS.
    held=rows[0].get("held");epoch=(rows[0].get("ready")or{}).get("epoch")
    if type(held)!=int or held<0 or type(epoch)!=int or epoch<0:raise ValueError("Explicit held-frame and source-epoch context required")
    if any(row.get("held")!=held or (row.get("ready")or{}).get("epoch")!=epoch for row in rows):raise ValueError("Nonsteady hold/epoch window; preserve unfiltered crossing evidence separately")
    start=rows[0]["sequence"];previous=start;events=[]
    if type(start)!=int or start<0:raise ValueError("Invalid sequence")
    for row in rows[1:]:
        end=row["sequence"];tail=row["tail"]
        if type(end)!=int or end<previous or not isinstance(tail,list):raise ValueError("Sequence reset or invalid ring")
        count=end-previous
        if count>len(tail):raise ValueError("Lost interval events; no percentiles permitted")
        if any(type(x)not in (int,float) or not math.isfinite(x) or x<0 for x in tail):raise ValueError("Invalid interval")
        if count:events.extend(tail[-count:])
        previous=end
    if not events:raise ValueError("No new events")
    ordered=sorted(events)
    percentile=lambda q:ordered[max(0,math.ceil(len(ordered)*q)-1)]
    return {"firstSequence":start+1,"lastSequence":previous,"count":len(events),"p50Ms":percentile(.5),"p95Ms":percentile(.95),"p99Ms":percentile(.99),"maxMs":max(events),"over33ms":sum(x>33 for x in events),"over50ms":sum(x>50 for x in events),"scope":"Unique backend world-interval events, nearest-rank percentiles; explicit unchanged hold counters and source epoch required. Nonsteady windows are rejected, never trimmed; preserve their unfiltered crossing gaps separately."}
