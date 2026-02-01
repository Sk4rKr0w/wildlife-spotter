package com.wildlifespotter.app.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    var username by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var countryName by mutableStateOf("")

    var errorMessage by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    var user by mutableStateOf<FirebaseUser?>(auth.currentUser)
    var showGoogleProfileDialog by mutableStateOf(false)
    private var pendingGoogleUser: FirebaseUser? = null
    var isCheckingUsername by mutableStateOf(false)

    fun login() {
        if (email.isBlank() || password.isBlank()) return

        isLoading = true
        errorMessage = null

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        val isPasswordProvider = firebaseUser.providerData.any { it.providerId == "password" }
                        if (isPasswordProvider && !firebaseUser.isEmailVerified) {
                            auth.signOut()
                            user = null
                            errorMessage = "Email not verified. Please verify your email before logging in."
                            return@addOnCompleteListener
                        }
                        user = firebaseUser
                        viewModelScope.launch {
                            syncUserEmail(firebaseUser)
                        }
                    } else {
                        errorMessage = "Error during login"
                    }
                } else {
                    val e = task.exception
                    errorMessage = when (e) {
                        is FirebaseAuthInvalidCredentialsException -> "Invalid credentials. If you recently changed email, verify it first and log in with the previous email."
                        is FirebaseAuthInvalidUserException -> "User not found. If you recently changed email, verify it first and log in with the previous email."
                        else -> e?.message ?: "Error during login"
                    }
                }
            }
    }

    fun register() {
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank() || countryName.isBlank()) return
        if (password != confirmPassword) {
            errorMessage = "Password not corresponding"
            return
        }
        val countryCode = toAlpha3Country(countryName)
        if (countryCode == null) {
            errorMessage = "Invalid country"
            return
        }

        isLoading = true
        errorMessage = null

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    user = auth.currentUser
                    pendingGoogleUser = user
                    showGoogleProfileDialog = true
                    isLoading = false
                } else {
                    isLoading = false
                    errorMessage = task.exception?.message ?: "Error during registration"
                }
            }
    }

    private fun createUserDocument(firebaseUser: FirebaseUser, name: String, countryCode: String) {
        val userData = hashMapOf(
            "uid" to firebaseUser.uid,
            "email" to firebaseUser.email,
            "username" to name,
            "country" to countryCode,
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
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = "User created, but Firestore sync failed: ${e.message}"
            }
    }

    fun loginWithGoogle(idToken: String) {
        isLoading = true
        errorMessage = null
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser == null) {
                        isLoading = false
                        errorMessage = "Google sign-in failed"
                        return@addOnCompleteListener
                    }
                    db.collection("users")
                        .document(firebaseUser.uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            isLoading = false
                            if (doc.exists()) {
                                user = firebaseUser
                                viewModelScope.launch {
                                    syncUserEmail(firebaseUser)
                                }
                            } else {
                                pendingGoogleUser = firebaseUser
                                showGoogleProfileDialog = true
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            errorMessage = e.message ?: "Error during login"
                        }
                } else {
                    isLoading = false
                    errorMessage = task.exception?.message ?: "Error during login"
                }
            }
    }

    fun completeGoogleProfile(name: String, countryInput: String) {
        val firebaseUser = pendingGoogleUser ?: return
        if (name.isBlank()) {
            errorMessage = "Username cannot be empty"
            return
        }
        val countryCode = toAlpha3Country(countryInput)
        if (countryCode == null) {
            errorMessage = "Invalid country"
            return
        }
        isLoading = true
        errorMessage = null
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()
        firebaseUser.updateProfile(profileUpdates)
            ?.addOnCompleteListener { updateTask ->
                if (updateTask.isSuccessful) {
                    db.collection("users")
                        .whereEqualTo("username", name)
                        .get()
                        .addOnSuccessListener { snap ->
                            if (snap.documents.any { it.id != firebaseUser.uid }) {
                                isLoading = false
                                errorMessage = "Username already taken"
                                firebaseUser.delete()
                                auth.signOut()
                                pendingGoogleUser = null
                                showGoogleProfileDialog = false
                            } else {
                                createUserDocument(firebaseUser, name, countryCode)
                                user = firebaseUser
                                pendingGoogleUser = null
                                showGoogleProfileDialog = false
                                viewModelScope.launch {
                                    syncUserEmail(firebaseUser)
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            errorMessage = e.message ?: "Username check failed"
                            firebaseUser.delete()
                            auth.signOut()
                            pendingGoogleUser = null
                            showGoogleProfileDialog = false
                        }
                } else {
                    isLoading = false
                    errorMessage = "User created, error during saving username"
                }
            }
    }

    fun checkUsernameAvailable(name: String, onResult: (Boolean, String?) -> Unit) {
        if (name.isBlank()) {
            onResult(false, "Username cannot be empty")
            return
        }
        isCheckingUsername = true
        db.collection("users")
            .whereEqualTo("username", name)
            .get()
            .addOnSuccessListener { snap ->
                isCheckingUsername = false
                val available = snap.documents.isEmpty()
                onResult(available, null)
            }
            .addOnFailureListener { e ->
                isCheckingUsername = false
                onResult(false, e.message ?: "Unable to verify username")
            }
    }

    private suspend fun syncUserEmail(firebaseUser: FirebaseUser) {
        try {
            firebaseUser.reload().await()
            val authEmail = firebaseUser.email ?: return
            val doc = db.collection("users").document(firebaseUser.uid).get().await()
            val dbEmail = doc.getString("email")
            val pendingEmail = doc.getString("pendingEmail")
            if (authEmail != dbEmail || pendingEmail == authEmail) {
                val data = hashMapOf(
                    "email" to authEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                data["pendingEmail"] = ""
                db.collection("users")
                    .document(firebaseUser.uid)
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }
        } catch (_: Exception) {
        }
    }

    fun toAlpha3Country(country: String): String? {
        val input = country.trim()
        if (input.isEmpty()) return null
        for (code in Locale.getISOCountries()) {
            val locale = Locale("", code)
            val nameEn = locale.getDisplayCountry(Locale.ENGLISH)
            val nameLocal = locale.displayCountry
            if (nameEn.equals(input, ignoreCase = true) || nameLocal.equals(input, ignoreCase = true)) {
                return try {
                    locale.isO3Country
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    fun logout(context: Context) {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, gso).signOut()
        user = null
    }
}
