package com.wildlifespotter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SignIn(
    onSignInClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {},
    onCreateAccountClick: () -> Unit = {}
) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2EA333), Color.Black)
                )
            )
    ) {

        // Logo in alto a destra
        Image(
            painter = painterResource(id = R.drawable.whitelogo),
            contentDescription = "Logo",
            modifier = Modifier
                .width(187.dp)
                .height(115.dp)
                .align(Alignment.TopEnd)
                .padding(16.dp)  // distanza dai bordi
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Sign In",
                fontSize = 64.sp,
                color = Color.White,
                fontWeight = FontWeight.Thin
            )

            Spacer(modifier = Modifier.height(32.dp))

            // EMAIL
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = false
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

            Spacer(modifier = Modifier.height(16.dp))

            // PASSWORD
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = false
                },
                label = { Text("Password") },
                isError = passwordError,
                singleLine = true,
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        passwordVisible = !passwordVisible
                    }) {
                        Icon(
                            imageVector = if (passwordVisible)
                                Icons.Filled.VisibilityOff
                            else
                                Icons.Filled.Visibility,
                            contentDescription = "Toggle password visibility",
                            tint = Color.White
                        )
                    }
                },
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

            if (passwordError) {
                Text(
                    text = "Password must be at least 6 characters",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FORGOT PASSWORD
            Text(
                text = "Forgot password?",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onForgotPasswordClick() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // SIGN IN BUTTON
            Button(
                onClick = {
                    emailError = email.isBlank() || !email.contains("@")
                    passwordError = password.length < 6

                    if (!emailError && !passwordError) {
                        onSignInClick()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CREATE ACCOUNT
            Row {
                Text(
                    text = "Don't have an account?",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Create one",
                    color = Color(0xFF2EA333),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onCreateAccountClick() }
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun SignInPreview() {
    SignIn()
}
