# Natural Language AI Food Logging

Date: 2026-07-16

Tracking issue: [#419](https://github.com/maksimowiczm/FoodYou/issues/419)  

Claude plan file: [Natural Language AI food logging](../claude-plans/natural-language-ai-food.md)

## Summary

Add a new **"AI"** entry to the diary's create FloatingActionButtonMenu (alongside
*New Recipe* and *New Product*). It opens a screen where the user types a free-text
meal description (e.g. "two poached eggs and a piece of white toast"). The app sends
the text to an OpenAI-compatible LLM, which returns a structured list of food items.
The user **reviews and edits** the parsed items, then confirms. Each item is saved as
a reusable Product and logged into the current meal/date as a diary entry.

## Problem

Manual macro tracking has high friction: searching a database, adjusting serving
sizes, and estimating weights. This causes users to abandon logging. Natural-language
entry removes that friction for quick, approximate logging.

## Locked decisions

### 1. Provider — single OpenAI-compatible client

- POST `{baseUrl}/chat/completions`, `Authorization: Bearer {apiKey}`, configurable model.
- Uses `response_format: json_schema` (structured outputs) to constrain the item list.
- `baseUrl` defaults to `https://api.openai.com/v1`.
- This one wire format covers OpenAI, local runners (Ollama / LM Studio), and gateways
  (OpenRouter -> Claude/Gemini). Native Anthropic/Gemini schemas are **deferred**.
- Settings expose: `baseUrl`, `apiKey`, `model`.

### 2. Output mapping — per-100g nutrition + estimated grams

- The LLM returns, per item: `name`, `isLiquid`, per-100g nutrition, and an
  `estimatedGrams` portion.
- Mapped to a `DiaryFoodProduct` with `servingWeight = estimatedGrams`, logged with
  `Measurement.Serving(1)`.
- This preserves the per-100g invariant baked into `NutritionFacts`, `DiaryFood`, and
  the weight-scaling logic. (Per-portion absolute facts were rejected as violating that
  invariant.)

### 3. Nutrient scope — core macros + key micros

- LLM estimates: `energy`, `proteins`, `carbohydrates`, `fats`, `dietaryFiber`,
  `sugars`, `saturatedFats`, `sodium`.
- All other `NutritionFacts` fields -> `NutrientValue.Incomplete(null)`.
- Full 42-field estimation was rejected (micro-nutrient estimates are largely
  fabricated at that granularity and inflate the prompt/schema).

### 4. Persistence — reusable Product **and** diary entry

- Each confirmed item is persisted via `CreateProductUseCase` (with the new
  `FoodSource.Type.Ai`) **and** logged via `CreateFoodDiaryEntryUseCase`.
- Products become reusable in search rather than one-off diary snapshots.

### 5. Review before logging

- The screen shows the parsed items in an **editable, reviewable** list; the user
  confirms before anything is written. Fully-automatic logging (as the issue literally
  states) was rejected — LLM estimates are noisy, and this is a health-tracking app.
- Meal and date are inherited from the navigation context (the FAB lives on the
  meal/date-scoped `DiaryFoodSearchScreen`), so no meal picker is needed.

### 6. API key storage — encrypted with MasterCrypto

- The LLM API key is a billable credential, so it is stored **encrypted** via the
  existing hardware-backed `MasterCrypto` (AndroidKeyStore AES/GCM), modeled on
  `OpenFoodFactsCredentialsRepositoryImpl`. `baseUrl` and `model` may be plaintext.
- Plaintext storage (as USDA does today) was rejected for a billable secret.

### 7. Placement — single `app` module

- New feature lives under the existing `app` module per decision log
  [0002](../decision-log/0002-minimize-gradle-modules.md). **No new Gradle module.**
- Follows the per-feature domain / infrastructure / ui layering:
  - Domain: `food` domain (LLM preferences, a `ParseMealDescription` use case, a
    `MealItem` domain type, a provider interface with one implementation).
  - Infrastructure: `food/infrastructure/ai` (OpenAI data source, Ktor client + DI
    module, encrypted preferences repo, JSON DTOs, mapper, system prompt + json_schema).
  - UI: `app/ui/food/ai` (input screen + ViewModel; settings screen/dialog for
    baseUrl + apiKey + model).

## Ripple: `FoodSource.Type` is a closed enum

Adding `Ai` touches every exhaustive `when` over `FoodSource.Type`. Known sites:

- `common/domain/food/FoodSource.kt` — add the `Ai` enum case.
- `common/infrastructure/room/FoodSourceTypeConverter.kt` — add the case in both
  converter `when`s, and add `FoodSourceTypeSQLConstants.AI = 4`.
  **No Room migration required** — source type is stored as a stable integer constant,
  not an enum ordinal, so the addition is purely additive.
- The Room `FoodSourceType` twin enum — add the `Ai` case.
- `app/ui/food/component/FoodSource.kt` — add `Icon()` and `stringResource()` cases.
- `app/ui/food/product/ProductForm.kt` (`FoodSource.Type.entries` dropdown) — **decide**
  whether to filter `Ai` out of the manually-selectable source picker (recommended, so
  users can't hand-pick "AI" as the origin of a product they typed themselves).

## Verification

- Build the `app` module.
- Point `baseUrl` at a local mock or Ollama (OpenAI-compatible endpoint) to test the
  parse -> review -> log flow without a paid key.
- Confirm: parsed items appear editable; on confirm, a Product is created with
  `FoodSource.Type.Ai` and a diary entry is logged into the correct meal/date with the
  estimated grams as a single serving; day/meal totals update.
- Error/empty states: missing key, malformed or refused LLM response, empty input.

## Appendix: file map

New files, grouped by layer.

### Domain — `food/ai/domain/`

| File | Purpose |
|---|---|
| `LlmSettings.kt` | Plaintext config (baseUrl, model) as `UserPreferences`; holds the OpenAI defaults. Key is *not* here. |
| `LlmApiKeyRepository.kt` | Interface for storing/loading the API key (encrypted impl lives in infra). |
| `MealItem.kt` | Domain model for one parsed food: name, isLiquid, per-100g `NutritionFacts`, estimatedGrams. |
| `ParseMealError.kt` | Sealed error type (MissingApiKey, Network, Unauthorized, Malformed, Refused, …). |
| `MealDescriptionParser.kt` | Interface: `parse(description) → Result<List<MealItem>, ParseMealError>`. |
| `ParseMealDescriptionUseCase.kt` | Validates non-blank input, delegates to the parser. |
| `LogMealItemsUseCase.kt` | Orchestrates persistence: per item → create Product + create diary entry. |
| `AiDomainModule.kt` | Koin registration for the two use cases. |

### Infrastructure — `food/ai/infrastructure/`

| File | Purpose |
|---|---|
| `model/ChatCompletion.kt` | Request/response DTOs for the OpenAI `/chat/completions` wire format. |
| `model/MealItemsDto.kt` | The schema-shaped payload the model returns (items + nutrition DTO). |
| `MealParseSchema.kt` | The strict JSON schema + the system prompt. |
| `OpenAiRemoteDataSource.kt` | Stateless HTTP layer: builds request, POSTs, handles status codes, unwraps the two JSON layers. |
| `AiRemoteException.kt` | Infra-level failure types (Unauthorized, RateLimited, Network, Refused, Malformed…). |
| `AiMealMapper.kt` | DTO → domain `MealItem`; field-name translation + per-field validation. |
| `OpenAiMealDescriptionParser.kt` | Implements `MealDescriptionParser`: loads key+settings, calls data source, maps errors to domain. |
| `EncryptedLlmApiKeyRepository.kt` | Stores the key encrypted via `MasterCrypto` (`ai:api_key`). |
| `DataStoreLlmSettingsRepository.kt` | Persists baseUrl/model in DataStore. |
| `AiModule.kt` | Koin: HTTP client (with the `encodeDefaults = true` fix), data source, mapper, parser, repos. |

### UI — `app/ui/food/ai/` (dialog under `app/ui/database/externaldatabases/`)

| File | Purpose |
|---|---|
| `AiFoodLoggingUiState.kt` | Sealed UI state (Input/Loading/Review/Logging/Error) + `EditableMealItem`. |
| `AiFoodLoggingViewModel.kt` | Drives parse → review → confirm; holds mealId/date; emits the "logged" event. |
| `AiFoodLoggingScreen.kt` | The screen: input, loading, editable review list, error/empty states. |
| `AiFoodLoggingModule.kt` | Koin registration for the ViewModel. |
| `UpdateLlmSettingsDialog.kt` | Settings dialog (baseUrl + model + API key). **Removed by the UX-improvements pass** — superseded by the full AI settings screen (see below). |

### Test — `app/src/commonTest/`

| File | Purpose |
|---|---|
| `food/ai/infrastructure/AiMealMapperTest.kt` | Locks the mapper's invariants: per-100g mapping, name/grams validation, bad-value dropping. |

### Modified (already-tracked) files

| File | Change |
|---|---|
| `common/domain/food/FoodSource.kt` | Added `Type.Ai` enum case. |
| `common/infrastructure/room/FoodSourceType.kt` | Added `Ai` to the Room twin enum + both converter `when`s. |
| `common/infrastructure/room/FoodSourceTypeConverter.kt` | Added `AI = 4` SQL constant + both `@TypeConverter` branches. |
| `food/FoodModule.kt` | Wires `aiDomainModule()` + `aiModule()`. |
| `app/ui/food/diary/FoodDiaryModule.kt` | Wires `aiFoodLogging()` (ViewModel). |
| `app/ui/food/diary/search/DiaryFoodSearchScreen.kt` | Threads `onAiLog` to the FAB. |
| `app/ui/food/diary/search/FoodDiarySearchFloatingActionButton.kt` | Third FAB menu item ("AI food logging"). |
| `app/ui/food/component/FoodSource.kt` | Added `Ai` icon + label cases. |
| `app/ui/food/product/ProductForm.kt` | Filtered `Ai` out of the manual source picker. |
| `app/navigation/FoodYouAppNavHost.kt` | `FoodDiaryAiLog` + `LlmSettings` routes and their composable/dialog blocks. (The `LlmSettings` dialog route was later replaced by the `AiSettings` screen route — see the UX-improvements map below.) |
| `shared/resources/.../values/strings.xml` | AI feature strings (headline, actions, errors). |

---

## UX improvements pass (2026-07-17)

Follow-up UX work. Request transcript + implementation notes:
[`UX_Improvements.md`](./UX_Improvements.md); plan:
[`../claude-plans/ai-food-logging-ux-improvements.md`](../claude-plans/ai-food-logging-ux-improvements.md).

### New files

| File | Purpose |
|---|---|
| `food/ai/domain/LlmVendor.kt` | Vendor presets (OpenAI / OpenRouter / Custom) with curated model lists; `fromBaseUrl()`. UI-only, not persisted. |
| `food/ai/domain/LlmConnectionTester.kt` | Interface + `TestConnectionResult` for the settings TEST check (impl in infra). |
| `food/ai/domain/TestLlmConnectionUseCase.kt` | Tests the *pending* baseUrl/model/apiKey before saving. |
| `food/ai/domain/SaveMealItemAsProductUseCase.kt` | Persists one reviewed item as a reusable `FoodSource.Type.Ai` product (reuses `CreateProductUseCase`). |
| `food/ai/domain/SaveMealItemsAsRecipeUseCase.kt` | Saves items as products, then composes a recipe (`CreateRecipeUseCase`, `Measurement.Gram` ingredients). |
| `food/ai/infrastructure/OpenAiConnectionTester.kt` | Implements `LlmConnectionTester` over `OpenAiRemoteDataSource.testConnection`. |
| `app/ui/settings/ai/AiSettingsScreen.kt` | Full AI settings screen: vendor/model dropdowns, base URL, masked API key, TEST button, Save. |
| `app/ui/settings/ai/AiSettingsViewModel.kt` | Drives the settings screen; test/save/clear-key. |
| `app/ui/settings/ai/AiSettingsUiState.kt` | Settings UI state + `TestState`. |
| `app/ui/settings/ai/AiSettingsModule.kt` | Koin registration for `AiSettingsViewModel` (`aiSettings()`). |
| `app/ui/settings/AiSettingsListItem.kt` | Settings-list row that opens the AI settings screen. |
| `app/src/commonTest/.../app/ui/food/ai/EditableMealItemTest.kt` | Locks `EditableMealItem.toMealItem()` macro round-trip (per-100g, null→Incomplete). |

### Modified files

| File | Change |
|---|---|
| `food/ai/infrastructure/OpenAiRemoteDataSource.kt` | Added `testConnection()` (minimal chat request, no structured output). |
| `food/ai/infrastructure/model/ChatCompletion.kt` | Added `ChatCompletionTestRequest` DTO. |
| `food/ai/domain/AiDomainModule.kt` | Registers `TestLlmConnectionUseCase`, `SaveMealItemAsProductUseCase`, `SaveMealItemsAsRecipeUseCase`. |
| `food/ai/infrastructure/AiModule.kt` | Binds `OpenAiConnectionTester` → `LlmConnectionTester`. |
| `app/ui/food/ai/AiFoodLoggingUiState.kt` | `EditableMealItem` gains `EditableNutrition` (8 editable macros), `expanded`, `savedProductId`. |
| `app/ui/food/ai/AiFoodLoggingViewModel.kt` | `toggleExpanded`, `saveAsProduct`, `saveAsRecipe`, snackbar `events` channel; injects the two save use cases. |
| `app/ui/food/ai/AiFoodLoggingScreen.kt` | Collapsible macro editor, "Save as product", "Save all as recipe" (name dialog). |
| `app/ui/settings/SettingsScreen.kt` | Added `onAi` + the AI settings list row. |
| `app/ui/UiModule.kt` | Wires `aiSettings()`. |
| `app/navigation/FoodYouAppNavHost.kt` | Replaced the `LlmSettings` dialog route with an `AiSettings` screen route; `onConfigure` and the Settings screen route to it. |
| `shared/resources/.../values/strings.xml` | Settings-section, vendor, TEST, save-as-product/recipe, and per-macro-label strings. |

### Removed files

| File | Reason |
|---|---|
| `app/ui/database/externaldatabases/UpdateLlmSettingsDialog.kt` | Superseded by `app/ui/settings/ai/AiSettingsScreen.kt`. |
