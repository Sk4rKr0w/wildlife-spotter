package com.wildlifespotter.app.ui.signup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildlifespotter.app.R
import com.wildlifespotter.app.models.AuthViewModel

@Composable
fun SignUp(
    authViewModel: AuthViewModel = viewModel(),
    onSignUpClick: () -> Unit = {},
    onBackToSignIn: () -> Unit = {}
) {
    var step by remember { mutableStateOf(0) }
    var acceptedTerms by remember { mutableStateOf(false) }

    var usernameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var countryError by remember { mutableStateOf(false) }
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier
                    .size(36.dp)
                    .clickable {
                        when (step) {
                            0 -> onBackToSignIn()
                            else -> step--
                        }
                    },
                tint = Color.White
            )

            Image(
                painter = painterResource(id = R.drawable.whitelogo),
                contentDescription = "Logo",
                modifier = Modifier
                    .width(187.dp)
                    .height(115.dp)
            )
        }




        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ----- Contenuto principale -----
            Column(modifier = Modifier.padding(top = 130.dp)) {
                when (step) {
                    0 -> UsernameStep(authViewModel.username, onValueChange = { authViewModel.username = it }, error = usernameError)
                    1 -> EmailStep(
                        authViewModel.email,
                        onEmailChange = { authViewModel.email = it },
                        error = emailError
                    )
                    2 -> CountryStep(
                        country = authViewModel.countryName,
                        onCountryChange = {
                            authViewModel.countryName = it
                            countryError = false
                        },
                        error = countryError
                    )
                    3 -> PasswordStep(
                        authViewModel.password,
                        authViewModel.confirmPassword,
                        acceptedTerms,
                        onPasswordChange = { authViewModel.password = it },
                        onConfirmPasswordChange = { authViewModel.confirmPassword = it },
                        onTermsChange = { acceptedTerms = it },
                        error = passwordError
                    )
                }
            }

            // ----- Dots + Button in basso -----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DotsIndicator(currentStep = step)
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = if (step < 3) "Continue" else "Sign Up",
                    enabled = when (step) {
                        0 -> authViewModel.username.isNotBlank() && !usernameError
                        1 -> authViewModel.email.isNotBlank() && authViewModel.email.contains("@") && !emailError
                        2 -> authViewModel.countryName.isNotBlank() && !countryError
                        3 -> authViewModel.password.isNotBlank() && authViewModel.confirmPassword.isNotBlank() && authViewModel.password == authViewModel.confirmPassword && acceptedTerms && !passwordError
                        else -> false
                    },
                    onClick = {
                        when (step) {
                            0 -> {
                                if (authViewModel.username.isBlank()) usernameError = true else step++
                            }
                            1 -> {
                                if (authViewModel.email.isBlank() || !authViewModel.email.contains("@")) emailError = true
                                else step++
                            }
                            2 -> {
                                if (authViewModel.countryName.isBlank() || authViewModel.toAlpha3Country(authViewModel.countryName) == null) {
                                    countryError = true
                                } else {
                                    countryError = false
                                    step++
                                }
                            }
                            3 -> {
                                if (authViewModel.password.isBlank() || authViewModel.password != authViewModel.confirmPassword || !acceptedTerms) passwordError = true
                                else {
                                    // TODO: ASPETTARE IL BACKEND PER COMPLETARE SIGN-UP
                                    onSignUpClick()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

/* -------------------- STEPS -------------------- */

@Composable
fun UsernameStep(username: String, onValueChange: (String) -> Unit, error: Boolean) {
    Title("Choose a username")
    Description("Choose a proper username that will be showable to other users.")
    AppTextField(placeholder = "Mario Rossi", value = username, onValueChange = onValueChange)
    if (error) Text("Username cannot be empty", color = Color.Red, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun EmailStep(
    email: String,
    onEmailChange: (String) -> Unit,
    error: Boolean
) {
    Title("Insert your email")
    AppTextField(placeholder = "Email", value = email, onValueChange = onEmailChange)
    if (error) Text("Emails must be valid and match", color = Color.Red, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun CountryStep(
    country: String,
    onCountryChange: (String) -> Unit,
    error: Boolean
) {
    Title("Your country")
    Description("Insert your country name")
    AppTextField(placeholder = "Italy", value = country, onValueChange = onCountryChange)
    if (error) Text("Country not valid", color = Color.Red, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun PasswordStep(
    password: String,
    confirmPassword: String,
    acceptedTerms: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTermsChange: (Boolean) -> Unit,
    error: Boolean
) {
    Title("Create a password")
    AppTextField(placeholder = "Password", value = password, onValueChange = onPasswordChange)
    AppTextField(placeholder = "Confirm password", value = confirmPassword, onValueChange = onConfirmPasswordChange)
    if (error) Text("Passwords must match", color = Color.Red, style = MaterialTheme.typography.bodySmall)

    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = acceptedTerms,
            onCheckedChange = onTermsChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color.White,
                uncheckedColor = Color.White,
                checkmarkColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Accept Terms of Service", color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

/* -------------------- COMPONENTS -------------------- */

@Composable
fun Title(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
}

@Composable
fun Description(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
}

@Composable
fun AppTextField(placeholder: String, value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.6f)) },
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.White,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.White,
            unfocusedIndicatorColor = Color.White.copy(alpha = 0.5f),
            focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)
        )
    )
}

@Composable
fun DotsIndicator(currentStep: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(8.dp)
                    .background(
                        color = if (index == currentStep) Color.Green else Color.White.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.5f),
            disabledContentColor = Color.Black.copy(alpha = 0.5f)
        )
    ) {
        Text(text)
    }
}

/* -------------------- PREVIEW -------------------- */

@Preview(showBackground = true)
@Composable
fun SignUpPreview() {
    SignUp()
}
