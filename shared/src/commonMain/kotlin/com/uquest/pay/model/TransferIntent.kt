package com.uquest.pay.model

import com.uquest.pay.currency.Money

data class TransferIntent(
    val fromWalletId: WalletId,
    val toWalletId: WalletId,
    val amount: Money,
    val idempotencyKey: IdempotencyKey,
    val note: String? = null,
)
