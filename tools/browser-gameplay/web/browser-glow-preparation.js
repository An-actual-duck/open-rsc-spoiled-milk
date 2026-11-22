export function createGlowPreparation() {
  const maximum = 6;
  const plans = new Map();
  const stats = {submitted: 0, completed: 0, used: 0, earliestLeadMs: null, latestLeadMs: null, failures: 0};
  let worker, nextId = 1;
  return {
    stats,
    prepare(words) {
      if (!worker) {
        try {
          worker = new Worker(new URL('./browser-glow-preparer.js', import.meta.url), {type: 'module'});
        } catch {
          stats.failures++;
          return 0;
        }
        worker.onmessage = ({data}) => {
          const plan = plans.get(data.id);
          if (!plan) return;
          if (!(data.payload instanceof Int8Array)) {
            stats.failures++;
            plans.delete(data.id);
            return;
          }
          plan.payload = data.payload;
          plan.ready = performance.now();
          stats.completed++;
        };
      }
      const recipe = Int32Array.from(words);
      const id = nextId++;
      if (plans.size >= maximum) plans.delete(plans.keys().next().value);
      plans.set(id, {});
      worker.postMessage({id, words: recipe}, [recipe.buffer]);
      stats.submitted++;
      return id;
    },
    take(id) {
      const plan = plans.get(id);
      if (!plan?.payload) return null;
      plans.delete(id);
      const lead = performance.now() - plan.ready;
      stats.earliestLeadMs = Math.min(stats.earliestLeadMs ?? lead, lead);
      stats.latestLeadMs = Math.max(stats.latestLeadMs ?? lead, lead);
      stats.used++;
      return plan.payload;
    },
    dispose() {
      worker?.terminate();
      worker = null;
      plans.clear();
    },
  };
}
