# AI Food Logging — Session Notes

Date: 2026-07-16 → 2026-07-17

> A faithful recap of the working session that produced the natural-language AI
> food logging feature (issue #419). Not a verbatim transcript — a structured
> summary of the conversation, decisions, and work done.

## 1. Kickoff

- Request: implement [issue #419](https://github.com/maksimowiczm/FoodYou/issues/419)
  (natural-language AI food logging), and be grilled on design/architecture decisions.
- Fetched the issue: text box → LLM → JSON macros → auto-log into diary; users bring
  their own API key (OpenAI / Gemini / Anthropic / local) to keep the app zero-server-cost.

## 2. Exploration

Explored the codebase (parallel Explore agents + direct reads) and established:

- **Domain is richer than the issue assumes.** `NutritionFacts` has 42 fields and is
  **per-100g**; a diary entry needs a `DiaryFood` + `Measurement` + mealId + date, not
  just "calories". So the LLM must estimate **both** composition (per-100g) **and** the
  portion.
- Existing patterns to reuse: `RemoteProduct`/`RemoteProductRequest` (remote food source
  abstraction), USDA/OpenFoodFacts HTTP + user-supplied API keys via Koin, `MasterCrypto`
  (encrypted secrets, used by OpenFoodFacts creds but **not** USDA — USDA key is plaintext),
  `CreateProductUseCase`, `CreateFoodDiaryEntryUseCase`, the create FAB on the
  meal/date-scoped `DiaryFoodSearchScreen`.
- Read the decision log: single `app` module (0002), private development / no upstream code
  contributions (0004). → This work targets the user's fork, not upstream.
- Verified `FoodSource.Type` is a **closed enum** persisted as a **stable Int** (no CHECK
  constraint) → adding `Ai` is migration-free.

## 3. The grilling → locked decisions

Pushed back on the issue's naive framing and drove out 7 decisions with the user:

1. **Entry point:** third "AI" item in the create FAB; opens a dedicated screen. *(user's
   framing: like the existing New Product / New Recipe buttons.)*
2. **Review, not auto-log:** editable review list; user confirms before anything is written.
3. **Provider:** single OpenAI-compatible client + configurable baseUrl/model (covers OpenAI,
   local runners, OpenRouter). *User initially leaned multi-provider; I argued it was ~3x the
   surface before validating UX; user agreed to the single-wire approach.*
4. **Output mapping:** per-100g nutrition + `estimatedGrams`; log as `Serving(1)` with
   `servingWeight = estimatedGrams` (preserves the per-100g invariant).
5. **Nutrient scope:** energy, protein, carbs, fat, fiber, sugars, saturated fat, sodium.
6. **Persistence:** each item → reusable `Product` (`FoodSource.Type.Ai`) **and** diary entry.
7. **API key:** encrypted via `MasterCrypto` (not plaintext like USDA).

Plan written and approved via plan mode.

## 4. Implementation

Built bottom-up on branch `feature/ai-food-logging`:

1. **Enum ripple** — `FoodSource.Type.Ai` + Room twin/converter (`AI = 4`) + UI icon/label;
   filtered `Ai` out of the manual product source picker.
2. **Domain** — `food/ai/domain/` (settings, key repo interface, `MealItem`, parser interface,
   errors, `ParseMealDescriptionUseCase`, `LogMealItemsUseCase`).
3. **Infrastructure** — `food/ai/infrastructure/` (DTOs, JSON schema + system prompt, OpenAI
   data source, mapper, parser impl, encrypted key repo, settings DataStore repo, Koin module).
4. **UI** — `app/ui/food/ai/` (screen + ViewModel + UI state) and `UpdateLlmSettingsDialog`.
5. **Nav + strings** — `FoodDiaryAiLog` + `LlmSettings` routes; FAB wiring; ~24 string resources.

Verification: `:app:assembleDebug` passed; wrote `AiMealMapperTest` (per-100g mapping +
validation), which passed.

## 5. Live testing + the bug

- User supplied a real OpenAI key. Confirmed base URL is `https://api.openai.com/v1` (the
  shipped default); `/v1` matters because the code appends `/chat/completions`.
- First live call failed: **`400 Missing required parameter: 'response_format.type'`**.
- **Root cause:** kotlinx.serialization omits fields equal to their default value, so
  `response_format.type = "json_schema"` and `strict = true` were stripped from the request.
- **Fix:** the AI Ktor JSON client now uses `encodeDefaults = true`. Recompiled; user
  confirmed it's "mainly working".

## 6. Walkthrough + docs

- Walked through the runtime flow step by step (FAB click → navigation → ViewModel/state →
  Analyze/parse → parser loads key+settings → HTTP call → response handling / two-layer JSON →
  DTO→domain mapping + error translation). Paused mid-walkthrough at the user's request.
- Produced file-inventory tables and appended them to
  `docs/development/features-log/natural-language-ai-food-logging.md`.

## 7. Commit + PR

- Commit `133209c4` — `feat: add natural language AI food logging (#419)`.
- Pushed to `origin` (`Guernik/FoodYou`, the user's fork).
- Opened PR **#2** → `Guernik/FoodYou:main`. (Targets the fork, not upstream, per the
  private-dev constraint. `#419` reference is informational — it won't auto-close an upstream
  issue.)

## Outstanding

- UX adjustments still to come (noted by the user).
- Walkthrough Step 8 (review → confirm → persistence detail) not yet delivered.
- Not run on a device beyond the user's manual OpenAI test.
