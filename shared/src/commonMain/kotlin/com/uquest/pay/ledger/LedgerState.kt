package com.uquest.pay.ledger

import com.uquest.pay.model.IdempotencyKey
import com.uquest.pay.model.LedgerEntry
import com.uquest.pay.model.LedgerEntryKind
import com.uquest.pay.model.Transaction
import com.uquest.pay.model.TransactionId
import com.uquest.pay.model.Wallet
import com.uquest.pay.model.WalletId

data class LedgerState(
    val wallets: Map<WalletId, Wallet> = emptyMap(),
    val entries: List<LedgerEntry> = emptyList(),
    val transactions: Map<TransactionId, Transaction> = emptyMap(),
    val transactionsByIdempotencyKey: Map<IdempotencyKey, TransactionId> = emptyMap(),
) {
    fun wallet(id: WalletId): Wallet? = wallets[id]

    fun transaction(id: TransactionId): Transaction? = transactions[id]

    fun transactionByKey(key: IdempotencyKey): Transaction? =
        transactionsByIdempotencyKey[key]?.let { transactions[it] }

    fun withWallets(vararg wallets: Wallet): LedgerState =
        copy(wallets = this.wallets + wallets.associateBy { it.id })

    fun withTransaction(transaction: Transaction): LedgerState = copy(
        transactions = transactions + (transaction.id to transaction),
        transactionsByIdempotencyKey = transactionsByIdempotencyKey +
            (transaction.idempotencyKey to transaction.id),
    )

    fun netReserveCount(transactionId: TransactionId): Int {
        var net = 0
        for (entry in entries) {
            if (entry.transactionId != transactionId) continue
            when (entry.kind) {
                LedgerEntryKind.RESERVE -> net += 1
                LedgerEntryKind.RELEASE, LedgerEntryKind.POST_DEBIT -> net -= 1
                LedgerEntryKind.POST_CREDIT -> Unit
            }
        }
        return net
    }

    fun hasOpenReserve(transactionId: TransactionId): Boolean =
        netReserveCount(transactionId) == 1
}
