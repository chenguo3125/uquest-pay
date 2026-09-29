package com.uquest.pay.session

import com.uquest.pay.Clock
import com.uquest.pay.DomainResult
import com.uquest.pay.IdGenerator
import com.uquest.pay.PaymentDomain
import com.uquest.pay.RandomIdGenerator
import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import com.uquest.pay.ledger.LedgerState
import com.uquest.pay.model.IdempotencyKey
import com.uquest.pay.model.Transaction
import com.uquest.pay.model.TransactionDirection
import com.uquest.pay.model.TransactionId
import com.uquest.pay.model.TransferIntent
import com.uquest.pay.model.WalletId
import com.uquest.pay.transaction.TransactionStatus
import com.uquest.pay.validation.FailureReason

data class SessionPeer(
    val walletId: String,
    val displayName: String,
)

data class SessionTransactionRow(
    val id: String,
    val counterparty: String,
    val amountMinorUnits: Long,
    val status: String,
    val createdAtMillis: Long,
)

data class SessionSnapshot(
    val currentUserName: String,
    val currentWalletId: String,
    val availableBalanceMinorUnits: Long,
    val peers: List<SessionPeer>,
    val transactions: List<SessionTransactionRow>,
)

sealed class SendResult {
    data class Completed(val transactionId: String) : SendResult()
    data class Processing(val transactionId: String) : SendResult()
    data class Failed(
        val transactionId: String?,
        val reason: FailureReason,
    ) : SendResult()
}

/**
 * Owns [LedgerState] and is the only host-facing entry to [PaymentDomain].
 * View controllers must not call the ledger or domain directly.
 *
 * Mutations are serialized on [lock] so concurrent callers cannot overwrite
 * each other's state. [PaymentDomain] stays state-in / state-out.
 */
class PaymentSession(
    clock: Clock,
    private val currentWalletId: WalletId,
    initialState: LedgerState,
    private val peers: List<SessionPeer>,
    private val displayNames: Map<String, String>,
    private val ids: IdGenerator = RandomIdGenerator,
) {
    private val lock = SessionLock()
    private val domain = PaymentDomain(clock = clock, ids = ids)
    private var state: LedgerState = initialState

    fun snapshot(): SessionSnapshot = lock.withLock {
        snapshotLocked()
    }

    fun send(
        toWalletId: String,
        amountMajor: String,
        note: String?,
        idempotencyKey: String,
    ): SendResult = lock.withLock {
        sendLocked(toWalletId, amountMajor, note, idempotencyKey)
    }

    fun retry(transactionId: String): SendResult = lock.withLock {
        retryLocked(transactionId)
    }

    fun ledgerEntryCount(): Int = lock.withLock {
        state.entries.size
    }

    fun totalPostedMinorUnits(): Long = lock.withLock {
        state.wallets.values.sumOf { it.postedBalance.minorUnits }
    }

    private fun snapshotLocked(): SessionSnapshot {
        val wallet = state.wallet(currentWalletId)
            ?: error("Current user wallet missing from ledger")
        val rows = state.transactions.values
            .filter { it.fromWalletId == currentWalletId || it.toWalletId == currentWalletId }
            .sortedByDescending { it.createdAtMillis }
            .map { toRow(it) }
        return SessionSnapshot(
            currentUserName = displayName(currentWalletId.value),
            currentWalletId = currentWalletId.value,
            availableBalanceMinorUnits = wallet.availableBalance.minorUnits,
            peers = peers,
            transactions = rows,
        )
    }

    private fun sendLocked(
        toWalletId: String,
        amountMajor: String,
        note: String?,
        idempotencyKey: String,
    ): SendResult {
        if (toWalletId.isBlank()) {
            return SendResult.Failed(null, FailureReason.WalletNotFound)
        }
        if (idempotencyKey.isBlank()) {
            return SendResult.Failed(null, FailureReason.InvariantViolation)
        }
        val amount = Money.parseMajorDecimal(amountMajor, Currency.UQC)
            ?: return SendResult.Failed(null, FailureReason.InvalidAmount)
        val intent = TransferIntent(
            fromWalletId = currentWalletId,
            toWalletId = WalletId(toWalletId),
            amount = amount,
            idempotencyKey = IdempotencyKey(idempotencyKey),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val submitted = when (val result = domain.submitTransfer(state, intent)) {
            is DomainResult.Err -> return SendResult.Failed(
                transactionId = (result.reason as? FailureReason.IdempotencyConflict)?.existingTransactionId,
                reason = result.reason,
            )
            is DomainResult.Ok -> {
                state = result.state
                result.transaction
            }
        }
        return when (submitted.status) {
            TransactionStatus.Completed -> SendResult.Completed(submitted.id.value)
            TransactionStatus.Processing -> SendResult.Processing(submitted.id.value)
            TransactionStatus.Failed, TransactionStatus.Cancelled -> SendResult.Failed(
                submitted.id.value,
                submitted.failureReason ?: FailureReason.InvariantViolation,
            )
            TransactionStatus.Created, TransactionStatus.Validated -> advance(submitted.id)
        }
    }

    private fun retryLocked(transactionId: String): SendResult {
        if (transactionId.isBlank()) {
            return SendResult.Failed(null, FailureReason.InvariantViolation)
        }
        val id = TransactionId(transactionId)
        return when (val retried = domain.retry(state, id)) {
            is DomainResult.Err -> SendResult.Failed(transactionId, retried.reason)
            is DomainResult.Ok -> {
                state = retried.state
                advance(retried.transaction.id)
            }
        }
    }

    private fun advance(transactionId: TransactionId): SendResult {
        when (val processing = domain.startProcessing(state, transactionId)) {
            is DomainResult.Err -> return SendResult.Failed(transactionId.value, processing.reason)
            is DomainResult.Ok -> {
                state = processing.state
                if (processing.transaction.status == TransactionStatus.Failed) {
                    return SendResult.Failed(
                        processing.transaction.id.value,
                        processing.transaction.failureReason ?: FailureReason.InvariantViolation,
                    )
                }
            }
        }
        return when (val completed = domain.complete(state, transactionId)) {
            is DomainResult.Err -> SendResult.Processing(transactionId.value)
            is DomainResult.Ok -> {
                state = completed.state
                SendResult.Completed(completed.transaction.id.value)
            }
        }
    }

    private fun toRow(tx: Transaction): SessionTransactionRow {
        val counterpartyId = if (tx.fromWalletId == currentWalletId) {
            tx.toWalletId
        } else {
            tx.fromWalletId
        }
        val signed = when (tx.directionFor(currentWalletId)) {
            TransactionDirection.DEBIT -> -tx.amount.minorUnits
            TransactionDirection.CREDIT -> tx.amount.minorUnits
        }
        return SessionTransactionRow(
            id = tx.id.value,
            counterparty = displayName(counterpartyId.value),
            amountMinorUnits = signed,
            status = tx.status.name,
            createdAtMillis = tx.createdAtMillis,
        )
    }

    private fun displayName(walletId: String): String = displayNames[walletId] ?: walletId
}

internal fun userMessage(reason: FailureReason): String = when (reason) {
    is FailureReason.InsufficientFunds -> "Insufficient funds"
    FailureReason.InvalidAmount -> "Enter a valid amount"
    FailureReason.SameAccount -> "You cannot send to yourself"
    FailureReason.UnknownCurrency -> "Unsupported currency"
    FailureReason.WalletNotFound -> "Recipient not found"
    is FailureReason.IdempotencyConflict -> "This transfer was already submitted"
    is FailureReason.IllegalTransition,
    FailureReason.InvariantViolation,
    -> "Could not complete this transfer"
}
