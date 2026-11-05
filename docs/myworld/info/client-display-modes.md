# Desktop display modes

The Graphics menu's **Window mode** row cycles through **Windowed → Fullscreen
→ Borderless**. Borderless remains the default for fresh settings and both
renderer presets. Existing saved `windowed` and `borderless-fullscreen` choices
remain valid; `fullscreen` now selects the distinct native fullscreen mode.

- **Windowed** restores the decorated window and its saved position and size.
- **Fullscreen** attaches the GLFW window to the primary monitor at its current
  resolution and refresh rate, covering the desktop taskbar. An unknown refresh
  rate uses GLFW's default instead of requesting an artificial video mode.
- **Borderless** keeps the existing undecorated desktop-window behavior. Desktop
  taskbar visibility in this mode remains controlled by the window manager.

Transitions reuse the same OpenGL context and release/suppress held input as
before. Fullscreen falls back to Borderless if no native monitor is available.
No gameplay, server, map, or public release files change with this feature.

## Verification

- `./scripts/build-client.sh`: passed.
- `test-opengl-window-viewport-extraction.py`: covers defaults, persistence,
  all mode transitions, saved bounds, HiDPI surface dimensions, unknown refresh
  rates, missing-monitor fallback, and context lifetime.
- `test-renderer-settings-panel-extraction.py`: covers the three selected labels
  and the unchanged Graphics action mapping.
- Graphics options, profile application, legacy scaling, input modifiers, and
  LWJGL hot-handle checks: passed.
- Compiler warning gate and static analysis: no new gated findings.
- Real GLFW/OpenGL transitions on isolated Linux Xvfb/Openbox: passed, including
  a simulated taskbar, fullscreen monitor attachment, restored window geometry,
  paired input resets, and the same context across all six transitions. These
  tests did not modify the user's desktop.

The broad renderer guardrail suite stops at the final, unrelated
`test-renderer-v2-world-geometry.py` source assertion about projected pickable
faces. The identical failure was reproduced on unchanged main
`d87d2dfeb4ab642abb68aa65810df1dfdb12ebae`; this feature does not change Scene
geometry. The preceding suite entries passed. The desktop Graphics fixture also
needed its existing Android/remastered-sprite availability assertion updated to
match the unchanged baseline guard.

Native Windows, macOS, Wayland, and physical multi-monitor behavior were not
executed here. Fullscreen uses GLFW's cross-platform monitor API; the primary
monitor selection is consistent with the existing Borderless implementation.

Local native-test evidence and repeatable isolated-desktop harness are under
`/home/justin/Core-Framework-ai-1/output/tests/display-modes/native/`; build,
guardrail, compiler, and static-analysis reports are under that worktree's
`output/reviews/display-modes/`.
