package com.uquest.pay.session

import com.uquest.pay.Clock
import com.uquest.pay.IdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PaymentSessionTest {
    private val clock = Clock { 1_800_000_000_000L }
    private val session = PaymentSession(clock = clock, ids = SequentialIds())

    @Test
    fun seedMatchesCampusDashboard() {
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
    fun sendSucceedsAndUpdatesBalanceAndHistory() {
        val result = session.send("peer-jordan", "10.00", "lunch")
        assertEquals(SendResult.Ok, result)

        val snap = session.snapshot()
        assertEquals(11_450L, snap.availableBalanceMinorUnits)
        assertEquals(7, snap.transactions.size)
        assertEquals("Jordan Lee", snap.transactions[0].counterparty)
        assertEquals(-1_000L, snap.transactions[0].amountMinorUnits)
        assertEquals("Completed", snap.transactions[0].status)
        assertEquals(1_800_000_000_000L, snap.transactions[0].createdAtMillis)
    }

    @Test
    fun invalidAmountDoesNotCreateATransaction() {
        val before = session.snapshot()
        val result = session.send("peer-jordan", "1.234", null)
        assertIs<SendResult.Err>(result)
        assertEquals("Enter a valid amount", result.message)

        val after = session.snapshot()
        assertEquals(before.availableBalanceMinorUnits, after.availableBalanceMinorUnits)
        assertEquals(before.transactions.map { it.id }, after.transactions.map { it.id })
    }

    @Test
    fun insufficientFundsLeavesBalancesUnchanged() {
        val before = session.snapshot()
        val result = session.send("peer-jordan", "999.00", null)
        assertIs<SendResult.Err>(result)
        assertEquals("Insufficient funds", result.message)

        val after = session.snapshot()
        assertEquals(before.availableBalanceMinorUnits, after.availableBalanceMinorUnits)
        assertEquals("Failed", after.transactions[0].status)
        assertEquals(-99_900L, after.transactions[0].amountMinorUnits)
    }
}

private class SequentialIds : IdGenerator {
    private var n = 0
    override fun next(): String = "id-${++n}"
}
