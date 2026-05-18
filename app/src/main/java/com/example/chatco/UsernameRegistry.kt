package com.badew.chatco

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

/**
 * Kullanıcı adı → uid eşlemesi. Firestore'da koleksiyon sorgusu yerine
 * [usernames/{küçükHarf}] belgesi ile okuma yapılır (kurallar sorguya izin vermese bile çalışır).
 */
object UsernameRegistry {

    fun writeMapping(db: FirebaseFirestore, uid: String, usernameLower: String) {
        val key = usernameLower.trim().lowercase(Locale.ROOT)
        if (key.length < 2) return
        db.collection("usernames").document(key)
            .set(hashMapOf("uid" to uid), SetOptions.merge())
    }
}
