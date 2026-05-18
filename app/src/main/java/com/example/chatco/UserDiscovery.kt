package com.badew.chatco

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

/**
 * 1) [usernames/{küçükHarf}] → uid (tek belge okuma, kurallar genelde sorgudan daha kolay açılır)
 * 2) users üzerinde usernameLower / name sorguları (yedek)
 */
object UserDiscovery {

    fun findUserForFriendRequest(
        db: FirebaseFirestore,
        rawInput: String,
        onFound: (DocumentSnapshot) -> Unit,
        onNotFound: () -> Unit,
        onAmbiguousDisplayName: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val trimmed = rawInput.trim()
        if (trimmed.length < 2) {
            onNotFound()
            return
        }
        val lowerRoot = trimmed.lowercase(Locale.ROOT)
        val lowerTr = trimmed.lowercase(Locale.forLanguageTag("tr"))

        fun loadUserByUid(uid: String, onOk: (DocumentSnapshot) -> Unit, onMiss: () -> Unit) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { u ->
                    if (u.exists()) onOk(u) else onMiss()
                }
                .addOnFailureListener { onMiss() }
        }

        fun queryByExactName() {
            db.collection("users").whereEqualTo("name", trimmed).limit(5).get()
                .addOnSuccessListener { snap ->
                    when {
                        snap.isEmpty -> onNotFound()
                        snap.size() > 1 -> onAmbiguousDisplayName()
                        else -> onFound(snap.documents.first())
                    }
                }
                .addOnFailureListener { onError(it) }
        }

        fun tryTurkishLowerOnUsers() {
            if (lowerTr != lowerRoot) {
                db.collection("users").whereEqualTo("usernameLower", lowerTr).limit(1).get()
                    .addOnSuccessListener { s2 ->
                        if (!s2.isEmpty) onFound(s2.documents.first())
                        else queryByExactName()
                    }
                    .addOnFailureListener { queryByExactName() }
            } else {
                queryByExactName()
            }
        }

        fun queryUsersByUsernameLowerRoot() {
            db.collection("users").whereEqualTo("usernameLower", lowerRoot).limit(1).get()
                .addOnSuccessListener { s1 ->
                    if (!s1.isEmpty) {
                        onFound(s1.documents.first())
                    } else {
                        tryTurkishLowerOnUsers()
                    }
                }
                .addOnFailureListener { e -> onError(e) }
        }

        fun tryUsernameDoc(key: String, onMiss: () -> Unit) {
            db.collection("usernames").document(key).get()
                .addOnSuccessListener { d ->
                    val uid = d.getString("uid")
                    if (!uid.isNullOrBlank()) {
                        loadUserByUid(uid, onFound, onMiss)
                    } else {
                        onMiss()
                    }
                }
                .addOnFailureListener { onMiss() }
        }

        // Önce harita: ROOT, sonra TR (farklıysa), sonra users sorguları
        tryUsernameDoc(lowerRoot) {
            if (lowerTr != lowerRoot) {
                tryUsernameDoc(lowerTr) {
                    queryUsersByUsernameLowerRoot()
                }
            } else {
                queryUsersByUsernameLowerRoot()
            }
        }
    }
}
