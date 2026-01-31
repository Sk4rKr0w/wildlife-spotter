package com.wildlifespotter.app

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.wildlifespotter.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {

    /* ---------------- Firebase ---------------- */
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    /* ---------------- Coroutine ---------------- */
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    /* ---------------- UI State ---------------- */
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isEditMode by remember { mutableStateOf(false) }
    var showPasswordFields by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    var message by remember { mutableStateOf("") }
    var messageType by remember { mutableStateOf(MessageType.INFO) }

    var passwordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    /* ---------------- Theme ---------------- */
    val primary = Color(0xFF4CAF50)
    val accent = Color(0xFFFFC107)
    val error = Color(0xFFE53935)

    /* ---------------- Load User ---------------- */
    LaunchedEffect(Unit) {
        user?.let { firebaseUser ->
            try {
                val userDoc = db.collection("users")
                    .document(firebaseUser.uid)
                    .get()
                    .await()
                
                if (userDoc.exists()) {
                    username = userDoc.getString("username") ?: firebaseUser.displayName ?: "User"
                    email = userDoc.getString("email") ?: firebaseUser.email ?: ""
                } else {
                    username = firebaseUser.displayName ?: "User"
                    email = firebaseUser.email ?: ""
                }
            } catch (e: Exception) {
                username = firebaseUser.displayName ?: "User"
                email = firebaseUser.email ?: ""
            }
        }
        isLoadingProfile = false
    }

    /* ---------------- UI ---------------- */
    Box(modifier = Modifier.fillMaxSize()) {
        // Sfondo animato
        AnimatedWaveBackground(
            primaryColor = Color(0xFF4CAF50),
            secondaryColor = Color(0xFF2EA333)
        )
        
        FloatingParticles(particleCount = 10)
        
        if (isLoadingProfile) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = primary
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(30.dp))

            /* ---------------- Animated Avatar ---------------- */
            AnimatedProfileAvatar(
                initial = username.firstOrNull()?.uppercase() ?: "U",
                primaryColor = primary
            )

            Spacer(Modifier.height(20.dp))

            Text(
                username,
                color = Color(0xFF2E7D32),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                email,
                color = Color.Gray,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(32.dp))

            /* ---------------- Profile Card ---------------- */
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF374B5E).copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(24.dp)) {

                    Header(isEditMode) { isEditMode = !isEditMode }

                    Spacer(Modifier.height(24.dp))

                    ProfileField(
                        value = username,
                        onChange = { username = it },
                        label = "Username",
                        icon = Icons.Default.Person,
                        enabled = isEditMode,
                        primary = primary,
                        )

                    Spacer(Modifier.height(16.dp))

                    ProfileField(
                        value = email,
                        onChange = { email = it },
                        label = "Email",
                        icon = Icons.Default.Email,
                        enabled = isEditMode,
                        keyboardType = KeyboardType.Email,
                        primary = primary
                    )

                    if (isEditMode) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showPasswordFields = !showPasswordFields }) {
                            Icon(Icons.Default.Lock, null, tint = accent)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (showPasswordFields) "Hide Password" else "Change Password",
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isEditMode && showPasswordFields) {
                        PasswordField(
                            "Current Password",
                            currentPassword,
                            { currentPassword = it },
                            passwordVisible,
                            { passwordVisible = !passwordVisible },
                            primary
                        )

                        PasswordField(
                            "New Password",
                            newPassword,
                            { newPassword = it },
                            newPasswordVisible,
                            { newPasswordVisible = !newPasswordVisible },
                            primary
                        )

                        PasswordField(
                            "Confirm Password",
                            confirmPassword,
                            { confirmPassword = it },
                            confirmPasswordVisible,
                            { confirmPasswordVisible = !confirmPasswordVisible },
                            primary,
                            isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                        )
                    }

                    if (message.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        MessageCard(message, messageType, primary, error, accent)
                    }

                    if (isEditMode) {
                        Spacer(Modifier.height(24.dp))
                        SaveButton(
                            isLoading = isLoading,
                            primary = primary
                        ) {
                            scope.launch {
                                saveProfile(
                                    user,
                                    db,
                                    username,
                                    email,
                                    showPasswordFields,
                                    currentPassword,
                                    newPassword,
                                    confirmPassword,
                                    onSuccess = {
                                        message = "Profile updated successfully!"
                                        messageType = MessageType.SUCCESS
                                        isEditMode = false
                                        showPasswordFields = false
                                    },
                                    onError = {
                                        message = it
                                        messageType = MessageType.ERROR
                                    },
                                    setLoading = { isLoading = it }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            /* ---------------- Logout Button ---------------- */
            Button(
                onClick = {
                    auth.signOut()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = error),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(12.dp))
                Text("Logout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

/* ---------------- Animated Profile Avatar ---------------- */
@Composable
fun AnimatedProfileAvatar(
    initial: String,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                rotation += 0.5f
                if (rotation >= 360f) rotation = 0f
            }
        }
    }
    
    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Anello esterno animato
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeWidth = 6f
            val radius = size.minDimension / 2 - strokeWidth
            
            for (i in 0..3) {
                val startAngle = rotation + (i * 90f)
                drawArc(
                    color = when (i % 3) {
                        0 -> primaryColor
                        1 -> Color(0xFFFFC107)
                        else -> Color(0xFF2EA333)
                    },
                    startAngle = startAngle,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    alpha = 0.8f
                )
            }
        }
        
        // Cerchio avatar interno
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                    )
                )
                .border(4.dp, Color.White.copy(0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/* ---------------- Helpers ---------------- */

suspend fun saveProfile(
    user: com.google.firebase.auth.FirebaseUser?,
    db: FirebaseFirestore,
    username: String,
    email: String,
    changePassword: Boolean,
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    if (user == null) return
    try {
        setLoading(true)

        if (changePassword) {
            if (newPassword != confirmPassword) {
                onError("Passwords don't match")
                return
            }
            val credential = EmailAuthProvider.getCredential(
                user.email ?: "", currentPassword
            )
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
        }

        if (email != user.email) {
            user.updateEmail(email).await()
        }

        if (username != user.displayName) {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()
            user.updateProfile(profileUpdates).await()
        }

        db.collection("users")
            .document(user.uid)
            .set(
                mapOf(
                    "username" to username,
                    "email" to email,
                    "uid" to user.uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()

        onSuccess()
    } catch (e: Exception) {
        onError(e.message ?: "Unknown error")
    } finally {
        setLoading(false)
    }
}

/* ---------------- UI Components ---------------- */

@Composable
fun Header(isEdit: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isEdit) Icons.Default.Edit else Icons.Default.AccountCircle,
                null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (isEdit) "Edit Profile" else "Profile Details",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (isEdit) Icons.Default.Close else Icons.Default.Edit,
                null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ProfileField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    primary: Color,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = primary) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primary,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
            disabledBorderColor = Color.Gray.copy(alpha = 0.2f),
            focusedLabelColor = primary,
            unfocusedLabelColor = Color.Gray,
            disabledLabelColor = Color.Gray,
            cursorColor = primary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.Gray
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    visible: Boolean,
    onToggle: () -> Unit,
    primary: Color,
    isError: Boolean = false
) {
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null, tint = primary) },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    null,
                    tint = Color.Gray
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        isError = isError,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primary,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
            errorBorderColor = Color(0xFFE53935),
            focusedLabelColor = primary,
            unfocusedLabelColor = Color.Gray,
            cursorColor = primary,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SaveButton(isLoading: Boolean, primary: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = primary),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
        } else {
            Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(12.dp))
            Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun MessageCard(
    text: String,
    type: MessageType,
    primary: Color,
    error: Color,
    accent: Color
) {
    val color = when (type) {
        MessageType.SUCCESS -> primary
        MessageType.ERROR -> error
        MessageType.INFO -> accent
    }
    
    val icon = when (type) {
        MessageType.SUCCESS -> Icons.Default.CheckCircle
        MessageType.ERROR -> Icons.Default.Error
        MessageType.INFO -> Icons.Default.Info
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

enum class MessageType { SUCCESS, ERROR, INFO }
