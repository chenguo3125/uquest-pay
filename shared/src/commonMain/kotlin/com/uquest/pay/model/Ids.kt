package com.uquest.pay.model

data class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "AccountId must not be blank" }
    }
}

data class WalletId(val value: String) {
    init {
        require(value.isNotBlank()) { "WalletId must not be blank" }
    }
}

data class TransactionId(val value: String) {
    init {
        require(value.isNotBlank()) { "TransactionId must not be blank" }
    }
}

data class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "IdempotencyKey must not be blank" }
    }
}

data class LedgerEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "LedgerEntryId must not be blank" }
    }
}
