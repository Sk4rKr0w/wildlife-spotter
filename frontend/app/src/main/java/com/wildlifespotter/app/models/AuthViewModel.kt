package com.wildlifespotter.app.models

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

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
                isLoading = false
                if (task.isSuccessful) {
                    user = auth.currentUser

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { updateTask ->
                            isLoading = false
                            if (!updateTask.isSuccessful) {
                                errorMessage = "User created, error during saving username"
                            }
                        }
                } else {
                    errorMessage = task.exception?.message ?: "Error during registration"
                }
            }
    }

    fun logout() {
        auth.signOut()
        user = null
    }
}