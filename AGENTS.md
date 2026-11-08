# Lunar Agent Guide

## Agent Workflow (feature work)
When starting a new feature, follow this sequence:

1. **Explore first.** Read the README roadmap (`README.md`, "Roadmap Direction") and the actual code to see what is already implemented vs. stubbed. Cross-reference stated roadmap items with reality (the README can be stale vs. the source). Search for hard `UnsupportedOperationException` throw-sites and platform capability flags (`PlatformCapabilities` in `PlatformRuntime.kt`) to find genuinely unwired targets.

2. **Turn findings into GitHub issues.** Use the `gh` CLI (auth already configured for the current account) to create one issue per feature suggestion, each grounded in file:line references. Use `--body-file` for any body containing backticks or special characters to avoid shell-quoting corruption. Tag with `enhancement`; add `good first issue` for small, isolated tasks.

3. **Branch off `master`.** Create and checkout a feature branch: `git checkout -b feature/<short-name> master`.

4. **Implement incrementally, tests first/next.**
   - Prefer small, composable helpers; keep pure domain logic in `shared/`.
   - Add/adjust tests alongside the change (see "Testing Expectations").
   - Round-trip settings changes through stores; keep local-first behavior intact.

5. **Run the validation suite** (see "Validation Before Submitting") before opening a PR. Do not claim success without it. When possible, push a commit per logical step so the PR diff reads clearly.

6. **Open a PR against `master`** (`gh pr create`), filling the template with the summary + what was tested + residual risks. Do not commit or merge without being asked.


## Project Shape
- `composeApp/`: Compose Multiplatform app UI, app state, platform runtime wiring, desktop/Android packaging.
- `shared/`: shared models, repository/storage logic, query/filter logic, duplicate detection, source registry types.
- `server/`: small Ktor server module.

Keep pure domain logic in `shared` whenever possible. Keep UI orchestration and platform behavior in `composeApp`.

## Working Style
- Prefer small, composable helpers over long inline state or UI blocks.
- Preserve existing design language unless the task is explicitly a redesign.
- Use `LunarTheme` and `lunarThemePalette()` for app styling. Do not introduce hardcoded one-off colors for normal UI work.
- When a screen file grows, extract repeated scaffolding or derived-state helpers before splitting behavior across many places.
- When `LunarAppState` changes, prefer named transition helpers over repeating manual state resets in multiple methods.

## Testing Expectations
- Any behavior change should come with tests unless the change is genuinely untestable.
- Put tests as close to the behavior as possible:
  - `shared/src/commonTest`: repository, query, storage, and model behavior.
  - `composeApp/src/commonTest`: app-state and shared UI logic that does not need a desktop test harness.
  - `composeApp/src/jvmTest`: Compose desktop UI smoke tests and interaction tests.
  - `server/src/test`: server behavior.
- Prefer high-signal tests over snapshot spam. Cover:
  - persistence and repository mutations
  - state transitions and edge cases
  - UI flows that would otherwise regress silently

## Validation Before Submitting
- On Windows, run:
  - `./gradlew.bat :server:test :composeApp:jvmTest :shared:jvmTest koverXmlReport koverVerify :composeApp:compileKotlinJvm :composeApp:compileDebugKotlinAndroid`
- On Linux CI, the UI test portion runs under `xvfb-run --auto-servernum`.
- If you touch packaging or release flow, also run the relevant packaging task for the current platform when feasible.
- Do not claim success without calling out any validation you could not run.

## Coverage
- Kover is the coverage backstop for this repo.
- Keep coverage moving up when you add features, especially around new state transitions and shared data behavior.
- If a change adds a new flow without a clear automated check, add one before finishing.

## Code Search And Edits
- Prefer `rg` / `rg --files` for search.
- Avoid broad refactors unless they clearly simplify the touched area.
- Do not revert unrelated user changes.
- Keep new files and edits ASCII unless the file already relies on Unicode content.

## UI Notes
- Library, setlist, and sight-reading work are core flows. Regressions here should get UI smoke coverage.
- Settings changes should round-trip through `AppSettingsStore` rather than only updating visual state.
- Source/import changes should preserve the local-first/offline-friendly model described in the app.

## Final Check
- Make sure new behavior is tested.
- Make sure the main Gradle validation command passes.
- Summarize what changed, what was tested, and any residual risks.
