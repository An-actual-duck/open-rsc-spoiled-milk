# RSC-Style Billboard Sprite AI Training Brief

## Purpose

Build an experimental training pipeline that generates deliberately crude, low-resolution, multi-direction billboard sprites resembling the technical visual characteristics of RuneScape Classic-era sprites.

The target is not polished pixel art. Desired traits include awkward silhouettes, blunt shading, limited detail, tiny native dimensions, facing directions, animation states, and transparent game-ready output.

This is an implementation handoff for a coding agent. Do not begin a long training run until the asset license, dataset inventory, target format, and available GPU are recorded.

## Primary Recommendation

Start with a **Stable Diffusion 1.5 LoRA** trained on individually extracted, consistently aligned frames.

Reasons:

- The 512 px workflow is mature and relatively inexpensive.
- SD 1.5 is easier to run and fine-tune than FLUX-class models.
- Its weaker bias toward polished, anatomically coherent art may help with this intentionally rough target.
- Kohya sd-scripts and Hugging Face Diffusers have mature support.

Do not begin with FLUX. Its visual strengths work against the target and cost more to override.

If a rank-32 or rank-64 LoRA cannot sufficiently suppress polish, escalate to a **full SD 1.5 U-Net fine-tune**. Do not train a foundation model from scratch.

## Blocking Inputs

Record these before choosing exact packages or settings:

~~~yaml
gpu:
  model: TODO
  vram_gb: TODO
  operating_system: TODO
dataset:
  source_path: TODO
  sheet_count: TODO
  frame_count: TODO
  original_format: TODO
  direction_count: TODO
  animation_states: TODO
  license_reference: TODO
target:
  game_engine: TODO
  native_frame_dimensions: TODO
  sheet_layout: TODO
  transparency_format: TODO
~~~

If no suitable NVIDIA GPU is available, keep preprocessing and evaluation local and prepare training for a rented cloud GPU.

## Licensing Gate

Do not assume that open-source engine/server code also licenses original game artwork. Determine whether each asset group contains:

1. original Jagex assets;
2. community-created replacements;
3. modified Jagex assets; or
4. a mixture.

Record for each group:

~~~yaml
asset_group:
  source: TODO
  creator_or_rightsholder: TODO
  asset_license: TODO
  attribution_required: TODO
  share_alike_required: TODO
  source_redistribution_allowed: TODO
  trained_weight_redistribution: TODO_OR_UNKNOWN
~~~

Noncommercial intent does not itself make proprietary artwork open source. If rights remain unclear, do not publicly distribute the raw dataset or model weights.

References:

- Jagex Fan Content Policy: https://legal.jagex.com/docs/policies/fan-content-policy
- OpenRSC Core Framework: https://github.com/Open-RSC/Core-Framework

## Suggested Repository

~~~text
sprite-training/
├── README.md
├── pyproject.toml
├── configs/
│   ├── dataset.yaml
│   ├── train_lora_rank32.toml
│   ├── train_lora_rank64.toml
│   └── evaluation_prompts.txt
├── data/
│   ├── raw/
│   ├── manifests/
│   │   ├── inventory.csv
│   │   ├── licensing.csv
│   │   ├── train.csv
│   │   ├── validation.csv
│   │   └── test.csv
│   ├── extracted/
│   └── prepared_512/
├── scripts/
│   ├── inventory_assets.py
│   ├── extract_frames.py
│   ├── prepare_canvases.py
│   ├── create_captions.py
│   ├── split_by_entity.py
│   ├── validate_dataset.py
│   ├── generate_evaluation_grid.py
│   ├── restore_transparency.py
│   ├── quantize_and_downscale.py
│   └── assemble_sprite_sheet.py
├── outputs/
│   ├── checkpoints/
│   ├── samples/
│   └── reports/
└── tests/
~~~

Keep data/raw immutable. Make all preprocessing deterministic, configurable, and rerunnable.

## Dataset Manifest

Create one row per extracted frame:

~~~csv
frame_id,source_file,entity_id,entity_name,entity_class,action,direction,frame_index,native_width,native_height,anchor_x,anchor_y,palette_id,license_group,split,caption
~~~

Key rules:

- entity_id is shared by every view and animation of one entity.
- direction and action use controlled vocabularies.
- anchor coordinates represent a common ground-contact point.
- license_group links to the licensing manifest.
- split is assigned by entity, never independently by frame.

## Extraction

Extract complete sheets into individual lossless RGBA PNG frames.

- Never rescale during extraction.
- Preserve original RGB values and alpha/mask.
- Retain source coordinates, entity, direction, action, and frame index.
- Detect duplicates without deleting automatically.
- Generate contact sheets for human verification.
- Add a round-trip test that reconstructs a sheet and compares it to the source.
- Describe varying sheet layouts in configuration rather than hard-coding one layout.

## Alignment and 512 px Preparation

Align sprites by a semantic ground-contact anchor, normally the midpoint between the feet or the lowest grounded body point. Bounding-box centering alone causes animation jitter.

1. Determine the tight native bounding box while retaining offsets.
2. Place the sprite on a canonical low-resolution canvas.
3. Align using the recorded anchor.
4. Preserve meaningful relative scale between entities.
5. Enlarge the whole canvas to 512×512 using an exact integer nearest-neighbor factor.
6. Test that every source pixel becomes a uniform integer-sized block.

Never use bilinear, bicubic, Lanczos, antialiasing, JPEG, arbitrary rotations, random crops, blur, or color shifts. Avoid flips unless direction labels and asymmetric details are corrected.

### Transparency

Diffusion training is RGB rather than RGBA. Preserve masks separately.

For the first experiment, composite sprites on one fixed chroma color absent from the sprite palette. Record the color. At inference, remove conservatively connected chroma background regions and restore alpha.

If chroma leaks into subjects, test a controlled set of flat backgrounds plus mask-based postprocessing. Do not add scenic backgrounds.

## Captions

Use the unique trigger token rscbillboard.

Template:

~~~text
rscbillboard, {entity description}, {action}, {direction}-facing, animation frame, isolated billboard sprite, crude low-detail rendering, jagged silhouette, limited blunt shading
~~~

Example:

~~~text
rscbillboard, goblin holding a short sword, walking, east-facing, animation frame, isolated billboard sprite, crude low-detail rendering, jagged silhouette, limited blunt shading
~~~

Caption subject matter explicitly so armor, weapons, or humanoid anatomy do not become inseparable from the style trigger. Use fixed terms for directions and actions.

## Splits

Split by complete entity:

- training: about 80%;
- validation: about 10%;
- test: about 10%.

All directions and frames for one entity must remain in one split. Frame-level random splitting leaks near-duplicates and invalidates evaluation.

Approximately stratify by humanoid, quadruped, flying, undead, large monster, small creature, and equipped character. Inspect class balance so the trigger does not merely learn the dominant subject.

## Stage 1 Training

Use a minimally stylized, legally suitable SD 1.5 base checkpoint in safetensors format. Record its exact hash and license.

~~~yaml
model_family: stable-diffusion-1.5
method: lora
resolution: 512
network_rank: 32
network_alpha: 16_or_32
batch_size: 2_to_4_as_vram_allows
unet_learning_rate: 1.0e-4
text_encoder:
  initial_run: disabled
  optional_learning_rate: 5.0e-6
optimizer: AdamW8bit
mixed_precision: fp16
gradient_checkpointing: true_if_needed
cache_latents: true_if_compatible
save_every_n_steps: 500
initial_budget_steps: 3000_to_8000
seed: fixed_and_recorded
~~~

These are starting values, not guaranteed optimums. Calculate epochs and repeats from actual distinct frames. Adjacent animation frames may make the raw frame count overstate dataset diversity.

Implementations:

- Kohya sd-scripts: https://github.com/kohya-ss/sd-scripts
- Hugging Face Diffusers LoRA docs: https://huggingface.co/docs/diffusers/en/training/lora

Pin versions after the first successful smoke test. Save the command, config, Git commit, Python/CUDA versions, GPU, seed, and model hash with every run.

## Smoke Test

Before full training:

1. Train a representative subset for 100–300 steps.
2. Confirm finite loss and checkpoint saving.
3. Generate the fixed prompt set.
4. Confirm LoRA loading, output dimensions, and background behavior.
5. Resume from a checkpoint.
6. Reproduce the run in a fresh environment.

This validates plumbing, not visual quality.

## Evaluation

Version-control a fixed prompt matrix containing familiar and novel concepts:

~~~text
rscbillboard, rat, idle, south-facing, isolated billboard sprite
rscbillboard, armored skeleton holding a round shield, walking, east-facing, isolated billboard sprite
rscbillboard, large red demon holding a wooden club, attacking, north-facing, isolated billboard sprite
rscbillboard, merchant wearing green clothing, idle, southwest-facing, isolated billboard sprite
rscbillboard, horned swamp creature, walking, west-facing, isolated billboard sprite
~~~

For every checkpoint, hold prompts, negative prompts, seeds, sampler, steps, guidance, dimensions, and LoRA strength constant. Compare labeled grids at both 512 px and native sprite size.

Score:

- desired roughness and lack of polish;
- blunt/limited shading;
- target-like silhouette;
- subject recognition;
- direction, action, and equipment accuracy;
- uniform pixel-block size;
- background removability;
- readability at native size;
- absence of copied training sprites.

Loss alone does not select the best checkpoint.

## Memorization Checks

- Compare perceptual hashes against every training frame.
- Perform nearest-neighbor retrieval against the dataset.
- Flag close matches for human inspection.
- Test fully withheld entities.
- Vary seeds with prompts fixed.
- Test novel combinations of class, equipment, direction, and action.

The goal is a learned rendering domain, not archival reconstruction.

## Escalation

If outputs are too polished:

1. Audit for cleaner/later-era contaminating assets.
2. Verify preprocessing introduced no smoothing.
3. Compare early and late checkpoints.
4. Increase LoRA rank from 32 to 64.
5. Run a controlled rank-64 comparison.
6. If capacity is still insufficient, fine-tune the full SD 1.5 U-Net.

If outputs memorize:

1. Reduce repeats or steps.
2. Reduce rank.
3. Improve caption specificity.
4. Increase entity/pose diversity.
5. Select an earlier checkpoint.

Change one major variable per experiment. Do not move to FLUX merely because the first run is imperfect.

## Direction and Animation Limitation

Independent diffusion generations will not reliably preserve identity, equipment, proportions, or palette across a full sheet.

Initial production workflow:

1. Generate a canonical south-facing idle sprite.
2. Select and manually clean it.
3. Use it as an image-to-image reference for adjacent directions.
4. Use low denoising strength to retain identity.
5. Generate actions from the nearest cleaned pose.
6. Restore alpha and downscale.
7. Quantize and manually repair pixels.
8. Assemble and preview in-engine.

Generated output is a draft, not automatically production-ready.

If reliably aligned direction/action pairs exist, a later experiment can train a conditional image-to-image model:

~~~text
(source sprite, requested direction/action) -> target sprite
~~~

Do not implement that until Stage 1 shows that the rendering style is learnable.

## Deterministic Postprocessing

Build a command that:

1. removes the chroma background;
2. restores alpha;
3. downsizes by exact integer nearest-neighbor;
4. optionally maps to a chosen palette without dithering;
5. preserves the ground anchor;
6. writes lossless PNG;
7. records parameters in sidecar JSON;
8. optionally assembles the engine-required sheet.

Retain both raw generations and manually cleaned derivatives.

## Reproducibility and Repository Safety

- Use a virtual environment or container; do not install globally.
- Pin versions after verification.
- Verify model hashes.
- Never commit model weights, raw third-party assets, caches, secrets, or tokens publicly.
- Add raw data and checkpoints to .gitignore.
- Publish only manifests and scripts until licensing is resolved.
- Make preprocessing idempotent.
- Fail on unknown directions, missing captions, broken alpha, noninteger scaling, duplicate IDs, or split leakage.

## Milestones

### M0 — Rights and inventory

- Sources/licenses, GPU, sheet formats, and target format recorded.
- No unresolved mixed-provenance training files.

### M1 — Dataset pipeline

- Lossless extraction/reconstruction passes.
- Anchors are stable.
- Captions/manifests validate.
- Entity splits do not leak.
- Prepared images contain nearest-neighbor blocks only.

### M2 — Smoke test

- Base loads; LoRA trains, saves, reloads, and resumes.
- Evaluation grids generate.

### M3 — Rank-32 baseline

- Full baseline and memorization audit complete.
- Checkpoints compared at native size.
- Human review selects a checkpoint or names a failure mode.

### M4 — Targeted iteration

- Run rank-64 or revised-data experiment only if justified.
- Compare with identical evaluation settings.

### M5 — Game-ready prototype

- One novel entity has a complete directional/action set.
- Alpha, anchors, sheet assembly, and engine import work.
- Manual cleanup time per frame is measured.

## Success Definition

The experiment succeeds if it produces a novel, recognizable billboard creature that plausibly belongs beside the target sprites at native size and requires less work to clean than drawing from scratch.

Measure candidate acceptance rate, cleanup minutes per frame, cross-frame consistency, direction/action accuracy, memorization flags, and in-engine readability.

## First Instructions to the Coding Agent

1. Ask for GPU/VRAM, OS, dataset path, a representative sheet, layout metadata, target engine format, and asset-license evidence.
2. Inventory without modifying source files.
3. Create raw-asset and licensing manifests.
4. Implement and test lossless frame extraction.
5. Implement canonical anchoring and integer nearest-neighbor preparation.
6. Produce contact sheets for human approval.
7. Create entity-level train/validation/test splits.
8. Select and pin a trainer compatible with the GPU.
9. Run the 100–300-step smoke test.
10. Stop for visual review before a full training run.

Do not skip steps 1–6 to begin training sooner.
