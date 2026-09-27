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
        return Money(currency, addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(currency, subtractExact(minorUnits, other.minorUnits))
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
            return Money(currency, multiplyExact(majorUnits, factor))
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
                val wholeMinor = multiplyExact(whole.toLong(), scaleFactor(currency.scale))
                val fractionMinor = if (paddedFraction.isEmpty()) 0L else paddedFraction.toLong()
                val unsignedMinor = addExact(wholeMinor, fractionMinor)
                val minor = if (negative) subtractExact(0L, unsignedMinor) else unsignedMinor
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
                factor = multiplyExact(factor, 10L)
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

internal fun addExact(a: Long, b: Long): Long {
    val result = a + b
    if ((a xor result) and (b xor result) < 0L) {
        throw ArithmeticException("long overflow")
    }
    return result
}

internal fun subtractExact(a: Long, b: Long): Long {
    val result = a - b
    if ((a xor b) and (a xor result) < 0L) {
        throw ArithmeticException("long overflow")
    }
    return result
}

internal fun multiplyExact(a: Long, b: Long): Long {
    val result = a * b
    val absA = if (a >= 0) a else -a
    val absB = if (b >= 0) b else -b
    if ((absA or absB) ushr 31 != 0L) {
        if (a == Long.MIN_VALUE && b == -1L) {
            throw ArithmeticException("long overflow")
        }
        if (b != 0L && result / b != a) {
            throw ArithmeticException("long overflow")
        }
    }
    return result
}
