package com.maksimowiczm.foodyou.food.ai.infrastructure

/** Failure modes of a chat-completion call, mapped to ParseMealError by the parser. */
internal sealed class AiRemoteException(message: String?) : Exception(message) {
    class Unauthorized : AiRemoteException("AI endpoint rejected the API key")

    class RateLimited : AiRemoteException("AI endpoint rate limit exceeded")

    class Network(message: String?) : AiRemoteException(message)

    class Refused : AiRemoteException("AI refused or returned no items")

    class Malformed(message: String?) : AiRemoteException(message)

    class Unknown(message: String?) : AiRemoteException(message)
}
