package com.uquest.pay.session

internal actual class SessionLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = synchronized(this) { block() }
}
