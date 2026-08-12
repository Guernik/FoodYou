package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.database.TransactionScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.MealDescriptionParser
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealDescriptionUseCase
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError
import com.maksimowiczm.foodyou.food.ai.domain.SaveMealItemAsProductUseCase
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.usecase.CreateProductUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class QuickAddAiDelegateTest {

    // 250 g portion of a food with 20 g protein / 8 g carbs / 10 g fat / 200 kcal per 100 g.
    private val chicken =
        MealItem(
            name = "Chicken caesar salad",
            isLiquid = false,
            nutritionFactsPer100g =
                NutritionFacts(
                    energy = 200.0.toNutrientValue(),
                    proteins = 20.0.toNutrientValue(),
                    carbohydrates = 8.0.toNutrientValue(),
                    fats = 10.0.toNutrientValue(),
                    sodium = 0.4.toNutrientValue(),
                ),
            estimatedGrams = 250.0,
        )

    @Test
    fun `analysis reports nutrition as totals for the estimated portion`() = runBlocking {
        val delegate = delegate(parseResult = Ok(listOf(chicken)))

        var totals: QuickAddAiTotals? = null
        delegate.analyze(this, "chicken caesar salad") { totals = it }?.join()

        val result = assertNotNull(totals)
        assertEquals("Chicken caesar salad", result.name)
        // 250 g is 2.5x the per-100 g basis.
        assertEquals(500.0, result.energyKcal)
        assertEquals(50.0, result.proteins)
        assertEquals(20.0, result.carbohydrates)
        assertEquals(25.0, result.fats)
    }

    @Test
    fun `saving after an analysis converts the totals back to per 100g`() = runBlocking {
        val products = RecordingProductRepository()
        val delegate = delegate(parseResult = Ok(listOf(chicken)), productRepository = products)

        delegate.analyze(this, "chicken caesar salad") {}?.join()
        val saved =
            delegate.saveAsProduct(
                name = "Chicken caesar salad",
                energyKcal = 500.0,
                proteins = 50.0,
                carbohydrates = 20.0,
                fats = 25.0,
            )

        assertTrue(saved)
        val product = assertNotNull(products.inserted)
        assertEquals(250.0, product.servingWeight)
        assertEquals(FoodSource.Type.Ai, product.source.type)
        assertEquals(200.0, product.nutritionFacts.energy.value)
        assertEquals(20.0, product.nutritionFacts.proteins.value)
        assertEquals(8.0, product.nutritionFacts.carbohydrates.value)
        assertEquals(10.0, product.nutritionFacts.fats.value)
    }

    @Test
    fun `macros edited after an analysis are the ones persisted`() = runBlocking {
        val products = RecordingProductRepository()
        val delegate = delegate(parseResult = Ok(listOf(chicken)), productRepository = products)

        delegate.analyze(this, "chicken caesar salad") {}?.join()
        // The user corrected protein from 50 g down to 40 g for the whole 250 g portion.
        delegate.saveAsProduct(
            name = "Chicken caesar salad",
            energyKcal = 500.0,
            proteins = 40.0,
            carbohydrates = 20.0,
            fats = 25.0,
        )

        val product = assertNotNull(products.inserted)
        assertEquals(16.0, product.nutritionFacts.proteins.value)
        // Nutrients the quick add form cannot edit keep the AI's per 100 g value.
        assertEquals(0.4, product.nutritionFacts.sodium.value)
    }

    @Test
    fun `saving without an analysis treats the entry as a 100g serving`() = runBlocking {
        val products = RecordingProductRepository()
        val delegate = delegate(productRepository = products)

        val saved =
            delegate.saveAsProduct(
                name = "Hand typed snack",
                energyKcal = 250.0,
                proteins = 10.0,
                carbohydrates = 30.0,
                fats = 9.0,
            )

        assertTrue(saved)
        val product = assertNotNull(products.inserted)
        assertEquals(100.0, product.servingWeight)
        // At a 100 g basis the totals are already per 100 g.
        assertEquals(250.0, product.nutritionFacts.energy.value)
        assertEquals(10.0, product.nutritionFacts.proteins.value)
        assertEquals(30.0, product.nutritionFacts.carbohydrates.value)
        assertEquals(9.0, product.nutritionFacts.fats.value)
        // Nothing was analysed, so unknown nutrients stay unknown.
        assertNull(product.nutritionFacts.sodium.value)
    }

    @Test
    fun `a parse failure surfaces as an error state and saves nothing`() = runBlocking {
        val products = RecordingProductRepository()
        val delegate =
            delegate(
                parseResult = Err(ParseMealError.Network),
                productRepository = products,
            )

        var called = false
        delegate.analyze(this, "chicken caesar salad") { called = true }?.join()

        assertTrue(!called)
        val state = delegate.state.value
        assertTrue(state is QuickAddAiState.Error)
        assertEquals(ParseMealError.Network, state.error)
        assertNull(products.inserted)
    }

    @Test
    fun `dismissing an error returns to idle`() = runBlocking {
        val delegate = delegate(parseResult = Err(ParseMealError.Network))

        delegate.analyze(this, "chicken caesar salad") {}?.join()
        delegate.dismissError()

        assertTrue(delegate.state.value is QuickAddAiState.Idle)
    }

    private fun delegate(
        parseResult: Result<List<MealItem>, ParseMealError> = Ok(emptyList()),
        productRepository: RecordingProductRepository = RecordingProductRepository(),
        hasKey: Boolean = true,
    ): QuickAddAiDelegate {
        val parser =
            object : MealDescriptionParser {
                override suspend fun parse(
                    description: String,
                    singleProduct: Boolean,
                ): Result<List<MealItem>, ParseMealError> = parseResult
            }

        return QuickAddAiDelegate(
            parseMealDescriptionUseCase = ParseMealDescriptionUseCase(parser, NoopLogger),
            saveMealItemAsProductUseCase =
                SaveMealItemAsProductUseCase(
                    createProductUseCase =
                        CreateProductUseCase(
                            productRepository = productRepository,
                            historyRepository = NoopFoodHistoryRepository,
                            transactionProvider = DirectTransactionProvider,
                            logger = NoopLogger,
                        ),
                    dateProvider = FixedDateProvider,
                ),
            apiKeyRepository = FakeApiKeyRepository(hasKey),
        )
    }
}

/** Captures the product [CreateProductUseCase] would have written. */
private class RecordingProductRepository : ProductRepository {
    var inserted: InsertedProduct? = null
        private set

    override suspend fun insertProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
    ): FoodId.Product {
        inserted =
            InsertedProduct(
                name = name,
                isLiquid = isLiquid,
                servingWeight = servingWeight,
                source = source,
                nutritionFacts = nutritionFacts,
            )
        return FoodId.Product(1L)
    }

    override suspend fun insertUniqueProduct(
        name: String,
        brand: String?,
        barcode: String?,
        note: String?,
        isLiquid: Boolean,
        packageWeight: Double?,
        servingWeight: Double?,
        source: FoodSource,
        nutritionFacts: NutritionFacts,
    ): FoodId.Product? = throw UnsupportedOperationException()

    override fun observeProduct(id: FoodId.Product): Flow<Product?> = flowOf(null)

    override fun observeProducts(limit: Int, offset: Int): Flow<List<Product>> = flowOf(emptyList())

    override suspend fun updateProduct(product: Product) = Unit

    override suspend fun deleteProduct(product: Product) = Unit
}

private data class InsertedProduct(
    val name: String,
    val isLiquid: Boolean,
    val servingWeight: Double?,
    val source: FoodSource,
    val nutritionFacts: NutritionFacts,
)

private class FakeApiKeyRepository(private val hasKey: Boolean) : LlmApiKeyRepository {
    override fun hasKey(): Flow<Boolean> = flowOf(hasKey)

    override suspend fun loadKey(): String? = if (hasKey) "test-key" else null

    override suspend fun store(key: String) = Unit

    override suspend fun clear() = Unit
}

private object NoopFoodHistoryRepository : FoodHistoryRepository {
    override suspend fun insert(foodId: FoodId, history: FoodHistory) = Unit

    override fun observeFoodHistory(foodId: FoodId): Flow<List<FoodHistory>> = flowOf(emptyList())
}

private object DirectTransactionProvider : TransactionProvider {
    override suspend fun <T> withTransaction(block: suspend TransactionScope<T>.() -> T): T {
        val scope =
            object : TransactionScope<T> {
                override suspend fun rollback(result: T) = throw UnsupportedOperationException()
            }
        return scope.block()
    }
}

private object FixedDateProvider : DateProvider {
    override fun nowInstant(): Instant = Instant.fromEpochSeconds(1_700_000_000)

    override fun observeInstant(interval: Duration): Flow<Instant> = flowOf(nowInstant())

    override fun observeDate(timeZone: TimeZone): Flow<LocalDate> =
        flowOf(LocalDate.fromEpochDays(20_000))
}

private object NoopLogger : Logger {
    override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

    override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit

    override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

    override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
}
