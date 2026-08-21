# "AI food" Search Filter

Date: 2026-08-13

Tracking issue: none — requested directly.

Claude plan file: [AI food search filter](../claude-plans/ai-food-search-filter.md)

## Summary

> I want to add a filter into the search product view. Currently, we have "Recent",
> "Your food", and other filters depending on which datasets the user have enabled.
> Now, we should add a "AI food" filter, so the user can filter foods logged by our
> new AI analysis mechanism. We already tag the AI created food with `source=AI`, so
> we can use that.

Adds an **AI food** chip to the food search filter row, alongside *Recent*, *Your food*
and the per-dataset chips. It filters to products created by the AI analysis mechanism —
both the AI food logging screen and the Quick Add AI analysis — using the existing
`FoodSource.Type.Ai` tag applied at creation time. No new tagging or schema work was
required.

## Problem

AI-created products are indistinguishable from hand-created ones in search: the search
SQL deliberately folds `sourceType = AI` into the *Your food* source so AI products
would not vanish from the UI when the feature shipped. As a user accumulates AI
products there is no way to review, audit or re-find just those — which matters more
for AI output than for hand-entered food, because the values are estimates and worth
double-checking.

## Locked decisions

### 1. "Your food" keeps showing AI products — the chips overlap

The `PRODUCT_SOURCE_FILTER` clause in `FoodSearchDao.kt` that surfaces AI products under
the User source is **left untouched**:

```sql
:source IS NULL
    OR p.sourceType = :source
    OR (:source = ${FoodSourceTypeSQLConstants.USER} AND p.sourceType = ${FoodSourceTypeSQLConstants.AI})
```

*Your food* remains "everything you produced"; *AI food* is a narrower sub-view of it.
The two counts intentionally overlap and an AI product is listed under both chips.

**Rejected:** dropping that clause for clean, non-overlapping chips with counts that sum
correctly. It would have silently removed AI products from where users currently find
them — a regression in discoverability traded for tidier arithmetic. Keeping the overlap
also means **no SQL, DAO or Room changes at all**, so this feature carries no migration
risk.

### 2. The chip is always visible

`aiState` is built with `alwaysShowFilter = true`, pinning the chip like *Recent* and
*Your food*, so it renders even at zero count.

**Rejected:**
- *Count-gated visibility* (the Swiss Food Composition Database pattern, where
  `shouldShowFilter` hides a chip until `count > 0`). Hides the filter from exactly the
  users who have not tried AI logging yet, making an already-optional feature harder to
  discover.
- *API-key-gated visibility* via `LlmApiKeyRepository.hasKey()`. Ties chip visibility to
  feature configuration rather than to data, and would need new cross-module wiring from
  the AI domain into the search ViewModel for no user-visible gain.

### 3. The auto-switch fallback chain is left alone

`FoodSearchViewModel.init {}` walks Recent → YourFood → OpenFoodFacts → USDA and selects
the first source with results when a query comes up empty. `Ai` was deliberately **not**
inserted. Because of decision 1, AI products are already reachable through *Your food*;
adding `Ai` to the chain would bounce the user into a narrower view of results they can
already see.

## Constraint worth remembering: `combine` arity

`FoodSearchViewModel.uiState` was a 7-argument `combine` — the maximum arity defined in
`common/extension/FlowExt.kt`. Adding an eighth flow (`aiState`) had nowhere to go: the
file's other overloads take `Iterable<Flow<T>>` and require every flow to share one
element type, which these do not (`FoodSourceUiState` vs `FoodFilter` vs the history
list).

Resolved by **nesting**: the six source states now combine into a private `sourceStates`
map flow using the existing 6-arg overload, and the outer `uiState` combine drops to
three arguments. Anyone adding a seventh *source* will hit the same ceiling again and
should extend `sourceStates`, not `uiState`.

## Verification

- `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`.
  Three exhaustive `when`s over `FoodFilter.Source` (icon, label, and the per-source
  `LazyListState` lookup in `FoodSearchApp.kt`) make a missed branch a compile error, so
  a clean build covers most of the risk.
- Manual: open food search and confirm an **AI food** chip appears next to *Your food*
  with the ✨ AutoAwesome icon at zero count; log something via AI and confirm the count
  increments and the product is listed; confirm the same product **still appears under
  *Your food*** (intended per decision 1, not a bug); query-match an AI product to
  exercise the `observeFoodByQuery` path; confirm no loading spinner appears in the chip
  (local-only source); confirm the chip also renders in the expanded search-bar filter
  row.

## Appendix: file map

| File | Change |
|---|---|
| `app/ui/food/search/FoodFilter.kt` | Added `Source.Ai` (after `YourFood`, since enum order drives chip order) + its `Icon()` and `stringResource()` branches. Icon delegates to the existing `FoodSource.Type.Ai.Icon`. |
| `app/ui/food/search/FoodSearchViewModel.kt` | Added `aiPages` / `aiState` (`RemoteStatus.LocalOnly`, `alwaysShowFilter = true`); extracted `sourceStates` to work around the combine arity ceiling. |
| `app/ui/food/search/FoodSearchAppState.kt` | Added `ListStates.ai` so the AI tab keeps its own scroll position. |
| `app/ui/food/search/FoodSearchApp.kt` | Added the `Ai` branch to the `ListStates.state(source)` lookup. |
| `shared/resources/.../values/strings.xml` | New translatable `headline_ai_food` ("AI food"). The existing `headline_ai` was not reused — it is `translatable="false"` and reads as bare "AI". |

**Deliberately untouched:** `FoodSearchDao.kt` (see decision 1), both chip renderers
(`FoodSearchFilters.kt` and `FoodSearchView.kt` iterate `uiState.sources` and picked the
new entry up for free), `FoodSearchUseCase.kt` (its `else` branch already returns a null
remote mediator for `Ai`, which is correct — AI is local-only), and `FoodSearchPreferences`
(AI has no enable/disable toggle).
