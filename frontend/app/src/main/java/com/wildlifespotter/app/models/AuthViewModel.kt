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

    var resetPasswordEmail by mutableStateOf("")
    var resetPasswordSuccess by mutableStateOf(false)

    var errorMessage by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)

    var user by mutableStateOf<FirebaseUser?>(null)
    private val authStateListener: FirebaseAuth.AuthStateListener
    private var listenerActive = true

    init {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (listenerActive) {
                user = firebaseAuth.currentUser
            }
        }
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }

    var showGoogleProfileDialog by mutableStateOf(false)
    var showUsernameDialog by mutableStateOf(false)
    var pendingGoogleUser: FirebaseUser? by mutableStateOf(null)
    var pendingEmailUser: FirebaseUser? by mutableStateOf(null)
    var isCheckingUsername by mutableStateOf(false)
    var isCheckingEmail by mutableStateOf(false)

    var registrationState by mutableStateOf<RegistrationState>(RegistrationState.IDLE)

    sealed class StartupDestination {
        object Home : StartupDestination()
        object Onboarding : StartupDestination()
        object SignIn : StartupDestination()
    }


    fun login() {
        if (email.isBlank() || password.isBlank()) return

        isLoading = true
        errorMessage = null
        listenerActive = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        val isPasswordProvider = firebaseUser.providerData.any { it.providerId == "password" }
                        if (isPasswordProvider && !firebaseUser.isEmailVerified) {
                            auth.signOut()
                            isLoading = false
                            listenerActive = true
                            errorMessage = "Email not verified. Please verify your email before logging in."
                            return@addOnCompleteListener
                        }

                        db.collection("users")
                            .document(firebaseUser.uid)
                            .get()
                            .addOnSuccessListener { doc ->
                                isLoading = false
                                listenerActive = true
                                if (!doc.exists() || doc.getString("username").isNullOrBlank()) {
                                    pendingEmailUser = firebaseUser
                                    showUsernameDialog = true
                                } else {
                                    user = firebaseUser
                                    viewModelScope.launch {
                                        syncUserEmail(firebaseUser)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                listenerActive = true
                                errorMessage = e.message ?: "Error checking user data"
                            }
                    } else {
                        isLoading = false
                        listenerActive = true
                        errorMessage = "Error during login"
                    }
                } else {
                    isLoading = false
                    listenerActive = true
                    val e = task.exception
                    errorMessage = when (e) {
                        is FirebaseAuthInvalidCredentialsException -> "Invalid credentials. If you recently changed email, verify it first and log in with the previous email."
                        is FirebaseAuthInvalidUserException -> "User not found. If you recently changed email, verify it first and log in with the previous email."
                        else -> e?.message ?: "Error during login"
                    }
                }
            }
    }

    suspend fun resolveStartupDestination(): StartupDestination {
        val currentUser = auth.currentUser
        if (currentUser == null) return StartupDestination.Onboarding
        return try {
            val doc = db.collection("users")
                .document(currentUser.uid)
                .get()
                .await()
            if (doc.exists() && !doc.getString("username").isNullOrBlank()) {
                StartupDestination.Home
            } else {
                user = currentUser
                pendingEmailUser = currentUser
                if (!doc.exists() || doc.getString("username").isNullOrBlank()) {
                    showUsernameDialog = true
                }
                StartupDestination.Home
            }
        } catch (_: Exception) {
            StartupDestination.SignIn
        }
    }

    fun checkEmailAvailable(emailInput: String, onResult: (Boolean, String?) -> Unit) {
        val emailValue = emailInput.trim()
        if (emailValue.isBlank() || !emailValue.contains("@")) {
            onResult(false, "Invalid email")
            return
        }
        isCheckingEmail = true
        auth.fetchSignInMethodsForEmail(emailValue)
            .addOnSuccessListener { res ->
                isCheckingEmail = false
                val methods = res.signInMethods ?: emptyList()
                val available = methods.isEmpty()
                onResult(available, null)
            }
            .addOnFailureListener { e ->
                isCheckingEmail = false
                onResult(false, e.message ?: "Unable to verify email")
            }
    }

    fun register() {
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank() || countryName.isBlank()) return
        if (password != confirmPassword) {
            errorMessage = "Password not corresponding"
            return
        }
        if (!isPasswordStrong(password)) {
            errorMessage = "Password too weak"
            return
        }
        val countryCode = toAlpha3Country(countryName)
        if (countryCode == null) {
            errorMessage = "Invalid country"
            return
        }

        isLoading = true
        errorMessage = null
        listenerActive = false

        auth.fetchSignInMethodsForEmail(email.trim())
            .addOnSuccessListener { res ->
                val methods = res.signInMethods ?: emptyList()
                if (methods.isNotEmpty()) {
                    isLoading = false
                    listenerActive = true
                    errorMessage = "Email already in use"
                    return@addOnSuccessListener
                }
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            user?.sendEmailVerification()?.addOnCompleteListener { sendEmailTask ->
                                auth.signOut()
                                listenerActive = true
                                if(sendEmailTask.isSuccessful) {
                                    registrationState = RegistrationState.SUCCESS
                                } else {
                                    errorMessage = "User created, but failed to send verification email."
                                }
                                isLoading = false
                            }
                        } else {
                            isLoading = false
                            listenerActive = true
                            errorMessage = task.exception?.message ?: "Error during registration"
                        }
                    }
            }
            .addOnFailureListener { e ->
                isLoading = false
                listenerActive = true
                errorMessage = e.message ?: "Unable to verify email"
            }
    }

    fun sendResetEmail() {
        val emailInput = resetPasswordEmail.trim()
        if (emailInput.isBlank() || !emailInput.contains("@")) {
            errorMessage = "Please enter a valid email"
            return
        }
        isLoading = true
        errorMessage = null
        auth.sendPasswordResetEmail(emailInput)
            .addOnSuccessListener {
                isLoading = false
                resetPasswordSuccess = true
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMessage = e.message ?: "Failed to send reset email"
            }
    }

    private fun createUserDocument(
        firebaseUser: FirebaseUser,
        name: String,
        countryCode: String,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        val userData = hashMapOf(
            "uid" to firebaseUser.uid,
            "email" to firebaseUser.email,
            "username" to name,
            "username_lower" to name.lowercase(),
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
                onSuccess()
            }
            .addOnFailureListener { e ->
                isLoading = false
                val msg = "User created, but Firestore sync failed: ${e.message}"
                errorMessage = msg
                onFailure(msg)
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
                                createUserDocument(firebaseUser, name, countryCode,
                                    onSuccess = {
                                        user = firebaseUser
                                        pendingGoogleUser = null
                                        showGoogleProfileDialog = false
                                        viewModelScope.launch {
                                            syncUserEmail(firebaseUser)
                                        }
                                    },
                                    onFailure = {
                                        firebaseUser.delete()
                                        auth.signOut()
                                        pendingGoogleUser = null
                                        showGoogleProfileDialog = false
                                    }
                                )
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

    fun completeEmailProfile(name: String, countryInput: String) {
        val firebaseUser = pendingEmailUser ?: return
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
                            } else {
                                createUserDocument(firebaseUser, name, countryCode,
                                    onSuccess = {
                                        user = firebaseUser
                                        pendingEmailUser = null
                                        showUsernameDialog = false
                                        viewModelScope.launch {
                                            syncUserEmail(firebaseUser)
                                        }
                                    }
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            errorMessage = e.message ?: "Username check failed"
                        }
                } else {
                    isLoading = false
                    errorMessage = "Error saving username"
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

    fun resetAuthFields() {
        email = ""
        password = ""
        confirmPassword = ""
        errorMessage = null
    }

    fun resetRegistrationState() {
        registrationState = RegistrationState.IDLE
    }

    fun isPasswordStrong(password: String): Boolean {
        val minLength = 8
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return password.length >= minLength && hasUpperCase && hasLowerCase && hasDigit && hasSpecial
    }


}

enum class RegistrationState {
    IDLE,
    SUCCESS
}
