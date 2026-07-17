# Natural Language AI Food Logging (issue #419)

## Context

Manual food logging (search DB → pick serving → adjust weight) is high-friction and drives users to
abandon macro tracking. Issue #419 proposes a text box where a user types a plain-language meal
("two poached eggs and a piece of white toast"), an LLM estimates the nutrition, and the items are
logged into the diary. To keep the app open/zero-server-cost, users bring their own LLM endpoint + key.

**Note:** FoodYou does not accept upstream code contributions and develops in private (docs decision
log 0004); this work targets a fork/internal branch but still follows upstream architecture.

### Decisions locked with the user (do not re-open)
- **Entry point:** a third "AI" item in the existing create FAB menu (New Recipe / New Product / **AI**),
  opening a dedicated screen — *not* auto-log. The screen shows an **editable review list** the user
  confirms before anything is written. (Health-tracking safety.)
- **Provider (v1):** a single **OpenAI-compatible** client. `POST {baseUrl}/chat/completions`,
  `Authorization: Bearer {key}`, `response_format` = `json_schema` (strict structured outputs).
  Settings expose **baseUrl + apiKey + model**. This one wire covers OpenAI, local runners
  (Ollama/LM Studio/llama.cpp), and gateways (OpenRouter → Claude/Gemini). Native Anthropic/Gemini deferred.
- **Output→model mapping:** LLM returns **per-100g** nutrition + an **estimatedGrams** portion per item.
  Preserve the per-100g invariant: build a `DiaryFoodProduct` with `servingWeight = estimatedGrams`
  and log with `Measurement.Serving(1.0)`.
- **Nutrient scope:** LLM estimates 8 fields — `energy, proteins, carbohydrates, fats, dietaryFiber,
  sugars, saturatedFats, sodium`. The other 34 `NutritionFacts` fields stay `NutrientValue.Incomplete(null)`.
- **Persistence:** each confirmed item becomes both a reusable **Product** (`source = FoodSource.Type.Ai`)
  and a **diary entry**.
- **Key at rest:** **encrypt** the API key with existing `MasterCrypto` (like OpenFoodFacts creds),
  not plaintext like USDA. baseUrl/model are plaintext prefs.
- **Placement:** single `app` module (decision log 0002); reuse existing per-feature domain/infra/ui layering.

## Reuse (verified paths, under `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/`)
- FAB entry: `app/ui/food/diary/search/FoodDiarySearchFloatingActionButton.kt`; host
  `app/ui/food/diary/search/DiaryFoodSearchScreen.kt` already receives `date: LocalDate` + `mealId: Long`.
- Product create: `food/domain/usecase/CreateProductUseCase.kt` (`create(...): Result<FoodId.Product,_>`,
  `history = FoodHistory.Created(now)` — a `CreationHistory`).
- Diary log: `fooddiary/domain/usecase/CreateFoodDiaryEntryUseCase.kt`
  (`Measurement.Serving` requires `food.servingWeight != null` — satisfied by `estimatedGrams`).
- `fooddiary/domain/entity/DiaryFoodProduct.kt`; `common/domain/food/{NutritionFacts,NutrientValue,FoodSource}.kt`
  (`Double?.toNutrientValue()`); `common/domain/measurement/Measurement.kt` (`Serving(quantity: Double)`).
- HTTP/DI template: `food/infrastructure/usda/{USDARemoteDataSource,USDAModule}.kt`
  (named `HttpClient` single + `HttpTimeout` + `ContentNegotiation` JSON; `handleResponse/handleException`);
  `common/config/NetworkConfig.kt` (userAgent).
- Encrypted secret: `food/infrastructure/openfoodfacts/OpenFoodFactsCredentialsRepositoryImpl.kt`
  (`MasterCrypto` + `byteArrayPreferencesKey`); `common/crypto/MasterCrypto.kt`.
- Prefs: `food/search/domain/FoodSearchPreferences.kt` +
  `food/search/infrastructure/repository/DataStoreFoodSearchPreferencesRepository.kt`;
  `userPreferencesRepositoryOf(::…)` / `userPreferencesRepository()` helpers (`common/infrastructure/koin/`).
- Settings UI template: `app/ui/database/externaldatabases/{ExternalDatabasesScreen,UpdateUsdaApiKeyDialog}.kt`.
- Nav: `app/navigation/FoodYouAppNavHost.kt` (`@Serializable` routes, `forwardBackwardComposable`,
  `dialog<>`); `NavControllerExt.kt` (`navigateSingleTop`, `popBackStackInclusive`).
- Result/log: `common/result/Result.kt` (`Ok`/`Err`/`fold`), `common/log/Logger.kt` (`logAndReturnFailure`).

## Implementation

### 1. Domain — new pkg `food.ai.domain`
- `LlmSettings.kt` — `UserPreferences` with `baseUrl` (default `https://api.openai.com/v1`) + `model`
  (default `gpt-4o-mini`). **Do not** put the key here.
- `LlmApiKeyRepository.kt` — `store/clear/loadKey()/hasKey(): Flow<Boolean>` (mirror OFF creds repo).
- `MealItem.kt` — `data class MealItem(name, isLiquid, nutritionFactsPer100g: NutritionFacts, estimatedGrams: Double)`.
- `MealDescriptionParser.kt` — `suspend fun parse(desc): Result<List<MealItem>, ParseMealError>`.
- `ParseMealError.kt` — `EmptyInput, MissingApiKey, Network, RateLimited, Unauthorized, MalformedResponse,
  Refused, Unknown(msg)`.
- `ParseMealDescriptionUseCase.kt` — validate non-blank; delegate to parser; log failures.
- `LogMealItemsUseCase.kt` — per item: `CreateProductUseCase.create(... source=FoodSource(Type.Ai),
  servingWeight=estimatedGrams, nutritionFacts=per100g, history=FoodHistory.Created(now))`, then
  `CreateFoodDiaryEntryUseCase.createDiaryEntry(Measurement.Serving(1.0), mealId, date,
  DiaryFoodProduct(... servingWeight=estimatedGrams, source=FoodSource(Type.Ai)))`. Return per-item
  outcome so a partial failure keeps the review list.
- `AiDomainModule.kt` — `factoryOf` the two use cases; called from `food/FoodModule.kt`.

### 2. Infrastructure — new pkg `food.infrastructure.ai`
- `model/` DTOs (`@Serializable`, `Json { ignoreUnknownKeys = true }`): `ChatCompletionRequest`,
  `ChatMessage`, `ResponseFormat` (json_schema envelope, `strict=true`), `ChatCompletionResponse`/`Choice`
  (+ optional `refusal`), `ErrorResponse`, and `MealItemsDto`/`MealItemDto` (the 8-nutrient + name/isLiquid/
  estimatedGrams payload).
- `MealParseSchema.kt` — the JSON Schema (`strict`, `additionalProperties:false`, all fields required) +
  the system prompt (per-100g in grams, energy in kcal, sodium in grams; estimate a realistic portion;
  set isLiquid; never invent items).
- `OpenAiRemoteDataSource.kt` — modeled on `USDARemoteDataSource`; stateless `chatCompletion(baseUrl, model,
  apiKey, description): kotlin.Result<MealItemsDto>`; parse `choices[0].message.content` (a JSON string).
  Map `401→Unauthorized, 429→RateLimited, other→Unknown, connection→Network`; `refusal`/empty→Refused;
  re-throw `CancellationException`.
- `AiMealMapper.kt` — DTO→`MealItem`, building `NutritionFacts` via `toNutrientValue()` on the 8 fields
  (name mapping: `protein→proteins`, `fat→fats`). Reject `estimatedGrams <= 0`.
- `OpenAiMealDescriptionParser.kt : MealDescriptionParser` — read `LlmSettings` + key; null key→`MissingApiKey`;
  empty items→`Refused`; translate errors to `ParseMealError`.
- `EncryptedLlmApiKeyRepository.kt : LlmApiKeyRepository` — `byteArrayPreferencesKey("ai:api_key")` via `MasterCrypto`.
- `DataStoreLlmSettingsRepository.kt` — `stringPreferencesKey("ai:base_url"|"ai:model")` (like the food-search prefs repo).
- `AiModule.kt` — named `HttpClient` single (`HttpTimeout` + JSON `ContentNegotiation`); factories for
  data source / mapper / parser(`.bind<MealDescriptionParser>()`); `EncryptedLlmApiKeyRepository.bind<LlmApiKeyRepository>()`;
  `userPreferencesRepositoryOf(::DataStoreLlmSettingsRepository)`. Called from `food/infrastructure/FoodInfrastructureModule.kt`.
- Optionally add `RemoteFoodException.Ai.*` in `food/domain/entity/RemoteFoodException.kt` for symmetry.

### 3. `FoodSource.Type.Ai` enum ripple (migration-free — source stored as stable Int, no CHECK constraint)
Edit and add the `Ai` branch to each:
- `common/domain/food/FoodSource.kt` — add `Ai`.
- `common/infrastructure/room/FoodSourceType.kt` — add `Ai` + both `toDomain()`/`toEntity()` branches.
- `common/infrastructure/room/FoodSourceTypeConverter.kt` — add `FoodSourceTypeSQLConstants.AI = 4` + both `@TypeConverter` branches.
- `app/ui/food/component/FoodSource.kt` — `Icon()` + `stringResource()` (icon e.g. `Icons.Filled.AutoAwesome`, `Res.string.headline_ai`).
- After the enum change, `grep -rn "FoodSource.Type\." app/src` and let the compiler flag any remaining
  non-`else` exhaustive `when`. `FoodSearchUseCase.kt` has `else -> null` (no change needed).
- **Product decision to make:** `ProductForm.kt:779` iterates `FoodSource.Type.entries` in the manual
  source-type picker. Filter `Ai` out there (a hand-typed product shouldn't be selectable as "AI").

### 4. UI — new pkg `app.ui.food.ai` + settings dialog
- `AiFoodLoggingViewModel.kt` — `koinViewModel { parametersOf(date, mealId) }`; states `Input(text, hasApiKey)`
  / `Loading` / `Review(editable items)` / `Error(ParseMealError)` / empty. Actions: `parse()`, per-item
  edit/remove, `confirm()`→`LogMealItemsUseCase`; one-shot success event → navigate back + snackbar
  (mirror `DiaryFoodSearchViewModel` events).
- `AiFoodLoggingScreen.kt` — `Scaffold`+`TopAppBar` (meal name / formatted date) like `DiaryFoodSearchScreen`;
  Input (multiline field + "Analyze"; if `!hasApiKey` show "Add API key"→`onConfigure`); Review (`LazyColumn`
  of editable cards: name, grams, isLiquid, expandable nutrients, delete; bottom "Log N items"); Loading/Error/empty.
  Reuse nutrient input components from `app/ui/food/product/`.
- `app/ui/database/externaldatabases/UpdateLlmSettingsDialog.kt` — baseUrl + model via
  `UserPreferencesRepository<LlmSettings>.update{}`, key via `LlmApiKeyRepository` (`koinInject`). Optionally add
  an "AI food logging" card to `ExternalDatabasesScreen.kt` for discoverability.
- Edit `FoodDiarySearchFloatingActionButton.kt` (3rd `FloatingActionButtonMenuItem` + `onAiLog`) and
  `DiaryFoodSearchScreen.kt` (thread `onAiLog`, passing existing `date`/`mealId`).

### 5. Navigation — edit `app/navigation/FoodYouAppNavHost.kt`
- Routes: `@Serializable data class FoodDiaryAiLog(val date: Long, val mealId: Long)`, `@Serializable object LlmSettings`.
- In the `FoodDiarySearch` block: `onAiLog = { navController.navigateSingleTop(FoodDiaryAiLog(date, mealId)) }`.
- Add `forwardBackwardComposable<FoodDiaryAiLog>` rendering `AiFoodLoggingScreen(onBack/onLogged =
  popBackStackInclusive, onConfigure = navigateSingleTop(LlmSettings), date = LocalDate.fromEpochDays(date),
  mealId, animatedVisibilityScope = this)`, and `dialog<LlmSettings> { UpdateLlmSettingsDialog(...) }`.

### 6. Strings — `shared/resources/src/commonMain/composeResources/values/strings.xml`
`headline_ai`, `headline_ai_food_logging`, `action_analyze`, `action_log_items`,
`description_ai_food_logging` (privacy note: text is sent to the configured endpoint),
`headline_ai_base_url|model|api_key`, per-error `error_ai_*`, `description_ai_no_items`,
`neutral_ai_items_logged`. English defaults; other locales inherit until translated.

## Verification
1. **Compile after the enum change first:** `./gradlew :app:compileKotlinMetadata` (or
   `:app:compileDebugKotlinAndroid`) — fastest way to surface any missed exhaustive `when`; then `:app:assembleDebug`.
2. **Parse+log against a free endpoint (no OpenAI cost):** set `baseUrl=http://localhost:11434/v1`
   (Ollama) or LM Studio, dummy key; enter "two poached eggs and a piece of white toast"; expect 2 review
   items with plausible per-100g + grams. Confirm → each creates a Product (`Type.Ai`) and a diary entry
   logged as `Serving(1)` with `servingWeight = estimatedGrams`; check the diary shows weight ==
   estimatedGrams and per-100g nutrition scales correctly. A canned mock `/chat/completions` response gives
   deterministic mapping tests.
3. **Error paths:** bad key → Unauthorized; unreachable baseUrl → Network; mock malformed JSON → MalformedResponse;
   mock refusal/empty items → Refused/empty state (input preserved on retry).
4. **Encryption:** confirm `ai:api_key` is stored as an encrypted byte array (not plaintext) and round-trips via
   `MasterCrypto`; `hasKey()` reacts to store/clear.
