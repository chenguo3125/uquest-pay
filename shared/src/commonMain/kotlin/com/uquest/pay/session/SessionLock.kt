package com.uquest.pay.session

internal expect class SessionLock() {
    fun <T> withLock(block: () -> T): T
}
