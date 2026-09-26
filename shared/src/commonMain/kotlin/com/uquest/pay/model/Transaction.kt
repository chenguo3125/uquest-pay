package com.uquest.pay.model

import com.uquest.pay.currency.Money
import com.uquest.pay.transaction.TransactionStatus
import com.uquest.pay.validation.FailureReason

data class Transaction(
    val id: TransactionId,
    val fromWalletId: WalletId,
    val toWalletId: WalletId,
    val fromAccountId: AccountId,
    val toAccountId: AccountId,
    val amount: Money,
    val status: TransactionStatus,
    val idempotencyKey: IdempotencyKey,
    val attemptCount: Int = 0,
    val failureReason: FailureReason? = null,
    val note: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun directionFor(walletId: WalletId): TransactionDirection = when (walletId) {
        fromWalletId -> TransactionDirection.DEBIT
        toWalletId -> TransactionDirection.CREDIT
        else -> error("Wallet $walletId is not a party to transaction ${id.value}")
    }
}
