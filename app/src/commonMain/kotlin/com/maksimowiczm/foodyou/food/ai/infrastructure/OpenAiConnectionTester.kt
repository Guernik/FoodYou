package com.maksimowiczm.foodyou.food.ai.infrastructure

import com.maksimowiczm.foodyou.food.ai.domain.LlmConnectionTester
import com.maksimowiczm.foodyou.food.ai.domain.TestConnectionResult

internal class OpenAiConnectionTester(private val dataSource: OpenAiRemoteDataSource) :
    LlmConnectionTester {
    override suspend fun test(
        baseUrl: String,
        model: String,
        apiKey: String,
    ): TestConnectionResult =
        dataSource
            .testConnection(baseUrl = baseUrl, model = model, apiKey = apiKey)
            .fold(
                onSuccess = { TestConnectionResult.Success },
                onFailure = { throwable ->
                    when (throwable) {
                        is AiRemoteException.Unauthorized -> TestConnectionResult.Unauthorized
                        is AiRemoteException.RateLimited -> TestConnectionResult.RateLimited
                        is AiRemoteException.Network -> TestConnectionResult.Network
                        else -> TestConnectionResult.Unknown(throwable.message)
                    }
                },
            )
}
