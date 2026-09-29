package com.uquest.pay.session

import com.uquest.pay.Clock
import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import com.uquest.pay.ledger.LedgerState
import com.uquest.pay.model.AccountId
import com.uquest.pay.model.Wallet
import com.uquest.pay.model.WalletId
import com.uquest.pay.validation.FailureReason
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentSessionConcurrencyTest {
    @Test
    fun concurrentTransfersOfSixtyFromOneHundredCompleteAtMostOnce() {
        val alice = WalletId("wallet-alice")
        val bob = WalletId("wallet-bob")
        val session = PaymentSession(
            clock = Clock { 1_800_000_000_000L },
            currentWalletId = alice,
            initialState = LedgerState().withWallets(
                Wallet.funded(alice, AccountId("alice"), Money.ofMinor(100, Currency.UQC)),
                Wallet.funded(bob, AccountId("bob"), Money.ofMinor(0, Currency.UQC)),
            ),
            peers = listOf(SessionPeer(bob.value, "Bob")),
            displayNames = mapOf(alice.value to "Alice", bob.value to "Bob"),
            ids = JvmSequentialIds(),
        )
        val postedBefore = session.totalPostedMinorUnits()
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val results = mutableListOf<SendResult>()
        val pool = Executors.newFixedThreadPool(2)
        listOf("key-a", "key-b").forEach { key ->
            pool.execute {
                start.await()
                val result = session.send("wallet-bob", "0.60", null, key)
                synchronized(results) { results.add(result) }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdown()

        val completed = results.filterIsInstance<SendResult.Completed>()
        val failed = results.filterIsInstance<SendResult.Failed>()
        assertEquals(2, results.size)
        assertTrue(completed.size <= 1)
        assertEquals(2 - completed.size, failed.size)
        failed.forEach { assertTrue(it.reason is FailureReason.InsufficientFunds) }

        val available = session.snapshot().availableBalanceMinorUnits
        assertTrue(available == 40L || available == 100L)
        assertTrue(available >= 0L)
        assertEquals(postedBefore, session.totalPostedMinorUnits())
        if (completed.size == 1) {
            assertEquals(40L, available)
        }
    }
}

private class JvmSequentialIds : com.uquest.pay.IdGenerator {
    private var n = 0
    override fun next(): String = "id-${++n}"
}
