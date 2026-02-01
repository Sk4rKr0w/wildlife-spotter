package com.wildlifespotter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildlifespotter.app.models.AuthViewModel

@Composable
fun ForgotPasswordScreen(
    authViewModel: AuthViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    var emailError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authViewModel.resetPasswordEmail = ""
        authViewModel.resetPasswordSuccess = false
        authViewModel.errorMessage = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2EA333), Color.Black)
                )
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.whitelogo),
            contentDescription = "Logo",
            modifier = Modifier
                .width(187.dp)
                .height(115.dp)
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!authViewModel.resetPasswordSuccess) {
                Text(
                    text = "Forgot Password",
                    fontSize = 40.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Thin
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Enter your email and we'll send you a link to reset your password.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = authViewModel.resetPasswordEmail,
                    onValueChange = {
                        authViewModel.resetPasswordEmail = it
                        emailError = false
                        authViewModel.errorMessage = null
                    },
                    label = { Text("Email") },
                    isError = emailError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2EA333),
                        unfocusedBorderColor = Color.White,
                        focusedLabelColor = Color(0xFF2EA333),
                        unfocusedLabelColor = Color.White,
                        errorBorderColor = Color.Red
                    )
                )

                if (emailError) {
                    Text(
                        text = "Please enter a valid email",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                if (authViewModel.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = authViewModel.errorMessage ?: "",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val input = authViewModel.resetPasswordEmail.trim()
                        if (input.isBlank() || !input.contains("@")) {
                            emailError = true
                        } else {
                            authViewModel.sendResetEmail()
                        }
                    },
                    enabled = !authViewModel.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (authViewModel.isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Send Reset Link")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Back to Sign In",
                    color = Color(0xFF2EA333),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onBackClick() }
                )

            } else {
                Text(
                    text = "Email Sent",
                    fontSize = 40.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Thin
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Check your inbox for a link to reset your password. If you don't see it, check your spam folder.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = onBackClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Back to Sign In")
                }
            }
        }
    }
}
