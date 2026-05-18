package com.badew.chatco

fun Throwable.fullChainMessage(): String {
    val sb = StringBuilder(this::class.java.simpleName)
    val msg = message
    if (!msg.isNullOrBlank()) sb.append(": ").append(msg)
    var c = cause
    var depth = 0
    while (c != null && depth++ < 6) {
        val m = c.message
        if (!m.isNullOrBlank()) sb.append(" → ").append(m)
        c = c.cause
    }
    return sb.toString()
}
