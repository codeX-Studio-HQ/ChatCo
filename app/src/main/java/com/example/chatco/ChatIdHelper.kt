package com.badew.chatco

object ChatIdHelper {
    /** İki kullanıcı için her zaman aynı sohbet belgesi kimliği (UID sıralı birleşim). */
    fun forUsers(uidA: String, uidB: String): String {
        return if (uidA <= uidB) "${uidA}_$uidB" else "${uidB}_$uidA"
    }
}
