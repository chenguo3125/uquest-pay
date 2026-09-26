package com.uquest.pay.validation

import com.uquest.pay.currency.Money
import com.uquest.pay.transaction.TransactionEvent
import com.uquest.pay.transaction.TransactionStatus

sealed class FailureReason {
    data class InsufficientFunds(
        val available: Money,
        val required: Money,
    ) : FailureReason()

    data object InvalidAmount : FailureReason()

    data object SameAccount : FailureReason()

    data object UnknownCurrency : FailureReason()

    data class IllegalTransition(
        val from: TransactionStatus,
        val event: TransactionEvent,
    ) : FailureReason()

    data object InvariantViolation : FailureReason()

    data object WalletNotFound : FailureReason()

    data class IdempotencyConflict(
        val existingTransactionId: String,
    ) : FailureReason()
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: FailureReason) : ValidationResult()
}

class IllegalTransitionException(
    val from: TransactionStatus,
    val event: TransactionEvent,
) : IllegalStateException("Illegal transition: $from + $event")
