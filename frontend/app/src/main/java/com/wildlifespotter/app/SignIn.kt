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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlifespotter.app.models.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SignIn(
    authViewModel: AuthViewModel = viewModel(),
    onSignInClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {},
    onCreateAccountClick: () -> Unit = {}
) {

    var passwordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var googleUsername by remember { mutableStateOf("") }
    var googleCountry by remember { mutableStateOf("") }
    var googleCountryError by remember { mutableStateOf(false) }
    var countryExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                authViewModel.loginWithGoogle(idToken)
            } else {
                authViewModel.errorMessage = "Missing Google token"
            }
        } catch (e: Exception) {
            authViewModel.errorMessage = e.message ?: "Google sign-in failed"
        }
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
                value = authViewModel.email,
                onValueChange = {
                    authViewModel.email = it
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
                value = authViewModel.password,
                onValueChange = {
                    authViewModel.password = it
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
                    emailError = authViewModel.email.isBlank() || !authViewModel.email.contains("@")
                    passwordError = authViewModel.password.length < 6

                    if (!emailError && !passwordError) {
                        onSignInClick()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // GOOGLE SIGN IN
            OutlinedButton(
                onClick = {
                    val resId = context.resources.getIdentifier(
                        "default_web_client_id",
                        "string",
                        context.packageName
                    )
                    if (resId == 0) {
                        authViewModel.errorMessage = "Missing default_web_client_id"
                        return@OutlinedButton
                    }
                    val webClientId = context.getString(resId)
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(context, gso)
                    client.signOut().addOnCompleteListener {
                        googleLauncher.launch(client.signInIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(Color(0xFFDDDDDD), Color(0xFFDDDDDD)))
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google")
            }

            if (authViewModel.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = authViewModel.errorMessage ?: "",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
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

    if (authViewModel.showGoogleProfileDialog) {
        val countries = remember {
            Locale.getISOCountries()
                .map { code -> Locale("", code).displayCountry }
                .distinct()
                .sorted()
        }
        AlertDialog(
            onDismissRequest = {
                authViewModel.showGoogleProfileDialog = false
                googleUsername = ""
                googleCountry = ""
                googleCountryError = false
                countryExpanded = false
            },
            title = { Text("Complete profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = googleUsername,
                        onValueChange = { googleUsername = it },
                        label = { Text("Username") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = countryExpanded,
                        onExpandedChange = { countryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = googleCountry,
                            onValueChange = { },
                            label = { Text("Country") },
                            readOnly = true,
                            isError = googleCountryError,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded)
                            },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = countryExpanded,
                            onDismissRequest = { countryExpanded = false }
                        ) {
                            countries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text(country) },
                                    onClick = {
                                        googleCountry = country
                                        googleCountryError = false
                                        countryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (googleCountryError) {
                        Text("Country not valid", color = Color.Red)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (authViewModel.toAlpha3Country(googleCountry) == null) {
                            googleCountryError = true
                            return@TextButton
                        }
                        authViewModel.completeGoogleProfile(googleUsername, googleCountry)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    authViewModel.showGoogleProfileDialog = false
                    googleUsername = ""
                    googleCountry = ""
                    googleCountryError = false
                    countryExpanded = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun SignInPreview() {
    SignIn()
}
