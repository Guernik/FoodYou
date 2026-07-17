package com.maksimowiczm.foodyou.food.ai.infrastructure

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ChatCompletionRequest
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.ChatCompletionResponse
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
