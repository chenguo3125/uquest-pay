package com.uquest.pay.validation

import com.uquest.pay.currency.Currency
import com.uquest.pay.model.TransferIntent
import com.uquest.pay.model.Wallet

object IntentValidator {
    fun validate(intent: TransferIntent, from: Wallet, to: Wallet): ValidationResult {
        if (intent.fromWalletId != from.id || intent.toWalletId != to.id) {
            return ValidationResult.Invalid(FailureReason.InvariantViolation)
        }
        if (from.id == to.id || from.ownerAccountId == to.ownerAccountId) {
            return ValidationResult.Invalid(FailureReason.SameAccount)
        }
        if (intent.amount.currency != Currency.UQC ||
            from.currency != Currency.UQC ||
            to.currency != Currency.UQC
        ) {
            return ValidationResult.Invalid(FailureReason.UnknownCurrency)
        }
        if (intent.amount.currency != from.currency || intent.amount.currency != to.currency) {
            return ValidationResult.Invalid(FailureReason.UnknownCurrency)
        }
        if (!intent.amount.isPositive) {
            return ValidationResult.Invalid(FailureReason.InvalidAmount)
        }
        return ValidationResult.Valid
    }
}

object MoneyValidator {
    fun requireKnownCurrency(code: String): ValidationResult {
        return if (Currency.fromCode(code) == null) {
            ValidationResult.Invalid(FailureReason.UnknownCurrency)
        } else {
            ValidationResult.Valid
        }
    }
}
