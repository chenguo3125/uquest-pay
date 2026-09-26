package com.uquest.pay.model

import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money

data class Wallet(
    val id: WalletId,
    val ownerAccountId: AccountId,
    val postedBalance: Money,
    val reservedBalance: Money,
) {
    init {
        require(postedBalance.currency == reservedBalance.currency) {
            "Wallet posted and reserved balances must use the same currency"
        }
        require(postedBalance.isNonNegative) { "Posted balance must be non-negative" }
        require(reservedBalance.isNonNegative) { "Reserved balance must be non-negative" }
        require(reservedBalance.minorUnits <= postedBalance.minorUnits) {
            "Reserved balance cannot exceed posted balance"
        }
    }

    val currency: Currency get() = postedBalance.currency

    val availableBalance: Money get() = postedBalance - reservedBalance

    companion object {
        fun empty(id: WalletId, ownerAccountId: AccountId, currency: Currency = Currency.UQC): Wallet =
            Wallet(
                id = id,
                ownerAccountId = ownerAccountId,
                postedBalance = Money.zero(currency),
                reservedBalance = Money.zero(currency),
            )

        fun funded(
            id: WalletId,
            ownerAccountId: AccountId,
            postedBalance: Money,
        ): Wallet = Wallet(
            id = id,
            ownerAccountId = ownerAccountId,
            postedBalance = postedBalance,
            reservedBalance = Money.zero(postedBalance.currency),
        )
    }
}

enum class TransactionDirection {
    DEBIT,
    CREDIT,
}
