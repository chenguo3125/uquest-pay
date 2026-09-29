package com.uquest.pay

import com.uquest.pay.ledger.Ledger
import com.uquest.pay.ledger.LedgerApplyResult
import com.uquest.pay.ledger.LedgerState
import com.uquest.pay.model.LedgerEntry
import com.uquest.pay.model.LedgerEntryId
import com.uquest.pay.model.LedgerEntryKind
import com.uquest.pay.model.Transaction
import com.uquest.pay.model.TransactionId
import com.uquest.pay.model.TransferIntent
import com.uquest.pay.model.WalletId
import com.uquest.pay.transaction.TransactionEvent
import com.uquest.pay.transaction.TransactionStateMachine
import com.uquest.pay.transaction.TransactionStatus
import com.uquest.pay.validation.FailureReason
import com.uquest.pay.validation.IntentValidator
import com.uquest.pay.validation.ValidationResult
import kotlin.random.Random

fun interface Clock {
    fun nowMillis(): Long
}

fun interface IdGenerator {
    fun next(): String
}

object RandomIdGenerator : IdGenerator {
    override fun next(): String {
        val a = Random.nextLong().toULong().toString(16)
        val b = Random.nextLong().toULong().toString(16)
        return "$a-$b"
    }
}

sealed class DomainResult {
    data class Ok(
        val state: LedgerState,
        val transaction: Transaction,
    ) : DomainResult()

    data class Err(
        val reason: FailureReason,
        val state: LedgerState,
    ) : DomainResult()
}

class PaymentDomain(
    private val clock: Clock,
    private val ids: IdGenerator = RandomIdGenerator,
) {
    fun submitTransfer(state: LedgerState, intent: TransferIntent): DomainResult {
        val existing = state.transactionByKey(intent.idempotencyKey)
        if (existing != null) {
            return if (sameIntent(existing, intent)) {
                DomainResult.Ok(state, existing)
            } else {
                DomainResult.Err(
                    FailureReason.IdempotencyConflict(existing.id.value),
                    state,
                )
            }
        }
        val from = state.wallet(intent.fromWalletId)
            ?: return DomainResult.Err(FailureReason.WalletNotFound, state)
        val to = state.wallet(intent.toWalletId)
            ?: return DomainResult.Err(FailureReason.WalletNotFound, state)
        when (val validation = IntentValidator.validate(intent, from, to)) {
            is ValidationResult.Invalid -> return DomainResult.Err(validation.reason, state)
            ValidationResult.Valid -> Unit
        }
        val now = clock.nowMillis()
        val created = Transaction(
            id = TransactionId(ids.next()),
            fromWalletId = from.id,
            toWalletId = to.id,
            fromAccountId = from.ownerAccountId,
            toAccountId = to.ownerAccountId,
            amount = intent.amount,
            status = TransactionStatus.Created,
            idempotencyKey = intent.idempotencyKey,
            note = intent.note,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        val withCreated = state.withTransaction(created)
        return validate(withCreated, created.id)
    }

    fun validate(state: LedgerState, transactionId: TransactionId): DomainResult {
        val tx = state.transaction(transactionId)
            ?: return DomainResult.Err(FailureReason.InvariantViolation, state)
        return applyEvent(state, tx, TransactionEvent.Validate)
    }

    fun startProcessing(state: LedgerState, transactionId: TransactionId): DomainResult {
        val tx = requireTx(state, transactionId) ?: return missingTx(state)
        if (!TransactionStateMachine.canTransition(tx, TransactionEvent.StartProcessing)) {
            return illegal(state, tx, TransactionEvent.StartProcessing)
        }
        val sender = state.wallet(tx.fromWalletId)
            ?: return DomainResult.Err(FailureReason.WalletNotFound, state)
        if (sender.availableBalance < tx.amount) {
            val failed = TransactionStateMachine.transition(
                transaction = tx,
                event = TransactionEvent.Reject,
                nowMillis = clock.nowMillis(),
                failureReason = FailureReason.InsufficientFunds(sender.availableBalance, tx.amount),
            )
            return DomainResult.Ok(state.withTransaction(failed), failed)
        }
        val reserve = entry(tx, sender.id, LedgerEntryKind.RESERVE)
        return when (val applied = Ledger.apply(state, reserve)) {
            is LedgerApplyResult.Rejected -> DomainResult.Err(applied.reason, state)
            is LedgerApplyResult.Applied -> {
                val processing = TransactionStateMachine.transition(
                    transaction = tx,
                    event = TransactionEvent.StartProcessing,
                    nowMillis = clock.nowMillis(),
                )
                DomainResult.Ok(applied.state.withTransaction(processing), processing)
            }
        }
    }

    fun complete(state: LedgerState, transactionId: TransactionId): DomainResult {
        val tx = requireTx(state, transactionId) ?: return missingTx(state)
        if (!TransactionStateMachine.canTransition(tx, TransactionEvent.Complete)) {
            return illegal(state, tx, TransactionEvent.Complete)
        }
        val debit = entry(tx, tx.fromWalletId, LedgerEntryKind.POST_DEBIT)
        val credit = entry(tx, tx.toWalletId, LedgerEntryKind.POST_CREDIT)
        return when (val applied = Ledger.applyAll(state, listOf(debit, credit))) {
            is LedgerApplyResult.Rejected -> DomainResult.Err(applied.reason, state)
            is LedgerApplyResult.Applied -> {
                val completed = TransactionStateMachine.transition(
                    transaction = tx,
                    event = TransactionEvent.Complete,
                    nowMillis = clock.nowMillis(),
                )
                DomainResult.Ok(applied.state.withTransaction(completed), completed)
            }
        }
    }

    fun fail(state: LedgerState, transactionId: TransactionId, reason: FailureReason): DomainResult {
        val tx = requireTx(state, transactionId) ?: return missingTx(state)
        if (!TransactionStateMachine.canTransition(tx, TransactionEvent.Fail)) {
            return illegal(state, tx, TransactionEvent.Fail)
        }
        val release = entry(tx, tx.fromWalletId, LedgerEntryKind.RELEASE)
        return when (val applied = Ledger.apply(state, release)) {
            is LedgerApplyResult.Rejected -> DomainResult.Err(applied.reason, state)
            is LedgerApplyResult.Applied -> {
                val failed = TransactionStateMachine.transition(
                    transaction = tx,
                    event = TransactionEvent.Fail,
                    nowMillis = clock.nowMillis(),
                    failureReason = reason,
                )
                DomainResult.Ok(applied.state.withTransaction(failed), failed)
            }
        }
    }

    fun retry(state: LedgerState, transactionId: TransactionId): DomainResult {
        val tx = requireTx(state, transactionId) ?: return missingTx(state)
        return applyEvent(state, tx, TransactionEvent.Retry)
    }

    fun cancel(state: LedgerState, transactionId: TransactionId): DomainResult {
        val tx = requireTx(state, transactionId) ?: return missingTx(state)
        if (state.hasOpenReserve(tx.id)) {
            return illegal(state, tx, TransactionEvent.Cancel)
        }
        return applyEvent(state, tx, TransactionEvent.Cancel)
    }

    private fun applyEvent(
        state: LedgerState,
        tx: Transaction,
        event: TransactionEvent,
    ): DomainResult {
        if (!TransactionStateMachine.canTransition(tx, event)) {
            return illegal(state, tx, event)
        }
        val next = TransactionStateMachine.transition(tx, event, clock.nowMillis())
        return DomainResult.Ok(state.withTransaction(next), next)
    }

    private fun entry(
        tx: Transaction,
        walletId: WalletId,
        kind: LedgerEntryKind,
    ): LedgerEntry = LedgerEntry(
        id = LedgerEntryId(ids.next()),
        transactionId = tx.id,
        walletId = walletId,
        kind = kind,
        amount = tx.amount,
        createdAtMillis = clock.nowMillis(),
    )

    private fun requireTx(state: LedgerState, id: TransactionId): Transaction? =
        state.transaction(id)

    private fun missingTx(state: LedgerState): DomainResult =
        DomainResult.Err(FailureReason.InvariantViolation, state)

    private fun illegal(
        state: LedgerState,
        tx: Transaction,
        event: TransactionEvent,
    ): DomainResult = DomainResult.Err(FailureReason.IllegalTransition(tx.status, event), state)

    /**
     * Request identity is fromWalletId + toWalletId + amount + note.
     * [TransferIntent.note] is part of identity: the same idempotency key
     * with a different note is [FailureReason.IdempotencyConflict].
     */
    private fun sameIntent(existing: Transaction, intent: TransferIntent): Boolean =
        existing.fromWalletId == intent.fromWalletId &&
            existing.toWalletId == intent.toWalletId &&
            existing.amount == intent.amount &&
            existing.note == intent.note
}
