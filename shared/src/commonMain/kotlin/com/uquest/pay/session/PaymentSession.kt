package com.uquest.pay.session

import com.uquest.pay.Clock
import com.uquest.pay.DomainResult
import com.uquest.pay.IdGenerator
import com.uquest.pay.PaymentDomain
import com.uquest.pay.RandomIdGenerator
import com.uquest.pay.currency.Currency
import com.uquest.pay.currency.Money
import com.uquest.pay.ledger.LedgerState
import com.uquest.pay.model.AccountId
import com.uquest.pay.model.IdempotencyKey
import com.uquest.pay.model.Transaction
import com.uquest.pay.model.TransactionDirection
import com.uquest.pay.model.TransferIntent
import com.uquest.pay.model.Wallet
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
    data object Ok : SendResult()
    data class Err(val message: String) : SendResult()
}

/**
 * Owns [LedgerState] and is the only host-facing entry to [PaymentDomain].
 * View controllers must not call the ledger or domain directly.
 */
class PaymentSession(
    clock: Clock,
    private val ids: IdGenerator = RandomIdGenerator,
) {
    private val seedClock = SeedableClock(clock)
    private val domain = PaymentDomain(clock = seedClock, ids = ids)
    private val currentWalletId = WalletId(CampusDirectory.ALEX_WALLET)
    private var state: LedgerState = seedCampus(domain, seedClock)

    fun snapshot(): SessionSnapshot {
        val wallet = state.wallet(currentWalletId)
            ?: error("Current user wallet missing from ledger")
        val rows = state.transactions.values
            .filter { it.fromWalletId == currentWalletId || it.toWalletId == currentWalletId }
            .sortedByDescending { it.createdAtMillis }
            .map { toRow(it) }
        return SessionSnapshot(
            currentUserName = CampusDirectory.displayName(currentWalletId.value),
            currentWalletId = currentWalletId.value,
            availableBalanceMinorUnits = wallet.availableBalance.minorUnits,
            peers = CampusDirectory.sendablePeers,
            transactions = rows,
        )
    }

    fun send(toWalletId: String, amountMajor: String, note: String?): SendResult {
        if (toWalletId.isBlank()) {
            return SendResult.Err(userMessage(FailureReason.WalletNotFound))
        }
        val amount = Money.parseMajorDecimal(amountMajor, Currency.UQC)
            ?: return SendResult.Err(userMessage(FailureReason.InvalidAmount))
        val intent = TransferIntent(
            fromWalletId = currentWalletId,
            toWalletId = WalletId(toWalletId),
            amount = amount,
            idempotencyKey = IdempotencyKey(ids.next()),
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
        when (val submitted = domain.submitTransfer(state, intent)) {
            is DomainResult.Err -> return SendResult.Err(userMessage(submitted.reason))
            is DomainResult.Ok -> state = submitted.state
        }
        val txId = state.transactionByKey(intent.idempotencyKey)?.id
            ?: return SendResult.Err(userMessage(FailureReason.InvariantViolation))
        when (val processing = domain.startProcessing(state, txId)) {
            is DomainResult.Err -> return SendResult.Err(userMessage(processing.reason))
            is DomainResult.Ok -> {
                state = processing.state
                if (processing.transaction.status == TransactionStatus.Failed) {
                    val reason = processing.transaction.failureReason ?: FailureReason.InvariantViolation
                    return SendResult.Err(userMessage(reason))
                }
            }
        }
        return when (val completed = domain.complete(state, txId)) {
            is DomainResult.Err -> SendResult.Err(userMessage(completed.reason))
            is DomainResult.Ok -> {
                state = completed.state
                SendResult.Ok
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
            counterparty = CampusDirectory.displayName(counterpartyId.value),
            amountMinorUnits = signed,
            status = tx.status.name,
            createdAtMillis = tx.createdAtMillis,
        )
    }
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

private class SeedableClock(private val live: Clock) : Clock {
    var overrideMillis: Long? = null
    override fun nowMillis(): Long = overrideMillis ?: live.nowMillis()
}

private object CampusDirectory {
    const val ALEX_WALLET = "wallet-alex"
    const val JORDAN_WALLET = "peer-jordan"
    const val SAM_WALLET = "peer-sam"
    const val RILEY_WALLET = "peer-riley"
    const val CAFE_WALLET = "merchant-cafe"
    const val BOOKSTORE_WALLET = "merchant-bookstore"

    private val names = mapOf(
        ALEX_WALLET to "Alex Chen",
        JORDAN_WALLET to "Jordan Lee",
        SAM_WALLET to "Sam Patel",
        RILEY_WALLET to "Riley Nguyen",
        CAFE_WALLET to "Campus Cafe",
        BOOKSTORE_WALLET to "Bookstore",
    )

    val sendablePeers = listOf(
        SessionPeer(JORDAN_WALLET, "Jordan Lee"),
        SessionPeer(SAM_WALLET, "Sam Patel"),
        SessionPeer(RILEY_WALLET, "Riley Nguyen"),
    )

    fun displayName(walletId: String): String = names[walletId] ?: walletId
}

/**
 * Opening balances are chosen so that after the seeded history Alex has
 * 124.50 UQC available, matching the previous mock dashboard.
 */
private fun seedCampus(domain: PaymentDomain, clock: SeedableClock): LedgerState {
    var state = LedgerState().withWallets(
        Wallet.funded(
            WalletId(CampusDirectory.ALEX_WALLET),
            AccountId("account-alex"),
            Money.ofMinor(14_974, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDirectory.JORDAN_WALLET),
            AccountId("account-jordan"),
            Money.ofMinor(5_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDirectory.SAM_WALLET),
            AccountId("account-sam"),
            Money.ofMinor(5_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDirectory.RILEY_WALLET),
            AccountId("account-riley"),
            Money.ofMinor(3_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDirectory.CAFE_WALLET),
            AccountId("account-cafe"),
            Money.ofMinor(1_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDirectory.BOOKSTORE_WALLET),
            AccountId("account-bookstore"),
            Money.ofMinor(1_000, Currency.UQC),
        ),
    )

    // Sep 16 2026 16:00 UTC — Jordan pays Alex 5.00
    clock.overrideMillis = 1_789_574_400_000L
    state = receive(domain, state, from = CampusDirectory.JORDAN_WALLET, amount = 500, key = "seed-jordan-in")

    // Sep 18 2026 16:00 UTC — Alex pays Bookstore 32.99
    clock.overrideMillis = 1_789_747_200_000L
    state = pay(domain, state, to = CampusDirectory.BOOKSTORE_WALLET, amount = 3_299, key = "seed-bookstore", settle = Settle.Complete)

    // Sep 21 2026 16:00 UTC — Alex pays Riley 8.00 then the hold is released
    clock.overrideMillis = 1_790_006_400_000L
    state = pay(domain, state, to = CampusDirectory.RILEY_WALLET, amount = 800, key = "seed-riley", settle = Settle.Fail)

    // Sep 22 2026 16:00 UTC — Sam pays Alex 20.00
    clock.overrideMillis = 1_790_092_800_000L
    state = receive(domain, state, from = CampusDirectory.SAM_WALLET, amount = 2_000, key = "seed-sam-in")

    // Sep 23 2026 16:00 UTC — Alex pays Cafe 4.75, left processing (reserve held)
    clock.overrideMillis = 1_790_179_200_000L
    state = pay(domain, state, to = CampusDirectory.CAFE_WALLET, amount = 475, key = "seed-cafe", settle = Settle.Processing)

    // Sep 24 2026 16:00 UTC — Alex pays Jordan 12.50
    clock.overrideMillis = 1_790_265_600_000L
    state = pay(domain, state, to = CampusDirectory.JORDAN_WALLET, amount = 1_250, key = "seed-jordan-out", settle = Settle.Complete)

    clock.overrideMillis = null
    return state
}

private enum class Settle { Complete, Fail, Processing }

private fun pay(
    domain: PaymentDomain,
    state: LedgerState,
    to: String,
    amount: Long,
    key: String,
    settle: Settle,
): LedgerState {
    val submitted = domain.submitTransfer(
        state,
        TransferIntent(
            fromWalletId = WalletId(CampusDirectory.ALEX_WALLET),
            toWalletId = WalletId(to),
            amount = Money.ofMinor(amount, Currency.UQC),
            idempotencyKey = IdempotencyKey(key),
        ),
    ) as DomainResult.Ok
    val processing = domain.startProcessing(submitted.state, submitted.transaction.id) as DomainResult.Ok
    return when (settle) {
        Settle.Processing -> processing.state
        Settle.Complete -> (domain.complete(processing.state, processing.transaction.id) as DomainResult.Ok).state
        Settle.Fail -> (domain.fail(
            processing.state,
            processing.transaction.id,
            FailureReason.InvariantViolation,
        ) as DomainResult.Ok).state
    }
}

private fun receive(
    domain: PaymentDomain,
    state: LedgerState,
    from: String,
    amount: Long,
    key: String,
): LedgerState {
    val submitted = domain.submitTransfer(
        state,
        TransferIntent(
            fromWalletId = WalletId(from),
            toWalletId = WalletId(CampusDirectory.ALEX_WALLET),
            amount = Money.ofMinor(amount, Currency.UQC),
            idempotencyKey = IdempotencyKey(key),
        ),
    ) as DomainResult.Ok
    val processing = domain.startProcessing(submitted.state, submitted.transaction.id) as DomainResult.Ok
    return (domain.complete(processing.state, processing.transaction.id) as DomainResult.Ok).state
}
