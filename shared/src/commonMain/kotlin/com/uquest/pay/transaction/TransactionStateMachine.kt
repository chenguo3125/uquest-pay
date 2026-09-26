package com.uquest.pay.transaction

import com.uquest.pay.model.Transaction
import com.uquest.pay.validation.FailureReason
import com.uquest.pay.validation.IllegalTransitionException

object TransactionStateMachine {
    fun canTransition(transaction: Transaction, event: TransactionEvent): Boolean =
        TransactionTransition.isAllowed(transaction.status, event)

    fun transition(
        transaction: Transaction,
        event: TransactionEvent,
        nowMillis: Long,
        failureReason: FailureReason? = null,
    ): Transaction {
        if (!canTransition(transaction, event)) {
            throw IllegalTransitionException(transaction.status, event)
        }
        val nextStatus = TransactionTransition.targetOf(transaction.status, event)
        val nextAttempts = when (event) {
            TransactionEvent.Retry -> transaction.attemptCount + 1
            else -> transaction.attemptCount
        }
        val nextFailure = when (event) {
            TransactionEvent.Reject, TransactionEvent.Fail -> failureReason
            TransactionEvent.Validate, TransactionEvent.Retry, TransactionEvent.StartProcessing,
            TransactionEvent.Complete, TransactionEvent.Cancel,
            -> null
        }
        return transaction.copy(
            status = nextStatus,
            attemptCount = nextAttempts,
            failureReason = nextFailure,
            updatedAtMillis = nowMillis,
        )
    }
}
