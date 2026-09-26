package com.uquest.pay.currency

enum class Currency(
    val code: String,
    val scale: Int,
) {
    UQC("UQC", 2);

    companion object {
        fun fromCode(code: String): Currency? =
            entries.firstOrNull { it.code == code }
    }
}
