package com.wildlifespotter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // State variables
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingProfile by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var messageType by remember { mutableStateOf(MessageType.INFO) }
    var isEditMode by remember { mutableStateOf(false) }
    
    var showPasswordFields by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Load user data
    LaunchedEffect(user?.uid) {
        if (user != null) {
            try {
                username = user.displayName ?: "User"
                email = user.email ?: ""
            } catch (e: Exception) {
                message = "Error loading profile: ${e.message}"
                messageType = MessageType.ERROR
            } finally {
                isLoadingProfile = false
            }
        }
    }

    // Theme colors
    val primaryColor = Color(0xFF4CAF50)
    val accentColor = Color(0xFFFFC107)
    val errorColor = Color(0xFFE53935)
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A2332), Color(0xFF2D3E50))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        if (isLoadingProfile) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = primaryColor)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Profile Avatar with Initial
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(primaryColor, Color(0xFF388E3C))
                            )
                        )
                        .border(4.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = username.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = username.ifEmpty { "User" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Profile Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF374B5E))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEditMode) "Edit Profile" else "Profile Information",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            IconButton(onClick = { isEditMode = !isEditMode }) {
                                Icon(
                                    imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                                    contentDescription = if (isEditMode) "Cancel" else "Edit",
                                    tint = primaryColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val textFieldColors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            disabledTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2D3E50),
                            unfocusedContainerColor = Color(0xFF2D3E50),
                            disabledContainerColor = Color(0xFF2D3E50),
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFF4A5A6B),
                            disabledBorderColor = Color(0xFF4A5A6B),
                            cursorColor = accentColor,
                            focusedLabelColor = primaryColor,
                            unfocusedLabelColor = Color.Gray,
                            disabledLabelColor = Color.Gray
                        )

                        // Username Field
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = primaryColor)
                            },
                            singleLine = true,
                            enabled = isEditMode,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Email Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = primaryColor)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            enabled = isEditMode,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Change Password Toggle
                        if (isEditMode) {
                            TextButton(
                                onClick = { showPasswordFields = !showPasswordFields },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = accentColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (showPasswordFields) "Hide Password Fields" else "Change Password",
                                    color = accentColor
                                )
                            }
                        }

                        // Password Fields
                        if (isEditMode && showPasswordFields) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Current Password
                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = { currentPassword = it },
                                label = { Text("Current Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = primaryColor)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility 
                                                else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) 
                                    VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // New Password
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("New Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = primaryColor)
                                },

                                trailingIcon = {
                                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                        Icon(
                                            imageVector = if (newPasswordVisible) Icons.Default.Visibility 
                                                else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                },
                                visualTransformation = if (newPasswordVisible) 
                                    VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Confirm Password
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm New Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = primaryColor)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility 
                                                else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                },

                                visualTransformation = if (confirmPasswordVisible) 
                                    VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth(),
                                isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                            )
                            
                            if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                                Text(
                                    text = "Passwords do not match",
                                    color = errorColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Message Display
                        if (message.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (messageType) {
                                        MessageType.SUCCESS -> primaryColor.copy(alpha = 0.2f)
                                        MessageType.ERROR -> errorColor.copy(alpha = 0.2f)
                                        MessageType.INFO -> accentColor.copy(alpha = 0.2f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Icon(
                                        imageVector = when (messageType) {
                                            MessageType.SUCCESS -> Icons.Default.CheckCircle
                                            MessageType.ERROR -> Icons.Default.Error
                                            MessageType.INFO -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = when (messageType) {
                                            MessageType.SUCCESS -> primaryColor
                                            MessageType.ERROR -> errorColor
                                            MessageType.INFO -> accentColor
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = message,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Save Button
                        if (isEditMode) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        message = ""
                                        try {
                                            if (user != null) {
                                                // Validate passwords if changing
                                                if (showPasswordFields) {
                                                    if (currentPassword.isEmpty()) {
                                                        message = "Please enter your current password"
                                                        messageType = MessageType.ERROR
                                                        return@launch
                                                    }

                                                    if (newPassword.isEmpty()) {
                                                        message = "Please enter a new password"
                                                        messageType = MessageType.ERROR
                                                        return@launch
                                                    }
                                                    if (newPassword != confirmPassword) {
                                                        message = "Passwords do not match"
                                                        messageType = MessageType.ERROR
                                                        return@launch
                                                    }
                                                    if (newPassword.length < 6) {
                                                        message = "Password must be at least 6 characters"
                                                        messageType = MessageType.ERROR
                                                        return@launch
                                                    }

                                                    // Re-authenticate user before password change
                                                    val credential = EmailAuthProvider.getCredential(
                                                        user.email ?: "",
                                                        currentPassword
                                                    )
                                                    user.reauthenticate(credential).await()
                                                    user.updatePassword(newPassword).await()
                                                    
                                                    // Clear password fields
                                                    currentPassword = ""
                                                    newPassword = ""
                                                    confirmPassword = ""
                                                    showPasswordFields = false
                                                }

                                                // Update Firestore
                                                db.collection("users").document(user.uid)
                                                    .update(
                                                        mapOf(
                                                            "name" to username,
                                                            "username" to username
                                                        )
                                                    ).await()

                                                // Update email if changed
                                                if (email != user.email && email.isNotBlank()) {
                                                    user.updateEmail(email).await()
                                                }

                                                message = "Profile updated successfully!"
                                                messageType = MessageType.SUCCESS
                                                isEditMode = false
                                            }
                                        } catch (e: Exception) {
                                            message = when {
                                                e.message?.contains("password") == true -> 
                                                    "Current password is incorrect"
                                                e.message?.contains("email") == true -> 
                                                    "Error updating email. Email may already be in use."
                                                else -> "Error: ${e.message}"
                                            }
                                            messageType = MessageType.ERROR
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(16.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Logout Button
                Button(
                    onClick = {
                        auth.signOut()
                        onLogout()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = errorColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// Enum for message types
enum class MessageType {
    SUCCESS, ERROR, INFO
}
