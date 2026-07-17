package com.maksimowiczm.foodyou.food.ai.infrastructure.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat,
    val temperature: Double = 0.2,
)

@Serializable internal data class ChatMessage(val role: String, val content: String)

@Serializable
internal data class ResponseFormat(
    val type: String = "json_schema",
    @SerialName("json_schema") val jsonSchema: JsonSchemaWrapper,
)

@Serializable
internal data class JsonSchemaWrapper(
    val name: String,
    val strict: Boolean = true,
    val schema: JsonObject,
)

@Serializable internal data class ChatCompletionResponse(val choices: List<Choice> = emptyList())

@Serializable
internal data class Choice(
    val message: ResponseMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class ResponseMessage(val content: String? = null, val refusal: String? = null)

@Serializable internal data class ErrorResponse(val error: ErrorBody? = null)

@Serializable
internal data class ErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
