package com.uquest.pay.transaction

import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import com.uquest.pay.model.AccountId
import com.uquest.pay.model.IdempotencyKey
import com.uquest.pay.model.Transaction
import com.uquest.pay.model.TransactionId
import com.uquest.pay.model.WalletId
import com.uquest.pay.validation.FailureReason
import com.uquest.pay.validation.IllegalTransitionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionStateMachineTest {
    private fun tx(status: TransactionStatus, attempts: Int = 0) = Transaction(
        id = TransactionId("tx-1"),
        fromWalletId = WalletId("from"),
        toWalletId = WalletId("to"),
        fromAccountId = AccountId("a"),
        toAccountId = AccountId("b"),
        amount = Money.ofMinor(100, Currency.UQC),
        status = status,
        idempotencyKey = IdempotencyKey("k1"),
        attemptCount = attempts,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    @Test
    fun legalHappyPath() {
        var current = tx(TransactionStatus.Created)
        current = TransactionStateMachine.transition(current, TransactionEvent.Validate, 2)
        assertEquals(TransactionStatus.Validated, current.status)
        current = TransactionStateMachine.transition(current, TransactionEvent.StartProcessing, 3)
        assertEquals(TransactionStatus.Processing, current.status)
        current = TransactionStateMachine.transition(current, TransactionEvent.Complete, 4)
        assertEquals(TransactionStatus.Completed, current.status)
    }

    @Test
    fun rejectAndRetry() {
        var current = tx(TransactionStatus.Validated)
        current = TransactionStateMachine.transition(
            current,
            TransactionEvent.Reject,
            2,
            FailureReason.InvalidAmount,
        )
        assertEquals(TransactionStatus.Failed, current.status)
        assertEquals(FailureReason.InvalidAmount, current.failureReason)
        current = TransactionStateMachine.transition(current, TransactionEvent.Retry, 3)
        assertEquals(TransactionStatus.Validated, current.status)
        assertEquals(1, current.attemptCount)
        assertNull(current.failureReason)
    }

    @Test
    fun cancelFromCreatedValidatedAndFailed() {
        val created = TransactionStateMachine.transition(
            tx(TransactionStatus.Created),
            TransactionEvent.Cancel,
            2,
        )
        assertEquals(TransactionStatus.Cancelled, created.status)
        val validated = TransactionStateMachine.transition(
            tx(TransactionStatus.Validated),
            TransactionEvent.Cancel,
            2,
        )
        assertEquals(TransactionStatus.Cancelled, validated.status)
        val failed = TransactionStateMachine.transition(
            tx(TransactionStatus.Failed),
            TransactionEvent.Cancel,
            2,
        )
        assertEquals(TransactionStatus.Cancelled, failed.status)
    }

    @Test
    fun processingMayFail() {
        val failed = TransactionStateMachine.transition(
            tx(TransactionStatus.Processing),
            TransactionEvent.Fail,
            2,
            FailureReason.InvariantViolation,
        )
        assertEquals(TransactionStatus.Failed, failed.status)
    }

    @Test
    fun illegalTransitionsThrow() {
        val completed = tx(TransactionStatus.Completed)
        val error = assertFailsWith<IllegalTransitionException> {
            TransactionStateMachine.transition(completed, TransactionEvent.Fail, 2)
        }
        assertEquals(TransactionStatus.Completed, error.from)
        assertEquals(TransactionEvent.Fail, error.event)

        assertFailsWith<IllegalTransitionException> {
            TransactionStateMachine.transition(
                tx(TransactionStatus.Processing),
                TransactionEvent.Cancel,
                2,
            )
        }
        assertFailsWith<IllegalTransitionException> {
            TransactionStateMachine.transition(
                tx(TransactionStatus.Created),
                TransactionEvent.Complete,
                2,
            )
        }
        assertFailsWith<IllegalTransitionException> {
            TransactionStateMachine.transition(
                tx(TransactionStatus.Cancelled),
                TransactionEvent.Retry,
                2,
            )
        }
    }

    @Test
    fun allowedTableCoversPlanEvents() {
        assertTrue(TransactionTransition.isAllowed(TransactionStatus.Created, TransactionEvent.Validate))
        assertTrue(TransactionTransition.isAllowed(TransactionStatus.Validated, TransactionEvent.StartProcessing))
        assertTrue(TransactionTransition.isAllowed(TransactionStatus.Processing, TransactionEvent.Complete))
    }
}
