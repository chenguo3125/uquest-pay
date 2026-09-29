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
import com.uquest.pay.model.TransferIntent
import com.uquest.pay.model.Wallet
import com.uquest.pay.model.WalletId
import com.uquest.pay.validation.FailureReason

/**
 * Demo campus roster and seeded history. Not part of payment-session orchestration.
 */
object CampusDemo {
    const val ALEX_WALLET = "wallet-alex"
    const val JORDAN_WALLET = "peer-jordan"
    const val SAM_WALLET = "peer-sam"
    const val RILEY_WALLET = "peer-riley"
    const val CAFE_WALLET = "merchant-cafe"
    const val BOOKSTORE_WALLET = "merchant-bookstore"

    val displayNames: Map<String, String> = mapOf(
        ALEX_WALLET to "Alex Chen",
        JORDAN_WALLET to "Jordan Lee",
        SAM_WALLET to "Sam Patel",
        RILEY_WALLET to "Riley Nguyen",
        CAFE_WALLET to "Campus Cafe",
        BOOKSTORE_WALLET to "Bookstore",
    )

    val sendablePeers: List<SessionPeer> = listOf(
        SessionPeer(JORDAN_WALLET, "Jordan Lee"),
        SessionPeer(SAM_WALLET, "Sam Patel"),
        SessionPeer(RILEY_WALLET, "Riley Nguyen"),
    )

    fun session(
        clock: Clock,
        ids: IdGenerator = RandomIdGenerator,
    ): PaymentSession {
        val seedClock = SeedableClock(clock)
        val domain = PaymentDomain(clock = seedClock, ids = ids)
        return PaymentSession(
            clock = clock,
            currentWalletId = WalletId(ALEX_WALLET),
            initialState = seedCampus(domain, seedClock),
            peers = sendablePeers,
            displayNames = displayNames,
            ids = ids,
        )
    }
}

private class SeedableClock(private val live: Clock) : Clock {
    var overrideMillis: Long? = null
    override fun nowMillis(): Long = overrideMillis ?: live.nowMillis()
}

/**
 * Opening balances are chosen so that after the seeded history Alex has
 * 124.50 UQC available, matching the previous mock dashboard.
 */
private fun seedCampus(domain: PaymentDomain, clock: SeedableClock): LedgerState {
    var state = LedgerState().withWallets(
        Wallet.funded(
            WalletId(CampusDemo.ALEX_WALLET),
            AccountId("account-alex"),
            Money.ofMinor(14_974, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDemo.JORDAN_WALLET),
            AccountId("account-jordan"),
            Money.ofMinor(5_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDemo.SAM_WALLET),
            AccountId("account-sam"),
            Money.ofMinor(5_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDemo.RILEY_WALLET),
            AccountId("account-riley"),
            Money.ofMinor(3_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDemo.CAFE_WALLET),
            AccountId("account-cafe"),
            Money.ofMinor(1_000, Currency.UQC),
        ),
        Wallet.funded(
            WalletId(CampusDemo.BOOKSTORE_WALLET),
            AccountId("account-bookstore"),
            Money.ofMinor(1_000, Currency.UQC),
        ),
    )

    clock.overrideMillis = 1_789_574_400_000L
    state = receive(domain, state, from = CampusDemo.JORDAN_WALLET, amount = 500, key = "seed-jordan-in")

    clock.overrideMillis = 1_789_747_200_000L
    state = pay(domain, state, to = CampusDemo.BOOKSTORE_WALLET, amount = 3_299, key = "seed-bookstore", settle = Settle.Complete)

    clock.overrideMillis = 1_790_006_400_000L
    state = pay(domain, state, to = CampusDemo.RILEY_WALLET, amount = 800, key = "seed-riley", settle = Settle.Fail)

    clock.overrideMillis = 1_790_092_800_000L
    state = receive(domain, state, from = CampusDemo.SAM_WALLET, amount = 2_000, key = "seed-sam-in")

    clock.overrideMillis = 1_790_179_200_000L
    state = pay(domain, state, to = CampusDemo.CAFE_WALLET, amount = 475, key = "seed-cafe", settle = Settle.Processing)

    clock.overrideMillis = 1_790_265_600_000L
    state = pay(domain, state, to = CampusDemo.JORDAN_WALLET, amount = 1_250, key = "seed-jordan-out", settle = Settle.Complete)

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
            fromWalletId = WalletId(CampusDemo.ALEX_WALLET),
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
            toWalletId = WalletId(CampusDemo.ALEX_WALLET),
            amount = Money.ofMinor(amount, Currency.UQC),
            idempotencyKey = IdempotencyKey(key),
        ),
    ) as DomainResult.Ok
    val processing = domain.startProcessing(submitted.state, submitted.transaction.id) as DomainResult.Ok
    return (domain.complete(processing.state, processing.transaction.id) as DomainResult.Ok).state
}
