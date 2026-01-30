package com.wildlifespotter.app.models

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    var username by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var errorMessage by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    var user by mutableStateOf<FirebaseUser?>(auth.currentUser)

    fun login() {
        if (email.isBlank() || password.isBlank()) return

        isLoading = true
        errorMessage = null

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    user = auth.currentUser
                } else {
                    errorMessage = task.exception?.message ?: "Error during login"
                }
            }
    }

    // ✅ FUNZIONE AGGIORNATA: Crea automaticamente il documento Firestore
    fun register() {
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank() || username.isBlank()) return
        if (password != confirmPassword) {
            errorMessage = "Password not corresponding"
            return
        }

        isLoading = true
        errorMessage = null

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    user = auth.currentUser

                    // Aggiorna il profilo Firebase Auth
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                // ✅ CREA IL DOCUMENTO FIRESTORE
                                createUserDocument(user!!)
                            } else {
                                isLoading = false
                                errorMessage = "User created, error during saving username"
                            }
                        }
                } else {
                    isLoading = false
                    errorMessage = task.exception?.message ?: "Error during registration"
                }
            }
    }

    // ✅ NUOVA FUNZIONE: Crea documento utente in Firestore
    private fun createUserDocument(firebaseUser: FirebaseUser) {
        val userData = hashMapOf(
            "uid" to firebaseUser.uid,
            "email" to firebaseUser.email,
            "username" to username,
            "photoURL" to firebaseUser.photoUrl?.toString(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "totalSpots" to 0,
            "preferences" to hashMapOf(
                "notifications" to true,
                "theme" to "dark"
            )
        )

        db.collection("users")
            .document(firebaseUser.uid)
            .set(userData)
            .addOnSuccessListener {
                isLoading = false
                // Registrazione completata con successo
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = "User created, but Firestore sync failed: ${e.message}"
            }
    }

    fun logout() {
        auth.signOut()
        user = null
    }
}
