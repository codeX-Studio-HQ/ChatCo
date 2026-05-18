package com.badew.chatco

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.badew.chatco.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Not: Profil fotoğrafı özelliği kaldırıldı.

        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Lütfen tüm alanları doldurun", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val usernameLower = name.lowercase(Locale.ROOT)
            if (usernameLower.length < 2) {
                Toast.makeText(this, "Kullanıcı adı en az 2 karakter olmalı", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                        db.collection("users")
                            .whereEqualTo("usernameLower", usernameLower)
                            .limit(2)
                            .get()
                            .addOnSuccessListener { snap ->
                                val taken = snap.documents.any { it.id != userId }
                                if (taken) {
                                    auth.currentUser?.delete()?.addOnCompleteListener {
                                        Toast.makeText(
                                            this,
                                            "Bu kullanıcı adı zaten alınmış",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        auth.signOut()
                                    }
                                    return@addOnSuccessListener
                                }

                                val user = hashMapOf(
                                    "name" to name,
                                    "usernameLower" to usernameLower,
                                    "email" to email,
                                    "uid" to userId
                                )

                                db.collection("users").document(userId).set(user)
                                    .addOnSuccessListener {
                                        UsernameRegistry.writeMapping(db, userId, usernameLower)
                                        goMain()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(
                                            this,
                                            "Profil kaydedilemedi: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this,
                                    "Kontrol başarısız: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    } else {
                        Toast.makeText(this, "Kayıt hatası: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.tvLoginLink.setOnClickListener {
            finish()
        }
    }

    private fun goMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
