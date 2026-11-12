# Focused streaming slice review

Run `slice-budget-oracle.mjs --source-root ROOT --out FILE` and
`prefetch-gap-oracle.mjs --source-root ROOT --out FILE` with Node. Source files
are loaded from that explicit root and SHA256-bound in each result. Outputs
refuse overwrite; use a declared `output/tests/` subject/run destination.

The slice oracle calls the actual incremental packed-resource uploader and
scheduler. An independent ledger owns fake GL buffers, copied bytes, partial
allocations, synthetic source promises, RAF callbacks and a deterministic clock.
It checks aggregate admission/construction/upload steps and bytes, immediate tiny
resource throughput, delayed source ownership, time exhaustion, unpreemptible
source/constructor/GL overruns, cancellation before and after allocation, and
local pressure against optional versus required partial uploads.

Twelve fully occupied three-resource working sets traverse distinct generations.
Each new horizon must prefetch its next two resources by evicting stale optional
residents; each unchanged plan must perform no repeated prepare or eviction.
An oversized optional proposal must admit a stable fitting subset. Changing only
the optional proposal must preserve an in-flight required source. These checks
cover both useful forward prefetch and the absence of optional-on-optional churn.

The prefetch oracle independently filters a literal cardinal cross from dense
and sparse source rosters, including source edges, direction persistence, turns
and inactive reset. It verifies the initial maximum of 140 optional IDs without
adding active drawable membership. The held-gap oracle uses explicit timestamps
for 159 held images: the resumed fresh gap is 4265 ms in this synthetic example,
and a later short interval must not erase that maximum. Additional closed source
checks verify both caller hold paths use the helper and that the bounded early
return preserves `lastWorld` until the next fresh sample.

Final review of worker `f4a3d4da2` passes 152 slice checks and 1969 prefetch/gap
checks. Required active IDs remain protected; moved horizons can evict old
optional IDs, and same-plan optional work does not churn. Unallocated upload jobs
are not cancelled by their own reservation. A dispensable allocated optional job
can be released for local pressure, while an allocated required job is retained.

These are semantic/resource-accounting tests with a GL double and synthetic
time. Source guards are caller-wiring evidence, not browser execution. They do
not establish real GPU duration, pixels, FPS or travel improvements. The earlier
canary's held-frame/source-age evidence remains the hardware reference; its
28 ms tail omitted a cold held interval and cannot be treated as a complete
fresh-to-fresh travel tail.
