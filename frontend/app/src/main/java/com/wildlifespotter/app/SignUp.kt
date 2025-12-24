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
import com.wildlifespotter.app.R

@Composable
fun SignUp(
    onBackToSignIn: () -> Unit = {}
) {
    var step by remember { mutableStateOf(0) }
    var acceptedTerms by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var confirmEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var usernameError by remember { mutableStateOf(false) }
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
                    0 -> UsernameStep(username, onValueChange = { username = it }, error = usernameError)
                    1 -> EmailStep(
                        email,
                        confirmEmail,
                        onEmailChange = { email = it },
                        onConfirmEmailChange = { confirmEmail = it },
                        error = emailError
                    )
                    2 -> PasswordStep(
                        password,
                        confirmPassword,
                        acceptedTerms,
                        onPasswordChange = { password = it },
                        onConfirmPasswordChange = { confirmPassword = it },
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
                    text = if (step < 2) "Continue" else "Sign Up",
                    enabled = when (step) {
                        0 -> username.isNotBlank() && !usernameError
                        1 -> email.isNotBlank() && confirmEmail.isNotBlank() && email.contains("@") && email == confirmEmail && !emailError
                        2 -> password.isNotBlank() && confirmPassword.isNotBlank() && password == confirmPassword && acceptedTerms && !passwordError
                        else -> false
                    },
                    onClick = {
                        when (step) {
                            0 -> {
                                if (username.isBlank()) usernameError = true else step++
                            }
                            1 -> {
                                if (email.isBlank() || !email.contains("@") || email != confirmEmail) emailError = true
                                else step++
                            }
                            2 -> {
                                if (password.isBlank() || password != confirmPassword || !acceptedTerms) passwordError = true
                                else {
                                    // TODO: ASPETTARE IL BACKEND PER COMPLETARE SIGN-UP
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
    confirmEmail: String,
    onEmailChange: (String) -> Unit,
    onConfirmEmailChange: (String) -> Unit,
    error: Boolean
) {
    Title("Insert your email")
    AppTextField(placeholder = "Email", value = email, onValueChange = onEmailChange)
    AppTextField(placeholder = "Confirm email", value = confirmEmail, onValueChange = onConfirmEmailChange)
    if (error) Text("Emails must be valid and match", color = Color.Red, style = MaterialTheme.typography.bodySmall)
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
        repeat(3) { index ->
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
