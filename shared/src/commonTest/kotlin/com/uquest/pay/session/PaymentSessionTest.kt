package com.uquest.pay.session

import com.uquest.pay.Clock
import com.uquest.pay.IdGenerator
import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import com.uquest.pay.ledger.LedgerState
import com.uquest.pay.model.AccountId
import com.uquest.pay.model.Wallet
import com.uquest.pay.model.WalletId
import com.uquest.pay.validation.FailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentSessionTest {
    private val clock = Clock { 1_800_000_000_000L }

    @Test
    fun seedMatchesCampusDashboard() {
        val session = CampusDemo.session(clock, SequentialIds())
        val snap = session.snapshot()
        assertEquals("Alex Chen", snap.currentUserName)
        assertEquals("wallet-alex", snap.currentWalletId)
        assertEquals(12_450L, snap.availableBalanceMinorUnits)
        assertEquals(3, snap.peers.size)
        assertEquals("peer-jordan", snap.peers[0].walletId)
        assertEquals(6, snap.transactions.size)

        val latest = snap.transactions[0]
        assertEquals("Jordan Lee", latest.counterparty)
        assertEquals(-1_250L, latest.amountMinorUnits)
        assertEquals("Completed", latest.status)

        val processing = snap.transactions[1]
        assertEquals("Campus Cafe", processing.counterparty)
        assertEquals(-475L, processing.amountMinorUnits)
        assertEquals("Processing", processing.status)
    }

    @Test
    fun successfulPaymentUpdatesBalanceAndHistory() {
        val session = CampusDemo.session(clock, SequentialIds())
        val result = session.send("peer-jordan", "10.00", "lunch", "pay-1")
        val completed = assertIs<SendResult.Completed>(result)

        val snap = session.snapshot()
        assertEquals(11_450L, snap.availableBalanceMinorUnits)
        assertEquals(7, snap.transactions.size)
        assertEquals(completed.transactionId, snap.transactions[0].id)
        assertEquals("Jordan Lee", snap.transactions[0].counterparty)
        assertEquals(-1_000L, snap.transactions[0].amountMinorUnits)
        assertEquals("Completed", snap.transactions[0].status)
        assertEquals(1_800_000_000_000L, snap.transactions[0].createdAtMillis)
    }

    @Test
    fun invalidAmountDoesNotCreateATransaction() {
        val session = minimalSession()
        val before = session.snapshot()
        val result = session.send("wallet-bob", "1.234", null, "k")
        val failed = assertIs<SendResult.Failed>(result)
        assertNull(failed.transactionId)
        assertEquals(FailureReason.InvalidAmount, failed.reason)

        val after = session.snapshot()
        assertEquals(before.availableBalanceMinorUnits, after.availableBalanceMinorUnits)
        assertEquals(before.transactions.map { it.id }, after.transactions.map { it.id })
    }

    @Test
    fun walletNotFound() {
        val session = minimalSession()
        val result = session.send("missing-wallet", "0.10", null, "k")
        val failed = assertIs<SendResult.Failed>(result)
        assertNull(failed.transactionId)
        assertEquals(FailureReason.WalletNotFound, failed.reason)
        assertTrue(session.snapshot().transactions.isEmpty())
    }

    @Test
    fun sameAccountTransferIsRejected() {
        val alice = WalletId("wallet-alice")
        val other = WalletId("wallet-alice-2")
        val session = PaymentSession(
            clock = clock,
            currentWalletId = alice,
            initialState = LedgerState().withWallets(
                Wallet.funded(alice, AccountId("same"), Money.ofMinor(100, Currency.UQC)),
                Wallet.funded(other, AccountId("same"), Money.ofMinor(10, Currency.UQC)),
            ),
            peers = listOf(SessionPeer(other.value, "Alt")),
            displayNames = mapOf(alice.value to "Alice", other.value to "Alt"),
            ids = SequentialIds(),
        )
        val result = session.send(other.value, "0.10", null, "k")
        val failed = assertIs<SendResult.Failed>(result)
        assertEquals(FailureReason.SameAccount, failed.reason)
        assertTrue(session.snapshot().transactions.isEmpty())
    }

    @Test
    fun insufficientFundsLeavesBalancesUnchanged() {
        val session = minimalSession(alicePosted = 50)
        val postedBefore = session.totalPostedMinorUnits()
        val result = session.send("wallet-bob", "1.00", null, "k")
        val failed = assertIs<SendResult.Failed>(result)
        assertIs<FailureReason.InsufficientFunds>(failed.reason)
        assertEquals(50L, session.snapshot().availableBalanceMinorUnits)
        assertEquals(postedBefore, session.totalPostedMinorUnits())
        assertEquals("Failed", session.snapshot().transactions[0].status)
        assertEquals(-100L, session.snapshot().transactions[0].amountMinorUnits)
    }

    @Test
    fun duplicateIdempotencyKeyReturnsExistingTransaction() {
        val session = minimalSession()
        val first = assertIs<SendResult.Completed>(session.send("wallet-bob", "0.10", "note", "dup"))
        val entriesAfterFirst = session.ledgerEntryCount()
        val second = assertIs<SendResult.Completed>(session.send("wallet-bob", "0.10", "note", "dup"))
        assertEquals(first.transactionId, second.transactionId)
        assertEquals(entriesAfterFirst, session.ledgerEntryCount())
        assertEquals(90L, session.snapshot().availableBalanceMinorUnits)
    }

    @Test
    fun duplicateCompletedRequestDoesNotStartProcessingAgain() {
        val session = minimalSession()
        session.send("wallet-bob", "0.10", null, "done")
        val entries = session.ledgerEntryCount()
        val replay = session.send("wallet-bob", "0.10", null, "done")
        assertIs<SendResult.Completed>(replay)
        assertEquals(entries, session.ledgerEntryCount())
        assertEquals(1, session.snapshot().transactions.size)
    }

    @Test
    fun duplicateProcessingRequestReportsProcessing() {
        val session = overflowRecipientSession()
        val first = assertIs<SendResult.Processing>(session.send("wallet-bob", "0.01", null, "hold"))
        val entries = session.ledgerEntryCount()
        val available = session.snapshot().availableBalanceMinorUnits
        val replay = assertIs<SendResult.Processing>(session.send("wallet-bob", "0.01", null, "hold"))
        assertEquals(first.transactionId, replay.transactionId)
        assertEquals(entries, session.ledgerEntryCount())
        assertEquals(available, session.snapshot().availableBalanceMinorUnits)
        assertEquals("Processing", session.snapshot().transactions[0].status)
    }

    @Test
    fun retryAfterFailedCanComplete() {
        val session = CampusDemo.session(clock, SequentialIds())
        val failedRow = session.snapshot().transactions.first { it.status == "Failed" }
        val postedBefore = session.totalPostedMinorUnits()
        val result = session.retry(failedRow.id)
        assertIs<SendResult.Completed>(result)
        assertEquals("Completed", session.snapshot().transactions.first { it.id == failedRow.id }.status)
        assertEquals(postedBefore, session.totalPostedMinorUnits())
    }

    @Test
    fun completeFailureLeavesTransactionProcessing() {
        val session = overflowRecipientSession()
        val postedBefore = session.totalPostedMinorUnits()
        val result = session.send("wallet-bob", "0.01", null, "overflow")
        val processing = assertIs<SendResult.Processing>(result)
        val snap = session.snapshot()
        assertEquals(processing.transactionId, snap.transactions[0].id)
        assertEquals("Processing", snap.transactions[0].status)
        assertEquals(99L, snap.availableBalanceMinorUnits)
        assertEquals(postedBefore, session.totalPostedMinorUnits())
    }

    @Test
    fun postedBalancesAreConservedAcrossCompletedTransfer() {
        val session = minimalSession(alicePosted = 100, bobPosted = 20)
        val before = session.totalPostedMinorUnits()
        assertIs<SendResult.Completed>(session.send("wallet-bob", "0.60", null, "move"))
        assertEquals(before, session.totalPostedMinorUnits())
        assertEquals(40L, session.snapshot().availableBalanceMinorUnits)
    }

    @Test
    fun replayDoesNotDuplicateLedgerEntries() {
        val session = minimalSession()
        assertIs<SendResult.Completed>(session.send("wallet-bob", "0.10", "x", "once"))
        val entries = session.ledgerEntryCount()
        assertTrue(entries > 0)
        assertIs<SendResult.Completed>(session.send("wallet-bob", "0.10", "x", "once"))
        assertEquals(entries, session.ledgerEntryCount())
    }

    private fun minimalSession(
        alicePosted: Long = 100,
        bobPosted: Long = 20,
        ids: IdGenerator = SequentialIds(),
    ): PaymentSession {
        val alice = WalletId("wallet-alice")
        val bob = WalletId("wallet-bob")
        return PaymentSession(
            clock = clock,
            currentWalletId = alice,
            initialState = LedgerState().withWallets(
                Wallet.funded(alice, AccountId("alice"), Money.ofMinor(alicePosted, Currency.UQC)),
                Wallet.funded(bob, AccountId("bob"), Money.ofMinor(bobPosted, Currency.UQC)),
            ),
            peers = listOf(SessionPeer(bob.value, "Bob")),
            displayNames = mapOf(alice.value to "Alice", bob.value to "Bob"),
            ids = ids,
        )
    }

    private fun overflowRecipientSession(): PaymentSession {
        val alice = WalletId("wallet-alice")
        val bob = WalletId("wallet-bob")
        return PaymentSession(
            clock = clock,
            currentWalletId = alice,
            initialState = LedgerState().withWallets(
                Wallet.funded(alice, AccountId("alice"), Money.ofMinor(100, Currency.UQC)),
                Wallet.funded(bob, AccountId("bob"), Money.ofMinor(Long.MAX_VALUE, Currency.UQC)),
            ),
            peers = listOf(SessionPeer(bob.value, "Bob")),
            displayNames = mapOf(alice.value to "Alice", bob.value to "Bob"),
            ids = SequentialIds(),
        )
    }
}

internal class SequentialIds : IdGenerator {
    private var n = 0
    override fun next(): String = "id-${++n}"
}
