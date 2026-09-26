package com.uquest.pay.model

import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletTest {
    private val account = AccountId("alice")
    private val walletId = WalletId("w-alice")

    @Test
    fun availableIsPostedMinusReserved() {
        val wallet = Wallet(
            id = walletId,
            ownerAccountId = account,
            postedBalance = Money.ofMinor(500, Currency.UQC),
            reservedBalance = Money.ofMinor(200, Currency.UQC),
        )
        assertEquals(Money.ofMinor(300, Currency.UQC), wallet.availableBalance)
    }

    @Test
    fun rejectsNegativePosted() {
        assertFailsWith<IllegalArgumentException> {
            Wallet(
                id = walletId,
                ownerAccountId = account,
                postedBalance = Money.ofMinor(-1, Currency.UQC),
                reservedBalance = Money.zero(Currency.UQC),
            )
        }
    }

    @Test
    fun rejectsReservedAbovePosted() {
        assertFailsWith<IllegalArgumentException> {
            Wallet(
                id = walletId,
                ownerAccountId = account,
                postedBalance = Money.ofMinor(10, Currency.UQC),
                reservedBalance = Money.ofMinor(11, Currency.UQC),
            )
        }
    }
}
