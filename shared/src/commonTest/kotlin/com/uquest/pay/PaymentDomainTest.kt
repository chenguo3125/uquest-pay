package com.uquest.pay

import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import com.uquest.pay.ledger.LedgerState
import com.uquest.pay.model.AccountId
import com.uquest.pay.model.IdempotencyKey
import com.uquest.pay.model.TransferIntent
import com.uquest.pay.model.Wallet
import com.uquest.pay.model.WalletId
import com.uquest.pay.transaction.TransactionStatus
import com.uquest.pay.validation.FailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PaymentDomainTest {
    private val alice = WalletId("alice-wallet")
    private val bob = WalletId("bob-wallet")
    private val carol = WalletId("carol-wallet")
    private val domain = PaymentDomain(clock = Clock { 1_700_000_000_000L }, ids = SequentialIds())

    private fun state(alicePosted: Long = 500): LedgerState = LedgerState().withWallets(
        Wallet.funded(alice, AccountId("alice"), Money.ofMinor(alicePosted, Currency.UQC)),
        Wallet.funded(bob, AccountId("bob"), Money.ofMinor(20, Currency.UQC)),
        Wallet.funded(carol, AccountId("carol"), Money.ofMinor(0, Currency.UQC)),
    )

    private fun intent(
        amount: Long = 100,
        key: String = "key-1",
        to: WalletId = bob,
    ) = TransferIntent(
        fromWalletId = alice,
        toWalletId = to,
        amount = Money.ofMinor(amount, Currency.UQC),
        idempotencyKey = IdempotencyKey(key),
    )

    @Test
    fun submitCreatesValidatedTransactionWithoutTouchingBalances() {
        val result = domain.submitTransfer(state(), intent())
        assertIs<DomainResult.Ok>(result)
        assertEquals(TransactionStatus.Validated, result.transaction.status)
        assertEquals(Money.ofMinor(500, Currency.UQC), result.state.wallet(alice)!!.postedBalance)
        assertEquals(Money.zero(Currency.UQC), result.state.wallet(alice)!!.reservedBalance)
    }

    @Test
    fun sameAccountIsRejectedBeforeTransactionExists() {
        val sameOwner = LedgerState().withWallets(
            Wallet.funded(alice, AccountId("alice"), Money.ofMinor(500, Currency.UQC)),
            Wallet.funded(WalletId("alice-2"), AccountId("alice"), Money.ofMinor(10, Currency.UQC)),
        )
        val result = domain.submitTransfer(
            sameOwner,
            TransferIntent(
                fromWalletId = alice,
                toWalletId = WalletId("alice-2"),
                amount = Money.ofMinor(10, Currency.UQC),
                idempotencyKey = IdempotencyKey("k"),
            ),
        )
        assertIs<DomainResult.Err>(result)
        assertEquals(FailureReason.SameAccount, result.reason)
        assertTrue(result.state.transactions.isEmpty())
    }

    @Test
    fun nonPositiveAmountRejectedBeforeCreate() {
        val result = domain.submitTransfer(state(), intent(amount = 0))
        assertIs<DomainResult.Err>(result)
        assertEquals(FailureReason.InvalidAmount, result.reason)
        assertTrue(result.state.transactions.isEmpty())
    }

    @Test
    fun startProcessingReservesThenCompletePosts() {
        val submitted = domain.submitTransfer(state(), intent()) as DomainResult.Ok
        val processing = domain.startProcessing(submitted.state, submitted.transaction.id) as DomainResult.Ok
        assertEquals(TransactionStatus.Processing, processing.transaction.status)
        assertEquals(Money.ofMinor(100, Currency.UQC), processing.state.wallet(alice)!!.reservedBalance)
        assertEquals(Money.ofMinor(500, Currency.UQC), processing.state.wallet(alice)!!.postedBalance)

        val completed = domain.complete(processing.state, processing.transaction.id) as DomainResult.Ok
        assertEquals(TransactionStatus.Completed, completed.transaction.status)
        assertEquals(Money.ofMinor(400, Currency.UQC), completed.state.wallet(alice)!!.postedBalance)
        assertEquals(Money.zero(Currency.UQC), completed.state.wallet(alice)!!.reservedBalance)
        assertEquals(Money.ofMinor(120, Currency.UQC), completed.state.wallet(bob)!!.postedBalance)
    }

    @Test
    fun insufficientFundsFailsWithoutReserve() {
        val submitted = domain.submitTransfer(state(alicePosted = 50), intent(amount = 100)) as DomainResult.Ok
        val failed = domain.startProcessing(submitted.state, submitted.transaction.id) as DomainResult.Ok
        assertEquals(TransactionStatus.Failed, failed.transaction.status)
        assertIs<FailureReason.InsufficientFunds>(failed.transaction.failureReason)
        assertEquals(Money.zero(Currency.UQC), failed.state.wallet(alice)!!.reservedBalance)
        assertEquals(Money.ofMinor(50, Currency.UQC), failed.state.wallet(alice)!!.postedBalance)
    }

    @Test
    fun failFromProcessingReleasesReserveThenRetrySucceeds() {
        val submitted = domain.submitTransfer(state(), intent()) as DomainResult.Ok
        val processing = domain.startProcessing(submitted.state, submitted.transaction.id) as DomainResult.Ok
        val failed = domain.fail(
            processing.state,
            processing.transaction.id,
            FailureReason.InvariantViolation,
        ) as DomainResult.Ok
        assertEquals(TransactionStatus.Failed, failed.transaction.status)
        assertEquals(Money.ofMinor(500, Currency.UQC), failed.state.wallet(alice)!!.postedBalance)
        assertEquals(Money.zero(Currency.UQC), failed.state.wallet(alice)!!.reservedBalance)

        val retried = domain.retry(failed.state, failed.transaction.id) as DomainResult.Ok
        assertEquals(TransactionStatus.Validated, retried.transaction.status)
        assertEquals(1, retried.transaction.attemptCount)

        val processingAgain = domain.startProcessing(retried.state, retried.transaction.id) as DomainResult.Ok
        val completed = domain.complete(processingAgain.state, processingAgain.transaction.id) as DomainResult.Ok
        assertEquals(TransactionStatus.Completed, completed.transaction.status)
        assertEquals(Money.ofMinor(400, Currency.UQC), completed.state.wallet(alice)!!.postedBalance)
        assertEquals(Money.ofMinor(120, Currency.UQC), completed.state.wallet(bob)!!.postedBalance)
    }

    @Test
    fun idempotencyKeyDoesNotDoubleReserve() {
        val first = domain.submitTransfer(state(), intent()) as DomainResult.Ok
        val processing = domain.startProcessing(first.state, first.transaction.id) as DomainResult.Ok
        val replay = domain.submitTransfer(processing.state, intent()) as DomainResult.Ok
        assertEquals(first.transaction.id, replay.transaction.id)
        assertEquals(TransactionStatus.Processing, replay.transaction.status)
        assertEquals(Money.ofMinor(100, Currency.UQC), replay.state.wallet(alice)!!.reservedBalance)

        val completed = domain.complete(processing.state, processing.transaction.id) as DomainResult.Ok
        val replayCompleted = domain.submitTransfer(completed.state, intent()) as DomainResult.Ok
        assertEquals(completed.transaction.id, replayCompleted.transaction.id)
        assertEquals(Money.ofMinor(400, Currency.UQC), replayCompleted.state.wallet(alice)!!.postedBalance)
    }

    @Test
    fun conflictingIdempotencyPayloadIsRejected() {
        val first = domain.submitTransfer(state(), intent()) as DomainResult.Ok
        val conflict = domain.submitTransfer(first.state, intent(amount = 50))
        assertIs<DomainResult.Err>(conflict)
        assertIs<FailureReason.IdempotencyConflict>(conflict.reason)
    }

    @Test
    fun secondTransferFailsWhileFirstReserveIsHeld() {
        val first = domain.submitTransfer(state(alicePosted = 100), intent(amount = 100, key = "a")) as DomainResult.Ok
        val processing = domain.startProcessing(first.state, first.transaction.id) as DomainResult.Ok
        val secondSubmit = domain.submitTransfer(processing.state, intent(amount = 100, key = "b", to = carol)) as DomainResult.Ok
        val secondStart = domain.startProcessing(secondSubmit.state, secondSubmit.transaction.id) as DomainResult.Ok
        assertEquals(TransactionStatus.Failed, secondStart.transaction.status)
        assertIs<FailureReason.InsufficientFunds>(secondStart.transaction.failureReason)
        assertEquals(Money.ofMinor(100, Currency.UQC), secondStart.state.wallet(alice)!!.reservedBalance)
        assertEquals(Money.ofMinor(100, Currency.UQC), secondStart.state.wallet(alice)!!.postedBalance)
    }

    @Test
    fun illegalCompleteFromValidated() {
        val submitted = domain.submitTransfer(state(), intent()) as DomainResult.Ok
        val result = domain.complete(submitted.state, submitted.transaction.id)
        assertIs<DomainResult.Err>(result)
        assertIs<FailureReason.IllegalTransition>(result.reason)
    }

    @Test
    fun cancelFromValidated() {
        val submitted = domain.submitTransfer(state(), intent()) as DomainResult.Ok
        val cancelled = domain.cancel(submitted.state, submitted.transaction.id) as DomainResult.Ok
        assertEquals(TransactionStatus.Cancelled, cancelled.transaction.status)
    }

    @Test
    fun cannotCancelWhileProcessing() {
        val submitted = domain.submitTransfer(state(), intent()) as DomainResult.Ok
        val processing = domain.startProcessing(submitted.state, submitted.transaction.id) as DomainResult.Ok
        val result = domain.cancel(processing.state, processing.transaction.id)
        assertIs<DomainResult.Err>(result)
        assertIs<FailureReason.IllegalTransition>(result.reason)
        assertEquals(Money.ofMinor(100, Currency.UQC), processing.state.wallet(alice)!!.reservedBalance)
    }
}

private class SequentialIds : IdGenerator {
    private var n = 0
    override fun next(): String = "id-${++n}"
}
