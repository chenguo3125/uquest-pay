package com.uquest.pay.transaction

import com.uquest.pay.validation.FailureReason

enum class TransactionStatus {
    Created,
    Validated,
    Processing,
    Completed,
    Failed,
    Cancelled,
}

sealed class TransactionEvent {
    data object Validate : TransactionEvent()
    data object Reject : TransactionEvent()
    data object StartProcessing : TransactionEvent()
    data object Complete : TransactionEvent()
    data object Fail : TransactionEvent()
    data object Retry : TransactionEvent()
    data object Cancel : TransactionEvent()
}

data class TransitionSpec(
    val from: TransactionStatus,
    val event: TransactionEvent,
    val to: TransactionStatus,
)

object TransactionTransition {
    val allowed: Set<Pair<TransactionStatus, TransactionEvent>> = setOf(
        TransactionStatus.Created to TransactionEvent.Validate,
        TransactionStatus.Created to TransactionEvent.Cancel,
        TransactionStatus.Validated to TransactionEvent.StartProcessing,
        TransactionStatus.Validated to TransactionEvent.Reject,
        TransactionStatus.Validated to TransactionEvent.Cancel,
        TransactionStatus.Processing to TransactionEvent.Complete,
        TransactionStatus.Processing to TransactionEvent.Fail,
        TransactionStatus.Failed to TransactionEvent.Retry,
        TransactionStatus.Failed to TransactionEvent.Cancel,
    )

    fun isAllowed(from: TransactionStatus, event: TransactionEvent): Boolean =
        (from to event) in allowed

    fun targetOf(from: TransactionStatus, event: TransactionEvent): TransactionStatus {
        if (!isAllowed(from, event)) {
            throw com.uquest.pay.validation.IllegalTransitionException(from, event)
        }
        return when (event) {
            TransactionEvent.Validate -> TransactionStatus.Validated
            TransactionEvent.Reject -> TransactionStatus.Failed
            TransactionEvent.StartProcessing -> TransactionStatus.Processing
            TransactionEvent.Complete -> TransactionStatus.Completed
            TransactionEvent.Fail -> TransactionStatus.Failed
            TransactionEvent.Retry -> TransactionStatus.Validated
            TransactionEvent.Cancel -> TransactionStatus.Cancelled
        }
    }

    fun failureOf(from: TransactionStatus, event: TransactionEvent): FailureReason.IllegalTransition =
        FailureReason.IllegalTransition(from, event)
}
