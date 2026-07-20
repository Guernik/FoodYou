package com.maksimowiczm.foodyou.food.ai.infrastructure

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ChatCompletionRequest
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ChatCompletionResponse
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ChatCompletionTestRequest
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ChatMessage
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ErrorResponse
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.JsonSchemaWrapper
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.MealItemsDto
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ResponseFormat
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json

internal class OpenAiRemoteDataSource(private val client: HttpClient, private val logger: Logger) {

    suspend fun chatCompletion(
        baseUrl: String,
        model: String,
        apiKey: String,
        description: String,
    ): Result<MealItemsDto> {
        return try {
            val url = "${baseUrl.trimEnd('/')}/chat/completions"

            val request =
                ChatCompletionRequest(
                    model = model,
                    messages =
                        listOf(
                            ChatMessage(role = "system", content = MealParseSchema.SYSTEM_PROMPT),
                            ChatMessage(role = "user", content = description),
                        ),
                    responseFormat =
                        ResponseFormat(
                            jsonSchema =
                                JsonSchemaWrapper(
                                    name = MealParseSchema.SCHEMA_NAME,
                                    strict = true,
                                    schema = MealParseSchema.schema,
                                )
                        ),
                )

            val response =
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    headers { append(HttpHeaders.Authorization, "Bearer $apiKey") }
                    setBody(request)
                }

            handleResponse(response)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            handleException(e)
        }
    }

    /**
     * Lightweight connectivity/auth check. Sends a minimal chat request (no structured output) so it
     * is cheap and does not depend on the model supporting json_schema. Success = HTTP 200 with a
     * well-formed chat response.
     */
    suspend fun testConnection(baseUrl: String, model: String, apiKey: String): Result<Unit> {
        return try {
            val url = "${baseUrl.trimEnd('/')}/chat/completions"

            val request =
                ChatCompletionTestRequest(
                    model = model,
                    messages = listOf(ChatMessage(role = "user", content = "ping")),
                    maxCompletionTokens = 1,
                )

            val response =
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    headers { append(HttpHeaders.Authorization, "Bearer $apiKey") }
                    setBody(request)
                }

            when (response.status) {
                HttpStatusCode.OK -> {
                    // Ensure the body is a well-formed chat response, not an error page with a 200.
                    response.body<ChatCompletionResponse>()
                    Result.success(Unit)
                }

                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden -> Result.failure(AiRemoteException.Unauthorized())

                HttpStatusCode.TooManyRequests ->
                    Result.failure(AiRemoteException.RateLimited())

                else -> {
                    val message = response.errorMessage()
                    logger.e(TAG) { "AI test failed ${response.status}: $message" }
                    Result.failure(AiRemoteException.Unknown("${response.status}: $message"))
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            when (e) {
                is AiRemoteException -> Result.failure(e)
                else -> {
                    logger.e(TAG, e) { "AI test request failed" }
                    Result.failure(AiRemoteException.Network(e.message))
                }
            }
        }
    }

    private suspend fun handleResponse(response: HttpResponse): Result<MealItemsDto> {
        return when (response.status) {
            HttpStatusCode.OK -> parseBody(response)

            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden -> {
                val message = response.errorMessage()
                logger.e(TAG) { "AI endpoint unauthorized: $message" }
                Result.failure(AiRemoteException.Unauthorized())
            }

            HttpStatusCode.TooManyRequests -> {
                logger.w(TAG) { "AI endpoint rate limited" }
                Result.failure(AiRemoteException.RateLimited())
            }

            else -> {
                val message = response.errorMessage()
                logger.e(TAG) { "AI endpoint error ${response.status}: $message" }
                Result.failure(AiRemoteException.Unknown("${response.status}: $message"))
            }
        }
    }

    private suspend fun parseBody(response: HttpResponse): Result<MealItemsDto> {
        val completion = response.body<ChatCompletionResponse>()
        val message = completion.choices.firstOrNull()?.message

        if (message?.refusal != null) {
            logger.w(TAG) { "AI refused: ${message.refusal}" }
            return Result.failure(AiRemoteException.Refused())
        }

        val content = message?.content
        if (content.isNullOrBlank()) {
            return Result.failure(AiRemoteException.Refused())
        }

        return try {
            Result.success(lenientJson.decodeFromString<MealItemsDto>(content))
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logger.e(TAG, e) { "Failed to parse AI content as MealItemsDto" }
            Result.failure(AiRemoteException.Malformed(e.message))
        }
    }

    private suspend fun HttpResponse.errorMessage(): String? =
        try {
            body<ErrorResponse>().error?.message ?: bodyAsText().take(500)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            try {
                bodyAsText().take(500)
            } catch (_: Exception) {
                null
            }
        }

    private fun handleException(e: Exception): Result<MealItemsDto> =
        when (e) {
            is CancellationException -> throw e
            is AiRemoteException -> Result.failure(e)
            else -> {
                logger.e(TAG, e) { "AI request failed" }
                Result.failure(AiRemoteException.Network(e.message))
            }
        }

    private companion object {
        private const val TAG = "OpenAiRemoteDataSource"
        private val lenientJson = Json { ignoreUnknownKeys = true }
    }
}
