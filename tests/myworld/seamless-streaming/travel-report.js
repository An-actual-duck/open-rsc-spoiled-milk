// Staged only for the instrumented manual loopback preview.
let lastReported;
let previousFrame;
let visibleSince = performance.timeOrigin;

function reportTravel() {
  const travel = window.browserProof?.gpu?.travelTrace;
  if (!travel?.rows.length) return;
  const selected = [];
  let start = travel.rows.length;
  while (start > 0 && travel.rows[start - 1] !== lastReported) start--;
  if (start === 0) previousFrame = undefined;
  for (let i = start; i < travel.rows.length; i++) {
    const row = travel.rows[i];
    if (row.kind === 'transition') selected.push(row);
    if (row.kind !== 'frame') continue;
    if (previousFrame?.submittedAt >= visibleSince && row.submittedAt - previousFrame.submittedAt >= 100) {
      selected.push({kind: 'gap', previous: previousFrame, current: row});
    }
    previousFrame = row;
  }
  lastReported = travel.rows.at(-1);
  for (let i = 0; i < selected.length; i += 10) {
    navigator.sendBeacon('./private-travel-timings', JSON.stringify({
      timeOrigin: performance.timeOrigin,
      dropped: travel.dropped,
      rows: selected.slice(i, i + 10),
    }));
  }
}

window.setInterval(reportTravel, 2000);
window.addEventListener('pagehide', reportTravel);
document.addEventListener('visibilitychange', () => {
  reportTravel();
  visibleSince = document.hidden ? Infinity : performance.timeOrigin + performance.now();
});
