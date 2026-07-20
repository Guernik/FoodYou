# AI Food Logging — UX Improvements

Date: 2026-07-17

Follow-up UX work for the natural-language AI food logging feature (issue #419).
Verbatim transcript of the requested improvements.

## 1. Settings

We need to add a section into the app settings to properly configure the AI related
items. Also, we should provide with vendor prefilled options, so if I choose OpenAI,
the baseURL is prefilled, and I get a dropdown of models to choose, with the option to
manually override the model name (but initially should be a dropdown). Also the API Key
should be set up in this screen. If the api key has been already set up, then show
asterisks, with the ability for the user to set up a new key, in case they made a
mistake. Also a "TEST" button should be present, so the user can validate the settings.
Upon pressing the TEST button, a sample request should be made to the LLM (just something
so we know the network and settings are OK).

## 2. Food logging

After the response from the LLM, each food item should have the option to be stored as a
new Product. Also, each food should have a collapsible section, in which the user should
see the values of macronutrients (and fiber, etc) returned by the LLM, with the ability
to edit those. Finally, the user should be able to save the combination of foods as a new
Recipe. Saved foods should behave as the already existing products and recipes, with the
ability to edit and delete.

---

## Implementation notes (2026-07-17)

Plan: `docs/development/claude-plans/ai-food-logging-ux-improvements.md`.

### Part 1 — AI settings section

- **`LlmVendor`** (`food/ai/domain/LlmVendor.kt`): OpenAI / OpenRouter / Custom presets, each
  with a curated static model list. `fromBaseUrl()` infers the vendor from a persisted base URL.
  Vendor is UI-only; persistence still stores just `baseUrl` + `model` (+ encrypted key).
- **TEST connection**: `OpenAiRemoteDataSource.testConnection()` sends a minimal chat request
  (`ChatCompletionTestRequest`, no structured output, `max_completion_tokens = 1`). Wrapped by
  the domain `LlmConnectionTester` interface (impl `OpenAiConnectionTester`) and
  `TestLlmConnectionUseCase`, which validate the *on-screen* values before saving.
- **`AiSettingsScreen` / `AiSettingsViewModel`** (`app/ui/settings/ai/`): vendor dropdown,
  base-URL field (read-only for presets), model dropdown + "enter manually" toggle, API-key
  field (masked placeholder + supporting text when a key is stored, "Clear key"), TEST button
  with inline result, Save. Registered via `aiSettings()` in `UiModule`.
- **Entry point**: new `AiSettingsListItem` row in `SettingsScreen` → `AiSettings` nav route
  (`forwardBackwardComposable`). The AI logging screen's `onConfigure` now routes here. The old
  `UpdateLlmSettingsDialog` + `LlmSettings` dialog route were removed.

### Part 2 — Review screen

- **`EditableMealItem`** extended with `EditableNutrition` (the 8 AI fields, per-100g), `expanded`,
  and `savedProductId`. `toMealItem()` writes edited macros back via `Double?.toNutrientValue()`.
- **Save use cases** (`food/ai/domain/`): `SaveMealItemAsProductUseCase` (reuses
  `CreateProductUseCase`, `FoodSource.Type.Ai`) and `SaveMealItemsAsRecipeUseCase` (creates each
  item as a product, then a `CreateRecipeUseCase` recipe with `Measurement.Gram` ingredients).
- **UI**: `ReviewItemCard` gains a collapsible per-100g macro editor, a per-item "Save as product"
  button (→ "Saved" state), and the review footer adds "Save all as recipe" (name dialog) beside
  "Log N items". Snackbar events surfaced via a new `events` channel on the ViewModel.
- Saved products/recipes are ordinary catalog rows — editable/deletable through the existing
  product/recipe UI. Logging remains independent of saving.

### Verification

- `./gradlew :app:assembleDebug` — success.
- `./gradlew :app:testDebugUnitTest` — `AiMealMapperTest` (4) + new `EditableMealItemTest` (2)
  pass, 0 failures. Not yet exercised on a device beyond the build.

---

## Round 2 (2026-07-17)

Three follow-ups. Plan: `../claude-plans/ai-food-logging-ux-improvements.md` §"round 2".

### Bug fix — AI products invisible in "Your food"

Root cause: `food/search/infrastructure/room/FoodSearchDao.kt` product CTEs filter
`p.sourceType = :source`, and the "Your food" tab passes `source = User`. AI products are
saved with `sourceType = Ai`, matching no product tab (they persisted fine, hence worked as
recipe ingredients). Fix: a shared `PRODUCT_SOURCE_FILTER` SQL constant that also admits
`Ai` when `:source = User`, applied to the six product queries (browse / text / barcode ×
data+count). Recipe CTEs and the `observeRecent*` queries untouched. Query-only, no
migration; surfaces previously-saved AI products retroactively.

### Prefill recipe name

`AiFoodLoggingViewModel` now retains the last parse description (`lastDescription`). The
`SaveRecipeDialog` seeds its field with that text, whitespace-collapsed and capped to
`MAX_RECIPE_NAME_LENGTH = 60`, fully selected (`TextRange(0, len)`) for quick overwrite,
with `InputTransformation.maxLength` enforcing the cap on typing.

### Primary + overflow review actions

Footer replaced with an accent **"Save as recipe and log"** primary button plus an overflow
(`DropdownMenu`) exposing "Save as recipe" and "Log N items". New VM method
`saveAsRecipeAndLog` composes the existing `SaveMealItemsAsRecipeUseCase` + the log path
(logging refactored into a shared private `logItems`). Per-item "Save as product" unchanged.

### Verification

- `./gradlew :app:assembleDebug` — success (KSP/Room validated the DAO SQL change).
- `./gradlew :app:testDebugUnitTest` — `AiMealMapperTest` (4) + `EditableMealItemTest` (2),
  0 failures. Device pass still pending (esp. confirming AI products now show under
  "Your food").