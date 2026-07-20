# AI Food Logging — UX Improvements

## Context

The natural-language AI food logging feature (issue #419) shipped as a working
first cut but with minimal UX. Two rough edges remain:

1. **Settings** live in a cramped `AlertDialog` (`UpdateLlmSettingsDialog`) reachable
   only from the AI logging screen. There is no vendor guidance (user must know the
   baseURL and model string by hand), no way to verify the config works before
   burning a real parse call, and the API-key field gives no feedback when a key is
   already stored.
2. **Review screen** only lets the user edit name/grams/isLiquid and then logs every
   item as a Product + diary entry in one shot. The user can't see or correct the
   LLM's macro estimates, can't save an item as a reusable Product without logging it,
   and can't compose the parsed items into a Recipe.

The goal is a proper AI settings section with vendor presets + a TEST button, and a
richer review screen (editable macros, per-item "save as product", "save all as
recipe") — with saved foods behaving like any other Product/Recipe (editable,
deletable) via the existing use cases.

Decisions locked with the user:
- Vendor presets: **OpenAI + OpenRouter + Custom** (static curated model dropdowns; no
  `/models` network fetch). Manual model override always available.
- Settings location: **new full screen** off the main Settings list (the existing
  dialog gets redirected here).
- Save vs log: **per-item save + optional log** — save and log are independent, which
  matches the already-decoupled catalog-Product / diary-snapshot design.

---

## Part 1 — AI Settings section

### 1a. Vendor presets (domain)

New file `food/ai/domain/LlmVendor.kt` — a small enum/sealed set describing presets:

```kotlin
enum class LlmVendor(val baseUrl: String, val models: List<String>) {
    OpenAI("https://api.openai.com/v1", listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1")),
    OpenRouter("https://openrouter.ai/api/v1", listOf(
        "openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "google/gemini-flash-1.5", ...)),
    Custom(baseUrl = "", models = emptyList());
    companion object { fun fromBaseUrl(url: String): LlmVendor = /* match or Custom */ }
}
```

Model lists are curated static defaults — keep them short and current; the manual
override covers anything missing. `fromBaseUrl` lets the settings screen infer the
selected vendor from the persisted `LlmSettings.baseUrl` on load. Persistence is
unchanged — we still store only `baseUrl` + `model` in `LlmSettings`
(`food/ai/domain/LlmSettings.kt`) and the encrypted key via `LlmApiKeyRepository`.
Vendor is a UI-derived convenience, not new persisted state.

### 1b. TEST connection (domain + infra)

Add a lightweight validation call that exercises network + auth + model without a full
parse:

- Infra: add `suspend fun testConnection(baseUrl, model, apiKey): Result<Unit, AiRemoteException>`
  to `food/ai/infrastructure/OpenAiRemoteDataSource.kt`. Reuse the existing named
  `HttpClient`, URL building (`"${baseUrl.trimEnd('/')}/chat/completions"`), and status
  mapping (401/403 → Unauthorized, 429 → RateLimited, network → Network) already present
  in `chatCompletion`. Body = a minimal `ChatCompletionRequest` (model + one user
  message like "ping", **no** `response_format`, `max_tokens`/`max_completion_tokens`
  small) so it's cheap and doesn't depend on structured-output support. Success = HTTP
  200 with a well-formed chat response.
- Domain: new `food/ai/domain/TestLlmConnectionUseCase.kt` that takes the *pending*
  baseUrl/model/apiKey (so the user can test **before** saving) and delegates to the data
  source, translating `AiRemoteException` → a small `TestConnectionResult`
  (Success / Unauthorized / Network / RateLimited / Unknown). Register in
  `AiDomainModule.kt`.
- The data source is currently only referenced by the parser; expose it for injection
  (it's already a Koin `factory` in `AiModule.kt`, so the use case can `get()` it).

### 1c. Settings screen (UI)

New `app/ui/settings/ai/` package:
- `AiSettingsScreen.kt` — full `Scaffold` + `LargeFlexibleTopAppBar` (mirror
  `ExternalDatabasesScreen`). Contents:
  - **Vendor** dropdown (`ExposedDropdownMenuBox`) → OpenAI / OpenRouter / Custom.
    Selecting a preset fills baseUrl and resets the model dropdown to that vendor's list.
  - **Base URL** `OutlinedTextField` — read-only-ish for presets (editable for Custom;
    presets can still be overridden but that flips selection toward Custom on mismatch).
  - **Model**: dropdown of the vendor's models **plus** an "Enter manually" toggle that
    swaps the dropdown for a free-text field (the manual override the user asked for).
  - **API key** `OutlinedTextField` (password visual transformation). When
    `apiKeyRepository.hasKey()` is true and the field is untouched, show a masked
    placeholder (e.g. `••••••••`) with a "Replace key" affordance; blank on save =
    leave unchanged (preserve existing dialog semantics). Add a "Clear key" action
    wired to `apiKeyRepository.clear()`.
  - **TEST** button → calls the VM, shows an inline result row (spinner → ✓ "Connection
    OK" / ✗ mapped error string). Uses the *current on-screen* values, not the saved ones.
  - **Save** button → same write path as the dialog today
    (`settingsRepository.update { copy(baseUrl, model) }` + conditional
    `apiKeyRepository.store(key)`).
- `AiSettingsViewModel.kt` — holds editable baseUrl/model/vendor/manual-toggle state,
  `hasKey` flow, `testState` (Idle/Testing/Result), `test()` and `save()` methods.
  Inject `UserPreferencesRepository<LlmSettings>` (qualified
  `named(LlmSettings::class.qualifiedName!!)`), `LlmApiKeyRepository`,
  `TestLlmConnectionUseCase`. Register via a new `aiSettings()` Koin block wired where
  the settings VMs are registered.

### 1d. Wiring

- `app/ui/settings/AiSettingsListItem.kt` — thin wrapper over the shared
  `SettingsListItem` (model on `DatabaseSettingsListItem.kt`); icon + label +
  supporting text.
- `app/ui/settings/SettingsScreen.kt` — add an `item { AiSettingsListItem(onClick = onAi) }`
  and an `onAi: () -> Unit` param (place near Database).
- `app/navigation/FoodYouAppNavHost.kt` — add `@Serializable object AiSettings` route,
  register with `forwardBackwardComposable<AiSettings> { AiSettingsScreen(onBack = pop) }`,
  and thread `onAi = { navController.navigateSingleTop(AiSettings) }` from the Settings
  composable. **Redirect the existing dialog**: change the AI logging screen's
  `onConfigure` (and the `LlmSettings` dialog route) to navigate to `AiSettings` instead;
  the old `UpdateLlmSettingsDialog` + `LlmSettings` route can be deleted once nothing
  references them.

---

## Part 2 — Review screen: editable macros, save as product, save as recipe

### 2a. Expand the editable model

`app/ui/food/ai/AiFoodLoggingUiState.kt` — extend `EditableMealItem`:
- Add editable per-item nutrition. Simplest faithful approach: keep an
  `EditableNutrition` holding the 8 AI-populated fields as nullable Doubles
  (energy, proteins, carbohydrates, fats, dietaryFiber, sugars, saturatedFats, sodium),
  seeded from `item.nutritionFactsPer100g`. `toMealItem()` writes them back into a
  copied `NutritionFacts` via `Double?.toNutrientValue()`
  (`common/domain/food/NutrientValue.kt`), leaving the other 34 fields at their existing
  `Incomplete` defaults.
- Add `val expanded: Boolean` for the collapsible macro section.
- Add `val savedProductId: FoodId.Product?` to reflect "already saved as product".

Note the macros are entered/edited **per-100g** (that's the stored invariant); the card
can still display the computed `≈ kcal` for the current grams as it does today.

### 2b. New per-item / batch use cases (domain)

Reuse existing use cases — no new persistence primitives needed:
- **Save as product**: new `SaveMealItemAsProductUseCase` (or a method on a small AI
  orchestration class) calling the existing
  `food/domain/usecase/CreateProductUseCase.create(...)` with
  `source = FoodSource(FoodSource.Type.Ai)`, `servingWeight = grams`,
  `nutritionFacts = per-100g`, `history = FoodHistory.Created(now)` — exactly the product
  half of the current `LogMealItemsUseCase.logItem`. Returns the new
  `FoodId.Product`. Consider `insertUniqueProduct` to avoid duplicates on re-save.
- **Save all as recipe**: new `SaveMealItemsAsRecipeUseCase` that first ensures each item
  is a saved Product (create if not already), collects the resulting `FoodId.Product`s,
  then calls `food/domain/usecase/CreateRecipeUseCase.create(name, servings = 1, note,
  isLiquid, ingredients = productIds.map { it to Measurement.Gram(grams) }, history)`.
  (Recipe ingredients are passed **by FoodId + Measurement**, per `CreateRecipeUseCase`;
  a recipe derives nutrition from its ingredients, so grams-based measurement is correct.)
  Needs a recipe name — prompt the user with a small dialog / text field.
- Register both in `AiDomainModule.kt`; inject into the ViewModel via `FoodDiaryModule`'s
  `aiFoodLogging()` block.

Logging is unchanged: `LogMealItemsUseCase` stays as the "Log N items" path (still
creates Product + diary snapshot per item). Save and log remain independent —
consistent with the diary snapshot already being decoupled from the catalog Product
(confirmed in `RoomFoodDiaryEntryRepository`).

### 2c. Review UI

`app/ui/food/ai/AiFoodLoggingScreen.kt` — evolve `ReviewItemCard`:
- Add a collapsible macro section (expand/collapse chevron toggling
  `EditableMealItem.expanded`) with `OutlinedTextField`s for the 8 editable fields
  (numeric, per-100g), propagating edits via the existing `onEdit { copy(...) }` channel.
- Add a per-item **"Save as product"** button → VM `saveAsProduct(id)`; reflect
  `savedProductId != null` with a check/"Saved" state.
- Below the list, add **"Save all as recipe"** (opens a name dialog → VM
  `saveAsRecipe(name)`) alongside the existing **"Log N items"** button.

`AiFoodLoggingViewModel.kt` — add:
- `saveAsProduct(id)` → calls `SaveMealItemAsProductUseCase`, stores the returned id on
  that item, surfaces a snackbar.
- `saveAsRecipe(name)` → calls `SaveMealItemsAsRecipeUseCase` over the current items,
  snackbar on success.
- `toggleExpanded(id)` and macro-edit passthroughs (reuse `editItem`).
- Keep `confirm()`/`loggedEvents` as-is.

### 2d. Saved foods behave like existing products/recipes

No extra work: products created via `CreateProductUseCase` and recipes via
`CreateRecipeUseCase` are ordinary catalog rows. They already appear in search and are
editable via `UpdateProductUseCase` / `UpdateRecipeUseCase` and deletable via
`DeleteFoodUseCase` through the existing product/recipe UI. The only care point: keep
`FoodSource.Type.Ai` filtered out of the manual `ProductForm` source picker (already
done) so users can't hand-pick "AI" as an origin.

---

## Strings

Add resources to `shared/resources/.../values/strings.xml` (mirror the existing
`headline_ai_*` / `action_ai_*` naming): settings section title/subtitle, vendor labels
(OpenAI/OpenRouter/Custom), "Enter model manually", "Replace key", "Clear key", "Test
connection", test result strings (OK / failed variants), "Save as product", "Saved",
"Save as recipe", recipe-name dialog title, expand/collapse macro labels, and per-field
macro labels (reuse existing nutrition-field strings where they already exist).

---

## Critical files

**Reuse (do not reinvent):**
- `food/domain/usecase/CreateProductUseCase.kt` (`create`, `insertUniqueProduct` path)
- `food/domain/usecase/CreateRecipeUseCase.kt` (`create(..., ingredients: List<Pair<FoodId, Measurement>>, ...)`)
- `food/domain/usecase/UpdateProductUseCase.kt`, `UpdateRecipeUseCase.kt`, `DeleteFoodUseCase.kt` (edit/delete — already wired to product/recipe UI)
- `common/domain/food/NutritionFacts.kt` + `NutrientValue.kt` (`toNutrientValue`)
- `food/ai/infrastructure/OpenAiRemoteDataSource.kt` (extend for `testConnection`)
- `app/ui/common/component/SettingsListItem` + `DatabaseSettingsListItem.kt` (row pattern)
- `app/ui/database/externaldatabases/ExternalDatabasesScreen.kt` (full-settings-screen pattern)
- `NavControllerExt.kt` (`navigateSingleTop`, `popBackStackInclusive`)

**New:**
- `food/ai/domain/`: `LlmVendor.kt`, `TestLlmConnectionUseCase.kt`, `SaveMealItemAsProductUseCase.kt`, `SaveMealItemsAsRecipeUseCase.kt`
- `app/ui/settings/ai/`: `AiSettingsScreen.kt`, `AiSettingsViewModel.kt`
- `app/ui/settings/AiSettingsListItem.kt`

**Modify:**
- `food/ai/infrastructure/OpenAiRemoteDataSource.kt`, `food/ai/domain/AiDomainModule.kt`
- `app/ui/food/ai/AiFoodLoggingUiState.kt`, `AiFoodLoggingScreen.kt`, `AiFoodLoggingViewModel.kt`
- `app/ui/food/diary/FoodDiaryModule.kt` (`aiFoodLogging()` — inject new use cases)
- `app/ui/settings/SettingsScreen.kt`, `app/navigation/FoodYouAppNavHost.kt`
- `shared/resources/.../values/strings.xml`
- Delete once unreferenced: `app/ui/database/externaldatabases/UpdateLlmSettingsDialog.kt` + `LlmSettings` dialog route

---

## Verification

- `./gradlew :app:assembleDebug` compiles.
- Extend `AiMealMapperTest` region or add a small test for `EditableMealItem.toMealItem()`
  round-tripping edited macros into `NutritionFacts` (per-100g, `toNutrientValue` mapping).
- Manual (device/emulator, OpenAI key — the tested path):
  - Settings → **AI** → screen shows OpenAI preset, model dropdown, masked key when set.
  - **TEST** with a valid key → "Connection OK"; with a bad key → Unauthorized message;
    offline → Network message. (Point baseUrl at Ollama / a mock to test without spend.)
  - Parse a meal → expand an item, edit a macro, confirm the `≈ kcal` updates.
  - **Save as product** on one item → it appears in product search, is editable/deletable.
  - **Save all as recipe** → recipe appears in search with summed nutrition, editable/deletable.
  - **Log N items** still logs to the diary and day/meal totals update.
