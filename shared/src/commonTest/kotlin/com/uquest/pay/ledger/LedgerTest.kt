package com.uquest.pay.ledger

import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import com.uquest.pay.model.AccountId
import com.uquest.pay.model.LedgerEntry
import com.uquest.pay.model.LedgerEntryId
import com.uquest.pay.model.LedgerEntryKind
import com.uquest.pay.model.TransactionId
import com.uquest.pay.model.Wallet
import com.uquest.pay.model.WalletId
import com.uquest.pay.validation.FailureReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LedgerTest {
    private val senderId = WalletId("sender")
    private val recipientId = WalletId("recipient")
    private val txId = TransactionId("tx")
    private val amount = Money.ofMinor(100, Currency.UQC)

    private fun funded(): LedgerState = LedgerState().withWallets(
        Wallet.funded(senderId, AccountId("a"), Money.ofMinor(500, Currency.UQC)),
        Wallet.funded(recipientId, AccountId("b"), Money.ofMinor(10, Currency.UQC)),
    )

    private fun entry(
        kind: LedgerEntryKind,
        walletId: WalletId,
        id: String = kind.name,
    ) = LedgerEntry(
        id = LedgerEntryId(id),
        transactionId = txId,
        walletId = walletId,
        kind = kind,
        amount = amount,
        createdAtMillis = 1,
    )

    @Test
    fun reserveCompletePostsBothSides() {
        val reserved = Ledger.apply(funded(), entry(LedgerEntryKind.RESERVE, senderId))
        assertIs<LedgerApplyResult.Applied>(reserved)
        assertEquals(Money.ofMinor(100, Currency.UQC), reserved.state.wallet(senderId)!!.reservedBalance)
        assertEquals(Money.ofMinor(500, Currency.UQC), reserved.state.wallet(senderId)!!.postedBalance)

        val posted = Ledger.applyAll(
            reserved.state,
            listOf(
                entry(LedgerEntryKind.POST_DEBIT, senderId, "debit"),
                entry(LedgerEntryKind.POST_CREDIT, recipientId, "credit"),
            ),
        )
        assertIs<LedgerApplyResult.Applied>(posted)
        val sender = posted.state.wallet(senderId)!!
        val recipient = posted.state.wallet(recipientId)!!
        assertEquals(Money.ofMinor(400, Currency.UQC), sender.postedBalance)
        assertEquals(Money.zero(Currency.UQC), sender.reservedBalance)
        assertEquals(Money.ofMinor(110, Currency.UQC), recipient.postedBalance)
    }

    @Test
    fun failReleasesReserveWithoutChangingPosted() {
        val reserved = Ledger.apply(funded(), entry(LedgerEntryKind.RESERVE, senderId)) as LedgerApplyResult.Applied
        val released = Ledger.apply(reserved.state, entry(LedgerEntryKind.RELEASE, senderId, "rel"))
        assertIs<LedgerApplyResult.Applied>(released)
        val sender = released.state.wallet(senderId)!!
        assertEquals(Money.ofMinor(500, Currency.UQC), sender.postedBalance)
        assertEquals(Money.zero(Currency.UQC), sender.reservedBalance)
    }

    @Test
    fun cannotReserveMoreThanAvailable() {
        val result = Ledger.apply(
            funded(),
            entry(LedgerEntryKind.RESERVE, senderId).copy(amount = Money.ofMinor(501, Currency.UQC)),
        )
        assertIs<LedgerApplyResult.Rejected>(result)
        assertEquals(FailureReason.InvariantViolation, result.reason)
    }

    @Test
    fun cannotDoubleReserveSameTransaction() {
        val first = Ledger.apply(funded(), entry(LedgerEntryKind.RESERVE, senderId)) as LedgerApplyResult.Applied
        val second = Ledger.apply(first.state, entry(LedgerEntryKind.RESERVE, senderId, "r2"))
        assertIs<LedgerApplyResult.Rejected>(second)
    }

    @Test
    fun postDebitRequiresOpenReserve() {
        val result = Ledger.apply(funded(), entry(LedgerEntryKind.POST_DEBIT, senderId))
        assertIs<LedgerApplyResult.Rejected>(result)
        assertTrue(result.reason is FailureReason.InvariantViolation)
    }
}
