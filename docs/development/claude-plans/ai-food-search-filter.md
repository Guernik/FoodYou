# "AI food" search filter

## Context

The food search screen shows a row of filter chips: **Recent**, **Your food**, and one per
enabled dataset (Open Food Facts, USDA, Swiss Food Composition Database). AI-created products
are already tagged `FoodSource.Type.Ai` at creation time, but there is no way to see only them —
today they are folded into "Your food" by a special clause in the search SQL.

This adds an **"AI food"** chip so users can filter to exactly the foods produced by the AI
analysis mechanism (the AI logging screen and the new Quick Add AI analysis).

The user also asked for this request to be transcribed and logged under
`docs/development/features-log/`, alongside the existing AI feature docs.

## Decisions (confirmed with user)

1. **Keep the "Your food" overlap.** The `PRODUCT_SOURCE_FILTER` clause that surfaces AI products
   under the User source stays as-is. "Your food" remains everything the user produced;
   "AI food" is a narrower sub-view of it. Counts intentionally overlap.
   → **No SQL and no DAO changes at all.**
2. **Chip always visible**, pinned with `alwaysShowFilter = true` like Recent and Your food.

Both decisions keep this a pure UI/ViewModel change.

## Reuse (do not write new equivalents)

| Need | Existing thing to reuse |
|---|---|
| Chip icon | `FoodSource.Type.Ai.Icon(modifier)` — `app/ui/food/component/FoodSource.kt:44`, already `Icons.Filled.AutoAwesome` |
| Chip label | new `headline_ai_food` string (see below); `headline_ai` exists but is `translatable="false"` and reads just "AI" |
| Count flow | `observeFoodCount(FoodSource.Type.Ai)` — private helper already in `FoodSearchViewModel.kt:132` |
| Pages flow | `observeFoodPages(FoodSource.Type.Ai)` — same file, line 139 |
| Room enum mapping | `FoodSource.Type.Ai` → `FoodSourceType` already handled in `common/infrastructure/room/FoodSourceType.kt`; SQL constant `AI = 4` exists |
| Local-only marker | `RemoteStatus.LocalOnly` — `FoodSearchUiState.kt:22` |
| Chip rendering | `FoodSearchFilters.kt` and `FoodSearchView.kt` both iterate `uiState.sources`, so they pick the new entry up **with no changes** |

`FoodSearchUseCase.remoteMediatorFactory` already returns `null` for `Ai` via its `else` branch —
correct, AI is local-only.

---

## Implementation

### 1. `app/ui/food/search/FoodFilter.kt` — add the enum case

Add `Ai` to `FoodFilter.Source`. Both `Icon()` and `stringResource()` are exhaustive `when`s, so
the compiler will point at the two places to fill in:

```kotlin
Ai -> FoodSource.Type.Ai.Icon(modifier)
Ai -> stringResource(Res.string.headline_ai_food)
```

Place `Ai` directly after `YourFood` in the enum — enum order drives chip order, and AI food
belongs next to the other local/user sources rather than after the remote datasets.

### 2. `app/ui/food/search/FoodSearchViewModel.kt` — add the source flows

Follow the `yourFoodState` pattern exactly (local-only + pinned):

```kotlin
private val aiPages = observeFoodPages(FoodSource.Type.Ai).cachedIn(viewModelScope)
private val aiState =
    observeFoodCount(FoodSource.Type.Ai).map { count ->
        FoodSourceUiState(
            remoteEnabled = RemoteStatus.LocalOnly,
            pages = aiPages,
            count = count,
            alwaysShowFilter = true,
        )
    }
```

Then add `FoodFilter.Source.Ai to aiState` to the `sources` map in the `uiState` combine.

**The one real snag — arity.** `uiState` currently uses a 7-argument `combine` and adding
`aiState` makes 8. Verified in `common/extension/FlowExt.kt` (47 lines): the project's own
`combine` defines typed overloads only up to **7** arguments — the current call is already using
that ceiling — plus two `Iterable<Flow<T>>` variants that require all flows to share one element
type, which these do not (`FoodSourceUiState` vs `FoodFilter` vs the history list). So there is
no drop-in 8-arg overload.

Recommended fix: **nest the five source states into their own combine**, which also reads better
than an 8-way destructure:

```kotlin
private val sourceStates =
    combine(recentFoodState, yourFoodState, aiState, openFoodFactsState, usdaState, swissState) {
        recent, yourFood, ai, off, usda, swiss ->
        mapOf(
            FoodFilter.Source.Recent to recent,
            FoodFilter.Source.YourFood to yourFood,
            FoodFilter.Source.Ai to ai,
            FoodFilter.Source.OpenFoodFacts to off,
            FoodFilter.Source.USDA to usda,
            FoodFilter.Source.SwissFoodCompositionDatabase to swiss,
        )
    }
```

That uses the existing 6-arg overload, and the outer `uiState` combine drops to 3 arguments
(`sourceStates`, `filter`, `searchHistory`). Adding an 8-arg overload to `FlowExt.kt` is the
alternative, but touches shared infrastructure for one call site.

**Leave the `init {}` auto-switch chain alone.** It walks Recent → YourFood → OpenFoodFacts →
USDA when a query yields nothing. Since AI products are still reachable through "Your food"
(decision 1), inserting `Ai` into that chain would just bounce the user to a narrower view of
results they can already see. Not adding it is the deliberate choice.

### 3. `shared/resources/src/commonMain/composeResources/values/strings.xml`

One new translatable string, near the other `headline_*` filter labels:

```xml
<string name="headline_ai_food">AI food</string>
```

Do **not** reuse `headline_ai` — it is `translatable="false"` and renders as bare "AI".
English `values/` only; the 15 translation directories are not touched.

### 4. `docs/development/features-log/ai-food-search-filter.md` — the feature log

This is an explicit deliverable of the request, not a byproduct. New doc following the house
style of `natural-language-ai-food-logging.md` (dated H1 → Summary → Problem → Locked decisions
→ Verification → Appendix file map). Sections:

- **Date:** 2026-08-13. No tracking issue (this came in directly, unlike #419).
- **Summary** — transcribe the request in the user's own framing: the search view currently has
  "Recent", "Your food", and per-dataset filters; add an "AI food" filter so users can filter to
  foods logged by the new AI analysis mechanism, using the `source=AI` tag already applied at
  creation.
- **Problem** — AI-created products are indistinguishable from hand-created ones in search; as
  users accumulate AI products there is no way to review or re-find just those.
- **Locked decisions** — the two confirmed above, each with the rejected alternative:
  1. *Keep the "Your food" overlap.* Rejected: dropping the `PRODUCT_SOURCE_FILTER` clause for
     clean non-overlapping chips — it would silently remove AI products from where users
     currently find them.
  2. *Chip always visible.* Rejected: count-gated visibility (the Swiss pattern) and API-key-gated
     visibility — the first hides the feature from users who have not used AI yet, the second
     needs new cross-module wiring from `LlmApiKeyRepository` into the search ViewModel.
- **Note the arity constraint** — `FlowExt.kt` tops out at a 7-arg `combine`; record the nesting
  workaround so the next person touching `uiState` does not rediscover it.
- **Appendix: file map** — table of the three modified files + this doc, matching the format used
  in the existing AI feature logs.

Also add a cross-reference line to this new doc from
`docs/development/features-log/natural-language-ai-food-logging.md`, so the AI feature docs stay
linked (that file already cross-links its UX-improvements pass the same way).

---

## Files touched

**Modified**
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/search/FoodFilter.kt`
- `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/search/FoodSearchViewModel.kt`
- `shared/resources/src/commonMain/composeResources/values/strings.xml`
- `docs/development/features-log/natural-language-ai-food-logging.md` (one cross-reference line)

**New**
- `docs/development/features-log/ai-food-search-filter.md`

**Deliberately untouched** — `FoodSearchDao.kt` (`PRODUCT_SOURCE_FILTER` keeps the overlap),
both chip renderers (`FoodSearchFilters.kt`, `FoodSearchView.kt`), `FoodSearchUseCase.kt`, the
Room schema, and `FoodSearchPreferences` (no enable/disable toggle for AI).

---

## Verification

1. **Build** — `./gradlew :app:assembleDebug`. The two exhaustive `when`s in `FoodFilter.kt`
   mean a missed branch is a compile error, so a clean build covers most of the risk here.
   Then `./gradlew :app:testDebugUnitTest` (no existing test covers search filtering; this is
   just a regression check).
2. **Formatting** — `just format` (needs `$KTFMT_JAR`; it was unset in the last session, so it
   may need setting before this passes).
3. **Manual, end-to-end:**
   - Open food search (diary → add food). Confirm an **AI food** chip appears next to
     "Your food", with the ✨ AutoAwesome icon, even at zero count.
   - Log something via AI (either the AI FAB or Quick Add → AI analysis with "Also save as
     product" ticked), return to search: the AI food count increments and the product is
     listed under that chip.
   - Confirm the same product still appears under **Your food** — that overlap is intended
     per decision 1, not a bug.
   - Type a query that matches an AI product and confirm it is found under the AI food chip
     (exercises the `observeFoodByQuery` path, not just the blank-query listing).
   - Confirm selecting the chip does not trigger a network fetch and that no loading spinner
     appears in it (it is local-only, so the chip shows a plain count).
   - Check the chip also renders in the expanded search-bar filter row (`FoodSearchView`).
