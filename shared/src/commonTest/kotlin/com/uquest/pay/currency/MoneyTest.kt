package com.uquest.pay.currency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun ofMajorUsesScaleTwoForUqc() {
        val money = Money.ofMajor(12, Currency.UQC)
        assertEquals(1200L, money.minorUnits)
    }

    @Test
    fun ofMajorDecimalRejectsExtraPrecision() {
        assertNull(Money.parseMajorDecimal("1.234", Currency.UQC))
        assertFailsWith<InvalidAmountException> {
            Money.ofMajor("1.234", Currency.UQC)
        }
    }

    @Test
    fun ofMajorDecimalAcceptsScaleOrFewerDigits() {
        assertEquals(1250L, Money.ofMajor("12.5", Currency.UQC).minorUnits)
        assertEquals(1205L, Money.ofMajor("12.05", Currency.UQC).minorUnits)
        assertEquals(1200L, Money.ofMajor("12", Currency.UQC).minorUnits)
    }

    @Test
    fun additionAndSubtractionAreExact() {
        val a = Money.ofMinor(150, Currency.UQC)
        val b = Money.ofMinor(50, Currency.UQC)
        assertEquals(Money.ofMinor(200, Currency.UQC), a + b)
        assertEquals(Money.ofMinor(100, Currency.UQC), a - b)
    }

    @Test
    fun comparisonRequiresMatchingCurrency() {
        val left = Money.ofMinor(1, Currency.UQC)
        val right = Money.ofMinor(2, Currency.UQC)
        assertTrue(left < right)
    }

    @Test
    fun overflowIsChecked() {
        val max = Money.ofMinor(Long.MAX_VALUE, Currency.UQC)
        assertFailsWith<ArithmeticException> { max + Money.ofMinor(1, Currency.UQC) }
        assertFailsWith<ArithmeticException> {
            Money.ofMajor(Long.MAX_VALUE, Currency.UQC)
        }
    }

    @Test
    fun signHelpers() {
        assertTrue(Money.zero(Currency.UQC).isZero)
        assertTrue(Money.ofMinor(1, Currency.UQC).isPositive)
        assertTrue(Money.ofMinor(-1, Currency.UQC).isNegative)
        assertFalse(Money.ofMinor(-1, Currency.UQC).isNonNegative)
    }

    @Test
    fun unknownCurrencyCode() {
        assertNull(Currency.fromCode("USD"))
        assertEquals(Currency.UQC, Currency.fromCode("UQC"))
    }
}
