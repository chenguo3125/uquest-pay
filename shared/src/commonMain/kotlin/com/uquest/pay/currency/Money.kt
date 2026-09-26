package com.uquest.pay.currency

import com.uquest.pay.validation.FailureReason

data class Money(
    val currency: Currency,
    val minorUnits: Long,
) : Comparable<Money> {

    init {
        require(currency.scale >= 0) { "Currency scale must be non-negative" }
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L
    val isNonNegative: Boolean get() = minorUnits >= 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(currency, Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(currency, Math.subtractExact(minorUnits, other.minorUnits))
    }

    fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.code} vs ${other.currency.code}"
        }
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    companion object {
        fun zero(currency: Currency): Money = Money(currency, 0L)

        fun ofMinor(minorUnits: Long, currency: Currency): Money =
            Money(currency, minorUnits)

        fun ofMajor(majorUnits: Long, currency: Currency): Money {
            val factor = scaleFactor(currency.scale)
            return Money(currency, Math.multiplyExact(majorUnits, factor))
        }

        /**
         * Builds money from a major-unit decimal string. Extra fractional digits
         * beyond [Currency.scale] are rejected (no rounding).
         */
        fun ofMajor(decimal: String, currency: Currency): Money {
            val parsed = parseMajorDecimal(decimal, currency)
                ?: throw InvalidAmountException(decimal)
            return parsed
        }

        fun parseMajorDecimal(decimal: String, currency: Currency): Money? {
            val trimmed = decimal.trim()
            if (trimmed.isEmpty()) return null
            val negative = trimmed.startsWith("-")
            val unsigned = if (negative) trimmed.drop(1) else trimmed
            if (unsigned.isEmpty()) return null
            val parts = unsigned.split('.')
            if (parts.size > 2) return null
            val whole = parts[0]
            val fraction = parts.getOrNull(1).orEmpty()
            if (whole.isEmpty() || !whole.all { it.isDigit() }) return null
            if (fraction.any { !it.isDigit() }) return null
            if (fraction.length > currency.scale) return null
            val paddedFraction = fraction.padEnd(currency.scale, '0')
            return try {
                val wholeMinor = Math.multiplyExact(whole.toLong(), scaleFactor(currency.scale))
                val fractionMinor = if (paddedFraction.isEmpty()) 0L else paddedFraction.toLong()
                val unsignedMinor = Math.addExact(wholeMinor, fractionMinor)
                val minor = if (negative) Math.subtractExact(0L, unsignedMinor) else unsignedMinor
                Money(currency, minor)
            } catch (_: ArithmeticException) {
                null
            } catch (_: NumberFormatException) {
                null
            }
        }

        fun scaleFactor(scale: Int): Long {
            var factor = 1L
            repeat(scale) {
                factor = Math.multiplyExact(factor, 10L)
            }
            return factor
        }
    }
}

class InvalidAmountException(val raw: String) : IllegalArgumentException(
    "Invalid major-unit amount: '$raw'",
)

fun Money.validatedPositive(): FailureReason? =
    if (isPositive) null else FailureReason.InvalidAmount
