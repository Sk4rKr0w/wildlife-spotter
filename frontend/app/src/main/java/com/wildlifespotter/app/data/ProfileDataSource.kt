package com.wildlifespotter.app.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object ProfileDataSource {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    data class ProfileLoadResult(
        val username: String,
        val email: String,
        val supportsPassword: Boolean
    )

    data class ProfileSaveResult(
        val success: Boolean,
        val message: String,
        val shouldLogout: Boolean
    )

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun loadProfile(user: FirebaseUser): ProfileLoadResult {
        val db: FirebaseFirestore = FirebaseFirestore.getInstance()

        val supportsPassword = user.providerData.any { it.providerId == "password" }
        return try {
            val userDoc = db.collection("users")
                .document(user.uid)
                .get()
                .await()

            val username = if (userDoc.exists()) {
                userDoc.getString("username") ?: (user.displayName ?: "User")
            } else {
                user.displayName ?: "User"
            }
            val email = if (userDoc.exists()) {
                userDoc.getString("email") ?: (user.email ?: "")
            } else {
                user.email ?: ""
            }
            ProfileLoadResult(username, email, supportsPassword)
        } catch (_: Exception) {
            ProfileLoadResult(user.displayName ?: "User", user.email ?: "", supportsPassword)
        }
    }

    suspend fun saveProfile(
        context: Context,
        user: FirebaseUser,
        username: String,
        email: String,
        changePassword: Boolean,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): ProfileSaveResult {
        val db: FirebaseFirestore = FirebaseFirestore.getInstance()

        val needsReauth = changePassword || email != user.email
        if (needsReauth) {
            val supportsPassword = user.providerData.any { it.providerId == "password" }
            if (supportsPassword) {
                if (currentPassword.isBlank()) {
                    return ProfileSaveResult(false, "Current password required to update email or password", false)
                }
                val credential = EmailAuthProvider.getCredential(user.email ?: "", currentPassword)
                user.reauthenticate(credential).await()
            } else {
                val resId = context.resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    context.packageName
                )
                if (resId == 0) {
                    return ProfileSaveResult(false, "Missing default_web_client_id", false)
                }
                val webClientId = context.getString(resId)
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                val account = try {
                    client.silentSignIn().await()
                } catch (_: Exception) {
                    null
                }
                val idToken = account?.idToken
                if (idToken.isNullOrBlank()) {
                    return ProfileSaveResult(false, "Re-authentication required. Please sign in again.", false)
                }
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                user.reauthenticate(credential).await()
            }
        }

        if (changePassword) {
            if (newPassword != confirmPassword) {
                return ProfileSaveResult(false, "Passwords don't match", false)
            }
            user.updatePassword(newPassword).await()
        }

        val emailChanged = email != user.email
        val supportsPassword = user.providerData.any { it.providerId == "password" }
        if (emailChanged && !supportsPassword) {
            return ProfileSaveResult(false, "Email change not available for Google accounts", false)
        }
        if (emailChanged) {
            user.verifyBeforeUpdateEmail(email).await()
        }

        val usernameChanged = username != user.displayName
        if (usernameChanged) {
            val existing = db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .await()
            val conflict = existing.documents.any { it.id != user.uid }
            if (conflict) {
                return ProfileSaveResult(false, "Username already taken", false)
            }
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()
            user.updateProfile(profileUpdates).await()
        }

        val data = hashMapOf(
            "username" to username,
            "username_lower" to username.lowercase(),
            "uid" to user.uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!emailChanged) {
            data["email"] = email
            data.remove("pendingEmail")
        } else {
            data["pendingEmail"] = email
        }

        db.collection("users")
            .document(user.uid)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()

        val shouldLogout = emailChanged || changePassword
        val message = if (emailChanged) {
            "Verification email sent. Check your inbox to update the email."
        } else {
            "Profile updated successfully!"
        }
        return ProfileSaveResult(true, message, shouldLogout)
    }
}
