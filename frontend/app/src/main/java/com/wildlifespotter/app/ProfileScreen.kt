package com.wildlifespotter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var company by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            try {
                val doc = db.collection("users").document(user.uid).get().await()
                name = doc.getString("name") ?: ""
                company = doc.getString("company") ?: ""
            } catch (e: Exception) {
                message = "Error loading profile: ${e.message}"
            }
        }
    }

    val primaryColor = Color(0xFF4CAF50)
    val accentColor = Color(0xFFFFC107)
    val errorColor = Color(0xFFE53935)
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1E1E2C), Color(0xFF2C2C3C))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "My Profile",
                color = Color.White,
                fontSize = 28.sp,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF33334D))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF444466),
                        unfocusedContainerColor = Color(0xFF444466),
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color(0xFF666699),
                        cursorColor = accentColor,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color(0xFFAAAAAA)
                    )

                    // Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Company
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Company") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("New Password (leave blank to keep current)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    if (message.isNotEmpty()) {
                        Text(
                            text = message,
                            color = if (message.contains("Error") || message.contains("error"))
                                errorColor else accentColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                message = ""
                                try {
                                    if (user != null) {
                                        // Update Firestore
                                        db.collection("users").document(user.uid)
                                            .update(
                                                mapOf(
                                                    "name" to name,
                                                    "company" to company
                                                )
                                            ).await()

                                        // Update email if changed
                                        if (email != user.email && email.isNotBlank()) {
                                            user.updateEmail(email).await()
                                        }

                                        // Update password if provided
                                        if (password.isNotBlank()) {
                                            user.updatePassword(password).await()
                                        }

                                        message = "Profile updated successfully!"
                                        password = "" // Clear password field
                                    }
                                } catch (e: Exception) {
                                    message = "Error: ${e.message}"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(15.dp),
                        enabled = !loading
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Text("Save Changes", color = Color.White, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            auth.signOut()
                            onLogout()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = errorColor),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("Logout", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}