package com.uquest.pay.ledger

import com.uquest.pay.model.LedgerEntry
import com.uquest.pay.model.LedgerEntryKind
import com.uquest.pay.model.Wallet
import com.uquest.pay.validation.FailureReason

sealed class LedgerApplyResult {
    data class Applied(val state: LedgerState) : LedgerApplyResult()
    data class Rejected(val reason: FailureReason) : LedgerApplyResult()
}

object Ledger {
    fun apply(state: LedgerState, entry: LedgerEntry): LedgerApplyResult {
        val wallet = state.wallets[entry.walletId]
            ?: return LedgerApplyResult.Rejected(FailureReason.WalletNotFound)
        if (entry.amount.currency != wallet.currency) {
            return LedgerApplyResult.Rejected(FailureReason.UnknownCurrency)
        }
        val updatedWallet = when (entry.kind) {
            LedgerEntryKind.RESERVE -> reserve(state, wallet, entry) ?: return invariant()
            LedgerEntryKind.RELEASE -> release(state, wallet, entry) ?: return invariant()
            LedgerEntryKind.POST_DEBIT -> postDebit(state, wallet, entry) ?: return invariant()
            LedgerEntryKind.POST_CREDIT -> postCredit(wallet, entry) ?: return invariant()
        }
        return LedgerApplyResult.Applied(
            state.copy(
                wallets = state.wallets + (updatedWallet.id to updatedWallet),
                entries = state.entries + entry,
            ),
        )
    }

    fun applyAll(state: LedgerState, entries: List<LedgerEntry>): LedgerApplyResult {
        var current = state
        for (entry in entries) {
            when (val result = apply(current, entry)) {
                is LedgerApplyResult.Applied -> current = result.state
                is LedgerApplyResult.Rejected -> return result
            }
        }
        return LedgerApplyResult.Applied(current)
    }

    private fun reserve(state: LedgerState, wallet: Wallet, entry: LedgerEntry): Wallet? {
        if (state.hasOpenReserve(entry.transactionId)) return null
        if (wallet.availableBalance < entry.amount) return null
        return wallet.copy(reservedBalance = wallet.reservedBalance + entry.amount)
    }

    private fun release(state: LedgerState, wallet: Wallet, entry: LedgerEntry): Wallet? {
        if (!state.hasOpenReserve(entry.transactionId)) return null
        if (wallet.reservedBalance < entry.amount) return null
        return wallet.copy(reservedBalance = wallet.reservedBalance - entry.amount)
    }

    private fun postDebit(state: LedgerState, wallet: Wallet, entry: LedgerEntry): Wallet? {
        if (!state.hasOpenReserve(entry.transactionId)) return null
        if (wallet.reservedBalance < entry.amount) return null
        if (wallet.postedBalance < entry.amount) return null
        return wallet.copy(
            postedBalance = wallet.postedBalance - entry.amount,
            reservedBalance = wallet.reservedBalance - entry.amount,
        )
    }

    private fun postCredit(wallet: Wallet, entry: LedgerEntry): Wallet? {
        return try {
            wallet.copy(postedBalance = wallet.postedBalance + entry.amount)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun invariant(): LedgerApplyResult.Rejected =
        LedgerApplyResult.Rejected(FailureReason.InvariantViolation)
}
