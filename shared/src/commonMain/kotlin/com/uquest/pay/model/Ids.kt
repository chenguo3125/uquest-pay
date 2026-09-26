package com.uquest.pay.model

@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId must not be blank" }
    }
}

@JvmInline
value class WalletId(val value: String) {
    init {
        require(value.isNotBlank()) { "WalletId must not be blank" }
    }
}

@JvmInline
value class TransactionId(val value: String) {
    init {
        require(value.isNotBlank()) { "TransactionId must not be blank" }
    }
}

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "IdempotencyKey must not be blank" }
    }
}

@JvmInline
value class LedgerEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "LedgerEntryId must not be blank" }
    }
}
