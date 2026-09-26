package com.uquest.pay.model

import com.uquest.pay.currency.Money

enum class LedgerEntryKind {
    RESERVE,
    RELEASE,
    POST_DEBIT,
    POST_CREDIT,
}

data class LedgerEntry(
    val id: LedgerEntryId,
    val transactionId: TransactionId,
    val walletId: WalletId,
    val kind: LedgerEntryKind,
    val amount: Money,
    val createdAtMillis: Long,
) {
    init {
        require(amount.isPositive) { "Ledger entry amount must be positive" }
    }
}
